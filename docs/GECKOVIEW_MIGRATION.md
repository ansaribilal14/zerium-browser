# GeckoView Migration Assessment

**Question:** *Can we migrate Zerium's existing browser layer to GeckoView while preserving everything we've already built — without a full Chromium fork?*

**Status update (v1.6.0):** Phase 0 is done — the `:gecko-spike` module exists in this repository, compiles in CI as a separate non-gating job, and boots a real GeckoSession. See [`GECKOVIEW_SPIKE.md`](GECKOVIEW_SPIKE.md). Everything below remains the assessment of what a real migration would take.

**Short answer: yes.** GeckoView is precisely the "no Chromium fork" route: it is Mozilla's production Gecko engine shipped as a prebuilt Android library (a Maven AAR, consumed like any dependency — no source checkout, no 100 GB build farm, CI stays feasible). But it is **not a drop-in swap**. The app layer of Zerium ports almost untouched; the engine wrapper layer (everything that touches `WebView` directly) must be rewritten against GeckoView's delegate model. This document is the honest, file-by-file assessment, the full API mapping, the costs, and a phased plan.

---

## 1. Why GeckoView, and why not a Chromium fork

The original project brief targeted a Chromium/Cromite fork. `docs/ARCHITECTURE.md` records why that was postponed: a Chromium checkout is ~100 GB, a full build needs 16 cores and 8+ hours, and GitHub's standard runners (4 cores, 14 GB RAM/disk) cannot do it. A fork also converts a one-maintainer project into a permanent build-infrastructure project.

GeckoView avoids every one of those problems:

| Constraint | Chromium fork | GeckoView embed |
|---|---|---|
| Source checkout | ~100 GB + depot_tools | None — binary AAR from Maven |
| Build cost | 16-core / 8 h+ | None (dependency download, ~2 min) |
| CI feasibility | Not on standard runners | Trivial — same Gradle build as today |
| Security patches | We would own them (dangerous) | Mozilla ships a new train every ~4 weeks |
| Real-world validation | Ours alone | Firefox for Android, Brave, DuckDuckGo Go, Tor Browser all ship GeckoView or forks of it |

Brave on Android is itself a `geckoview`-class consumer (a Chromium fork on the Java layer, but the point stands): embedding a mature engine is a legitimate, proven shipping strategy — not a compromise hack.

## 2. What ports untouched (~70% of the codebase)

Zerium was structured engine-agnostic from v1 (`Tab` wraps a browser view; storage and settings know nothing about WebView). These port as-is or with a type swap:

- **Tab management** — `TabManager`, `TabsAdapter`, session serialization. The `WebView` field in `Tab` becomes an interface handle backed by `GeckoSession`.
- **Storage** — `BookmarksDB`, `HistoryDB` (SQLite, dependency-free), `DownloadsActivity`, `BookmarksActivity`, `HistoryActivity`.
- **Settings** — `Prefs`, `SettingsActivity` (plus new engine-specific toggles).
- **App plumbing** — `MainActivity` chrome UI, `ZeriumApp`, `StartPage` (generated HTML renders fine in Gecko), `Utils`, all layouts/themes.
- **Everything outside the app** — CI pipeline, release channels, governance files, docs.

## 3. What must be rewritten (~30%) — the API mapping

WebView and GeckoView model the same concepts with different shapes. GeckoView replaces the callback-style clients with per-concern **delegates** on each `GeckoSession`, and global settings move to `GeckoRuntime`/`GeckoSessionSettings`.

