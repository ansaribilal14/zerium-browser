package com.zerium.browser;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Site-specific ad suppression for YouTube web (youtube.com, m., music., nocookie).
 *
 * In-stream ads share delivery endpoints with the video itself
 * (googlevideo.com/videoplayback), so they cannot be separated at the
 * network layer. The suppression script (app/src/main/assets/yt-block.js)
 * therefore mirrors the client-side technique used by actively maintained
 * scriptlet-based web blockers (uBlock Origin uAssets quick-fixes):
 *
 *  1. Prune-before-load ("block" behavior): adPlacements / adSlots /
 *     playerAds / adBreaks are deep-pruned from every player JSON —
 *     the initial ytInitialPlayerResponse, /youtubei/ fetch responses
 *     and /youtubei/ XHR responses — before the player parses them, so
 *     the player never schedules those ads at all.
 *  2. UI cleanup: skip buttons and overlay-close buttons are clicked the
 *     moment they appear; the anti-adblock enforcement dialog is dismissed.
 *  3. In-stream fallback: only a confirmed in-stream ad (.ad-showing /
 *     .ad-interrupting on the player root) may mute and fast-forward the
 *     shared video element, with rate/mute captured before and restored
 *     after — overlay ads and normal playback are never touched.
 *  4. CSS hiding of ad renderer elements in feeds/search/watch.
 *
 * Honest scope: this is an arms race. Effectiveness varies as YouTube
 * changes; some formats can still slip through. It is still a large
 * real-world reduction versus no suppression.
 */
public final class YouTubeFilter {

    private static volatile String cached;

    private YouTubeFilter() {}

    public static boolean matches(String host) {
        if (host == null) return false;
        return host.contains("youtube.com") || host.contains("youtube-nocookie.com");
    }

    /** Suppression script, read once from assets (single source of truth). */
    public static String script() {
        String script = cached;
        if (script != null) return script;
        synchronized (YouTubeFilter.class) {
            if (cached == null) cached = readAsset("yt-block.js");
            return cached;
        }
    }

    private static String readAsset(String name) {
        try {
            Context context = ZeriumApp.appContext();
            InputStream in = context.getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
