package com.zerium.browser;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules for the Zerium Anti-Adblock Bypass layer ("Zerium Protection").
 *
 * <p>Two rule families, kept deliberately separate from the EasyList-derived
 * host list in {@link AdBlocker}: this file only knows about anti-adblock
 * machinery itself — the scripts sites run to detect blockers and the walls
 * they display. Nothing here blocks ads; disabling ad blocking does not
 * change what this layer matches.</p>
 *
 * <p>Pure JVM (no Android imports) so the matching logic is unit-testable.</p>
 */
public final class AntiAdblockRules {

    private AntiAdblockRules() {}

    /**
     * URL substrings (lowercase) of known anti-adblock detection scripts.
     * Curated by hand: each entry names a specific detector family, never a
     * generic token like "adblock" — an article path such as
     * /adblock-myths-explained must not match. Checked against non-main-frame
     * requests only (see AntiAdblock), so a false match costs one subresource,
     * never a navigation.
     */
    public static final String[] DETECTOR_URL_PATTERNS = {
            // WordPress "CHP Ads Block Detector" (Code Help Pro) — medeberiyal.com
            // and many similar sites; the detector itself is inlined by the
            // plugin, this pattern catches its asset/CDN requests (pro CDN
            // included). The inline detector is handled by the injected script.
            "chp-ads-block-detector", "chpadblock.com",
            // Standalone detector scripts / widget bundles
            "blockadblock", "killadblock", "detectadblock", "adblock-detector",
            "adblockdetector", "ad-block-detector", "ads-block-detector",
            "adblock-killer", "adblockkiller", "adblock-detect",
            // Admiral (getadmiral) detection CDN
            "getadmiral.com", "admiralscripts.com", "fuhanami",
            // Ad recovery / anti-adblock killer families
            "ads-recover", "adsrecover", "anti-adblock", "antiadblock"
    };

    /**
     * CSS selectors of wall containers whose names are stable across sites
     * (published widgets). Short by design: anything else is handled by the
     * structural + text scoring in the injected script, which is how walls
     * with randomized class names (like CHP) are matched.
     *
     * <p>Attribute selectors target artifacts the CHP Ads Block Detector
     * family cannot randomize: the branding links and the plugin asset paths
     * rendered INSIDE the wall (the injected script walks up from these hits
     * to the wall container itself).</p>
     */
    public static final String[] WALL_SELECTORS = {
            "#detectadblock", "#adblock-detected", "#blockadblock",
            "#killadblock", "#adblock-detector", "#ad-blocker-detected",
            ".adblock-detected", ".adsblocker-detected",
            // CHP Ads Block Detector (free + pro): stable branding/asset hooks
            "[href*=\"chpadblock.com\"]", "[href*=\"toolkitspro.com\"]",
            "[src*=\"chp-ads-block-detector\"]", "#chp_ads_blocker-modal",
            ".chpadbpro_wrap_title"
    };

    /** True when the URL looks like a known detector script. Case-insensitive. */
    public static boolean isDetectorUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        for (String p : DETECTOR_URL_PATTERNS) {
            if (u.contains(p)) return true;
        }
        return false;
    }

    /** Copy of the wall selectors for the injected script (joined, comma separated). */
    public static String wallSelectorList() {
        return String.join(",", WALL_SELECTORS);
    }

    /**
     * Aggressive per-domain overrides ("Fix this site"): hosts where the user
     * asked for the lower removal threshold. Accepts the newline/comma
     * separated storage format used by the other per-site lists.
     */
    public static List<String> parseFixSites(String raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String s : raw.split("[\\n,]")) {
                s = s.trim().toLowerCase();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    /** True when host (or its registrable parents) is in the fix list. */
    public static boolean isFixSite(List<String> fixSites, String host) {
        if (host == null || fixSites == null || fixSites.isEmpty()) return false;
        String h = host.toLowerCase();
        String cur = h;
        while (cur != null) {
            if (fixSites.contains(cur)) return true;
            int dot = cur.indexOf('.');
            if (dot < 0) break;
            cur = cur.substring(dot + 1);
        }
        return false;
    }
}
