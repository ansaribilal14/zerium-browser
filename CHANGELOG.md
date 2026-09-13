# Changelog

All notable changes to Zerium Browser are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html): **MAJOR** for engine/UX-breaking changes, **MINOR** for user-facing features, **PATCH** for fixes.

Stable builds are published as immutable releases on version tags (see [Releases](https://github.com/ansaribilal14/zerium-browser/releases)); the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- GeckoView engine track (v2.0): full migration assessment delivered in `docs/GECKOVIEW_MIGRATION.md`; next step is the Phase-0 spike branch. WebExtensions support, engine-level blocking, per-profile cookie isolation.
- Per-site toggles (JavaScript, cookies, blocking) via a site panel
- HTTPS-first mode with automatic upgrade and downgrade warnings

## [1.4.0] — 2026-09-14

### Changed
- **YouTube mid-roll blocking hardened (prune-before-load v2).** Two gaps in the response-pruning layer are closed. Endpoint matching no longer requires a query string — a bare `/youtubei/v1/player` request used to pass through unpruned — and `get_watch`/`ssap` are now covered alongside the existing player endpoints. XHR interception is rewritten to be order-independent: `response`/`responseText` getters are installed at `open()` and prune on access, so YouTube's own listeners (which register before `send()`) can no longer read raw ad data first, as the v1.2.0 listener allowed. Ad pruning additionally operates at the **renderer** level (`adSlotRenderer`, `adBreakAdRenderer`, `adPlacementRenderer`, `inVideoAdCta`), removing ad schedules nested under containers whose names we do not know, and the key lookup no longer resolves through `Object.prototype`. When present, YouTube's web "network machine" experiment flags are switched off client-side (a config toggle mirroring uBO's current quick-fixes; no client identity, user agent, or header is changed). The suppression script ships with a 21-case black-box test suite (`scripts/test_yt_block.js`): endpoint matching, fetch/XHR order-independence, renderer-level pruning, non-ad endpoint pass-through, and content integrity.

### Fixed
- **Skip-button sweep now covers the modern overlay layout** (`.ytp-ad-player-overlay-layout` skip container plus a class-substring fallback), so client-side mid-rolls that slip through are auto-skipped instead of relying on the fast-forward fallback.

### Honest scope
- A share of mid-roll ads is now delivered via **SSAP (server-side ad placement)**: the ad segments are stitched into the video stream itself. No client-side blocker can remove those from an already-stitched stream without masquerading as another client and forcing a stream reload (uBO's `serverContract` approach — deliberately rejected here for fragility and client misrepresentation). Client-side *scheduled* mid-rolls are pruned and never appear; SSAP-stitched ads that still reach the player are muted and fast-forwarded through by the confirmed-ad watchdog, exactly as observed in testing. Documented in `docs/CONTENT_BLOCKING.md`.

## [1.3.1] — 2026-09-14

### Fixed
- **Pull-to-refresh no longer hijacks upward scrolling.** Scrolling back up through a page (finger dragging down) triggered the refresh spinner instead of moving the page, anywhere above the gesture's start point. Root cause: `SwipeRefreshLayout.canChildScrollUp()` only asks its *direct* child — the plain `FrameLayout` tab container, which can never scroll — so the layout believed every page was at the top and armed the refresh gesture on any downward drag. The new `BrowserSwipeLayout` forwards the check to the actually visible WebView, so the refresh gesture arms only when the page genuinely sits at its top; scrolled pages scroll normally. The check runs per gesture (never cached), so tab switches, scroll restoration and back/forward navigation are always reflected without any wiring. The gesture is additionally disabled on the start page, where reloading the generated local page is meaningless (this also removes the pointless spinner flash when pulling down there).

## [1.3.0] — 2026-09-13

### Added
- **Tab switcher with live page previews.** Each card now shows a scaled screenshot of the tab's last loaded page (never captured for incognito tabs) with the site host beneath the title. Cards are larger with rounded corners, and the active tab carries an accent outline.
- **Expanded network blocking.** The curated URL-pattern rule set grew from 26 to ~60 rules: Google ad/measurement endpoints (including the IMA video-ad SDK and the GA4/GTM loaders), social and analytics pixels (Meta, TikTok, X, Pinterest, LinkedIn, Bing/Clarity, Hotjar, Yandex), header-bidding exchanges (PubMatic, Rubicon, Criteo, Index, Sovrn and peers), native/content-ad and popup networks (Taboola, Outbrain, MGID, RevContent, PopAds, PropellerAds, Adsterra and peers). Every rule is a plain substring match, deliberately conservative to avoid breaking page function; the full list is documented in `docs/CONTENT_BLOCKING.md`.

### Changed
- **Cosmetic filtering is now first-paint and ~12× larger.** The element-hiding list grew from 106 hand-curated selectors to 1,200 sanitized rules: a validated generic-hide subset of EasyList (CC-BY-SA-3.0, attribution and license recorded in the file header and `docs/RESEARCH_SUMMARY.md`) merged with the original curated additions, deduplicated, ad-related rules prioritized, and id/class/attribute kinds mixed. On WebViews that support document-start scripting the cosmetic script now runs at document start on every http(s) origin, so ad containers are hidden *before the page first paints* instead of after; page-finish injection remains as a fallback for older engines. Selectors are applied in comma-joined batches (one engine pass per batch, with per-selector fallback when a batch fails to parse) to keep the larger set cheap on mutation-heavy pages.
- **Start page redesigned.** Gradient wordmark, search pill with focus ring and submit button, eight quick links with colored monogram tiles, and a stats card carrying the all-time blocked count. The version string is now read from the app's package info instead of a hardcoded constant.
- **Browser chrome polish.** Taller rounded omnibox with an accent focus ring and scheme-stripped URL display (the padlock already conveys TLS state), hairline toolbar dividers, accent-tinted progress bar, and a rounded tab-count badge on the bottom bar.

## [1.2.1] — 2026-09-13

### Changed
- **Commit SHAs removed from published artifacts.** APK filenames are now `Zerium-v<version>-release.apk` / `Zerium-v<version>-debug.apk`, and auto-generated release notes no longer embed commit hashes (the "What's changed" list shows plain commit subjects). `SHA256SUMS.txt` is unaffected — those are content-integrity checksums of the APK files, not commit identifiers. Historical stable releases keep their original asset names (immutable by design); the rolling `latest` channel picks up the new naming from this build onward.

### Added
- **GeckoView migration assessment** (`docs/GECKOVIEW_MIGRATION.md`): the complete answer to "can we move to GeckoView without a Chromium fork" — what ports untouched (~70% of the codebase), what must be rewritten with an API-by-API WebView→GeckoSession mapping, what gets better (engine-level blocking via `webRequest`/`filterResponseData` and ETP, anti-fingerprinting, extension support), what it costs (APK size, memory, Mozilla train tracking), and a phased ~2–3-week plan targeting a v2.0.0 release.

## [1.2.0] — 2026-09-13

### Fixed
- **Main video no longer fast-forwards after an ad.** The v1.1.0 watchdog matched `.ytp-ad-player-overlay` / `.ytp-ad-module`, containers that can persist during normal playback, and reset playback to a hardcoded `1x` — so the main video was muted and fast-forwarded alongside the ad and user speed settings were clobbered. The in-stream fallback now triggers only on a confirmed in-stream ad (`.ad-showing` / `.ad-interrupting` on the player root), captures the user's rate and mute state *before* any change, and restores them exactly when the ad state ends. Overlay ads never touch playback.

### Added
- **Prune-before-load ("block") layer for YouTube**, mirroring uBlock Origin's maintained YouTube scriptlets (uAssets quick-fixes): `adPlacements`, `adSlots`, `playerAds`, `adBreaks` and related structures are deep-pruned from the initial player response and from `/youtubei/` fetch/XHR responses at any nesting depth, so the player never schedules those ads — most ads do not show up at all, matching Brave's aggressive-mode experience as closely as a WebView allows. The suppression script moved to a readable asset (`app/src/main/assets/yt-block.js`) with unit-tested prune logic.
- Instant skip: skip/overlay-close buttons are clicked the moment they appear (MutationObserver + rAF), with no artificial delay; the anti-adblock enforcement dialog is auto-dismissed.
- Expanded cosmetic hiding for YouTube's ad renderer elements (feed, search, masthead, companion, promoted sparkles).
- Network layer: all `/api/stats/` telemetry beacons and `play.google.com/log` are now blocked.

### Changed
- `YouTubeFilter` loads its script from assets via a new `ZeriumApp.appContext()` accessor; injection call sites guard against an empty script.
- `docs/CONTENT_BLOCKING.md` documents the four-layer design, the research basis, and the techniques evaluated and rejected (premium-client masquerade, ad-segment network blocking).

## [1.1.0] — 2026-09-13

### Fixed
- **Settings screen layout**: title and summary rows rendered with zero width because layout weights designed for vertical containers were reused inside horizontal rows. Every row now carries explicit layout parameters, and the screen gained a Material toolbar with back navigation and consistent card/section styling.
- **Bookmarks, History and Downloads screens** now share the same toolbar-based layout system instead of bare list screens.

### Added
- **YouTube ad suppression** (`YouTubeFilter`, on by default, toggle in Settings): a document-start script injected via `WebViewCompat.addDocumentStartJavaScript` before the player initializes. It prunes `adPlacements`, `adSlots` and `playerAds` from `/youtubei/` player responses through `fetch`/XHR hooks and a setter trap on `ytInitialPlayerResponse`, runs a watchdog that mutes and fast-forwards unskippable segments while auto-clicking skip and overlay-close controls, and CSS-hides remaining ad containers. This is the same client-side technique scriptlet-based blockers use on the web; network-level removal is impossible because in-stream ads share endpoints with the video itself (see `docs/CONTENT_BLOCKING.md`).
- **Release engineering**: immutable version tags with stable releases, SHA-256 checksums (`SHA256SUMS.txt`) on every release, structured auto-generated release notes, and version-derived APK filenames.

## [1.0.0] — 2026-09-13

### Added
- Initial release: privacy-first Android browser on the System WebView engine — a deliberate, documented tradeoff (`docs/ARCHITECTURE.md`) that keeps the project small, fast and fully CI-buildable.
- **Network-level blocking** of ads, trackers and malware domains via the bundled StevenBlack unified hosts list (~140k domains, MIT) plus a curated URL-pattern rule set, implemented in `shouldInterceptRequest`; every blocked request is counted per page, per session and all-time.
- **Cosmetic filtering** with a curated, sanitized element-hiding list and a debounced MutationObserver for dynamically inserted ads.
- **Tabs**: multi-tab browsing with a grid switcher, incognito tabs that skip history and bookmarks, session restore across restarts.
- **Privacy controls**: third-party cookie blocking (on by default), global JavaScript/cookie switches, Do Not Track + Global Privacy Control headers, per-site allowlist, one-tap data clearing.
- **Browser plumbing**: omnibox with URL/search detection, six search engines, bookmarks and history (SQLite), system DownloadManager integration, file upload, camera/microphone and geolocation prompts, SSL error warnings, find-in-page, share targets, full-screen video, Material You dynamic theming.
- **Filter list updates** from Settings with basic integrity validation.
- **CI/CD**: GitHub Actions builds signed release + debug APKs on every push to `main`, publishes them to the rolling `latest` release and sends Telegram notifications.

[unreleased]: https://github.com/ansaribilal14/zerium-browser/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/ansaribilal14/zerium-browser/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ansaribilal14/zerium-browser/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ansaribilal14/zerium-browser/releases/tag/v1.0.0
