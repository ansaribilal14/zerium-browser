# Changelog

All notable changes to Zerium Browser are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html): **MAJOR** for engine/UX-breaking changes, **MINOR** for user-facing features, **PATCH** for fixes.

Stable builds are published as immutable releases on version tags (see [Releases](https://github.com/ansaribilal14/zerium-browser/releases)); the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- GeckoView engine evaluation track (WebExtensions support, per-profile cookie isolation)
- Per-site toggles (JavaScript, cookies, blocking) via a site panel
- HTTPS-first mode with automatic upgrade and downgrade warnings

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

[unreleased]: https://github.com/ansaribilal14/zerium-browser/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/ansaribilal14/zerium-browser/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ansaribilal14/zerium-browser/releases/tag/v1.0.0
