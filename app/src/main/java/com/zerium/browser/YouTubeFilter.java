package com.zerium.browser;

/**
 * Site-specific ad suppression for YouTube web (youtube.com, m., music., nocookie).
 * This mirrors the client-side approach used by scriptlet-based web blockers
 * (the technique behind uBlock Origin / Brave's web ad filtering), because
 * in-stream ads share delivery endpoints with the video itself and cannot be
 * separated at the network layer.
 *
 * Three mechanisms, all client-side JS:
 *  1. Player API pruning: adPlacements / adSlots / playerAds / adBreaks are
 *     deleted from player JSON before the player consumes it (fetch hook,
 *     XHR hook, and a setter trap on ytInitialPlayerResponse).
 *  2. Auto-skip: detects the ad UI, mutes and fast-forwards the ad, clicks
 *     skip and overlay-close buttons, restores playback afterwards.
 *  3. CSS hiding of ad overlay/message containers.
 *
 * Honest scope: this is an arms race. Effectiveness varies as YouTube changes;
 * some formats can still slip through. It is still a large real-world
 * reduction versus no suppression.
 */
public final class YouTubeFilter {

    private YouTubeFilter() {}

    public static boolean matches(String host) {
        if (host == null) return false;
        return host.contains("youtube.com") || host.contains("youtube-nocookie.com");
    }

    public static String script() {
        return "(function(){"
                + "if(window.__zeriumYT)return;window.__zeriumYT=true;"
                + "var KEYS=['adPlacements','adSlots','playerAds','adBreaks','adBreakHeartbeatParams'];"
                + "function prune(o){if(!o||typeof o!=='object')return o;"
                + "for(var i=0;i<KEYS.length;i++){try{delete o[KEYS[i]];}catch(e){}}"
                + "if(o.response&&typeof o.response==='object'){try{prune(o.response);}catch(e){}}"
                + "return o;}"
                // Setter trap for the initial player response, whenever it is assigned
                + "try{var y=window.ytInitialPlayerResponse;"
                + "if(y){prune(y);}"
                + "Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,"
                + "get:function(){return y;},set:function(v){try{prune(v);}catch(e){}y=v;}});}catch(e){}"
                // fetch hook for /youtubei/ API calls
                + "if(window.fetch){var of=window.fetch;"
                + "window.fetch=function(){var a=arguments;var p=of.apply(this,a);"
                + "try{var u=(a[0]&&a[0].url)||a[0];"
                + "if(typeof u==='string'&&u.indexOf('/youtubei/')>=0){"
                + "return p.then(function(r){try{var oj=r.json.bind(r);"
                + "r.json=function(){return oj().then(function(j){try{return prune(j);}catch(e){return j;}});};}catch(e){}return r;});}}"
                + "catch(e){}return p;};}"
                // XHR hook for /youtubei/ API calls
                + "try{var oo=XMLHttpRequest.prototype.open;"
                + "XMLHttpRequest.prototype.open=function(m,u){this.__zu=u;return oo.apply(this,arguments);};"
                + "var os=XMLHttpRequest.prototype.send;"
                + "XMLHttpRequest.prototype.send=function(){var x=this;"
                + "try{if(typeof x.__zu==='string'&&x.__zu.indexOf('/youtubei/')>=0){"
                + "x.addEventListener('load',function(){try{"
                + "var t=x.responseText;if(t&&t.charAt(0)==='{'){"
                + "var j=JSON.parse(t);prune(j);var nt=JSON.stringify(j);"
                + "try{Object.defineProperty(x,'responseText',{value:nt,configurable:true});}catch(e){}"
                + "try{Object.defineProperty(x,'response',{value:nt,configurable:true});}catch(e){}"
                + "}}catch(e){}});}}catch(e){}return os.apply(this,arguments);};}catch(e){}"
                // CSS hiding of ad containers
                + "try{var st=document.createElement('style');"
                + "st.textContent='.ytp-ad-overlay-container{display:none!important}"
                + ".ytp-ad-message-container{display:none!important}"
                + ".ytp-paid-content-overlay{display:none!important}';"
                + "(document.head||document.documentElement).appendChild(st);}catch(e){}"
                // Auto-skip watchdog
                + "setInterval(function(){try{"
                + "var v=document.querySelector('video.html5-main-video')||document.querySelector('video');"
                + "var ad=document.querySelector('.ytp-ad-player-overlay,.ad-showing,.ytp-ad-module');"
                + "if(v){if(ad){v.muted=true;try{v.playbackRate=16;}catch(e){}v.__zuAd=true;}"
                + "else if(v.__zuAd){v.__zuAd=false;v.muted=false;try{v.playbackRate=1;}catch(e){}}}"
                + "var sk=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button-modern,"
                + ".ytp-ad-skip-button,.ytp-ad-skip-button-slot button');"
                + "if(sk){sk.click();}"
                + "var ov=document.querySelector('.ytp-ad-overlay-close-button');"
                + "if(ov){ov.click();}"
                + "}catch(e){}},250);"
                + "})();";
    }
}
