package com.zerium.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
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
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.zerium.browser.dl.DownloadEngine;
import com.zerium.browser.dl.MediaRegistry;
import com.zerium.browser.dl.MediaRegistryHolder;
import com.zerium.browser.ui.MediaGrabberSheet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity implements MediaGrabberSheet.Host {

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
    private static final int MENU_SITE_SETTINGS = 18;
    private static final int MENU_DELETE_DATA = 19;
    private static final int MENU_BACK = 20;
    private static final int MENU_FORWARD = 21;
    private static final int MENU_RELOAD = 22;
    private static final int MENU_SHARE_QA = 23;
    private static final int MENU_MEDIA = 24;
    private static final int MENU_SPACES = 25;

    // Long-press context menu actions
    private static final int CTX_OPEN_NEW_TAB = 1;
    private static final int CTX_COPY = 2;
    private static final int CTX_SHARE = 3;
    private static final int CTX_DOWNLOAD_IMAGE = 4;

    private static final int REQ_FILE_CHOOSER = 41;
    private static final int REQ_PERMISSION = 42;
    private static final int REQ_NOTIF = 43;

    private Prefs prefs;
    private AdBlocker adBlocker;
    private BookmarksDB bookmarks;
    private HistoryDB history;
    private final TabManager tabs = new TabManager();
    private final AtomicLong sessionBlocked = new AtomicLong(0);
    private DownloadEngine engine;
    private MediaRegistry mediaRegistry;
    private String mediaGrabScript;
    private Spaces spaces;
    private TabGroups tabGroups;
    private LinearLayout spaceChips;

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
    private ItemTouchHelper dragHelper;

    // Long-press drag state (tab grouping inside the active space)
    private Tab draggedTab;
    private float dragDistance;
    private float dragLastX, dragLastY;
    private RecyclerView.ViewHolder hoverVH;
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
        spaces = Spaces.load(prefs.spacesJson());
        tabGroups = TabGroups.load(prefs.tabGroupsJson());
        // Retry space profiles the engine refused to delete last session —
        // runs before anything resolves them, so the engine lets go.
        String pending = prefs.pendingSpaceProfiles();
        if (pending != null && !pending.isEmpty()) {
            prefs.pendingSpaceProfiles(SpaceProfiles.retryPendingDeletions(pending));
        }
        engine = DownloadEngine.get(this);
        mediaRegistry = MediaRegistryHolder.get(this);
        mediaGrabScript = readAsset(this, "media-grab.js");
        adBlocker = new AdBlocker();
        adBlocker.init(this, prefs);
        bookmarks = new BookmarksDB(this);
        history = new HistoryDB(this);

        WebContainerLayout container = findViewById(R.id.webContainer);
        container.setGestureListener(new WebContainerLayout.Listener() {
            @Override
            public void onSwitchPrevious() { cycleTab(-1); }

            @Override
            public void onSwitchNext() { cycleTab(1); }
        });
        container.setGesturesEnabled(prefs.gestures());
        webContainer = container;
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
        spaceChips = findViewById(R.id.spaceChips);

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(0xFF000000);
        addContentView(fullscreenContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.GONE);

        // Soft M3 refresh indicator, tinted with the active primary.
        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary));

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
        tabsAdapter = new TabsAdapter(tabs.tabs(), tabs, tabGroups, new TabsAdapter.Listener() {
            @Override
            public void onOpen(Tab tab) {
                hideTabSwitcher();
                switchTab(tab);
            }

            @Override
            public void onClose(Tab tab) {
                closeTab(tab);
            }

            @Override
            public void onLongPress(Tab tab) {
                showMoveTabDialog(tab);
            }

            @Override
            public void onGroupToggle(TabGroups.Group group) {
                tabGroups.setCollapsed(group.id, !group.collapsed);
                persistGroups();
                tabsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onGroupMenu(TabGroups.Group group) {
                showGroupMenu(group);
            }
        });
        tabsAdapter.setActiveSpaceId(spaces.activeId());
        tabsGrid.setAdapter(tabsAdapter);
        dragHelper = buildDragHelper();
        dragHelper.attachToRecyclerView(tabsGrid);
        tabsAdapter.setDragStarter(vh -> dragHelper.startDrag(vh));
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
        findViewById(R.id.btnCloseIncognito).setOnClickListener(v -> closeAllIncognitoTabs());

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
            openExternalLink(intent.getDataString());
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                String url = Utils.smartUrl(text, prefs);
                if (url != null) openExternalLink(url);
            }
        }
    }

    /**
     * Opens a link handed over by another app (chooser, share sheet) in the
     * space chosen under Settings → Links. Only http(s) is accepted; the
     * default space is the landing zone, so an incoming link never ends up
     * in an isolated profile the user did not explicitly pick.
     */
    private void openExternalLink(String url) {
        if (url == null) return;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) return;
        int mode = prefs.externalLinks();
        if (mode == Prefs.LINKS_ASK) {
            showLinkSpacePicker(u);
            return;
        }
        openTab(u, false,
                mode == Prefs.LINKS_CURRENT ? spaces.activeId() : Spaces.DEFAULT_ID);
    }

    /** Space picker for the "Ask every time" external-link mode. */
    private void showLinkSpacePicker(final String url) {
        final java.util.List<Spaces.Space> all = spaces.all();
        if (all.isEmpty()) return;
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Spaces.Space sp = all.get(i);
            names[i] = sp.isolated ? sp.name + "  \u00b7  "
                    + getString(R.string.spaces_badge_isolated) : sp.name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.external_links_pick)
                .setItems(names, (d, which) -> openTab(url, false, all.get(which).id))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Tab lifecycle ----------

    private void openTab(String url, boolean incognito) {
        openTab(url, incognito, null);
    }

    /**
     * Opens a tab in a Zerium Space. New normal tabs join the active space;
     * incognito tabs belong to no space. Isolated spaces get their engine
     * profile attached as the very first WebView API call.
     */
    private void openTab(String url, boolean incognito, String spaceId) {
        WebView w = new WebView(this);
        String sp = incognito ? Spaces.DEFAULT_ID
                : (spaceId != null && spaces.get(spaceId) != null ? spaceId : spaces.activeId());
        SpaceProfiles.attachProfile(w, incognito ? null : sp);
        w.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        webContainer.addView(w);
        Tab tab = tabs.add(w, incognito);
        tab.spaceId = sp;
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
        if (!tab.incognito && tab.spaceId != null) {
            pruneEmptyGroups(tab.spaceId, null);
            persistGroups();
        }
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

    /** Closes the active space's tabs (the switcher's current view scope). */
    private void closeAllTabs() {
        String scope = spaces.activeId();
        int i = 0;
        while (i < tabs.tabs().size()) {
            Tab t = tabs.tabs().get(i);
            if (!t.incognito && scope.equals(t.spaceId)) {
                webContainer.removeView(t.webView);
                tabs.remove(t);
                t.destroy();
            } else {
                i++;
            }
        }
        hideTabSwitcher();
        if (tabs.count() == 0) openTab(null, false);
        else {
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
        tabGroups.removeSpace(scope);
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
        buildSpaceChips();
    }

    /** Closes every incognito tab in one step (tab-switcher toolbar action). */
    private void closeAllIncognitoTabs() {
        int removed = 0;
        int i = 0;
        while (i < tabs.tabs().size()) {
            Tab t = tabs.tabs().get(i);
            if (t.incognito) {
                webContainer.removeView(t.webView);
                tabs.remove(t);
                t.destroy();
                removed++;
            } else {
                i++;
            }
        }
        if (removed == 0) {
            toast(R.string.no_incognito_tabs);
            return;
        }
        if (tabs.count() == 0) {
            openTab(null, false);
        } else {
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
        tabsAdapter.notifyDataSetChanged();
        Toast.makeText(this, getString(R.string.incognito_tabs_closed, removed),
                Toast.LENGTH_SHORT).show();
    }

    /** Edge-swipe tab cycling; direction -1 previous, 1 next. */
    private void cycleTab(int dir) {
        int n = tabs.count();
        if (n < 2) return;
        int idx = ((tabs.current() + dir) % n + n) % n;
        switchTab(tabs.tabs().get(idx));
    }

    // ---------- WebView setup ----------

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(Tab tab) {
        WebView w = tab.webView;
        WebSettings s = w.getSettings();
        tab.mobileUA = s.getUserAgentString();
        applyWebSettings(s);
        // Space-aware cookie jar: isolated spaces carry their own profile
        // manager; everything else uses the global (default profile) one.
        CookieManager cm = SpaceProfiles.cookiesFor(tab.spaceId);
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

        // Long-press on a link or image opens the context menu
        // (open in new tab / copy / share / download).
        w.setOnLongClickListener(v -> showLinkContextMenu(tab));

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

        // Media grabber: in-page scanner runs at document start (early
        // MutationObserver capture) and reports through a per-tab bridge.
        try {
            if (prefs.mediaGrabber() && mediaGrabScript != null && !mediaGrabScript.isEmpty()
                    && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                java.util.Set<String> allOrigins = new java.util.HashSet<>(
                        java.util.Arrays.asList("http://*/*", "https://*/*"));
                WebViewCompat.addDocumentStartJavaScript(w, mediaGrabScript, allOrigins);
            }
        } catch (Exception ignored) {}
        if (prefs.mediaGrabber() && prefs.javascriptEnabled()) {
            w.addJavascriptInterface(new MediaBridge(tab), "__zeriumMedia");
        }

        // Anti-adblock bypass: engine runs at document start, before any page
        // script, so bait countermeasures are in place before detectors run.
        try {
            if (prefs.antiAdblock()
                    && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                String aabScript = AntiAdblock.documentStartScript(this, prefs);
                if (aabScript != null && !aabScript.isEmpty()) {
                    java.util.Set<String> allOrigins = new java.util.HashSet<>(
                            java.util.Arrays.asList("http://*/*", "https://*/*"));
                    WebViewCompat.addDocumentStartJavaScript(w, aabScript, allOrigins);
                }
            }
        } catch (Exception ignored) {}

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
            // Media grabber network sniffing (URL-shape based; the DOM scan
            // catches the mime-known cases this misses).
            if (prefs.mediaGrabber() && !tab.incognito) {
                String u = request.getUrl().toString();
                String mtag = MediaRegistry.classifyUrl(u);
                if (mtag != null) {
                    mediaRegistry.add(tab.id,
                            new MediaRegistry.Item(u, mtag, "", "", -1));
                }
            }
            // Anti-adblock layer: detector scripts are answered with a 404 so
            // walls that depend on them never arm. Non-main-frame only — a
            // navigation must never be blocked by this list. Works whether or
            // not ad blocking is on: this is blocker *detection* blocking.
            if (prefs.antiAdblock() && !request.isForMainFrame()) {
                String reqUrl = request.getUrl().toString();
                if (AntiAdblock.isDetectorUrl(reqUrl)) {
                    try {
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Blocked",
                                Collections.<String, String>emptyMap(),
                                new ByteArrayInputStream(new byte[0]));
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
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
            mediaRegistry.clear(tab.id);
            // Per-site JavaScript (site panel): enforce the effective value and
            // reload once when a navigation lands on a host with a different
            // rule than the tab currently runs (e.g. a link to another site).
            String host = Utils.hostOf(url);
            if (host != null && url != null && !url.startsWith("about:")) {
                boolean effectiveJs = prefs.javascriptEnabled()
                        && !Utils.siteListContains(prefs.jsOffSites(), host);
                try {
                    if (view.getSettings().getJavaScriptEnabled() != effectiveJs) {
                        view.getSettings().setJavaScriptEnabled(effectiveJs);
                        view.reload();
                        return;
                    }
                } catch (Exception ignored) {}
            }
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
            injectAabFallback(tab);
            injectMediaGrab(view);
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
        // Per-site desktop memory (site panel): hosts remembered as Desktop
        // load with the desktop UA from the very first request, so the site
        // never sees the mobile UA at all. The translate proxy host is
        // skipped — the memory belongs to the real site, not Google's proxy.
        String loadHost = Utils.hostOf(url);
        if (loadHost != null && !loadHost.endsWith(".translate.goog") && !tab.desktopMode
                && Utils.siteListContains(prefs.desktopSites(), loadHost)) {
            tab.desktopMode = true;
            WebSettings ws = tab.webView.getSettings();
            if (tab.mobileUA == null || tab.mobileUA.isEmpty()) tab.mobileUA = ws.getUserAgentString();
            ws.setUserAgentString(desktopUA());
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

    /**
     * Anti-adblock fallback for WebViews without document-start script
     * support: the engine is injected at page finish instead. Walls that arm
     * before that point are still caught by the engine's own MutationObserver
     * and scans; only the before-any-detection-script bait placement is lost.
     */
    private void injectAabFallback(Tab tab) {
        if (!prefs.antiAdblock() || tab.aabInjected
                || WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        String script = AntiAdblock.documentStartScript(this, prefs);
        if (script == null || script.isEmpty()) return;
        tab.aabInjected = true;
        tab.webView.evaluateJavascript(script, null);
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
        int n = tabs.count();
        btnTabs.setText(n > 99 ? "99+" : String.valueOf(n));
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

    // ---------- Menu ----------

    private void showMenu(View anchor) {
        Tab t = tabs.currentTab();
        boolean onPage = t != null && !isStartPage(t);
        java.util.List<MenuSheet.Entry> entries = new java.util.ArrayList<>();
        // Quick actions (Brave-style circular row).
        entries.add(MenuSheet.action(MENU_BACK, R.string.qa_back, R.drawable.ic_back,
                t != null && t.webView.canGoBack()));
        entries.add(MenuSheet.action(MENU_FORWARD, R.string.qa_forward, R.drawable.ic_forward,
                t != null && t.webView.canGoForward()));
        entries.add(MenuSheet.action(MENU_RELOAD, R.string.qa_refresh, R.drawable.ic_refresh, onPage));
        entries.add(MenuSheet.action(MENU_SHARE_QA, R.string.qa_share, R.drawable.ic_share, onPage));
        entries.add(MenuSheet.divider());
        // Browsing
        entries.add(MenuSheet.item(MENU_NEW_TAB, R.string.menu_new_tab, R.drawable.ic_plus));
        entries.add(MenuSheet.item(MENU_NEW_INCOGNITO, R.string.menu_new_incognito, R.drawable.ic_incognito));
        entries.add(MenuSheet.item(MENU_SPACES, R.string.menu_spaces, R.drawable.ic_spaces));
        entries.add(MenuSheet.divider());
        entries.add(MenuSheet.item(MENU_BOOKMARK_ADD, isCurrentBookmarked()
                        ? R.string.menu_remove_bookmark : R.string.menu_add_bookmark,
                R.drawable.ic_bookmark, onPage));
        entries.add(MenuSheet.item(MENU_BOOKMARKS, R.string.menu_bookmarks, R.drawable.ic_bookmark));
        entries.add(MenuSheet.item(MENU_HISTORY, R.string.menu_history, R.drawable.ic_history));
        entries.add(MenuSheet.item(MENU_DOWNLOADS, R.string.menu_downloads, R.drawable.ic_download));
        entries.add(MenuSheet.item(MENU_MEDIA, R.string.menu_media, R.drawable.ic_video,
                onPage && t != null && mediaRegistry.count(t.id) > 0));
        entries.add(MenuSheet.item(MENU_FIND, R.string.menu_find, R.drawable.ic_search, onPage));
        entries.add(MenuSheet.divider());
        // Page tools
        entries.add(MenuSheet.check(MENU_DESKTOP, R.string.menu_desktop, R.drawable.ic_desktop,
                t != null && t.desktopMode));
        entries.add(MenuSheet.item(MENU_READER, R.string.menu_reader, R.drawable.ic_reader, onPage));
        entries.add(MenuSheet.item(MENU_TRANSLATE, isOnTranslatedPage(t)
                        ? R.string.menu_view_original : R.string.menu_translate,
                R.drawable.ic_translate, onPage));
        entries.add(MenuSheet.item(MENU_PRINT, R.string.menu_print, R.drawable.ic_print, onPage));
        entries.add(MenuSheet.item(MENU_PIN, R.string.menu_pin, R.drawable.ic_download, onPage));
        entries.add(MenuSheet.item(MENU_SITE_SETTINGS, R.string.menu_site_settings,
                R.drawable.ic_globe, onPage));
        entries.add(MenuSheet.item(MENU_BLOCK_INFO, R.string.menu_block_info, R.drawable.ic_shield));
        entries.add(MenuSheet.divider());
        // App
        entries.add(MenuSheet.item(MENU_DELETE_DATA, R.string.menu_delete_data, R.drawable.ic_trash));
        entries.add(MenuSheet.item(MENU_SETTINGS, R.string.menu_settings, R.drawable.ic_gear));
        entries.add(MenuSheet.item(MENU_EXIT, R.string.menu_exit, R.drawable.ic_exit));
        MenuSheet.show(this, entries, e -> handleMenu(e.id));
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
            case MENU_SPACES: showSpacesSheet(); break;
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
                startActivity(new Intent(this, com.zerium.browser.ui.DownloadsActivity.class));
                break;
            case MENU_MEDIA:
                if (t != null) {
                    MediaGrabberSheet.show(getSupportFragmentManager(), t.id);
                }
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
            case MENU_SITE_SETTINGS: showSiteSettings(); break;
            case MENU_DESKTOP: toggleDesktop(); break;
            case MENU_READER: toggleReader(); break;
            case MENU_TRANSLATE: translatePage(); break;
            case MENU_PRINT: printPage(); break;
            case MENU_PIN: addToHomeScreen(); break;
            case MENU_SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case MENU_EXIT: finishAffinity(); break;
            case MENU_DELETE_DATA: deleteBrowsingData(); break;
            case MENU_BACK: {
                if (t != null && t.webView.canGoBack()) t.webView.goBack();
                break;
            }
            case MENU_FORWARD: {
                if (t != null && t.webView.canGoForward()) t.webView.goForward();
                break;
            }
            case MENU_RELOAD: {
                if (t != null && !isStartPage(t)) t.webView.reload();
                break;
            }
            case MENU_SHARE_QA: {
                if (t != null && !isStartPage(t)) {
                    Intent qa = new Intent(Intent.ACTION_SEND);
                    qa.setType("text/plain");
                    qa.putExtra(Intent.EXTRA_TEXT, t.url);
                    startActivity(Intent.createChooser(qa, getString(R.string.menu_share)));
                }
                break;
            }
        }
    }

    /** Brave-style "Delete browsing data": history, cookies and WebView
     *  caches. Cookies/site data clear across the default jar AND every
     *  isolated space's profile jar (androidx.webkit multi-profile). When
     *  the engine lacks multi-profile support there is only the shared jar
     *  — the documented shared-jar limitation of this edition. */
    private void deleteBrowsingData() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        CheckBox historyBox = new CheckBox(this);
        historyBox.setText(R.string.delete_data_history);
        historyBox.setChecked(true);
        CheckBox siteBox = new CheckBox(this);
        siteBox.setText(R.string.delete_data_site_data);
        siteBox.setChecked(true);
        CheckBox cacheBox = new CheckBox(this);
        cacheBox.setText(R.string.delete_data_cache);
        cacheBox.setChecked(true);
        box.addView(historyBox);
        box.addView(siteBox);
        box.addView(cacheBox);
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_data_title)
                .setView(box)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (historyBox.isChecked()) new HistoryDB(this).clear();
                    if (siteBox.isChecked()) {
                        SpaceProfiles.clearAllSiteData(spaces.all());
                        android.webkit.WebStorage.getInstance().deleteAllData();
                    }
                    if (cacheBox.isChecked()) {
                        for (Tab tab : tabs.tabs()) tab.webView.clearCache(true);
                    }
                    toast(R.string.delete_data_done);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showBlockInfo() {
        showProtection();
    }

    /**
     * Zerium Protection sheet: the four blocking toggles (ad blocking,
     * anti-adblock bypass, cosmetic filtering, automatic recovery) plus the
     * per-site "Fix this site" action and the block statistics. Switches that
     * affect document-start injection reload the current tab; the rest apply
     * live. Kept programmatic like the site-settings panel.
     */
    private void showProtection() {
        Tab t = tabs.currentTab();
        boolean onPage = t != null && !isStartPage(t);
        final String host = onPage ? Utils.hostOf(t.url) : null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);

        TextView stats = new TextView(this);
        stats.setTextSize(12.5f);
        stats.setText(getString(R.string.protection_stats,
                t == null ? 0 : t.blockedOnPage,
                sessionBlocked.get(),
                prefs.totalBlocked() + (t == null ? 0 : t.blockedOnPage)));
        box.addView(stats);
        box.addView(makeHairline());

        Switch swAds = new Switch(this);
        swAds.setPadding(0, dp(10), 0, dp(10));
        swAds.setText(R.string.protection_ads);
        swAds.setChecked(prefs.blockAds());
        swAds.setOnCheckedChangeListener((b, v) -> prefs.blockAds(v));
        box.addView(swAds);

        Switch swAa = new Switch(this);
        swAa.setPadding(0, dp(10), 0, dp(10));
        swAa.setText(R.string.protection_aa);
        swAa.setChecked(prefs.antiAdblock());
        swAa.setOnCheckedChangeListener((b, v) -> {
            prefs.antiAdblock(v);
            reloadIfBlockingChanged(t);
        });
        box.addView(swAa);

        Switch swCosmetic = new Switch(this);
        swCosmetic.setPadding(0, dp(10), 0, dp(10));
        swCosmetic.setText(R.string.protection_cosmetic);
        swCosmetic.setChecked(prefs.blockCosmetic());
        swCosmetic.setOnCheckedChangeListener((b, v) -> {
            prefs.blockCosmetic(v);
            reloadIfBlockingChanged(t);
        });
        box.addView(swCosmetic);

        Switch swAuto = new Switch(this);
        swAuto.setPadding(0, dp(10), 0, dp(10));
        swAuto.setText(R.string.protection_auto);
        swAuto.setChecked(prefs.antiAdblockAuto());
        swAuto.setEnabled(prefs.antiAdblock());
        swAuto.setOnCheckedChangeListener((b, v) -> {
            prefs.antiAdblockAuto(v);
            applyLiveAabConfig();
        });
        box.addView(swAuto);

        box.addView(makeHairline());
        TextView fixHint = new TextView(this);
        fixHint.setTextSize(12f);
        fixHint.setPadding(0, dp(8), 0, dp(2));
        fixHint.setText(R.string.protection_fix_hint);
        box.addView(fixHint);
        if (!prefs.antiAdblock()) {
            TextView offHint = new TextView(this);
            offHint.setTextSize(12f);
            offHint.setPadding(0, dp(2), 0, dp(2));
            offHint.setText(R.string.protection_aa_off_hint);
            box.addView(offHint);
        }

        TextView note = new TextView(this);
        note.setTextSize(11.5f);
        note.setPadding(0, dp(10), 0, 0);
        note.setText(R.string.protection_note);
        box.addView(note);

        AlertDialog.Builder b2 = new AlertDialog.Builder(this)
                .setTitle(R.string.protection_title)
                .setView(box)
                .setPositiveButton(R.string.ok, null);
        if (onPage && host != null && !host.isEmpty()) {
            b2.setNeutralButton(R.string.protection_fix, (d, w) -> toggleFixSite(host));
            b2.setNegativeButton(R.string.allow_site, (d, w) -> {
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
        b2.show();
    }

    private View makeHairline() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(0x1F888888);
        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** "Fix this site" toggle: lower removal threshold for this host + reload. */
    private void toggleFixSite(String host) {
        String cur = prefs.antiAdblockFixSites();
        java.util.List<String> list = AntiAdblockRules.parseFixSites(cur);
        boolean had = AntiAdblockRules.isFixSite(list, host);
        if (had) {
            list.removeIf(s -> s.equals(host) || host.endsWith("." + s) || s.endsWith("." + host));
            prefs.antiAdblockFixSites(String.join("\n", list));
            toast(getString(R.string.protection_fix_off, host));
        } else {
            list.add(host);
            prefs.antiAdblockFixSites(String.join("\n", list));
            toast(getString(R.string.protection_fix_on, host));
        }
        applyLiveAabConfig();
        Tab t = tabs.currentTab();
        if (t != null && !isStartPage(t)) t.webView.reload();
    }

    /** Push the current anti-adblock config into every live WebView. */
    private void applyLiveAabConfig() {
        String js = AntiAdblock.liveConfigUpdate(prefs);
        for (Tab tab : tabs.tabs()) {
            try {
                tab.webView.evaluateJavascript(js, null);
            } catch (Exception ignored) {}
        }
    }

    /** Toggles that change document-start injection need a fresh page. */
    private void reloadIfBlockingChanged(Tab t) {
        applyLiveAabConfig();
        if (t != null && !isStartPage(t)) t.webView.reload();
    }

    /**
     * Per-site settings panel for the current host: JavaScript, ad-blocking
     * exemption and Desktop-site memory. Everything is stored on top of the
     * global settings; per-site cookie rules are deliberately not offered —
     * the system WebView's CookieManager is global, so such a toggle could
     * not be enforced honestly.
     */
    private void showSiteSettings() {
        final Tab t = tabs.currentTab();
        if (t == null || isStartPage(t)) return;
        final String host = Utils.hostOf(t.url);
        if (host == null || host.isEmpty() || host.endsWith(".translate.goog")
                || host.equals("translate.goog")) {
            toast(R.string.no_site_settings);
            return;
        }
        final boolean jsOn = prefs.javascriptEnabled()
                && !Utils.siteListContains(prefs.jsOffSites(), host);
        final boolean exempt = Utils.siteListContains(prefs.allowlist(), host);
        final boolean desktopRemembered = Utils.siteListContains(prefs.desktopSites(), host);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        final CheckBox js = new CheckBox(this);
        js.setText(R.string.site_settings_js);
        js.setChecked(jsOn);
        box.addView(js);
        final CheckBox allow = new CheckBox(this);
        allow.setText(R.string.site_settings_allow);
        allow.setChecked(exempt);
        box.addView(allow);
        final CheckBox desk = new CheckBox(this);
        desk.setText(R.string.site_settings_desktop);
        desk.setChecked(desktopRemembered || t.desktopMode);
        box.addView(desk);
        TextView hint = new TextView(this);
        hint.setText(R.string.site_settings_hint);
        hint.setTextSize(12f);
        box.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.site_settings_title, host))
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    boolean changed = false;
                    boolean needsReload = false;
                    // JavaScript kill-list (only meaningful while global JS is on)
                    if (js.isChecked() != jsOn) {
                        prefs.setJsOffSites(js.isChecked()
                                ? Utils.siteListRemove(prefs.jsOffSites(), host)
                                : Utils.siteListAdd(prefs.jsOffSites(), host));
                        boolean effectiveJs = prefs.javascriptEnabled()
                                && !Utils.siteListContains(prefs.jsOffSites(), host);
                        try {
                            if (t.webView.getSettings().getJavaScriptEnabled() != effectiveJs) {
                                t.webView.getSettings().setJavaScriptEnabled(effectiveJs);
                                needsReload = true;
                            }
                        } catch (Exception ignored) {}
                        changed = true;
                    }
                    // Blocking exemption (allowlist) — no reload needed; the
                    // network layer consults it on every request.
                    if (allow.isChecked() != exempt) {
                        prefs.setAllowlist(allow.isChecked()
                                ? Utils.siteListAdd(prefs.allowlist(), host)
                                : Utils.siteListRemove(prefs.allowlist(), host));
                        adBlocker.rebuildAllowlist(prefs.allowlist());
                        changed = true;
                    }
                    // Desktop memory — applied to this tab immediately and to
                    // every future load of this host (the per-tab menu toggle
                    // stays ephemeral, exactly as documented since v1.5.0).
                    if (desk.isChecked() != (desktopRemembered || t.desktopMode)) {
                        prefs.setDesktopSites(desk.isChecked()
                                ? Utils.siteListAdd(prefs.desktopSites(), host)
                                : Utils.siteListRemove(prefs.desktopSites(), host));
                        t.desktopMode = desk.isChecked();
                        WebSettings s = t.webView.getSettings();
                        if (t.desktopMode) {
                            if (t.mobileUA == null || t.mobileUA.isEmpty()) {
                                t.mobileUA = s.getUserAgentString();
                            }
                            s.setUserAgentString(desktopUA());
                        } else {
                            s.setUserAgentString(t.mobileUA != null && !t.mobileUA.isEmpty()
                                    ? t.mobileUA : WebSettings.getDefaultUserAgent(this));
                        }
                        needsReload = true;
                        changed = true;
                    }
                    if (needsReload) t.webView.reload();
                    if (changed) toast(R.string.site_saved);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Long-press context menu on links and images. For image-anchored links
     * the WebView only reports the link target synchronously, so the menu
     * acts on the link (never on the image behind it — that is an honest
     * platform limit, no async hit-test hacks). Non-http targets (mailto:,
     * javascript:) can be copied and shared but never opened or downloaded.
     */
    private boolean showLinkContextMenu(Tab tab) {
        if (tabSwitcher.getVisibility() == View.VISIBLE) return false;
        WebView.HitTestResult r;
        try {
            r = tab.webView.getHitTestResult();
        } catch (Exception e) {
            return false;
        }
        if (r == null) return false;
        final String url = r.getExtra();
        if (url == null || url.isEmpty()) return false;
        int type = r.getType();
        boolean link = type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        boolean image = type == WebView.HitTestResult.IMAGE_TYPE;
        if (!link && !image) return false;
        boolean http = url.startsWith("http://") || url.startsWith("https://");

        java.util.List<String> items = new java.util.ArrayList<>();
        java.util.List<Integer> actions = new java.util.ArrayList<>();
        if (link) {
            if (http) {
                items.add(getString(R.string.ctx_open_new_tab));
                actions.add(CTX_OPEN_NEW_TAB);
            }
            items.add(getString(R.string.ctx_copy_link));
            actions.add(CTX_COPY);
            items.add(getString(R.string.ctx_share_link));
            actions.add(CTX_SHARE);
        } else { // plain image
            if (http) {
                items.add(getString(R.string.ctx_open_new_tab));
                actions.add(CTX_OPEN_NEW_TAB);
                items.add(getString(R.string.ctx_download_image));
                actions.add(CTX_DOWNLOAD_IMAGE);
            }
            items.add(getString(R.string.ctx_copy_link));
            actions.add(CTX_COPY);
        }
        if (items.isEmpty()) return false;

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    switch (actions.get(which)) {
                        case CTX_OPEN_NEW_TAB:
                            openTab(url, false);
                            break;
                        case CTX_COPY: {
                            ClipboardManager cm =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("link", url));
                                toast(R.string.copied);
                            }
                            break;
                        }
                        case CTX_SHARE: {
                            Intent si = new Intent(Intent.ACTION_SEND);
                            si.setType("text/plain");
                            si.putExtra(Intent.EXTRA_TEXT, url);
                            startActivity(Intent.createChooser(si, getString(R.string.menu_share)));
                            break;
                        }
                        case CTX_DOWNLOAD_IMAGE:
                            startDownload(url, tab.webView.getSettings().getUserAgentString(),
                                    null, null, 0);
                            break;
                    }
                })
                .show();
        return true;
    }

    private void showFindBar() {
        if (tabs.currentTab() == null) return;
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
        buildSpaceChips();
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

    /** Restores tabs (with their spaces) saved by {@link #saveSession}.
     *  Legacy "a||b" sessions land in the default space. */
    private void restoreSession() {
        String saved = prefs.savedTabs();
        int index = prefs.savedTabIndex();
        int opened = 0;
        if (saved != null && saved.startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(saved);
                for (int i = 0; i < arr.length() && opened < MAX_RESTORED_TABS; i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String u = o.optString("u", "");
                    String sp = o.optString("sp", Spaces.DEFAULT_ID);
                    if (u.isEmpty()) continue;
                    if (spaces.get(sp) == null) sp = Spaces.DEFAULT_ID;
                    openTab(u, false, sp);
                    // Re-bind the tab's group (group must exist in that space).
                    Tab rt = tabs.tabs().get(tabs.count() - 1);
                    String g = o.optString("g", "");
                    if (!g.isEmpty() && tabGroups.belongsTo(g, sp)) rt.groupId = g;
                    opened++;
                }
            } catch (Exception ignored) {}
        } else if (saved != null && !saved.isEmpty()) {
            for (String u : saved.split("\\|\\|")) {
                if (u == null || u.trim().isEmpty()) continue;
                if (opened >= MAX_RESTORED_TABS) break;
                openTab(u.trim(), false, Spaces.DEFAULT_ID);
                opened++;
            }
        }
        if (opened == 0) {
            openTab(null, false);
        } else {
            tabs.setCurrent(Math.max(0, Math.min(index, opened - 1)));
            // Land in the active space: jump to one of its tabs, or open a
            // fresh one there so the chrome always matches the chips.
            Tab cur = tabs.currentTab();
            if (cur != null && !cur.incognito && !spaces.activeId().equals(cur.spaceId)) {
                int idx = firstTabInSpace(spaces.activeId());
                if (idx >= 0) tabs.setCurrent(idx);
                else openTab(null, false);
            }
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
    }

    /** Persists every non-incognito tab with its space id. */
    private void saveSession() {
        try {
            JSONArray arr = new JSONArray();
            int current = 0;
            int i = 0;
            for (Tab t : tabs.tabs()) {
                if (t.incognito) continue;
                String u = t.url == null ? "" : t.url;
                if (u.startsWith("data:")) u = HOME_URL;
                if (i == tabs.current()) current = i;
                JSONObject o = new JSONObject();
                o.put("u", u);
                o.put("sp", t.spaceId == null ? Spaces.DEFAULT_ID : t.spaceId);
                if (t.groupId != null
                        && tabGroups.belongsTo(t.groupId, t.spaceId)) {
                    o.put("g", t.groupId);
                }
                arr.put(o);
                i++;
                if (i >= MAX_RESTORED_TABS) break;
            }
            prefs.savedTabs(arr.toString());
            prefs.savedTabIndex(current);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
        persistGroups();
        SpaceProfiles.flushAll(spaces.all());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed while we were away: text zoom applies live
        // to every tab, and a filter-list download (possibly completed in the
        // Settings screen) reloads the network blocklist without a restart.
        ((WebContainerLayout) webContainer).setGesturesEnabled(prefs.gestures());
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

    // ---------- Zerium Spaces ----------

    private void persistSpaces() {
        prefs.spacesJson(spaces.dump());
    }

    /** Open normal tabs currently living in a space. */
    private int countForSpace(String spaceId) {
        int n = 0;
        for (Tab t : tabs.tabs()) {
            if (!t.incognito && spaceId.equals(t.spaceId)) n++;
        }
        return n;
    }

    private int firstTabInSpace(String spaceId) {
        for (int i = 0; i < tabs.tabs().size(); i++) {
            Tab t = tabs.tabs().get(i);
            if (!t.incognito && spaceId.equals(t.spaceId)) return i;
        }
        return -1;
    }

    /** Rebuilds the switcher's space chip row (tabs of each space, active
     *  highlighted, plus a new-space chip). Long-press opens the manager. */
    private void buildSpaceChips() {
        if (spaceChips == null) return;
        spaceChips.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        String activeId = spaces.activeId();
        for (final Spaces.Space sp : spaces.all()) {
            TextView chip = spaceChip(sp.name + "  \u00b7  " + countForSpace(sp.id),
                    sp.id.equals(activeId), d,
                    () -> switchSpace(sp),
                    () -> showSpacesSheet());
            chip.setTag(sp.id); // drag-a-tab-onto-chip target (see resolveDrop)
            spaceChips.addView(chip);
        }
        spaceChips.addView(spaceChip("\u002b " + getString(R.string.spaces_new), false, d,
                this::showNewSpaceDialog, null));
    }

    private TextView spaceChip(String text, boolean active, float d,
                               Runnable onClick, Runnable onLongClick) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setSingleLine(true);
        chip.setPadding((int) (13 * d), (int) (6 * d), (int) (13 * d), (int) (6 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(ContextCompat.getColor(this, active
                ? R.color.primary : R.color.surface_container_high));
        chip.setBackground(bg);
        chip.setTextColor(ContextCompat.getColor(this, active
                ? R.color.on_primary : R.color.on_surface));
        chip.setOnClickListener(v -> onClick.run());
        if (onLongClick != null) {
            chip.setOnLongClickListener(v -> {
                onLongClick.run();
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) (8 * d), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    /** Switches to a space: re-filters the switcher and focuses one of its
     *  tabs (or opens a fresh one there). In-process — every space's tabs
     *  and profiles stay alive on this edition. */
    private void switchSpace(Spaces.Space sp) {
        if (sp == null || sp.id.equals(spaces.activeId())) return;
        spaces.setActive(sp.id);
        persistSpaces();
        tabsAdapter.setActiveSpaceId(sp.id);
        int idx = firstTabInSpace(sp.id);
        if (idx < 0) {
            openTab(null, false);
        } else {
            tabs.setCurrent(idx);
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
        tabsAdapter.notifyDataSetChanged();
        buildSpaceChips();
        toast(getString(R.string.spaces_switched, sp.name));
    }

    private void showSpacesSheet() {
        SpacesSheet.show(this, new SpacesSheet.Host() {
            @Override
            public Spaces spaces() {
                return spaces;
            }

            @Override
            public int tabCount(String spaceId) {
                return countForSpace(spaceId);
            }

            @Override
            public String footerText() {
                return SpaceProfiles.supported()
                        ? getString(R.string.spaces_footer_webview)
                        : getString(R.string.spaces_isolated_unsupported);
            }

            @Override
            public void onSwitch(Spaces.Space space) {
                switchSpace(space);
            }

            @Override
            public void onNewSpace() {
                showNewSpaceDialog();
            }

            @Override
            public void onRename(Spaces.Space space) {
                showRenameDialog(space);
            }

            @Override
            public void onDelete(Spaces.Space space) {
                deleteSpace(space);
            }
        });
    }

    private void showNewSpaceDialog() {
        SpacesSheet.promptNewSpace(this, SpaceProfiles.supported(), (name, isolated) -> {
            Spaces.Space sp = spaces.create(name, isolated && SpaceProfiles.supported());
            if (sp == null) return;
            persistSpaces();
            buildSpaceChips();
            toast(R.string.spaces_created);
        });
    }

    private void showRenameDialog(final Spaces.Space sp) {
        SpacesSheet.promptRename(this, sp.name, (name, unused) -> {
            spaces.rename(sp.id, name);
            persistSpaces();
            buildSpaceChips();
        });
    }

    private void deleteSpace(final Spaces.Space sp) {
        if (sp == null || sp.id.equals(Spaces.DEFAULT_ID)) {
            toast(R.string.spaces_default_locked);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.spaces_delete_title, sp.name))
                .setMessage(getString(R.string.spaces_delete_confirm,
                        sp.name, countForSpace(sp.id)))
                .setPositiveButton(R.string.delete, (dlg, w) -> reallyDeleteSpace(sp))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void reallyDeleteSpace(Spaces.Space sp) {
        // Close the space's tabs first: the engine refuses profile deletion
        // while living WebViews hold it.
        int i = 0;
        while (i < tabs.tabs().size()) {
            Tab t = tabs.tabs().get(i);
            if (!t.incognito && sp.id.equals(t.spaceId)) {
                webContainer.removeView(t.webView);
                tabs.remove(t);
                t.destroy();
            } else {
                i++;
            }
        }
        if (!SpaceProfiles.deleteProfileData(sp.id)) {
            // Engine still holds a reference — retry on next launch before
            // anything resolves the profile.
            String pending = prefs.pendingSpaceProfiles();
            String name = SpaceProfiles.profileName(sp.id);
            if (!pending.contains(name)) {
                prefs.pendingSpaceProfiles(pending.isEmpty() ? name : pending + "," + name);
            }
        }
        spaces.remove(sp.id);
        persistSpaces();
        tabGroups.removeSpace(sp.id);
        persistGroups();
        tabsAdapter.setActiveSpaceId(spaces.activeId());
        if (tabs.count() == 0) openTab(null, false);
        else {
            showCurrentWebView();
            loadPending(tabs.currentTab());
            updateChrome(tabs.currentTab());
        }
        tabsAdapter.notifyDataSetChanged();
        buildSpaceChips();
        toast(R.string.spaces_deleted);
    }

    /** Long-press on a tab card: move it to another space. Moving across an
     *  isolation boundary recreates the tab (the engine cannot migrate a
     *  live WebView between cookie jars), same-profile moves just re-tag. */
    private void showMoveTabDialog(final Tab t) {
        if (t.incognito) {
            toast(R.string.spaces_no_incognito);
            return;
        }
        final java.util.List<Spaces.Space> targets = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Spaces.Space sp : spaces.all()) {
            if (sp.id.equals(t.spaceId)) continue;
            targets.add(sp);
            names.add(sp.isolated ? sp.name + "  \u00b7  "
                    + getString(R.string.spaces_badge_isolated) : sp.name);
        }
        if (targets.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.spaces_move_title)
                .setMessage(R.string.spaces_move_note)
                .setItems(names.toArray(new String[0]), (dlg, which) ->
                        moveTabToSpace(t, targets.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moveTabToSpace(Tab t, Spaces.Space target) {
        if (t == null || target == null || target.id.equals(t.spaceId)) return;
        Spaces.Space source = spaces.get(t.spaceId);
        boolean sameProfile = !SpaceProfiles.isIsolated(source) && !SpaceProfiles.isIsolated(target);
        if (sameProfile) {
            String oldSpace = t.spaceId;
            t.spaceId = target.id;
            t.groupId = null; // groups live inside one space and never follow
            pruneEmptyGroups(oldSpace, null);
            persistGroups();
            persistSpaces();
            tabsAdapter.notifyDataSetChanged();
            buildSpaceChips();
            return;
        }
        String url = (t.url == null || t.url.isEmpty())
                ? (t.pendingUrl != null ? t.pendingUrl : HOME_URL) : t.url;
        closeTab(t);
        openTab(url, false, target.id);
        buildSpaceChips();
    }

    // ---------- Tab groups (inside Spaces) ----------

    private void persistGroups() {
        prefs.tabGroupsJson(tabGroups.dump());
    }

    /**
     * Long-press drag engine for grouping. Long-press starts a drag; the
     * drop position decides the action:
     * <ul>
     *   <li>release without moving — the old long-press behaviour (move to
     *       another space),</li>
     *   <li>drop on a space chip — move the tab to that space,</li>
     *   <li>drop on a tab — group the pair (or join the target's group),</li>
     *   <li>drop on a group header — join that group,</li>
     *   <li>drop on the "Ungrouped" header or empty space — ungroup.</li>
     * </ul>
     * The dragged tab's WebView is never recreated: only its group id moves.
     */
    private ItemTouchHelper buildDragHelper() {
        return new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // started from the card's long-press listener
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // ordering is session order; drag only groups
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) { }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                if (vh != null) {
                    draggedTab = tabAt(vh);
                    dragDistance = 0f;
                    vh.itemView.setAlpha(0.9f);
                    vh.itemView.setScaleX(1.04f);
                    vh.itemView.setScaleY(1.04f);
                } else {
                    if (draggedTab != null) {
                        resolveDrop();
                        // restore visuals (resolveDrop may have rebound rows)
                        tabsAdapter.notifyDataSetChanged();
                    }
                    setHover(hoverVH, false);
                    hoverVH = null;
                    draggedTab = null;
                }
                super.onSelectedChanged(vh, actionState);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                if (isCurrentlyActive && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    updateDragPointer(vh, dX, dY);
                    dragDistance = Math.max(dragDistance,
                            (float) Math.hypot(dX, dY));
                    updateHover();
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                vh.itemView.setAlpha(1f);
                vh.itemView.setScaleX(1f);
                vh.itemView.setScaleY(1f);
                super.clearView(rv, vh);
            }
        });
    }

    private static Tab tabAt(RecyclerView.ViewHolder vh) {
        Object tag = vh.itemView.getTag();
        return tag instanceof Tab ? (Tab) tag : null;
    }

    /** Screen-space centre of the dragged card, from layout pos + drag delta. */
    private void updateDragPointer(RecyclerView.ViewHolder vh, float dX, float dY) {
        int[] loc = new int[2];
        tabsGrid.getLocationOnScreen(loc);
        dragLastX = loc[0] + vh.itemView.getLeft() + dX + vh.itemView.getWidth() / 2f;
        dragLastY = loc[1] + vh.itemView.getTop() + dY + vh.itemView.getHeight() / 2f;
    }

    private void updateHover() {
        if (draggedTab == null) return;
        RecyclerView.ViewHolder h = hoverTargetAt(dragLastX, dragLastY);
        if (h == hoverVH) return;
        setHover(hoverVH, false);
        hoverVH = h;
        setHover(hoverVH, true);
    }

    private void setHover(RecyclerView.ViewHolder h, boolean on) {
        if (h == null) return;
        View it = h.itemView;
        it.setAlpha(on ? 0.7f : 1f);
    }

    /** What the given screen point hovers over: a Tab card, a Group header,
     *  the "Ungrouped" header tag — or null for empty space. */
    private Object dropTargetAt(float screenX, float screenY) {
        int[] loc = new int[2];
        tabsGrid.getLocationOnScreen(loc);
        View child = tabsGrid.findChildViewUnder(screenX - loc[0], screenY - loc[1]);
        if (child == null) return null;
        RecyclerView.ViewHolder h = tabsGrid.getChildViewHolder(child);
        if (draggedTab != null && tabAt(h) == draggedTab) return null;
        return child.getTag();
    }

    private RecyclerView.ViewHolder hoverTargetAt(float screenX, float screenY) {
        int[] loc = new int[2];
        tabsGrid.getLocationOnScreen(loc);
        View child = tabsGrid.findChildViewUnder(screenX - loc[0], screenY - loc[1]);
        if (child == null) return null;
        RecyclerView.ViewHolder h = tabsGrid.getChildViewHolder(child);
        if (draggedTab != null && tabAt(h) == draggedTab) return null;
        return h;
    }

    /** Space chip under the given screen point, or null. */
    private Spaces.Space chipAt(float screenX, float screenY) {
        if (spaceChips == null) return null;
        for (int i = 0; i < spaceChips.getChildCount(); i++) {
            View chip = spaceChips.getChildAt(i);
            Object tag = chip.getTag();
            if (!(tag instanceof String)) continue; // the "+ New space" chip
            int[] loc = new int[2];
            chip.getLocationOnScreen(loc);
            if (screenX >= loc[0] && screenX <= loc[0] + chip.getWidth()
                    && screenY >= loc[1] && screenY <= loc[1] + chip.getHeight()) {
                return spaces.get((String) tag);
            }
        }
        return null;
    }

    private void resolveDrop() {
        final Tab t = draggedTab;
        if (t == null) return;
        // Bare long-press (no real movement): keep the move-to-space dialog.
        if (dragDistance < 20 * getResources().getDisplayMetrics().density) {
            showMoveTabDialog(t);
            return;
        }
        Spaces.Space chip = chipAt(dragLastX, dragLastY);
        if (chip != null) {
            if (!chip.id.equals(t.spaceId)) moveTabToSpace(t, chip);
            return;
        }
        Object target = dropTargetAt(dragLastX, dragLastY);
        if (target instanceof Tab) {
            Tab o = (Tab) target;
            if (o == t || o.incognito || !o.spaceId.equals(t.spaceId)) return;
            TabGroups.Group og = o.groupId == null ? null : tabGroups.get(o.groupId);
            if (og != null) {
                assignToGroup(t, og);
            } else if (t.groupId != null) {
                ungroup(t);
            } else {
                createGroupWith(t, o);
            }
            return;
        }
        if (target instanceof TabGroups.Group) {
            assignToGroup(t, (TabGroups.Group) target);
            return;
        }
        if (target == TabsAdapter.TAG_UNGROUPED) {
            ungroup(t);
            return;
        }
        if (t.groupId != null) ungroup(t);
    }

    private void assignToGroup(Tab t, TabGroups.Group g) {
        if (t == null || g == null || !g.spaceId.equals(t.spaceId)) return;
        if (g.id.equals(t.groupId)) return;
        t.groupId = g.id;
        pruneEmptyGroups(t.spaceId, g.id);
        // A tab dropped into a collapsed group expands it so the move is visible.
        if (g.collapsed) tabGroups.setCollapsed(g.id, false);
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
    }

    /** Starts a new group holding exactly the two given same-space tabs. */
    private void createGroupWith(Tab a, Tab b) {
        if (a == null || b == null || !a.spaceId.equals(b.spaceId)) return;
        TabGroups.Group g = tabGroups.create(a.spaceId,
                getString(R.string.group_new_name), tabGroups.nextColor());
        if (g == null) return;
        a.groupId = g.id;
        b.groupId = g.id;
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
    }

    private void ungroup(Tab t) {
        if (t == null || t.groupId == null) return;
        t.groupId = null;
        pruneEmptyGroups(t.spaceId, null);
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
    }

    /** Deletes groups of the space that no longer have any member tabs.
     *  {@code keep} protects the group that just received a tab. */
    private void pruneEmptyGroups(String spaceId, String keep) {
        for (TabGroups.Group g : tabGroups.forSpace(spaceId)) {
            if (g.id.equals(keep)) continue;
            boolean hasMember = false;
            for (Tab t : tabs.tabs()) {
                if (!t.incognito && g.id.equals(t.groupId)) {
                    hasMember = true;
                    break;
                }
            }
            if (!hasMember) tabGroups.remove(g.id);
        }
    }

    /** Header long-press: rename, colour, ungroup all. */
    private void showGroupMenu(final TabGroups.Group g) {
        new AlertDialog.Builder(this)
                .setTitle(g.name)
                .setItems(new CharSequence[]{
                                getString(R.string.group_rename),
                                getString(R.string.group_colour),
                                getString(R.string.group_ungroup_all)},
                        (dlg, which) -> {
                            if (which == 0) promptRenameGroup(g);
                            else if (which == 1) promptGroupColor(g);
                            else ungroupAll(g);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptRenameGroup(final TabGroups.Group g) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(g.name);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_rename_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (tabGroups.rename(g.id, input.getText().toString())) {
                        persistGroups();
                        tabsAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptGroupColor(final TabGroups.Group g) {
        String[] names = getResources().getStringArray(R.array.group_colors);
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_colour)
                .setSingleChoiceItems(names, g.color, (d, which) -> {
                    tabGroups.setColor(g.id, which);
                    persistGroups();
                    tabsAdapter.notifyDataSetChanged();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void ungroupAll(final TabGroups.Group g) {
        for (Tab t : tabs.tabs()) {
            if (!t.incognito && g.id.equals(t.groupId)) t.groupId = null;
        }
        tabGroups.remove(g.id);
        persistGroups();
        tabsAdapter.notifyDataSetChanged();
        toast(R.string.group_removed);
    }

    // ---------- Downloads ----------

    private void startDownload(String url, String userAgent, String contentDisposition,
                               String mimeType, long contentLength) {
        try {
            ensureNotifPermission();
            String source = contentDisposition != null && !contentDisposition.isEmpty()
                    ? contentDisposition : url;
            Tab t = tabs.currentTab();
            engine.enqueue(url, Utils.fileNameFromUrl(source), mimeType, userAgent,
                    url, t != null ? t.url : "", t != null ? t.title : "",
                    false, prefs.turboDownloads());
            toast(R.string.download_started);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.download_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Entry point for media-grabber rows (videos, streams, files, blobs). */
    @Override
    public void onMediaDownloadRequested(MediaRegistry.Item item) {
        if (item == null || item.url == null) return;
        if (MediaRegistry.TAG_BLOB.equals(item.tag)) {
            captureBlob(item.url);
            return;
        }
        startTurboDownload(item.url, MediaRegistry.TAG_HLS.equals(item.tag), item.mime);
    }

    private void startTurboDownload(String url, boolean hls, String mime) {
        try {
            ensureNotifPermission();
            Tab t = tabs.currentTab();
            engine.enqueue(url, Utils.fileNameFromUrl(url), mime,
                    t != null ? t.webView.getSettings().getUserAgentString() : null,
                    url, t != null ? t.url : "", t != null ? t.title : "",
                    hls, prefs.turboDownloads());
            toast(R.string.download_started);
        } catch (Exception e) {
            toast(R.string.download_failed_generic);
        }
    }

    /** Asks the page to fetch a blob: URL and stream it over the bridge. */
    private void captureBlob(String blobUrl) {
        Tab t = tabs.currentTab();
        if (t == null || mediaGrabScript == null) return;
        String sid = Long.toString(System.currentTimeMillis(), 36)
                + (int) (Math.random() * 100);
        String safeUrl = blobUrl.replace("'", "");
        String js = "window.__zeriumMediaGrab && window.__zeriumMediaGrab('"
                + safeUrl + "','" + sid + "',300);";
        t.webView.evaluateJavascript(js, null);
        toast(R.string.download_started);
    }

    private void ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF);
        }
    }

    /** JavaScript bridge for the in-page media scanner (per tab). */
    private class MediaBridge {
        private final Tab tab;

        MediaBridge(Tab tab) { this.tab = tab; }

        @JavascriptInterface
        public void onMedia(String json) {
            if (!prefs.mediaGrabber() || tab.incognito) return;
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length() && i < 50; i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String u = o.optString("url", "");
                    if (u.isEmpty()) continue;
                    mediaRegistry.add(tab.id, new MediaRegistry.Item(
                            u, o.optString("tag", "video"), o.optString("mime", ""),
                            o.optString("label", ""), o.optLong("size", -1)));
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void onBlobBegin(String id, long size, String mime) {
            if (!prefs.mediaGrabber() || tab.incognito) return;
            String name = (tab.title == null || tab.title.isEmpty()
                    ? Utils.hostOf(tab.url) : tab.title);
            if (name == null || name.isEmpty()) name = "capture";
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            String ext = mime != null && mime.contains("video/") ? ".mp4"
                    : mime != null && mime.contains("audio/") ? ".m4a" : ".bin";
            engine.beginBlob(id, size, mime, name + ext);
        }

        @JavascriptInterface
        public void onBlobChunk(String id, String b64) {
            if (id == null || b64 == null || b64.isEmpty()) return;
            try {
                File f = engine.blobFile(id);
                if (f == null) return;
                byte[] data = Base64.getDecoder().decode(b64);
                try (FileOutputStream os = new FileOutputStream(f, true)) {
                    os.write(data);
                }
                engine.blobProgress(id, f.length());
            } catch (Exception e) {
                engine.endBlob(id, "write-failed");
            }
        }

        @JavascriptInterface
        public void onBlobEnd(String id, String error) {
            if (id == null) return;
            engine.endBlob(id, error == null ? "" : error);
            if ("too-large".equals(error)) {
                runOnUiThread(() -> toast(R.string.dl_blob_too_large));
            }
        }
    }

    /** Page-finish fallback injection for the media scanner (doc-start is
     *  the primary path; the script self-guards double injection). */
    private void injectMediaGrab(WebView view) {
        if (!prefs.mediaGrabber() || mediaGrabScript == null || mediaGrabScript.isEmpty()) {
            return;
        }
        try {
            view.evaluateJavascript(mediaGrabScript, null);
        } catch (Exception ignored) {}
    }

    private static String readAsset(Context c, String name) {
        try (InputStream is = c.getAssets().open(name)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
