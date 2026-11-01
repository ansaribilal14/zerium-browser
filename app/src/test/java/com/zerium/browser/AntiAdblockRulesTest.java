package com.zerium.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Zerium Anti-Adblock rules: detector URL matching and fix-site parsing. */
public class AntiAdblockRulesTest {

    @Test
    public void knownDetectorUrlsMatch() {
        // CHP Ads Block Detector (medeberiyal.com and friends)
        assertTrue(AntiAdblockRules.isDetectorUrl(
                "https://medeberiyal.com/wp-content/plugins/chp-ads-block-detector/assets/js/main.js"));
        // Other detector families
        assertTrue(AntiAdblockRules.isDetectorUrl("https://cdn.example.com/blockadblock.js"));
        assertTrue(AntiAdblockRules.isDetectorUrl("https://getadmiral.com/sources/v3.js"));
        assertTrue(AntiAdblockRules.isDetectorUrl("https://foo.xyz/anti-adblock/killer.js"));
        assertTrue(AntiAdblockRules.isDetectorUrl("HTTPS://EXAMPLE.COM/KILLADBLOCK/SCRIPT.JS"));
    }

    @Test
    public void normalUrlsDoNotMatch() {
        assertFalse(AntiAdblockRules.isDetectorUrl("https://example.com/adblock-myths-explained"));
        assertFalse(AntiAdblockRules.isDetectorUrl("https://example.com/article/blockers-overview"));
        assertFalse(AntiAdblockRules.isDetectorUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertFalse(AntiAdblockRules.isDetectorUrl(null));
        assertFalse(AntiAdblockRules.isDetectorUrl(""));
    }

    @Test
    public void fixSiteParsingAndParents() {
        List<String> list = AntiAdblockRules.parseFixSites("medeberiyal.com\n\nwww.other.org, third.net");
        assertEquals(3, list.size());
        assertTrue(AntiAdblockRules.isFixSite(list, "medeberiyal.com"));
        assertTrue(AntiAdblockRules.isFixSite(list, "www.medeberiyal.com"));
        assertTrue(AntiAdblockRules.isFixSite(list, "deep.sub.www.other.org"));
        assertFalse(AntiAdblockRules.isFixSite(list, "notmedeberiyal.com"));
        assertFalse(AntiAdblockRules.isFixSite(list, null));
        assertFalse(AntiAdblockRules.isFixSite(Collections.<String>emptyList(), "medeberiyal.com"));
    }

    @Test
    public void wallSelectorListIsWellFormed() {
        String sel = AntiAdblockRules.wallSelectorList();
        assertTrue(sel.contains("#detectadblock"));
        assertFalse(sel.startsWith(","));
        assertFalse(sel.endsWith(","));
        for (String part : sel.split(",")) {
            assertTrue(part.startsWith("#") || part.startsWith("."));
        }
    }
}