| Current (WebView) | GeckoView equivalent | Effort |
|---|---|---|
| `WebView.loadUrl()` | `GeckoSession.load(Loader)` | Trivial |
| `WebViewClient.shouldOverrideUrlLoading` | `NavigationDelegate.onLoadRequest` | Low |
| `onPageStarted / onPageFinished` | `ProgressDelegate.onPageStart / onPageStop` | Low |
| `onProgressChanged` | `ProgressDelegate.onProgress` | Low |
| `WebViewClient.onReceivedError` | `NavigationDelegate.onLoadError` (can render custom error pages) | Low |
| history/back-forward | `session.goBack()/goForward()`, `NavigationDelegate.onHistoryState` | Low |
| `window.open` / target=_blank | `NavigationDelegate.onNewSession` → create new tab | Low |
| `WebChromeClient.onJsAlert/Confirm/Prompt` | `PromptDelegate.onAlert / onConfirm / onPrompt` | Low |
| `onShowFileChooser` | `PromptDelegate.onFilePrompt` | Low |
| `onGeolocationPermissionsShowPrompt` | `PermissionDelegate.onContentPermissionRequest` (Geolocation) | Low |
| `onPermissionRequest` (camera/mic) | `PermissionDelegate.onContentPermissionRequest` + `onAndroidPermissionsRequest` | Medium |
| `onShowCustomView` (fullscreen video) | `ContentDelegate.onFullScreen` | Low |
| `setDownloadListener` | `ContentDelegate.onExternalResponse` (drive our DownloadsActivity) | Medium |
| `findAllAsync` / find-in-page | `session.finder.find()` (`SessionFinder`) | Low |
| `CookieManager` | `GeckoRuntime.getStorageController()` (`StorageController`: per-domain clearing, `setCookiesEnabled`, persistence) | Medium |
| `WebSettings` (JS, UA, zoom, mixed content, dark) | `GeckoSessionSettings` per session + `RuntimeSettings` global | Medium |
| `shouldInterceptRequest` (AdBlocker) | **Upgrade:** engine-level `ContentBlockingController` (ETP Strict/Custom) + bundled WebExtension `webRequest.onBeforeRequest` (blocking MV2) | Medium — behavior improves |
| `WebViewCompat.addDocumentStartJavaScript` (yt-block.js) | **Upgrade:** WebExtension content script / user script with `run_at: document_start` | Medium — sandbox adaptation |
| `onRenderProcessGone` | `ContentDelegate.onCrash / onKillProcess` (per-tab crash recovery) | Low |

Two lines of that table deserve emphasis because they are the strategic win:

- **Network blocking becomes engine-level.** `AdBlocker` currently runs in `shouldInterceptRequest` — a WebView hook that only sees what the page fetches and can only kill requests. On GeckoView, a bundled WebExtension gets Mozilla's `webRequest` API with **`filterResponseData`** — the same engine-level response-body rewriting that uBlock Origin and AdGuard use on Firefox desktop/Android. The `/youtubei/` ad-pruning in `yt-block.js` would move **out of page JavaScript into the engine's network stack**, which is the closest any embeddable Android engine gets to Brave's adblock-rust placement.
- **Content blocking is built into the engine.** `ContentBlockingController` gives ETP Strict/Custom categories, the Disconnect list, fingerprinting and cryptomining protection, and per-origin exception management — maintained by Mozilla, not by us.

## 4. What the migration buys (ranked)

1. **Engine-level ad/tracker blocking** — the Brave-class behavior the project brief asks for: blocking decisions in the network stack, not JS hooks. Extensions-level `webRequest` + `filterResponseData` + ETP categories.
2. **Real anti-fingerprinting** — Gecko ships `resistFingerprinting` and first-party isolation; System WebView exposes nothing comparable.
3. **Extension support** — GeckoView supports bundled MV2/MV3 extensions. Zerium could ship its blocking as a proper extension (auditable, updatable outside app releases) and later allow user-installed ones.
4. **Per-profile storage isolation** — `StorageController` enables per-session cookie jars (the "per-profile cookie isolation" item already on the roadmap/unreleased section of the changelog).
5. **Independent security cadence** — engine patches arrive with Mozilla trains without us maintaining anything.

## 5. What it costs (stated plainly)

