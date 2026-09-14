package com.zerium.browser;

import android.graphics.Bitmap;
import android.webkit.WebView;

/** One browser tab. Holds its WebView and per-tab state. */
public class Tab {
    public final long id;
    public final WebView webView;
    public final boolean incognito;
    public String url;
    public String title;
    public boolean desktopMode;
    /** The tab's stock (mobile) WebView user agent; captured once at creation. */
    public String mobileUA;
    /** Original URL before a translate.goog proxy hop, for the View-original action. */
    public String translateSourceUrl;
    /** True while the reader view replaces this tab's rendered DOM. */
    public boolean readerActive;
    /** Site icon received from the page (pinned-shortcut icon). Not recycled eagerly. */
    public Bitmap favicon;
    /** URL to load when the tab is first shown (used when restoring sessions). */
    public String pendingUrl;
    /** Blocked network requests counted for the currently loaded page. */
    public volatile long blockedOnPage;
    /** True when cosmetic rules were already injected at document start for this tab. */
    public volatile boolean cosmeticAtStart;
    /** Scaled screenshot of the last finished page (tab switcher preview). Never captured for incognito tabs. */
    public Bitmap preview;

    public Tab(long id, WebView webView, boolean incognito) {
        this.id = id;
        this.webView = webView;
        this.incognito = incognito;
        this.url = "";
        this.title = "";
    }

    public void destroy() {
        if (preview != null) {
            preview.recycle();
            preview = null;
        }
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        } catch (Exception ignored) {}
    }
}
