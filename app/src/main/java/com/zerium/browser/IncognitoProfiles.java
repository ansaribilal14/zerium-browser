package com.zerium.browser;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;

/**
 * Ephemeral engine profiles for genuine Incognito sessions (System WebView
 * edition).
 *
 * <p>Before this class existed, incognito WebViews had no profile attached
 * and silently shared the default profile's cookie jar and site storage —
 * private tabs were never actually private. Here every active Incognito
 * session maps to <em>one</em> dedicated engine profile named
 * {@code incognito_<token>} (see {@link IncognitoProfilePolicy}): all
 * private tabs of that session share it, and it shares nothing with the
 * default profile, with any Space profile, or with any other Incognito
 * session.</p>
 *
 * <p>Lifecycle contract (mirrors {@link SpaceProfiles}' strict call-order
 * contract; Spaces are not touched by this class):</p>
 * <ul>
 *   <li>{@link #acquire} MUST be the first API call on a fresh incognito
 *       WebView — before settings, cookies, clients, anything.</li>
 *   <li>When the last private tab closes, {@link #release} wipes the
 *       session's cookies and site storage and deletes the profile. The
 *       host MUST have destroyed all private WebViews first (AndroidX
 *       refuses to delete a profile with live WebView references).</li>
 *   <li>If the engine refuses deletion (lifecycle restrictions), the name
 *       stays registered in {@code Prefs.pendingIncognitoProfiles} and is
 *       retried at the next startup — before any profile is resolved and
 *       before any WebView is created.</li>
 *   <li>Private tabs are never restored after process death, so any
 *       registered profile seen at startup is an orphan by definition.</li>
 * </ul>
 *
 * <p>When the WebView provider lacks MULTI_PROFILE support there is no
 * honest way to run a private session: {@link #supported()} returns false
 * and callers must refuse to create the tab. This class never falls back
 * to the default profile and never pretends isolation happened.</p>
 */
public final class IncognitoProfiles {

    private static String activeProfileName;
    private static int activeTabCount;

    private IncognitoProfiles() {}

    /**
     * MULTI_PROFILE support (same engine requirement as SpaceProfiles, so
     * a device either gets genuine isolation everywhere or nowhere).
     */
    public static boolean supported() {
        return SpaceProfiles.supported();
    }

    // ---------- Policy (pure logic lives in IncognitoProfilePolicy) ----------

    /** Engine profile name for an Incognito session token. */
    public static String sessionProfileName(long sessionToken) {
        return IncognitoProfilePolicy.sessionProfileName(sessionToken);
    }

    /** True for ephemeral Incognito profile names (never a Space profile). */
    public static boolean isIncognitoProfileName(String name) {
        return IncognitoProfilePolicy.isIncognitoProfileName(name);
    }

    /** Adds a profile name to the persisted CSV registry. */
    public static String registerInCsv(String csv, String name) {
        return IncognitoProfilePolicy.registerInCsv(csv, name);
    }

    /** Removes a profile name from the persisted CSV registry. */
    public static String unregisterFromCsv(String csv, String name) {
        return IncognitoProfilePolicy.unregisterFromCsv(csv, name);
    }

    /**
     * Deletes every registered Incognito profile. Called at startup before
     * anything resolves a profile or creates a WebView, so the engine has
     * no live references. Returns the CSV of names that still refused —
     * the caller stores it for the next attempt.
     */
    public static String retryPendingDeletions(String csv) {
        if (csv == null || csv.isEmpty()) return "";
        StringBuilder remaining = new StringBuilder();
        for (String name : csv.split(",")) {
            String n = name.trim();
            if (n.isEmpty()) continue;
            boolean ok;
            try {
                ProfileStore.getInstance().deleteProfile(n);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            if (!ok) {
                if (remaining.length() > 0) remaining.append(',');
                remaining.append(n);
            }
        }
        return remaining.toString();
    }

    // ---------- Session lifecycle (UI thread only) ----------

    /** True while at least one private tab of the current session is alive. */
    public static synchronized boolean hasActiveSession() {
        return activeProfileName != null && activeTabCount > 0;
    }

    /** Profile name of the running Incognito session, or null. */
    public static synchronized String activeSessionProfileName() {
        return activeProfileName;
    }

    /**
     * Joins (or starts) the current Incognito session and binds the fresh
     * WebView to its profile. MUST be the first call on that WebView.
     * Returns the profile name, or null when multi-profile is unsupported —
     * callers must then refuse to open the private tab.
     */
    public static synchronized String acquire(WebView webView, long sessionToken) {
        if (!supported() || webView == null) return null;
        if (activeProfileName == null) {
            activeProfileName = IncognitoProfilePolicy.sessionProfileName(sessionToken);
            activeTabCount = 0;
        }
        boolean bound = false;
        try {
            WebViewCompat.setProfile(webView, activeProfileName);
            bound = true;
        } catch (Throwable ignored) {
            // Provider race: the WebView keeps the default profile. The
            // caller must refuse this tab — running it on the default jar
            // would leak private traffic into a persistent profile.
        }
        if (!bound) return null;
        activeTabCount++;
        return activeProfileName;
    }

    /**
     * One private tab of the session closed. When the last one is gone:
     * cookies + site storage wiped, profile deleted, registry updated.
     * Must run after all private WebViews were destroyed. Returns the CSV
     * to persist (empty when the registry is clean).
     */
    public static synchronized String release(String registeredCsv) {
        if (activeTabCount > 0) activeTabCount--;
        if (activeTabCount > 0 || activeProfileName == null) {
            return registeredCsv;
        }
        String name = activeProfileName;
        activeProfileName = null;
        activeTabCount = 0;
        try {
            androidx.webkit.Profile p = ProfileStore.getInstance().getProfile(name);
            if (p != null) {
                try {
                    CookieManager cm = p.getCookieManager();
                    cm.removeAllCookies(null);
                    cm.removeSessionCookies(null);
                    cm.flush();
                } catch (Throwable ignored) {}
                try {
                    p.getWebStorage().deleteAllData();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        boolean deleted = false;
        try {
            ProfileStore.getInstance().deleteProfile(name);
            deleted = true;
        } catch (Throwable ignored) {
            // Engine refused (lifecycle restriction) — retried next launch.
        }
        return deleted ? IncognitoProfilePolicy.unregisterFromCsv(registeredCsv, name)
                : IncognitoProfilePolicy.registerInCsv(registeredCsv, name);
    }

    /**
     * Cookie manager of the running Incognito session — a dedicated jar
     * that shares nothing with the default profile or any Space. Null when
     * no session is active; callers must treat that as a hard error.
     */
    public static synchronized CookieManager cookiesFor() {
        if (activeProfileName == null) return null;
        try {
            androidx.webkit.Profile p =
                    ProfileStore.getInstance().getProfile(activeProfileName);
            if (p != null) return p.getCookieManager();
        } catch (Throwable ignored) {}
        return null;
    }

    /** WebStorage of the running Incognito session (null when inactive). */
    public static synchronized WebStorage storageFor() {
        if (activeProfileName == null) return null;
        try {
            androidx.webkit.Profile p =
                    ProfileStore.getInstance().getProfile(activeProfileName);
            if (p != null) return p.getWebStorage();
        } catch (Throwable ignored) {}
        return null;
    }
}
