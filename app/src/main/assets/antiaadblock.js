/* Zerium Anti-Adblock Bypass — in-page engine.
 *
 * Runs at document start on every http(s) page. Four layers, in the spirit
 * of ShadowBlock (2019): known wall selectors, confidence-based overlay
 * removal, bait-element countermeasures, and MutationObserver recovery for
 * late-appearing walls. It never spoofs page APIs globally and never touches
 * consent dialogs, paywalls, logins or CAPTCHAs — see the veto lists below.
 *
 * Config is baked in by AntiAdblock.java before this file:
 *   window.__ZAA_CFG = { en:bool, auto:bool, fix:[hosts] };
 * Runtime updates (menu toggles): window.__ZAA.setCfg(cfg).
 */
(function () {
  'use strict';
  if (typeof window === 'undefined' || !window.location) return;
  var proto = window.location.protocol || '';
  if (proto !== 'https:' && proto !== 'http:') return;
  if (window.__zeriumAAB) return; // one engine per frame

  var cfg = (typeof window.__ZAA_CFG === 'object' && window.__ZAA_CFG) || { en: true, auto: true, fix: [] };
  var removedCount = 0;
  var MAX_REMOVALS = 20;          // surrender valve per page
  var detectedTamper = false;     // our bait was found and attacked
  var baits = [];

  function host() { try { return (window.location.hostname || '').toLowerCase(); } catch (e) { return ''; } }
  function isFixSite() {
    var h = host();
    if (!h) return false;
    var parts = h.split('.');
    for (var i = 0; i < parts.length - 1; i++) {
      if (cfg.fix.indexOf(parts.slice(i).join('.')) !== -1) return true;
    }
    return false;
  }
  function aggressive() { return isFixSite(); }
  function active() { return !!cfg.en; }

  /* ---------------- text signals ---------------- */
  /* Strong phrases are exact anti-adblock wording — each alone is nearly
   * conclusive. Weak words only ever tip a structural match, never alone
   * (a cookie banner can contain the word "ads"). */
  var STRONG = [
    'adblock detected', 'ad-blocker detected', 'ad blocker detected',
    'adblocker detected', 'ads blocker detected', 'adblock enabled',
    'adblocker enabled', 'ad blocker is enabled', 'adblock is enabled',
    'you are using an adblock', 'you are using an ad blocker',
    'you are using an adblocker', 'using extensions to block ads',
    'disable your adblock', 'disable your ad blocker',
    'disable your adblocker', 'disable adblock', 'turn off your adblock',
    'turn off your ad blocker', 'please disable your ad',
    'deactivate your adblock', 'whitelist us', 'white-list us',
    'allow us to show ads', 'support us by disabling',
    "it looks like you're using an ad blocker",
    'we noticed you are using an ad blocker',
    'we noticed that you are using an ad blocker',
    'found an adblocker', 'we have detected that you are using extensions'
  ];
  var WEAK = ['adblock', 'ad-blocker', 'ad blocker', 'adsblocker', 'ads blocker',
    'blocker', 'advertisement', 'countdown', 'please wait'];
  var VETO_TEXT = [
    'subscribe to continue', 'sign up to continue', 'sign in to continue',
    'log in to continue', 'login to continue', 'create an account to continue',
    'create a free account', 'start your free trial', 'become a member',
    'join now to read', 'premium content', 'members only', 'members-only',
    'to continue reading', 'to keep reading', 'this article is for',
    'confirm your choices', 'manage your preferences', 'we value your privacy',
    'cookie settings', 'privacy settings', 'accept all cookies',
    'reject all cookies', 'consent management'
  ];

  function textSignals(text) {
    if (!text) return { strong: 0, weak: 0 };
    var t = text.toLowerCase();
    if (t.length > 8000) t = t.slice(0, 8000); // bounded scan
    var s = 0, w = 0, i;
    for (i = 0; i < STRONG.length; i++) if (t.indexOf(STRONG[i]) !== -1) s += 3;
    for (i = 0; i < WEAK.length; i++) if (t.indexOf(WEAK[i]) !== -1) w += 1;
    for (i = 0; i < VETO_TEXT.length; i++) if (t.indexOf(VETO_TEXT[i]) !== -1) return { strong: 0, weak: 0, veto: true };
    return { strong: s, weak: w };
  }

  /* ---------------- DOM helpers ---------------- */
  function num(v, d) { var n = parseFloat(v); return isNaN(n) ? d : n; }
  function computed(el, prop) {
    try {
      var cs = window.getComputedStyle(el);
      return cs ? cs[prop] : '';
    } catch (e) { return ''; }
  }
  function coverage(el) {
    var r = el.getBoundingClientRect();
    if (!r || r.width <= 0 || r.height <= 0) return 0;
    var vw = window.innerWidth || 1, vh = window.innerHeight || 1;
    var w = Math.min(r.width, vw) / vw;
    var h = Math.min(r.height, vh) / vh;
    return w * h;
  }
  function isFixedish(el) {
    var p = computed(el, 'position');
    return p === 'fixed' || p === 'absolute';
  }
  function hasBackdrop(el) {
    var bg = computed(el, 'backgroundColor') || '';
    var m = bg.match(/rgba?\(([^)]+)\)/);
    if (m) {
      var parts = m[1].split(',').map(function (x) { return parseFloat(x); });
      if (parts.length >= 4 && parts[3] >= 0.25) return true;
    }
    var bf = (computed(el, 'backdropFilter') || '') + (computed(el, 'webkitBackdropFilter') || '');
    if (bf && bf !== 'none') return true;
    return false;
  }
  function highZ(el) {
    var z = num(computed(el, 'zIndex'), 0);
    return z >= 100;
  }
  function contentInside(el) {
    // Walls contain a card: a heading, a paragraph, a button. Real page
    // sections contain any of these structural/content containers.
    try {
      if (el.querySelector('article, main, nav, table, video, iframe, form, h1, canvas, select, textarea, svg[data-testid]')) return true;
      var links = el.querySelectorAll('a');
      return links.length > 25;
    } catch (e) { return true; }
  }
  function hiddenByCss(el) {
    var d = computed(el, 'display');
    var v = computed(el, 'visibility');
    return d === 'none' || v === 'hidden';
  }

  /* ---------------- wall scoring ---------------- */
  function scoreWall(el) {
    if (!el || el === document.documentElement || el === document.body) return null;
    var text = '';
    try { text = (el.innerText || el.textContent || ''); } catch (e) { }
    var sig = textSignals(text);
    if (sig.veto) return { el: el, veto: true, score: 0, textScore: 0, structural: 0 };
    try {
      if (el.querySelector('input[type="password"]')) return { el: el, veto: true, score: 0, textScore: 0, structural: 0 };
      if (el.querySelector('.g-recaptcha, .h-captcha, .cf-turnstile, [class*="captcha"], [id*="captcha"]')) {
        return { el: el, veto: true, score: 0, textScore: 0, structural: 0 };
      }
    } catch (e) { }
    var structural = 0;
    var cov = coverage(el);
    if (isFixedish(el)) structural += 1;
    if (cov >= 0.9) structural += 2; else if (cov >= 0.7) structural += 1;
    if (cov >= 0.55 && highZ(el)) structural += 1;
    if (hasBackdrop(el)) structural += 1;
    var role = (el.getAttribute && (el.getAttribute('role') || el.getAttribute('aria-modal'))) || '';
    if (('' + role).toLowerCase().indexOf('dialog') !== -1 || ('' + role).toLowerCase() === 'alert') structural += 1;
    if (sig.strong > 0 && detectedTamper) structural += 1; // a detector is demonstrably active
    var textScore = sig.strong + Math.min(sig.weak, 2) * 0.5;
    var selectorHit = false;
    try {
      var idc = (el.id || '') + ' ' + (el.className && el.className.baseVal === undefined ? el.className : '');
      var sel = (cfg.sel || '');
      if (sel) {
        var list = sel.split(',');
        for (var k = 0; k < list.length; k++) {
          var s = list[k].trim();
          if (!s) continue;
          if (s.charAt(0) === '#' && ('#' + el.id) === s) { selectorHit = true; break; }
          if (s.charAt(0) === '.' && idc.indexOf(s.slice(1)) !== -1) { selectorHit = true; break; }
        }
      }
    } catch (e) { }
    var score = 0;
    if (textScore >= 3) score += 4;
    else if (textScore >= 1.5) score += 2;
    else if (textScore >= 1) score += 1;
    if (structural >= 4) score += 4; else if (structural >= 3) score += 2; else if (structural >= 2) score += 1;
    if (selectorHit) score += 3;
    return { el: el, veto: false, score: score, textScore: textScore, structural: structural, coverage: cov, selectorHit: selectorHit };
  }

  function worthRemoving(sc) {
    if (!sc || sc.veto) return false;
    var agg = aggressive();
    if (!agg) {
      return sc.score >= 6 || (sc.structural >= 3 && sc.textScore >= 3) ||
        (sc.selectorHit && sc.textScore >= 1 && sc.structural >= 2);
    }
    return sc.score >= 4 || (sc.structural >= 2 && sc.textScore >= 1);
  }

  /* ---------------- removal ---------------- */
  function unlockScroll() {
    try {
      var html = document.documentElement, body = document.body;
      if (!body) return;
      var hs = computed(html, 'overflow'), bs = computed(body, 'overflow');
      if (bs === 'hidden' || hs === 'hidden') {
        var saved = window.__ZAA_SAVED;
        if (!saved) saved = window.__ZAA_SAVED = { html: html.getAttribute('style') || '', body: body.getAttribute('style') || '' };
        body.style.setProperty('overflow', 'auto', 'important');
        html.style.setProperty('overflow', 'auto', 'important');
        body.style.setProperty('position', 'static', 'important');
        body.style.setProperty('height', 'auto', 'important');
      }
      // Blur on the content behind the wall (some walls blur html/body).
      var hf = computed(html, 'filter'), bf = computed(body, 'filter');
      if ((hf && hf !== 'none') || (bf && bf !== 'none')) {
        html.style.setProperty('filter', 'none', 'important');
        body.style.setProperty('filter', 'none', 'important');
      }
    } catch (e) { }
  }

  function removeWall(el) {
    try {
      el.setAttribute('data-zerium-removed', '1');
      if (el.parentNode) el.parentNode.removeChild(el);
      else el.style.setProperty('display', 'none', 'important');
      removedCount++;
      unlockScroll();
      try { document.documentElement.setAttribute('data-zerium-aab', '1'); } catch (e) { }
      return true;
    } catch (e) { return false; }
  }

  function scan() {
    if (!active() || removedCount >= MAX_REMOVALS) return;
    if (!document.body) return;
    var candidates = [];
    try {
      var kids = document.body.children || [];
      for (var i = 0; i < kids.length; i++) candidates.push(kids[i]);
      var hinted = document.querySelectorAll(
        '[class*="adblock" i],[id*="adblock" i],[class*="adsblock" i],[id*="adsblock" i],' +
        '[class*="ad-block" i],[id*="ad-block" i],[class*="blocker" i],[id*="blocker" i]');
      for (var j = 0; j < hinted.length; j++) candidates.push(hinted[j]);
      var sel = (cfg.sel || '').trim();
      if (sel) {
        var known = document.querySelectorAll(sel);
        for (var k2 = 0; k2 < known.length; k2++) candidates.push(known[k2]);
      }
    } catch (e) { return; }
    var best = null;
    for (var c = 0; c < candidates.length; c++) {
      var el = candidates[c];
      if (!el || el === document.body || el === document.documentElement) continue;
      if (el.getAttribute && el.getAttribute('data-zerium-removed')) continue;
      if (el.__zeriumChecked) continue;      // scored already and kept
      if (hiddenByCss(el)) continue;         // invisible: not a live wall
      var sc = scoreWall(el);
      if (!sc) continue;
      if (sc.veto) { el.__zeriumChecked = true; continue; }
      if (worthRemoving(sc) && !contentInside(el)) {
        if (!best || sc.score > best.score) best = sc;
      } else {
        el.__zeriumChecked = true;
      }
    }
    if (best) removeWall(best.el);
  }

  /* ---------------- bait countermeasures ---------------- */
  /* Hidden classic bait elements. If a page script hides or removes one, a
   * detector is demonstrably running: re-assert the bait (blockadblock-style
   * counters) and raise confidence for wall removal. No page API is spoofed. */
  var BAIT_CLASSES = 'adsbox ad-banner textads banner-ads ad-unit adsbygoogle pub_300x250';
  function makeBait() {
    var d = document.createElement('div');
    d.className = BAIT_CLASSES;
    d.setAttribute('data-zerium-bait', '1');
    d.style.cssText = 'width:1px!important;height:1px!important;position:absolute!important;left:-10000px!important;top:-1000px!important;';
    return d;
  }
  function baitHealthy(b) {
    var r = b.getBoundingClientRect();
    if (!r || r.width < 1 || r.height < 1) return false;
    if (computed(b, 'display') === 'none' || computed(b, 'visibility') === 'hidden') return false;
    return true;
  }
  function installBaits() {
    try {
      if (!document.body || baits.length) return;
      for (var i = 0; i < 3; i++) {
        var b = makeBait();
        document.body.appendChild(b);
        baits.push(b);
      }
    } catch (e) { }
  }
  var baitFixes = 0;
  function checkBaits() {
    if (!active() || !baits.length || baitFixes >= 10) return;
    for (var i = 0; i < baits.length; i++) {
      var b = baits[i];
      var dead = false;
      try { dead = !document.body.contains(b) || !baitHealthy(b); } catch (e) { dead = true; }
      if (dead) {
        detectedTamper = true;
        baitFixes++;
        try {
          if (!document.body.contains(b)) document.body.appendChild(b);
          b.className = BAIT_CLASSES;
          b.style.cssText = 'width:1px!important;height:1px!important;position:absolute!important;left:-10000px!important;top:-1000px!important;';
        } catch (e) { }
      }
    }
    if (detectedTamper) scan(); // confidence boost may clear a pending wall
  }

  /* ---------------- observer ---------------- */
  var observerInstalled = false, periodicStarted = false;
  var debounce = null;
  function scheduleScan() {
    if (debounce) return;
    debounce = setTimeout(function () { debounce = null; scan(); }, 150);
  }
  function startPeriodicScan() {
    if (periodicStarted) return;
    periodicStarted = true;
    setInterval(scan, 4000);
  }
  function installObserver() {
    if (observerInstalled || typeof MutationObserver === 'undefined' || !cfg.auto) return;
    observerInstalled = true;
    var mo = new MutationObserver(function (muts) {
      if (!active()) return;
      for (var i = 0; i < muts.length; i++) {
        var m = muts[i];
        if (m.type === 'attributes') {
          // A pre-rendered hidden wall being revealed (class/style flip).
          var t = m.target;
          try {
            if (t && t.getAttribute && t.getAttribute('data-zerium-removed')) continue;
            if (t) t.__zeriumChecked = false; // re-score after class/style flips
            if (t && !hiddenByCss(t)) scheduleScan();
          } catch (e) { }
        } else {
          scheduleScan();
        }
      }
    });
    try {
      mo.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
    } catch (e) { }
  }

  /* ---------------- public hook ---------------- */
  window.__ZAA = {
    setCfg: function (c) {
      if (!c) return;
      cfg = c;
      if (cfg.auto) { installObserver(); startPeriodicScan(); }
      scan();
    },
    stats: function () {
      return { removed: removedCount, tamper: detectedTamper, aggressive: aggressive(), host: host() };
    }
  };

  /* ---------------- boot ---------------- */
  function boot() {
    if (!active()) return;
    installObserver();
    installBaits();
    scan();
    setTimeout(scan, 600);
    setTimeout(scan, 2500);
    setInterval(checkBaits, 1200);
    if (cfg.auto) startPeriodicScan();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
