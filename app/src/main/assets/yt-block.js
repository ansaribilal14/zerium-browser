/* Zerium Browser — YouTube ad suppression (v1.2.0)
 *
 * Injected at document start on youtube.com / youtube-nocookie.com /
 * music.youtube.com via WebViewCompat.addDocumentStartJavaScript, before
 * the YouTube player initializes.
 *
 * Technique mirrors the actively maintained uBlock Origin YouTube
 * scriptlets (uAssets quick-fixes): ad structures are pruned from player
 * JSON before the player parses them, so ads are never scheduled — the
 * "block" behavior, where the ad does not show up at all. Whatever still
 * renders is handled by instant skip clicks, overlay closing, CSS hiding
 * and a strictly-scoped in-stream fallback.
 *
 * Evaluated and deliberately rejected from the reference implementations:
 *   - uBO's premium-client masquerade (user-agent rewriting plus global
 *     Promise/Map/Array prototype hooking): fragile across releases and
 *     misrepresents the client to Google's servers.
 *   - Network-blocking of ad video segments: in-stream ads share delivery
 *     endpoints with the video itself; failing those requests can stall
 *     playback instead of skipping cleanly.
 */
(function () {
  'use strict';
  if (window.__zeriumYT) return;
  window.__zeriumYT = true;

  /* ------------------------------------------------------------------ *
   * 1. Response pruning — the "block" layer
   * ------------------------------------------------------------------ */
  var AD_KEYS = {
    adPlacements: 1,
    adSlots: 1,
    playerAds: 1,
    adBreaks: 1,
    adBreakHeartbeatParams: 1,
    adPlacementsForThirdParty: 1
  };
  var PLAYER_API = /\/youtubei\/v1\/(player|next|video_details|get_midroll_info|viewer|playlist)\?/;

  function deepPrune(node, depth) {
    if (!node || typeof node !== 'object' || depth > 60) return node;
    if (Array.isArray(node)) {
      for (var i = 0; i < node.length; i++) deepPrune(node[i], depth + 1);
      return node;
    }
    for (var key in node) {
      if (!Object.prototype.hasOwnProperty.call(node, key)) continue;
      if (AD_KEYS[key]) {
        try { delete node[key]; } catch (e) { node[key] = undefined; }
        continue;
      }
      deepPrune(node[key], depth + 1);
    }
    return node;
  }

  function prunedResponse(res) {
    return res.clone().json().then(function (data) {
      deepPrune(data, 0);
      var headers = new Headers();
      res.headers.forEach(function (value, name) {
        if (name === 'content-encoding' || name === 'content-length') return;
        try { headers.set(name, value); } catch (e) {}
      });
      return new Response(JSON.stringify(data), {
        status: res.status,
        statusText: res.statusText,
        headers: headers
      });
    }).catch(function () { return res; });
  }

  /* fetch hook */
  try {
    var nativeFetch = window.fetch;
    if (typeof nativeFetch === 'function') {
      window.fetch = function (input) {
        var url = '';
        try {
          url = input && input.url ? String(input.url) : (typeof input === 'string' ? input : '');
        } catch (e) {}
        var promise = nativeFetch.apply(this, arguments);
        return PLAYER_API.test(url) ? promise.then(prunedResponse) : promise;
      };
    }
  } catch (e) {}

  /* XHR hook */
  try {
    var nativeOpen = XMLHttpRequest.prototype.open;
    var nativeSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      try { this.__zeriumUrl = String(url || ''); } catch (e) {}
      return nativeOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var xhr = this;
      try {
        if (PLAYER_API.test(xhr.__zeriumUrl || '')) {
          xhr.addEventListener('readystatechange', function () {
            try {
              if (xhr.readyState !== 4) return;
              if (xhr.responseType === 'json' && xhr.response) {
                deepPrune(xhr.response, 0);
                return;
              }
              var text = xhr.responseText;
              if (typeof text === 'string' && text.length > 1 && text.charAt(0) === '{') {
                var data = JSON.parse(text);
                deepPrune(data, 0);
                var out = JSON.stringify(data);
                Object.defineProperty(xhr, 'responseText', { value: out, configurable: true });
                Object.defineProperty(xhr, 'response', { value: out, configurable: true });
              }
            } catch (e) {}
          });
        }
      } catch (e) {}
      return nativeSend.apply(this, arguments);
    };
  } catch (e) {}

  /* Initial player response: prune before the page ever reads it */
  try {
    var playerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return playerResponse; },
      set: function (value) { playerResponse = deepPrune(value, 0); }
    });
  } catch (e) {}

  /* ------------------------------------------------------------------ *
   * 2. UI layer — instant skip, overlay close, enforcement dismiss
   * ------------------------------------------------------------------ */
  var SKIP = '.ytp-ad-skip-button-modern,.ytp-ad-skip-button,.ytp-skip-ad-button,'
           + '.ytp-ad-skip-button-slot button';

  function visible(el) { return !!el && el.offsetParent !== null; }
  function click(el) { try { el.click(); return true; } catch (e) { return false; } }

  function sweep() {
    var skip = document.querySelector(SKIP);
    if (visible(skip) && !skip.disabled) click(skip);

    var overlayClose = document.querySelector('.ytp-ad-overlay-close-button');
    if (visible(overlayClose)) click(overlayClose);

    var enforcement = document.querySelector('ytd-enforcement-message-view-model');
    if (enforcement && !enforcement.__zeriumHandled) {
      enforcement.__zeriumHandled = true;
      var dialog = enforcement.closest('tp-yt-paper-dialog');
      if (dialog) dialog.style.display = 'none';
      document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function (b) {
        b.style.display = 'none';
      });
      var confirm = enforcement.querySelector(
        'button.yt-spec-button-shape-next--filled,button.yt-spec-button-shape-next--tonal,button');
      if (confirm) click(confirm);
    }
  }

  /* ------------------------------------------------------------------ *
   * 3. In-stream fallback state machine
   *
   * Only a CONFIRMED in-stream ad (.ad-showing / .ad-interrupting on the
   * player root) may touch the shared <video> element. Overlay ads never
   * trigger this. Rate and mute are captured BEFORE any change and
   * restored exactly when the ad state ends. (v1.1.0 matched the always
   * -present .ytp-ad-module and reset the rate to a hardcoded 1, which
   * fast-forwarded the main video — fixed here.)
   * ------------------------------------------------------------------ */
  var saved = null;

  function adStateMachine() {
    var player = document.querySelector('.html5-video-player');
    if (!player) return;
    var video = player.querySelector('video.html5-main-video') || player.querySelector('video');
    if (!video) return;

    var inStreamAd = player.classList.contains('ad-showing')
                  || player.classList.contains('ad-interrupting');

    if (inStreamAd && !saved) {
      saved = { rate: video.playbackRate || 1, muted: !!video.muted };
      try { video.muted = true; video.playbackRate = 16; } catch (e) {}
    } else if (!inStreamAd && saved) {
      try { video.playbackRate = saved.rate; video.muted = saved.muted; } catch (e) {}
      saved = null;
    }
  }

  /* ------------------------------------------------------------------ *
   * 4. CSS hiding — ad renderers in feeds/search/watch + leftovers
   * ------------------------------------------------------------------ */
  var CSS = [
    '#masthead-ad',
    'ytd-ad-slot-renderer',
    'ytd-in-feed-ad-layout-renderer',
    'ytd-display-ad-renderer',
    'ytd-compact-promoted-video-renderer',
    'ytd-promoted-sparkles-web-renderer',
    'ytd-promoted-sparkles-text-search-renderer',
    'ytd-video-masthead-ad-v3-renderer',
    'ytd-video-masthead-ad-advertiser-info-renderer',
    'ytd-search-pyv-renderer',
    'ytd-promoted-video-renderer',
    'ytd-companion-slot-renderer',
    'ytd-player-legacy-desktop-watch-ads-renderer',
    '#player-ads',
    'ytd-mealbar-promo-renderer',
    'ytm-promoted-video-renderer',
    'ytd-enforcement-message-view-model',
    '.ytp-ad-player-overlay',
    '.ytp-ad-message-container',
    '.ytp-ad-overlay-container',
    '.ytp-ad-text-overlay',
    '.ytp-ad-image-overlay',
    '.ytp-ad-action-interstitial',
    '.ytp-paid-content-overlay'
  ].join(',') + '{display:none!important}';

  function injectCss() {
    try {
      if (!document.getElementById('zerium-yt-css')) {
        var style = document.createElement('style');
        style.id = 'zerium-yt-css';
        style.textContent = CSS;
        (document.head || document.documentElement).appendChild(style);
      }
    } catch (e) {}
  }

  /* ------------------------------------------------------------------ *
   * 5. Loop — MutationObserver for instant reaction + 500 ms safety net
   * ------------------------------------------------------------------ */
  function tick() { injectCss(); sweep(); adStateMachine(); }

  try {
    var queued = false;
    var observer = new MutationObserver(function () {
      if (queued) return;
      queued = true;
      requestAnimationFrame(function () { queued = false; tick(); });
    });
    var attach = function () {
      if (document.body) observer.observe(document.body, { childList: true, subtree: true });
      else setTimeout(attach, 400);
    };
    attach();
  } catch (e) {}

  setInterval(tick, 500);
  tick();
})();
