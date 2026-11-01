package com.zerium.browser;

import android.content.Context;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Zerium Anti-Adblock Bypass ("Zerium Protection" > Anti-adblock bypass).
 *
 * <p>Orchestrates the in-page engine ({@code assets/antiaadblock.js}) and the
 * network layer ({@link AntiAdblockRules}): the script is registered with
 * {@code WebViewCompat.addDocumentStartJavaScript} so it runs before any page
 * script, and detector-script requests are answered with a 404 by
 * MainActivity's client. Ad blocking itself is untouched — this layer only
 * handles blocker detection and the walls it produces.</p>
 */
public final class AntiAdblock {

    private static final String ASSET = "antiaadblock.js";
    private static volatile String cachedScript;

    /** True when the URL is a known detector script (non-main-frame callers). */
    public static boolean isDetectorUrl(String url) {
        return AntiAdblockRules.isDetectorUrl(url);
    }

    /**
     * Document-start script with the current configuration baked in: master
     * switch, automatic recovery and the per-host "Fix this site" overrides.
     * Returns null when the feature is off or JavaScript is disabled (with
     * page scripts off, nothing can detect or wall anything).
     */
    public static String documentStartScript(Context c, Prefs prefs) {
        if (!prefs.antiAdblock() || !prefs.javascriptEnabled()) return null;
        String body = loadAsset(c);
        if (body == null || body.isEmpty()) return null;
        String cfg = "{\"en\":true"
                + ",\"auto\":" + prefs.antiAdblockAuto()
                + ",\"fix\":" + jsonHostArray(prefs.antiAdblockFixSites())
                + ",\"sel\":\"" + escape(AntiAdblockRules.wallSelectorList()) + "\"}";
        return "(function(){try{window.__ZAA_CFG=" + cfg + ";}catch(e){}})();" + body;
    }

    /**
     * Same as {@link #documentStartScript} but as a live config update for a
     * WebView that already has the engine (menu toggles without a reload of
     * the whole tab stack).
     */
    public static String liveConfigUpdate(Prefs prefs) {
        StringBuilder cfg = new StringBuilder();
        cfg.append("{\"en\":").append(prefs.antiAdblock())
                .append(",\"auto\":").append(prefs.antiAdblockAuto())
                .append(",\"fix\":").append(jsonHostArray(prefs.antiAdblockFixSites()))
                .append("}");
        return "(function(){try{if(window.__ZAA)window.__ZAA.setCfg(" + cfg + ");}catch(e){}})();";
    }

    private static String jsonHostArray(String raw) {
        StringBuilder sb = new StringBuilder("[");
        java.util.List<String> hosts = AntiAdblockRules.parseFixSites(raw);
        for (int i = 0; i < hosts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(hosts.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String loadAsset(Context c) {
        String cached = cachedScript;
        if (cached != null) return cached;
        synchronized (AntiAdblock.class) {
            if (cachedScript != null) return cachedScript;
            try (InputStream is = c.getAssets().open(ASSET)) {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                cachedScript = sb.toString();
            } catch (Exception e) {
                cachedScript = "";
            }
            return cachedScript;
        }
    }
}
