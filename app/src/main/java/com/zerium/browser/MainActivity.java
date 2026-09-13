package com.zerium.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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

    private static final String HOME_URL = "about:home";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
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
    private static final int MENU_DESKTOP = 9;
    private static final int MENU_BLOCK_INFO = 10;
    private static final int MENU_SETTINGS = 11;
    private static final int MENU_EXIT = 12;

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
                String url = Utils.smartUrl(text, prefs.searchEngine());
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

        w.setWebViewClient(new ZeriumWebViewClient(tab));
        w.setWebChromeClient(new ZeriumChromeClient(tab));
        w.setDownloadListener(this::startDownload);
        w.setFindListener((activeMatch, nbMatches, isDoneCounting) ->
                updateChrome(tabs.currentTab()));
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
        s.setMediaPlaybackRequiresUserGesture(true);
    }

    private class ZeriumWebViewClient extends WebViewClient {
        private final Tab tab;

        ZeriumWebViewClient(Tab tab) { this.tab = tab; }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            if (scheme.equals("http") || scheme.equals("https")) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {}
            return true;
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
        String url = Utils.smartUrl(text, prefs.searchEngine());
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
        if (isStartPage(tab)) {
            omnibox.setText("");
            omnibox.setHint(R.string.start_page);
            btnSecurity.setImageResource(R.drawable.ic_home);
        } else {
            omnibox.setText(tab.url);
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
        pm.getMenu().add(0, MENU_NEW_TAB, 0, R.string.menu_new_tab);
        pm.getMenu().add(0, MENU_NEW_INCOGNITO, 1, R.string.menu_new_incognito);
        pm.getMenu().add(0, MENU_BOOKMARK_ADD, 2, isCurrentBookmarked()
                ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark);
        pm.getMenu().add(0, MENU_BOOKMARKS, 3, R.string.menu_bookmarks);
        pm.getMenu().add(0, MENU_HISTORY, 4, R.string.menu_history);
        pm.getMenu().add(0, MENU_DOWNLOADS, 5, R.string.menu_downloads);
        pm.getMenu().add(0, MENU_FIND, 6, R.string.menu_find);
        pm.getMenu().add(0, MENU_SHARE, 7, R.string.menu_share);
        pm.getMenu().add(0, MENU_BLOCK_INFO, 8, R.string.menu_block_info);
        pm.getMenu().add(0, MENU_SETTINGS, 9, R.string.menu_settings);
        pm.getMenu().add(0, MENU_EXIT, 10, R.string.menu_exit);
        pm.setOnMenuItemClickListener(item -> {
            handleMenu(item.getItemId());
            return true;
        });
        pm.show();
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
            case MENU_SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case MENU_EXIT: finishAffinity(); break;
        }
    }

    private void showBlockInfo() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.block_info_title)
                .setMessage(getString(R.string.block_info_body,
                        tabs.currentTab() == null ? 0 : tabs.currentTab().blockedOnPage,
                        sessionBlocked.get(),
                        prefs.totalBlocked() + (tabs.currentTab() == null ? 0 : tabs.currentTab().blockedOnPage)))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showFindBar() {
        Tab t = tabs.currentTab();
        if (t == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(24, 12, 24, 12);
        final EditText input = new EditText(this);
        input.setHint(R.string.find_in_page);
        input.setSingleLine(true);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(input);
        final TextView count = new TextView(this);
        count.setPadding(16, 0, 16, 0);
        count.setText("0/0");
        box.addView(count);

        t.webView.setFindListener((active, total, done) ->
                count.setText(active + "/" + total));

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_find)
                .setView(box)
                .setPositiveButton(R.string.find_next, (d, w) -> t.webView.findNext(true))
                .setNegativeButton(R.string.find_close, (d, w) -> t.webView.clearMatches())
                .setOnDismissListener(d -> {
                    t.webView.clearMatches();
                    t.webView.setFindListener(null);
                })
                .show();
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() > 0) t.webView.findAllAsync(s.toString());
                else t.webView.clearMatches();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            t.webView.findNext(true);
            return true;
        });
    }

    // ---------- Tab switcher ----------

    private void showTabSwitcher() {
        tabsAdapter.notifyDataSetChanged();
        tabSwitcher.setVisibility(View.VISIBLE);
        hideKeyboard();
        omnibox.clearFocus();
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
