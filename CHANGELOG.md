# Changelog

All notable changes to Zerium Browser are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html): **MAJOR** for engine/UX-breaking changes, **MINOR** for user-facing features, **PATCH** for fixes.

Stable builds are published as immutable releases on version tags (see [Releases](https://github.com/ansaribilal14/zerium-browser/releases)); the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- GeckoView engine track (v2.0): full migration assessment delivered in `docs/GECKOVIEW_MIGRATION.md`; next step is the Phase-0 spike branch. WebExtensions support, engine-level blocking, per-profile cookie isolation.
- Per-site toggles (JavaScript, cookies, blocking) via a site panel
- HTTPS-first mode with automatic upgrade and downgrade warnings

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