- **APK size: 5.3 MB → ~85–105 MB.** GeckoView bundles `libxul.so` for every ABI in a universal APK (Firefox for Android is ~90 MB for the same reason). Per-ABI splits or App Bundle delivery can cut device-download size, but GitHub-APK distribution means universal files. This is the single largest user-facing regression and must be stated in the README the day it ships.
- **Startup and memory.** First `GeckoRuntime` init and profile load are heavier than WebView creation; low-end devices will feel it (hundreds of MB RSS under load vs WebView's shared-process model, which other apps pre-pay for).
- **Train tracking / API churn.** GeckoView has a new release every ~4 weeks (`org.mozilla.geckoview:geckoview:<train>.0.<n>` from Maven Central). We pin a release train and bump deliberately; delegate signatures occasionally change across trains, so each bump is a small review, not a fire-and-forget upgrade.
- **No full `shouldInterceptRequest` parity.** GeckoView can block, redirect, and rewrite response bodies via the WebExtension API, but the WebView trick of synthesizing arbitrary local responses (Zerium uses it only for blocked-request 404s — trivially replaced) has no general equivalent. Anything that relied on custom-response injection would need rethinking; today nothing critical does.
- **The YouTube script must be ported, not copied.** `yt-block.js` runs in a content-script sandbox: `window.fetch`/XHR hooks and the `ytInitialPlayerResponse` setter trap need `exportFunction`/`cloneInto`-style shims (or move wholesale into `filterResponseData`, which is the stronger position anyway). The arms race restarts — with better tools.
- **Dual-engine QA is a trap.** Keeping a WebView flavor "for small APKs" doubles the test matrix forever. The recommendation is a hard switch after parity, not parallel flavors.

Play Store policy is not a blocker: GeckoView-based browsers are first-class on Play (Firefox, Brave, DuckDuckGo).

## 6. Phased plan (single maintainer, ~2–3 focused weeks)

| Phase | Scope | Est. |
|---|---|---|
| **0 — Spike** | Branch `geckoview-spike`: add the AAR, `GeckoRuntime` + `GeckoSession` in a bare activity, load pages, exercise delegates, confirm runner build time | 1–2 days |
| **1 — Engine abstraction** | Extract a `BrowserEngine` interface out of `Tab`; implement `GeckoEngine` (all delegates wired to existing UI callbacks); WebView impl stays temporarily as fallback | 3–5 days |
| **2 — Blocking port** | ETP Strict as default; hosts list + cosmetic lists as a bundled extension; port the YouTube prune to `filterResponseData` / content script | 2–4 days |
| **3 — Parity sweep** | Downloads, prompts, fullscreen, find-in-page, history, cookie controls, settings toggles, session restore; fix gaps against the mapping table | 3–5 days |
| **4 — Switch & ship** | GV becomes the only engine flavor; README/limitations rewritten honestly; ship as **v2.0.0** (a MINOR bump would undersell an engine change) | 1–2 days |

CI impact: none structurally — same Gradle build, artifact grows to the ~90 MB class, runners stay fine.

## 7. Recommendation

**Yes — migrate, as the v2.0 track, not as a v1.x patch.** The v1 WebView line keeps shipping quick fixes (as 1.2.x just did) while the `geckoview-spike` branch starts Phase 0. The migration preserves tabs, storage, settings, UI, and release engineering; it rewrites exactly the layer that has to change; and it converts the project's biggest honest limitation ("client-side-only blocking") into its strongest feature (engine-level blocking with Mozilla's maintenance behind it). The costs — size, memory, train tracking — are real, bounded, and worth stating in the README rather than discovering later.

## References

- GeckoView documentation & javadoc — https://mozilla.github.io/geckoview/ (consumer docs), https://hg.mozilla.org (source), Maven Central `org.mozilla.geckoview:geckoview`
- Firefox for Android (Fenix) — the reference GeckoView consumer; its `BrowserEngine` abstraction (android-components) is the architectural precedent for Phase 1
- uBlock Origin Firefox response filtering (`filterResponseData`) — https://github.com/gorhill/uBlock
- AdGuard for Android technique notes — https://github.com/AdguardTeam
- This repo: `docs/ARCHITECTURE.md` (engine decision matrix), `docs/CONTENT_BLOCKING.md` (current WebView-era blocking ceiling)
