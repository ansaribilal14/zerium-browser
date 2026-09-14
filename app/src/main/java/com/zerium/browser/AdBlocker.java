package com.zerium.browser;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Network-level ad and tracker blocker.
 * Combines a hosts-derived domain blocklist (StevenBlack, MIT) with a small
 * set of URL pattern rules. Domain matching walks up parent domains so an
 * entry for "ads.example.com" also blocks its subdomains, and an entry for
 * "example.com" blocks all of its known-bad tree (hosts-file semantics).
 */
public class AdBlocker {

    private static final String UPDATE_FILE = "hosts_updated.txt";
    private static final String ASSET_FILE = "blocklists/hosts.txt";

    /**
     * URL-pattern rules layered on top of the hosts list. Hosts entries block
     * by domain; these catch ad/tracker endpoints that share domains with
     * content or are path-based. Patterns are plain substring matches on the
     * request URL — deliberately conservative to avoid breaking page function
     * (availability over purity), so no generic short tokens like "/ad".
     */
    private static final String[] URL_PATTERNS = {
            // --- Google ads / measurement ---
            "/pagead/", "/pagead2/", "/adsbygoogle", "googlesyndication.com",
            "google-analytics.com", "analytics.google.com", "doubleclick.net",
            "adservice.google.", "googleadservices.com", "googletagservices.com",
            "imasdk.googleapis.com",                       // Google IMA video-ad SDK
            "googletagmanager.com/gtag/js",                // GA4 loader
            "googletagmanager.com/gtm.js",                 // GTM container (consent-pixel carrier)
            "google.com/adsense", "/api/stats/", "play.google.com/log",
            // --- Social & analytics pixels ---
            "connect.facebook.net", "facebook.com/tr?", "analytics.tiktok.com",
            "ads-twitter.com", "ct.pinterest.com", "snap.licdn.com", "px.ads.linkedin.com",
            "bat.bing.com", "clarity.ms", "hotjar.com", "hotjar.io", "fullstory.com",
            "mouseflow.com", "cdn.segment.com", "api.amplitude.com", "cdn.mxpnl.com",
            "heapanalytics.com", "mc.yandex.ru", "an.yandex.ru", "quantserve.com",
            "scorecardresearch.com", "moatads.com", "moatpixel", "adsafeprotected.com",
            // --- Header bidding / exchange endpoints ---
            "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.", "adnxs.com",
            "smartadserver.com", "media.net", "sharethrough.com", "33across.com",
            "teads.tv", "sovrn.com", "casalemedia.com", "indexww.com", "bidswitch.net",
            "adform.net", "improvedigital.com", "districtm.io", "amazon-adsystem.com",
            // --- Native / content-ad and popup networks ---
            "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "zergnet.com",
            "popads.net", "popcash.net", "propellerads", "adsterra", "adcash",
            "hilltopads", "clickadu", "exoclick", "juicyads", "trafficjunky",
            "adcolony", "applovin.com", "unityads",
            // --- YouTube internals & generic ad paths ---
            "get_midroll_info", "/ptracking?", "ad.click", "/adserver/",
            "/ads.js", "/pagead2", "/popunder", "/prebid"
    };

    private volatile boolean ready = false;
    private volatile long loadedAt = 0L;
    private final Set<String> blockedDomains = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> allowDomains = Collections.synchronizedSet(new HashSet<String>());

    public boolean isReady() { return ready; }

    /** When the current blocklist was loaded (ms epoch), for reload comparisons. */
    public long loadedAt() { return loadedAt; }

    /** Loads the blocklist off the main thread. Prefers an updated list downloaded by the user. */
    public void init(Context context, Prefs prefs) {
        Thread t = new Thread(() -> load(context, prefs), "zerium-blocklist-load");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /**
     * Re-reads the blocklist from disk (after a filter-list download). Safe to
     * call repeatedly; the load happens on a background thread and swaps the
     * domain set atomically, so in-flight requests keep using the old set.
     */
    public void reload(Context context, Prefs prefs) {
        Thread t = new Thread(() -> load(context, prefs), "zerium-blocklist-reload");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private void load(Context context, Prefs prefs) {
        Set<String> set = new HashSet<>();
        File updated = new File(context.getFilesDir(), UPDATE_FILE);
        int count = 0;
        if (updated.exists() && updated.length() > 0) {
            count = readDomains(updated, set);
        }
        if (count < 1000) {
            count = readAssetDomains(context, set);
        }
        synchronized (blockedDomains) {
            blockedDomains.clear();
            blockedDomains.addAll(set);
        }
        rebuildAllowlist(prefs.allowlist());
        ready = count > 0;
        loadedAt = System.currentTimeMillis();
    }

    private int readAssetDomains(Context c, Set<String> out) {
        try (InputStream is = c.getAssets().open(ASSET_FILE)) {
            return parse(is, out);
        } catch (Exception e) {
            return 0;
        }
    }

    private int readDomains(File f, Set<String> out) {
        try (InputStream is = new FileInputStream(f)) {
            return parse(is, out);
        } catch (Exception e) {
            return 0;
        }
    }

    private int parse(InputStream is, Set<String> out) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        int n = 0;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
            String domain;
            if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                String[] parts = line.split("\\s+");
                domain = parts.length > 1 ? parts[1] : null;
            } else {
                String[] parts = line.split("\\s+");
                domain = parts[0];
            }
            if (domain == null) continue;
            domain = domain.toLowerCase();
            if (domain.equals("localhost") || domain.equals("localhost.localdomain")
                    || domain.equals("local") || domain.equals("ip6-localhost")
                    || domain.equals("broadcasthost")) continue;
            if (domain.indexOf('.') > 0) {
                out.add(domain);
                n++;
            }
        }
        return n;
    }

    public void rebuildAllowlist(String raw) {
        Set<String> set = new HashSet<>();
        if (raw != null) {
            for (String s : raw.split("[\\n,]")) {
                s = s.trim().toLowerCase();
                if (!s.isEmpty()) set.add(s);
            }
        }
        synchronized (allowDomains) {
            allowDomains.clear();
            allowDomains.addAll(set);
        }
    }

    /** True if this request should be blocked. Safe to call from any thread. */
    public boolean shouldBlock(String requestUrl, String pageUrl) {
        if (!ready) return false;
        if (requestUrl == null) return false;
        String host = Utils.hostOf(requestUrl);
        if (host == null || host.isEmpty()) return false;
        synchronized (allowDomains) {
            if (allowDomains.contains(host)) return false;
        }
        // Walk from the exact host up to the TLD, matching blocklist entries.
        String cur = host;
        while (cur != null) {
            synchronized (blockedDomains) {
                if (blockedDomains.contains(cur)) return true;
            }
            int dot = cur.indexOf('.');
            if (dot < 0) break;
            cur = cur.substring(dot + 1);
        }
        for (String p : URL_PATTERNS) {
            if (requestUrl.contains(p)) return true;
        }
        return false;
    }
}
