package com.zerium.browser;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.List;

/**
 * Engine bridge for isolated Spaces on System WebView.
 *
 * <p>Isolation uses the androidx.webkit multi-profile API: every isolated
 * space maps to one named engine profile with its own cookie jar, site
 * storage and service workers. Non-isolated spaces (including the default
 * space) share the default profile — they organize tabs without splitting
 * logins.</p>
 *
 * <p>Multi-profile needs a recent WebView provider; support is checked at
 * runtime. When it is missing, isolation is disabled up front (the create
 * dialog blocks the toggle) instead of pretending cookies are separated.</p>
 *
 * <p>Every MULTI_PROFILE touchpoint lives here so the rest of the app stays
 * engine-agnostic and the strict call-order contract
 * ({@code setProfile} must be the first WebView API call) is honored in one
 * place.</p>
 */
public final class SpaceProfiles {

    private static volatile Boolean supported;

    private SpaceProfiles() {}

    /** MULTI_PROFILE requires a recent WebView provider — checked once. */
    public static boolean supported() {
        Boolean s = supported;
        if (s == null) {
            boolean v;
            try {
                v = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
            } catch (Throwable t) {
                v = false;
            }
            supported = v;
            s = v;
        }
        return s;
    }

    /** Engine profile name for a space id. */
    public static String profileName(String spaceId) {
        return "space_" + spaceId;
    }

    /** True when this space really gets its own cookie jar and storage. */
    public static boolean isIsolated(Spaces.Space space) {
        return space != null && space.isolated && supported();
    }

    /**
     * Assigns the space's engine profile to a freshly created WebView.
     * MUST be the first call on that WebView (only view attach may happen
     * earlier). No-op for non-isolated spaces, which keep the default
     * profile, and no-op when the engine lacks multi-profile support.
     */
    public static void attachProfile(WebView webView, String spaceId) {
        if (!supported() || webView == null || spaceId == null
                || spaceId.equals(Spaces.DEFAULT_ID)) {
            return;
        }
        try {
            WebViewCompat.setProfile(webView, profileName(spaceId));
        } catch (Throwable ignored) {
            // Old provider race: the WebView keeps the default profile, so
            // it stays usable (inside the space it just shares logins).
        }
    }

    /**
     * Cookie manager for a tab's space. Isolated spaces return their own
     * profile's manager; everything else returns the global (default
     * profile) manager. Call after {@link #attachProfile}.
     */
    public static CookieManager cookiesFor(String spaceId) {
        if (supported() && spaceId != null && !spaceId.equals(Spaces.DEFAULT_ID)) {
            try {
                androidx.webkit.Profile p =
                        ProfileStore.getInstance().getProfile(profileName(spaceId));
                if (p != null) return p.getCookieManager();
            } catch (Throwable ignored) {}
        }
        return CookieManager.getInstance();
    }

    /** WebStorage for a space; isolated spaces get their own instance. */
    public static WebStorage storageFor(String spaceId) {
        if (supported() && spaceId != null && !spaceId.equals(Spaces.DEFAULT_ID)) {
            try {
                androidx.webkit.Profile p =
                        ProfileStore.getInstance().getProfile(profileName(spaceId));
                if (p != null) return p.getWebStorage();
            } catch (Throwable ignored) {}
        }
        return WebStorage.getInstance();
    }

    /** Flushes the default jar plus every existing space profile's jar. */
    public static void flushAll(List<Spaces.Space> spaces) {
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable ignored) {}
        if (!supported() || spaces == null) return;
        for (Spaces.Space sp : spaces) {
            if (!sp.isolated) continue;
            try {
                androidx.webkit.Profile p =
                        ProfileStore.getInstance().getProfile(profileName(sp.id));
                if (p != null) p.getCookieManager().flush();
            } catch (Throwable ignored) {}
        }
    }

    /** Clears cookies + site storage of every profile (default and spaces). */
    public static void clearAllSiteData(List<Spaces.Space> spaces) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.removeSessionCookies(null);
            cm.flush();
            WebStorage.getInstance().deleteAllData();
        } catch (Throwable ignored) {}
        if (!supported() || spaces == null) return;
        for (Spaces.Space sp : spaces) {
            if (sp.isolated) clearSpaceSiteData(sp.id);
        }
    }

    /** Clears cookies + site storage of one space's profile. */
    public static void clearSpaceSiteData(String spaceId) {
        if (!supported() || spaceId == null || spaceId.equals(Spaces.DEFAULT_ID)) return;
        try {
            androidx.webkit.Profile p =
                    ProfileStore.getInstance().getProfile(profileName(spaceId));
            if (p == null) return;
            CookieManager cm = p.getCookieManager();
            cm.removeAllCookies(null);
            cm.removeSessionCookies(null);
            cm.flush();
            p.getWebStorage().deleteAllData();
        } catch (Throwable ignored) {}
    }

    /**
     * Deletes a space's engine profile and its data. Returns false when the
     * engine refused (e.g. lingering references) — the caller marks the
     * profile for a retry on the next launch, before any profile object is
     * resolved.
     */
    public static boolean deleteProfileData(String spaceId) {
        if (!supported() || spaceId == null || spaceId.equals(Spaces.DEFAULT_ID)) {
            return true;
        }
        try {
            ProfileStore.getInstance().deleteProfile(profileName(spaceId));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Retries profiles whose deletion was refused last session. Runs before
     * any tab exists, so no profile object has been resolved yet. Returns
     * the CSV of names that still failed (empty when all done).
     */
    public static String retryPendingDeletions(String csv) {
        if (csv == null || csv.isEmpty() || !supported()) return "";
        StringBuilder remaining = new StringBuilder();
        for (String name : csv.split(",")) {
            if (name.isEmpty()) continue;
            boolean ok;
            try {
                ProfileStore.getInstance().deleteProfile(name);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            if (!ok) {
                if (remaining.length() > 0) remaining.append(',');
                remaining.append(name);
            }
        }
        return remaining.toString();
    }
}
