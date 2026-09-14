package com.zerium.browser;

import android.content.Context;
import android.content.SharedPreferences;

/** Central access to all Zerium user preferences. */
public class Prefs {
    private static final String NAME = "zerium_prefs";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // Content blocking
    public boolean blockAds() { return sp.getBoolean("block_ads", true); }
    public void blockAds(boolean v) { sp.edit().putBoolean("block_ads", v).apply(); }
    public boolean blockCosmetic() { return sp.getBoolean("block_cosmetic", true); }
    public void blockCosmetic(boolean v) { sp.edit().putBoolean("block_cosmetic", v).apply(); }
    public boolean youtubeSuppress() { return sp.getBoolean("youtube_suppress", true); }
    public void youtubeSuppress(boolean v) { sp.edit().putBoolean("youtube_suppress", v).apply(); }

    // Privacy
    public boolean cookiesEnabled() { return sp.getBoolean("cookies", true); }
    public void cookiesEnabled(boolean v) { sp.edit().putBoolean("cookies", v).apply(); }
    public boolean thirdPartyCookiesBlocked() { return sp.getBoolean("cookies_3p_blocked", true); }
    public void thirdPartyCookiesBlocked(boolean v) { sp.edit().putBoolean("cookies_3p_blocked", v).apply(); }
    public boolean javascriptEnabled() { return sp.getBoolean("javascript", true); }
    public void javascriptEnabled(boolean v) { sp.edit().putBoolean("javascript", v).apply(); }
    public boolean privacyHeaders() { return sp.getBoolean("privacy_headers", true); }
    public void privacyHeaders(boolean v) { sp.edit().putBoolean("privacy_headers", v).apply(); }
    public boolean forceDarkWeb() { return sp.getBoolean("force_dark_web", false); }
    public void forceDarkWeb(boolean v) { sp.edit().putBoolean("force_dark_web", v).apply(); }

    // Search engine: 0 DuckDuckGo, 1 Startpage, 2 Brave Search, 3 Google, 4 Bing, 5 Wikipedia
    public int searchEngine() { return sp.getInt("search_engine", 0); }
    public void searchEngine(int v) { sp.edit().putInt("search_engine", v).apply(); }

    // App theme: 0 system, 1 light, 2 dark
    public int appTheme() { return sp.getInt("app_theme", 0); }
    public void appTheme(int v) { sp.edit().putInt("app_theme", v).apply(); }

    // Stats
    public long totalBlocked() { return sp.getLong("total_blocked", 0L); }
    public void addTotalBlocked(long n) {
        if (n <= 0) return;
        sp.edit().putLong("total_blocked", totalBlocked() + n).apply();
    }

    // Site allowlist: newline separated domains exempt from network blocking
    public String allowlist() { return sp.getString("allowlist", ""); }
    public void setAllowlist(String v) { sp.edit().putString("allowlist", v == null ? "" : v).apply(); }

    // Browsing behaviour
    public boolean pullToRefresh() { return sp.getBoolean("pull_to_refresh", true); }
    public void pullToRefresh(boolean v) { sp.edit().putBoolean("pull_to_refresh", v).apply(); }
    public boolean mediaAutoplay() { return sp.getBoolean("media_autoplay", false); }
    public void mediaAutoplay(boolean v) { sp.edit().putBoolean("media_autoplay", v).apply(); }
    public boolean httpsUpgrade() { return sp.getBoolean("https_upgrade", true); }
    public void httpsUpgrade(boolean v) { sp.edit().putBoolean("https_upgrade", v).apply(); }
    public boolean forceZoom() { return sp.getBoolean("force_zoom", false); }
    public void forceZoom(boolean v) { sp.edit().putBoolean("force_zoom", v).apply(); }

    /** Web text zoom in percent (WebSettings.setTextZoom); 100 = page default. */
    public int textZoom() { return sp.getInt("text_zoom", 100); }
    public void textZoom(int v) { sp.edit().putInt("text_zoom", v).apply(); }

    // Filter list bookkeeping
    public boolean autoUpdateLists() { return sp.getBoolean("auto_update_lists", true); }
    public void autoUpdateLists(boolean v) { sp.edit().putBoolean("auto_update_lists", v).apply(); }
    public long listLastUpdate() { return sp.getLong("list_last_update", 0L); }
    public void listLastUpdate(long v) { sp.edit().putLong("list_last_update", v).apply(); }

    /**
     * Custom search engines as a JSON array of {"name":…,"url":…} objects.
     * URLs must contain the %s query placeholder. Engine index >= 6 (the
     * built-in count) selects customEngines[index - 6].
     */
    public String customEngines() { return sp.getString("custom_engines", ""); }
    public void customEngines(String v) { sp.edit().putString("custom_engines", v == null ? "" : v).apply(); }

    /**
     * Start-page shortcuts as a JSON array of {"name":…,"url":…} objects.
     * Empty means automatic mode: most-visited sites from history merged over
     * the built-in defaults.
     */
    public String homeTiles() { return sp.getString("home_tiles", ""); }
    public void homeTiles(String v) { sp.edit().putString("home_tiles", v == null ? "" : v).apply(); }

    // Open tabs persistence
    public String savedTabs() { return sp.getString("saved_tabs", ""); }
    public void savedTabs(String v) { sp.edit().putString("saved_tabs", v == null ? "" : v).apply(); }
    public int savedTabIndex() { return sp.getInt("saved_tab_index", 0); }
    public void savedTabIndex(int v) { sp.edit().putInt("saved_tab_index", v).apply(); }
}
