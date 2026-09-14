package com.zerium.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity {

    static final String HOME_URL = "about:home";
    private static final int MAX_RESTORED_TABS = 10;

    // Menu command ids
    private static final int MENU_NEW_TAB = 1;
    private static final int MENU_NEW_INCOGNITO = 2;
    private static final int MENU_BOOKMARK_ADD = 3;
    private static final int MENU_BOOKMARKS = 4;
    private static final int MENU_HISTORY = 5;
    private static final int MENU_DOWNLOADS = 6;
    private static final int MENU_FIND = 7;
    private static final int MENU_SHARE = 8;
    private static final int MENU_BLOCK_INFO = 10;
    private static final int MENU_SETTINGS = 11;
    private static final int MENU_EXIT = 12;
    private static final int MENU_DESKTOP = 13;
    private static final int MENU_READER = 14;
    private static final int MENU_TRANSLATE = 15;
    private static final int MENU_PRINT = 16;
    private static final int MENU_PIN = 17;

    private static final int REQ_FILE_CHOOSER = 41;
    private static final int REQ_PERMISSION = 42;

    private Prefs prefs;
    private AdBlocker adBlocker;
    private BookmarksDB bookmarks;
    private HistoryDB history;
    private final TabManager tabs = new TabManager();
    private final AtomicLong sessionBlocked = new AtomicLong(0);

    private FrameLayout webContainer;
    private SwipeRefreshLayout swipe;
    private EditText omnibox;
    private ImageButton btnSecurity, btnRefresh, btnBack, btnForward, btnHome;
    private TextView btnTabs;
    private ProgressBar progress;
    private LinearLayout topBar, bottomBar;
    private View tabSwitcher;
    private RecyclerView tabsGrid;
    private TabsAdapter tabsAdapter;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    // Find in page (inline bar)
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private Runnable findPending;

    private String cachedDesktopUA;

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        adBlocker = new AdBlocker();
        adBlocker.init(this, prefs);
        bookmarks = new BookmarksDB(this);
        history = new HistoryDB(this);

        webContainer = findViewById(R.id.webContainer);
        swipe = findViewById(R.id.swipe);
        omnibox = findViewById(R.id.omnibox);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnTabs = findViewById(R.id.btnTabs);
        progress = findViewById(R.id.progress);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tabSwitcher = findViewById(R.id.tabSwitcher);
        tabsGrid = findViewById(R.id.tabsGrid);

        fullscreenContainer = new FrameLayout(this);
        addContentView(fullscreenContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.GONE);

        btnSecurity.setOnClickListener(v -> showSecurityInfo());
        btnRefresh.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && !isStartPage(t)) t.webView.reload();
        });
        btnBack.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && t.webView.canGoBack()) t.webView.goBack();
        });
        btnForward.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null && t.webView.canGoForward()) t.webView.goForward();
        });
        btnHome.setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null) loadInTab(t, HOME_URL);
        });
        btnTabs.setOnClickListener(v -> {
            if (tabSwitcher.getVisibility() == View.VISIBLE) hideTabSwitcher();
            else showTabSwitcher();
        });
        findViewById(R.id.btnMenuTop).setOnClickListener(this::showMenu);
        findViewById(R.id.btnMenuBottom).setOnClickListener(this::showMenu);

        findBar = findViewById(R.id.findBar);
        findInput = findViewById(R.id.findInput);
        findCount = findViewById(R.id.findCount);
        findViewById(R.id.btnFindNext).setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null) t.webView.findNext(true);
        });
        findViewById(R.id.btnFindPrev).setOnClickListener(v -> {
            Tab t = tabs.currentTab();
            if (t != null) t.webView.findNext(false);
        });
        findViewById(R.id.btnFindClose).setOnClickListener(v -> hideFindBar());
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            Tab t = tabs.currentTab();
            if (t != null) t.webView.findNext(true);
            return true;
        });
        findInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (findPending != null) findBar.removeCallbacks(findPending);
                findPending = () -> {
                    Tab t = tabs.currentTab();
                    if (t == null) return;
                    String q = s.toString().trim();
                    if (q.isEmpty()) t.webView.clearMatches();
                    else t.webView.findAllAsync(q);
                };
                findBar.postDelayed(findPending, 250);
            }
        });

        maybeAutoUpdateLists();

        omnibox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                navigateOmnibox();
                return true;
            }
            return false;
        });
        omnibox.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                omnibox.post(() -> omnibox.selectAll());
            } else {
                updateChrome(tabs.currentTab());
                hideKeyboard();
            }
        });

        swipe.setOnRefreshListener(() -> {
            Tab t = tabs.currentTab();
            if (t != null && !isStartPage(t)) t.webView.reload();
            else swipe.setRefreshing(false);
        });

        tabsGrid.setLayoutManager(new GridLayoutManager(this, 2));
        tabsAdapter = new TabsAdapter(tabs.tabs(), tabs, new TabsAdapter.Listener() {
            @Override
            public void onOpen(Tab tab) {
                hideTabSwitcher();
                switchTab(tab);
            }

            @Override
            public void onClose(Tab tab) {
                closeTab(tab);
            }
        });
        tabsGrid.setAdapter(tabsAdapter);
        findViewById(R.id.btnNewTab).setOnClickListener(v -> {
            hideTabSwitcher();
            openTab(null, false);
        });
        findViewById(R.id.btnNewIncognito).setOnClickListener(v -> {
            hideTabSwitcher();
            openTab(null, true);
        });
        findViewById(R.id.btnSwitcherClose).setOnClickListener(v -> hideTabSwitcher());
        findViewById(R.id.btnCloseAllTabs).setOnClickListener(v -> closeAllTabs());

        restoreSession();
        handleIntent(getIntent());
    }

    // ---------- Intents ----------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String url = intent.getDataString();
            openTab(url, false);
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                String url = Utils.smartUrl(text, prefs);
                if (url != null) openTab(url, false);
            }
        }
    }

    // ---------- Tab lifecycle ----------

    @SuppressLint("SetJavaScriptEnabled")
    private void openTab(String url, boolean incognito) {
        WebView w = new WebView(this);
        w.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        webContainer.addView(w);
        Tab tab = tabs.add(w, incognito);
        setupWebView(tab);
        tab.pendingUrl = (url == null || url.isEmpty()) ? HOME_URL : url;
        showCurrentWebView();
        loadPending(tab);
        updateChrome(tab);
        tabsAdapter.notifyDataSetChanged();
    }

    private void switchTab(Tab tab) {
        int idx = tabs.tabs().indexOf(tab);
        if (idx < 0) return;
        hideFindBar();
        tabs.setCurrent(idx);
        showCurrentWebView();
        loadPending(tab);
        updateChrome(tab);
        tabsAdapter.notifyDataSetChanged();
    }

    private void loadPending(Tab tab) {
        if (tab.pendingUrl != null) {
            String url = tab.pendingUrl;
            tab.pendingUrl = null;
            loadInTab(tab, url);
        }
    }

    private void showCurrentWebView() {
        for (int i = 0; i < tabs.tabs().size(); i++) {
            Tab t = tabs.tabs().get(i);
            t.webView.setVisibility(i == tabs.current() ? View.VISIBLE : View.GONE);
        }
    }

    private void closeTab(Tab tab) {
        int idx = tabs.tabs().indexOf(tab);
        if (idx < 0) return;
        tab.webView.stopLoading();
        webContainer.removeView(tab.webView);
        tabs.remove(tab);
        tab.destroy();
        if (tabs.count() == 0) {
            openTab(null, false);
            tabsAdapter.notifyDataSetChanged();
            return;
        }
        showCurrentWebView();
        loadPending(tabs.currentTab());
        updateChrome(tabs.currentTab());
        tabsAdapter.notifyDataSetChanged();
    }

    private void closeAllTabs() {
        while (tabs.count() > 0) {
            Tab t = tabs.tabs().get(0);
            webContainer.removeView(t.webView);
            tabs.remove(t);
            t.destroy();
        }
        hideTabSwitcher();
        openTab(null, false);
    }

    // ---------- WebView setup ----------

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(Tab tab) {
        WebView w = tab.webView;
        WebSettings s = w.getSettings();
        tab.mobileUA = s.getUserAgentString();
        applyWebSettings(s);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(prefs.cookiesEnabled());
        cm.setAcceptThirdPartyCookies(w, !prefs.thirdPartyCookiesBlocked());

        try {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, prefs.forceDarkWeb());
        } catch (Exception ignored) {}

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // Inject the YouTube suppression script at document start so it runs
        // before the player initializes and consumes ad placements.
        try {
            String ytScript = YouTubeFilter.script();
            if (!ytScript.isEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                java.util.Set<String> origins = new java.util.HashSet<>(java.util.Arrays.asList(
                        "https://*.youtube.com", "https://*.youtube-nocookie.com",
                        "https://music.youtube.com"));
                WebViewCompat.addDocumentStartJavaScript(w, ytScript, origins);
            }
        } catch (Exception ignored) {}

        // Cosmetic (element-hiding) rules injected at document start when the
        // WebView supports it: ad containers never paint, instead of being
        // hidden after first paint. Page-finish injection below stays as the
        // fallback for older WebView versions.
        try {
            if (prefs.blockCosmetic()
                    && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                String cosmeticScript = CosmeticFilter.getScript(this, true);
                if (cosmeticScript != null && !cosmeticScript.isEmpty()) {
                    java.util.Set<String> allOrigins = new java.util.HashSet<>(
                            java.util.Arrays.asList("http://*/*", "https://*/*"));
                    WebViewCompat.addDocumentStartJavaScript(w, cosmeticScript, allOrigins);
                    tab.cosmeticAtStart = true;
                }
            }
        } catch (Exception ignored) {}

        // Force-zoom rewrites the page viewport meta so pinch zoom always works
        // (mirrors Firefox Focus "Always enable zoom" / Chrome "force enable zoom").
        if (prefs.forceZoom() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                String forceZoomJs = "(function(){function f(){try{"
                        + "var m=document.querySelector('meta[name=\"viewport\"]');"
                        + "if(m){m.setAttribute('content',"
                        + "'width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes');}"
                        + "}catch(e){}}f();"
                        + "document.addEventListener('DOMContentLoaded',f);})();";
                java.util.Set<String> allOrigins = new java.util.HashSet<>(
                        java.util.Arrays.asList("http://*/*", "https://*/*"));
                WebViewCompat.addDocumentStartJavaScript(w, forceZoomJs, allOrigins);
            } catch (Exception ignored) {}
        }

        w.setWebViewClient(new ZeriumWebViewClient(tab));
        w.setWebChromeClient(new ZeriumChromeClient(tab));
        w.setDownloadListener(this::startDownload);
        w.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (findBar == null || findBar.getVisibility() != View.VISIBLE) return;
            if (isDoneCounting) {
                int shown = numberOfMatches == 0 ? 0 : activeMatchOrdinal + 1;
                findCount.setText(shown + "/" + numberOfMatches);
            }
        });
    }

    private void applyWebSettings(WebSettings s) {
        s.setJavaScriptEnabled(prefs.javascriptEnabled());
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSavePassword(false);
        s.setMediaPlaybackRequiresUserGesture(!prefs.mediaAutoplay());
        s.setTextZoom(prefs.textZoom());
    }

    private class ZeriumWebViewClient extends WebViewClient {
        private final Tab tab;

        ZeriumWebViewClient(Tab tab) { this.tab = tab; }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            if (scheme.equals("http")) {
                // HTTPS-first: upgrade main-frame navigations, skipping local
                // addresses that have no TLS to upgrade to.
                if (prefs.httpsUpgrade() && request.isForMainFrame()
                        && isUpgradableHost(uri.getHost())) {
                    view.loadUrl(uri.buildUpon().scheme("https").build().toString());
                    return true;
                }
                return false;
            }
            if (scheme.equals("https")) return false;
            if (scheme.equals("intent")) {
                try {
                    startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME));
                } catch (Exception e) {
                    toast(R.string.no_app_for_link);
                }
                return true;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                if (scheme.equals("market")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store")));
                    } catch (Exception ignored) {}
                } else {
                    toast(R.string.no_app_for_link);
                }
            }
            return true;
        }

        private boolean isUpgradableHost(String host) {
            if (host == null || host.isEmpty()) return false;
            String h = host.toLowerCase();
            if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")
                    || h.endsWith(".lan") || h.endsWith(".internal") || h.endsWith(".home")) {
                return false;
            }
            if (h.startsWith("10.") || h.startsWith("127.") || h.startsWith("192.168.")
                    || h.startsWith("172.16.") || h.startsWith("172.17.") || h.startsWith("172.18.")
                    || h.startsWith("172.19.") || h.startsWith("172.2") || h.startsWith("172.30.")
                    || h.startsWith("172.31.")) {
                return false;
            }
            return !h.contains(":"); // IPv6 literals stay as-is
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (!prefs.blockAds() || !adBlocker.isReady()) return null;
            String url = request.getUrl().toString();
            String pageUrl = tab.url == null ? "" : tab.url;
            if (adBlocker.shouldBlock(url, pageUrl)) {
                tab.blockedOnPage++;
                sessionBlocked.incrementAndGet();
                try {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Blocked",
                            Collections.<String, String>emptyMap(),
                            new ByteArrayInputStream(new byte[0]));
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            tab.url = url;
            tab.blockedOnPage = 0;
            tab.readerActive = false;
            // Track the pre-translation URL for the View-original action. Links
            // inside a translated page stay on *.translate.goog, so a missing
            // exact source falls back to deriving it from the proxy URL.
            if (url != null && url.contains(".translate.goog")) {
                if (tab.translateSourceUrl == null) {
                    tab.translateSourceUrl = originalFromTranslateUrl(url);
                }
            } else {
                tab.translateSourceUrl = null;
            }
            if (tabs.currentTab() == tab) updateChrome(tab);
        }


        @Override
        public void onPageFinished(WebView view, String url) {
            tab.url = url;
            tab.title = view.getTitle() == null ? "" : view.getTitle();
            if (!tab.incognito && !isStartPage(tab)) {
                history.add(url, tab.title);
            }
            long blocked = tab.blockedOnPage;
            if (blocked > 0) prefs.addTotalBlocked(blocked);
            injectCosmetic(tab);
            injectYouTube(tab);
            if (tabs.currentTab() == tab) {
                updateChrome(tab);
                swipe.setRefreshing(false);
            }
            if (!tab.incognito && !isStartPage(tab)) {
                view.postDelayed(() -> capturePreview(tab), 350);
            }
            tabsAdapter.notifyDataSetChanged();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            tab.url = url;
            if (tabs.currentTab() == tab) updateChrome(tab);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            // WebView renders its own error page for main-frame failures.
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                       android.net.http.SslError error) {
            if (tab.incognito) {
                handler.cancel();
                return;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.ssl_error)
                    .setMessage(getString(R.string.ssl_error_message, String.valueOf(error.getPrimaryError())))
                    .setPositiveButton(R.string.proceed, (d, w) -> handler.proceed())
                    .setNegativeButton(R.string.cancel, (d, w) -> handler.cancel())
                    .setOnCancelListener(d -> handler.cancel())
                    .show();
        }
    }

    private class ZeriumChromeClient extends WebChromeClient {
        private final Tab tab;

        ZeriumChromeClient(Tab tab) { this.tab = tab; }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (tabs.currentTab() == tab) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            tab.title = title == null ? "" : title;
            if (tabs.currentTab() == tab) updateChrome(tab);
            tabsAdapter.notifyDataSetChanged();
        }

        @Override
        public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
            if (icon != null && !tab.incognito) tab.favicon = icon;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            webContainer.setVisibility(View.GONE);
            fullscreenContainer.setVisibility(View.VISIBLE);
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                Intent intent = params.createIntent();
                startActivityForResult(intent, REQ_FILE_CHOOSER);
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback callback) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.location_permission)
                    .setMessage(getString(R.string.location_permission_message, origin))
                    .setPositiveButton(R.string.allow, (d, w) -> callback.invoke(origin, true, false))
                    .setNegativeButton(R.string.deny, (d, w) -> callback.invoke(origin, false, false))
                    .show();
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                boolean wantsCamera = false, wantsMic = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                }
                if (!wantsCamera && !wantsMic) {
                    request.deny();
                    return;
                }
                boolean camOk = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;
                boolean micOk = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
                if ((!wantsCamera || camOk) && (!wantsMic || micOk)) {
                    request.grant(request.getResources());
                    return;
                }
                pendingPermission = request;
                java.util.ArrayList<String> needed = new java.util.ArrayList<>();
                if (wantsCamera && !camOk) needed.add(Manifest.permission.CAMERA);
                if (wantsMic && !micOk) needed.add(Manifest.permission.RECORD_AUDIO);
                requestPermissions(needed.toArray(new String[0]), REQ_PERMISSION);
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION && pendingPermission != null) {
            boolean allGranted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (allGranted) pendingPermission.grant(pendingPermission.getResources());
            else pendingPermission.deny();
            pendingPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    // ---------- Loading ----------

    private void loadInTab(Tab tab, String url) {
        if (tab == null || url == null) return;
        if (HOME_URL.equals(url)) {
            tab.url = HOME_URL;
            tab.title = getString(R.string.start_page);
            tab.webView.loadDataWithBaseURL(null, StartPage.html(this, prefs),
                    "text/html", "utf-8", null);
            if (tabs.currentTab() == tab) updateChrome(tab);
            return;
        }
        Map<String, String> headers = new HashMap<>();
        if (prefs.privacyHeaders()) {
            headers.put("DNT", "1");
            headers.put("Sec-GPC", "1");
        }
        if (headers.isEmpty()) tab.webView.loadUrl(url);
        else tab.webView.loadUrl(url, headers);
    }

    private void injectCosmetic(Tab tab) {
        if (!prefs.blockCosmetic() || isStartPage(tab)) return;
        if (tab.cosmeticAtStart) return; // already injected at document start
        String script = CosmeticFilter.getScript(this, true);
        if (script != null) {
            tab.webView.evaluateJavascript(script, null);
        }
    }

    /** Fallback injection for WebView versions without document-start scripting. */
    private void injectYouTube(Tab tab) {
        if (!prefs.blockAds() || !prefs.youtubeSuppress()) return;
        if (isStartPage(tab)) return;
        if (!YouTubeFilter.matches(Utils.hostOf(tab.url))) return;
        String script = YouTubeFilter.script();
        if (!script.isEmpty()) tab.webView.evaluateJavascript(script, null);
    }

    private void navigateOmnibox() {
        String text = omnibox.getText().toString().trim();
        if (text.isEmpty()) return;
        hideKeyboard();
        omnibox.clearFocus();
        Tab t = tabs.currentTab();
        if (t == null) return;
        if (text.equals("about:home") || text.equalsIgnoreCase("zerium://home")) {
            loadInTab(t, HOME_URL);
            return;
        }
        String url = Utils.smartUrl(text, prefs);
        if (url != null) loadInTab(t, url);
    }

    private boolean isStartPage(Tab tab) {
        return tab.url == null || tab.url.isEmpty()
                || tab.url.equals(HOME_URL)
                || tab.url.startsWith("data:");
    }

    // ---------- Chrome (UI) updates ----------

    private void updateChrome(Tab tab) {
        if (tab == null) return;
        // Refreshing the generated start page is meaningless; also kills the
        // pointless spinner flash when pulling down on it. Re-armed for real
        // pages unless the user disabled pull-to-refresh entirely.
        swipe.setEnabled(!isStartPage(tab) && prefs.pullToRefresh());
        if (isStartPage(tab)) {
            omnibox.setText("");
            omnibox.setHint(R.string.search_hint);
            btnSecurity.setImageResource(R.drawable.ic_home);
        } else {
            omnibox.setText(displayUrl(tab.url));
            omnibox.setHint(R.string.search_hint);
            String scheme = Uri.parse(tab.url).getScheme();
            btnSecurity.setImageResource("https".equals(scheme)
                    ? R.drawable.ic_lock : R.drawable.ic_globe);
        }
        btnBack.setEnabled(tab.webView.canGoBack());
        btnForward.setEnabled(tab.webView.canGoForward());
        btnBack.setAlpha(tab.webView.canGoBack() ? 1f : 0.4f);
        btnForward.setAlpha(tab.webView.canGoForward() ? 1f : 0.4f);
        btnTabs.setText(String.valueOf(tabs.count()));
    }

    /** Scheme stripped for display; the security icon already carries TLS state. */
    private static String displayUrl(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://", "");
    }

    private void showSecurityInfo() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) {
            Toast.makeText(this, R.string.start_page, Toast.LENGTH_SHORT).show();
            return;
        }
        String scheme = Uri.parse(t.url).getScheme();
        String msg = "https".equals(scheme)
                ? getString(R.string.security_secure)
                : getString(R.string.security_insecure);
        new AlertDialog.Builder(this)
                .setTitle(R.string.security_title)
                .setMessage(getString(R.string.security_body, msg, t.blockedOnPage))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Tab t = tabs.currentTab();
        pm.getMenu().add(0, MENU_NEW_TAB, 0, R.string.menu_new_tab);
        pm.getMenu().add(0, MENU_NEW_INCOGNITO, 1, R.string.menu_new_incognito);
        pm.getMenu().add(0, MENU_BOOKMARK_ADD, 2, isCurrentBookmarked()
                ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark);
        pm.getMenu().add(0, MENU_BOOKMARKS, 3, R.string.menu_bookmarks);
        pm.getMenu().add(0, MENU_HISTORY, 4, R.string.menu_history);
        pm.getMenu().add(0, MENU_DOWNLOADS, 5, R.string.menu_downloads);
        pm.getMenu().add(0, MENU_FIND, 6, R.string.menu_find);
        pm.getMenu().add(0, MENU_DESKTOP, 7, R.string.menu_desktop)
                .setCheckable(true).setChecked(t != null && t.desktopMode);
        pm.getMenu().add(0, MENU_READER, 8, R.string.menu_reader)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_TRANSLATE, 9, isOnTranslatedPage(t)
                ? R.string.menu_view_original : R.string.menu_translate)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_PRINT, 10, R.string.menu_print)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_PIN, 11, R.string.menu_pin)
                .setEnabled(t != null && !isStartPage(t));
        pm.getMenu().add(0, MENU_SHARE, 12, R.string.menu_share);
        pm.getMenu().add(0, MENU_BLOCK_INFO, 13, R.string.menu_block_info);
        pm.getMenu().add(0, MENU_SETTINGS, 14, R.string.menu_settings);
        pm.getMenu().add(0, MENU_EXIT, 15, R.string.menu_exit);
        pm.setOnMenuItemClickListener(item -> {
            handleMenu(item.getItemId());
            return true;
        });
        pm.show();
    }

    private boolean isOnTranslatedPage(Tab t) {
        return t != null && t.url != null && t.url.contains(".translate.goog");
    }

    private boolean isCurrentBookmarked() {
        Tab t = tabs.currentTab();
        return t != null && !isStartPage(t) && bookmarks.contains(t.url);
    }

    private void handleMenu(int id) {
        Tab t = tabs.currentTab();
        switch (id) {
            case MENU_NEW_TAB: openTab(null, false); break;
            case MENU_NEW_INCOGNITO: openTab(null, true); break;
            case MENU_BOOKMARK_ADD:
                if (t == null || isStartPage(t)) return;
                if (bookmarks.contains(t.url)) {
                    toast(R.string.bookmark_exists);
                    // Find and remove
                    for (BookmarksDB.Entry e : bookmarks.all()) {
                        if (e.url.equals(t.url)) { bookmarks.remove(e.id); break; }
                    }
                    toast(R.string.bookmark_removed);
                } else {
                    bookmarks.add(t.url, t.title);
                    toast(R.string.bookmark_added);
                }
                break;
            case MENU_BOOKMARKS:
                startActivityForResult(new Intent(this, BookmarksActivity.class), 101);
                break;
            case MENU_HISTORY:
                startActivityForResult(new Intent(this, HistoryActivity.class), 102);
                break;
            case MENU_DOWNLOADS:
                startActivity(new Intent(this, DownloadsActivity.class));
                break;
            case MENU_FIND: showFindBar(); break;
            case MENU_SHARE:
                if (t != null && !isStartPage(t)) {
                    Intent si = new Intent(Intent.ACTION_SEND);
                    si.setType("text/plain");
                    si.putExtra(Intent.EXTRA_TEXT, t.url);
                    startActivity(Intent.createChooser(si, getString(R.string.menu_share)));
                }
                break;
            case MENU_BLOCK_INFO: showBlockInfo(); break;
            case MENU_DESKTOP: toggleDesktop(); break;
            case MENU_READER: toggleReader(); break;
            case MENU_TRANSLATE: translatePage(); break;
            case MENU_PRINT: printPage(); break;
            case MENU_PIN: addToHomeScreen(); break;
            case MENU_SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case MENU_EXIT: finishAffinity(); break;
        }
    }

    private void showBlockInfo() {
        Tab t = tabs.currentTab();
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.block_info_title)
                .setMessage(getString(R.string.block_info_body,
                        t == null ? 0 : t.blockedOnPage,
                        sessionBlocked.get(),
                        prefs.totalBlocked() + (t == null ? 0 : t.blockedOnPage)))
                .setPositiveButton(R.string.ok, null);
        if (t != null && !isStartPage(t)) {
            final String host = Utils.hostOf(t.url);
            if (host != null && !host.isEmpty()) {
                b.setNeutralButton(R.string.allow_site, (d, w) -> {
                    String cur = prefs.allowlist();
                    String lower = cur == null ? "" : cur.toLowerCase();
                    if (lower.contains(host)) {
                        toast(R.string.site_already_allowed);
                    } else {
                        prefs.setAllowlist(cur == null || cur.isEmpty() ? host : cur + "\n" + host);
                        adBlocker.rebuildAllowlist(prefs.allowlist());
                        toast(R.string.site_allowed);
                    }
                });
            }
        }
        b.show();
    }

    private void showFindBar() {
        if (tabs.currentTab() == null) return;
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findCount.setText("0/0");
        findInput.requestFocus();
        findInput.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideFindBar() {
        if (findBar.getVisibility() != View.VISIBLE) return;
        findBar.setVisibility(View.GONE);
        Tab t = tabs.currentTab();
        if (t != null) t.webView.clearMatches();
        hideKeyboard();
    }

    // ---------- Page tools ----------

    /**
     * Desktop user agent derived from the device's own WebView engine so the
     * Chromium major version always matches what the device actually runs
     * (stale hardcoded versions trigger Google "unsupported browser" walls).
     * Desktop Chromium sends major.0.0.0 since the reduced-UA rollout.
     */
    private String desktopUA() {
        if (cachedDesktopUA != null) return cachedDesktopUA;
        String major = "130";
        try {
            String def = WebSettings.getDefaultUserAgent(this);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("Chrome/(\\d+)").matcher(def);
            if (m.find()) major = m.group(1);
        } catch (Exception ignored) {}
        cachedDesktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + major + ".0.0.0 Safari/537.36";
        return cachedDesktopUA;
    }

    /** Per-tab desktop-site toggle: swap the UA and reload (Lightning-style). */
    private void toggleDesktop() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        t.desktopMode = !t.desktopMode;
        WebSettings s = t.webView.getSettings();
        if (t.desktopMode) {
            if (t.mobileUA == null || t.mobileUA.isEmpty()) t.mobileUA = s.getUserAgentString();
            s.setUserAgentString(desktopUA());
        } else {
            s.setUserAgentString(t.mobileUA != null && !t.mobileUA.isEmpty()
                    ? t.mobileUA : WebSettings.getDefaultUserAgent(this));
        }
        t.webView.reload();
    }

    /**
     * Reader view toggle. Enters via bundled Mozilla Readability (Apache-2.0);
     * leaves by restoring the DOM snapshot cached on the page. Never
     * auto-triggered and never re-fetches anything: what the WebView already
     * holds is all the reader can show, so paywalled pages yield their stubs.
     */
    private void toggleReader() {
        final Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        if (t.readerActive) {
            t.readerActive = false;
            t.webView.evaluateJavascript(ReaderSupport.offScript(), null);
            return;
        }
        t.webView.evaluateJavascript(ReaderSupport.onScript(this), value -> {
            try {
                org.json.JSONObject o = new org.json.JSONObject(value);
                int code = o.optInt("ok", 0);
                if (code == 1) {
                    t.readerActive = true;
                    Toast.makeText(MainActivity.this, getString(
                            R.string.reader_minutes, o.optInt("minutes", 1)),
                            Toast.LENGTH_SHORT).show();
                } else if (code == 3) {
                    t.readerActive = true;
                } else {
                    Toast.makeText(MainActivity.this, R.string.reader_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, R.string.reader_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Translate the current page through Google's translate.goog proxy in the
     * same tab (links keep translating). No API key, no page re-fetch of our
     * own. On a proxy page the menu offers View original instead.
     */
    private void translatePage() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        Uri u = Uri.parse(t.url);
        String host = u.getHost();
        if (host == null || host.isEmpty()) return;
        if (host.endsWith(".translate.goog") || host.equals("translate.goog")) {
            String back = t.translateSourceUrl != null
                    ? t.translateSourceUrl : originalFromTranslateUrl(t.url);
            if (back != null) loadInTab(t, back);
            return;
        }
        String tl = java.util.Locale.getDefault().getLanguage();
        if (tl == null || tl.isEmpty()) tl = "en";
        Uri out = u.buildUpon()
                .scheme("https")
                .authority(host.replace('.', '-') + ".translate.goog")
                .appendQueryParameter("_x_tr_sl", "auto")
                .appendQueryParameter("_x_tr_tl", tl)
                .appendQueryParameter("_x_tr_hl", tl)
                .appendQueryParameter("_x_tr_pto", "ajax,elem")
                .build();
        t.translateSourceUrl = t.url;
        loadInTab(t, out.toString());
        toast(R.string.translating);
    }

    /** Best-effort inverse of the translate.goog host rewriting. */
    private static String originalFromTranslateUrl(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null) return null;
            int i = host.lastIndexOf(".translate.goog");
            if (i <= 0) return null;
            String origHost = host.substring(0, i).replace('-', '.');
            Uri.Builder b = u.buildUpon().scheme("https").authority(origHost).clearQuery();
            for (String key : u.getQueryParameterNames()) {
                if (key == null || key.startsWith("_x_tr_")) continue;
                for (String v : u.getQueryParameters(key)) b.appendQueryParameter(key, v);
            }
            return b.build().toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** System print dialog; its destination picker offers Save as PDF. */
    private void printPage() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        try {
            PrintManager pmgr = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pmgr == null) return;
            String title = t.title == null || t.title.isEmpty()
                    ? String.valueOf(Utils.hostOf(t.url)) : t.title;
            String jobName = getString(R.string.app_name) + " \u2014 " + title;
            pmgr.print(jobName, t.webView.createPrintDocumentAdapter(jobName),
                    new PrintAttributes.Builder().build());
        } catch (Exception e) {
            toast(R.string.print_failed);
        }
    }

    /** Pins a shortcut to the launcher with the site icon (API 26+ guaranteed). */
    private void addToHomeScreen() {
        Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                toast(R.string.pin_unsupported);
                return;
            }
            Intent si = new Intent(Intent.ACTION_VIEW, Uri.parse(t.url));
            si.setPackage(getPackageName());
            si.setClass(this, MainActivity.class);
            si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String label = (t.title == null || t.title.isEmpty())
                    ? Utils.hostOf(t.url) : t.title;
            if (label == null || label.isEmpty()) label = getString(R.string.app_name);
            ShortcutInfo info = new ShortcutInfo.Builder(this,
                    "home_" + String.valueOf(t.url.hashCode()))
                    .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                    .setLongLabel(label)
                    .setIntent(si)
                    .setIcon(t.favicon != null && !t.favicon.isRecycled()
                            ? Icon.createWithBitmap(t.favicon)
                            : Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .build();
            sm.requestPinShortcut(info, null);
            toast(R.string.pin_requested);
        } catch (Exception e) {
            toast(R.string.pin_unsupported);
        }
    }

    /** Weekly automatic filter-list refresh (can be disabled in Settings). */
    private void maybeAutoUpdateLists() {
        if (!FilterUpdater.dueForAutoUpdate(prefs)) return;
        FilterUpdater.updateAll(this, prefs, updated -> {
            if (updated > 0) {
                adBlocker.reload(this, prefs);
                CosmeticFilter.invalidate();
            }
        });
    }

    // ---------- Tab switcher ----------

    private void showTabSwitcher() {
        hideFindBar();
        Tab current = tabs.currentTab();
        if (current != null && !current.incognito && !isStartPage(current)) capturePreview(current);
        tabsAdapter.notifyDataSetChanged();
        tabSwitcher.setVisibility(View.VISIBLE);
        hideKeyboard();
        omnibox.clearFocus();
    }

    /** Scaled screenshot for the tab switcher card (kept small to bound memory). */
    private void capturePreview(Tab tab) {
        try {
            WebView w = tab.webView;
            int vw = w.getWidth(), vh = w.getHeight();
            if (vw <= 0 || vh <= 0) return;
            float scale = Math.min(1f, 320f / vw);
            Bitmap bmp = Bitmap.createBitmap(
                    Math.max(1, (int) (vw * scale)),
                    Math.max(1, (int) (vh * scale)),
                    Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(0xFFFFFFFF);
            w.draw(canvas);
            if (tab.preview != null) tab.preview.recycle();
            tab.preview = bmp;
            if (tabSwitcher.getVisibility() == View.VISIBLE) tabsAdapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    private void hideTabSwitcher() {
        tabSwitcher.setVisibility(View.GONE);
    }

    // ---------- Session persistence ----------

    private void restoreSession() {
        String saved = prefs.savedTabs();
        int index = prefs.savedTabIndex();
        int opened = 0;
        if (saved != null && !saved.isEmpty()) {
            for (String u : saved.split("\\|\\|")) {
                if (u == null || u.trim().isEmpty()) continue;
                if (opened >= MAX_RESTORED_TABS) break;
                openTab(u.trim(), false);
                opened++;
            }
        }
        if (opened == 0) {
            openTab(null, false);
        } else {
            int target = Math.max(0, Math.min(index, opened - 1));
            tabs.setCurrent(target);
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
    }

    private void saveSession() {
        StringBuilder sb = new StringBuilder();
        int current = 0;
        int i = 0;
        for (Tab t : tabs.tabs()) {
            if (t.incognito) continue;
            String u = t.url == null ? "" : t.url;
            if (u.startsWith("data:")) u = HOME_URL;
            if (sb.length() > 0) sb.append("||");
            if (i == tabs.current()) current = i;
            sb.append(u);
            i++;
            if (i >= MAX_RESTORED_TABS) break;
        }
        prefs.savedTabs(sb.toString());
        prefs.savedTabIndex(current);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed while we were away: text zoom applies live
        // to every tab, and a filter-list download (possibly completed in the
        // Settings screen) reloads the network blocklist without a restart.
        int tz = prefs.textZoom();
        for (Tab t : tabs.tabs()) {
            try {
                t.webView.getSettings().setTextZoom(tz);
            } catch (Exception ignored) {}
        }
        if (adBlocker.isReady() && prefs.listLastUpdate() > adBlocker.loadedAt()) {
            adBlocker.reload(this, prefs);
            CosmeticFilter.invalidate();
        }
        Tab t = tabs.currentTab();
        if (t != null) updateChrome(t);
    }

    // ---------- Downloads ----------

    private void startDownload(String url, String userAgent, String contentDisposition,
                               String mimeType, long contentLength) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    Utils.fileNameFromUrl(url));
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                toast(R.string.download_started);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.download_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Misc ----------

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(omnibox.getWindowToken(), 0);
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        fullscreenContainer.removeAllViews();
        fullscreenContainer.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenView = null;
        fullscreenCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen();
            return;
        }
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
            return;
        }
        if (tabSwitcher.getVisibility() == View.VISIBLE) {
            hideTabSwitcher();
            return;
        }
        Tab t = tabs.currentTab();
        if (t != null && t.webView.canGoBack()) {
            t.webView.goBack();
        } else if (t != null && !isStartPage(t)) {
            loadInTab(t, HOME_URL);
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
