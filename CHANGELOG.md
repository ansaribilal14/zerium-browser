# Changelog

All notable changes to Zerium Browser are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html): **MAJOR** for engine/UX-breaking changes, **MINOR** for user-facing features, **PATCH** for fixes.

Stable builds are published as immutable releases on version tags (see [Releases](https://github.com/ansaribilal14/zerium-browser/releases)); the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- Local export/import of user data (bookmarks, history, allowlist, per-site settings) — the honest, serverless alternative to sync (see `docs/SYNC_EVALUATION.md`).
- GeckoView engine track (v2.0): Phase-0 spike is in-repo and green in CI; the remaining phases follow `docs/GECKOVIEW_MIGRATION.md` behind the decision gate. WebExtensions support, engine-level blocking, per-profile cookie isolation.

## [1.8.0] — 2026-09-19

### Added
- **Brave-style menu.** The old popup menu is a sectioned bottom sheet now: a circular quick-action row (back / forward / refresh / share), then icon sections for browsing (new/incognito tab, bookmark add/remove, bookmarks, history, downloads, find in page), page tools (desktop site, reader view, translate, print, add-to-home-screen, site settings, blocked-on-this-page) and app entries (Delete browsing data, Settings, Exit).
- **Delete browsing data.** One dialog with three checkboxes — browsing history, cookies and site data, cached images and files — clearing the WebView cookie jar, web storage and every open tab's cache. Honest scope note in the dialog's code and docs: the system WebView keeps one shared cookie jar, so cookie clearing is global (the GeckoView edition does not share this constraint).
- **Privacy Stats card on the start page.** Three metrics in the Brave style — Trackers & Ads Blocked, Est. Data Saved, Est. Time Saved — using Brave's published conservative formula (≈50 KB and ≈50 ms per blocked request), computed on-device from the existing block counter and labelled "Est.".
- **Search suggestions on the start page.** While you type in the start page's search box, suggestions come from DuckDuckGo's suggestion endpoint (capped at six, requested only while typing there, fails silently offline — nothing fires in the omnibox or anywhere else).
- **Favicon shortcut tiles.** Start-page tiles now try the site's favicon over the colored monogram glyph and fall back to the monogram when the icon is missing (SmartCookieWeb's established fallback pattern).

### Changed
- The start page no longer pops the keyboard open on every new tab (Brave behavior); the search field keeps focus behavior on tap.

## [1.7.0] — 2026-09-19

### Changed
- **Material 3 soft-UI overhaul.** The whole interface moves to a tonal, rounded design language: a complete M3 color-role palette (primary/secondary/tertiary containers, surfaceContainer tiers, outline variants) in light and dark, a soft shape system (16/20/28 dp component radii, a 26 dp pill omnibox, 24 dp elevation-less tonal cards, 22 dp tab cards, tonal toolbars and bars), a primary-tinted pull-to-refresh indicator, and a softened start page (pill search field, rounded tiles, calm shadows). Dynamic color on Android 12+ still personalizes the theme; the static tonal palette covers everything else. Status-bar and navigation-bar colors follow the tonal surfaces with matching light/dark icons.

### Fixed
- The tab-count badge now caps at "99+" instead of overflowing its pill.
- Fullscreen video renders over an opaque black backdrop instead of transparent leftovers.
- The Downloads screen refreshes sizes, progress and statuses whenever it reappears (it previously froze at what was loaded on first open).

## [1.6.0] — 2026-09-19

### Added
- **Site settings panel.** A new *Site settings* menu entry opens a per-site panel for the host you are on: **JavaScript** on/off, **exemption from ad blocking** (writes the same allowlist as the existing quick action), and **Desktop-site memory** — hosts remembered here load with the desktop user agent from the very first request of every future visit, and the per-tab menu toggle from v1.5.0 stays available on top. The panel states plainly what the system WebView cannot do: per-site cookie rules are not offered, because the WebView cookie jar is global and such a toggle could not be enforced honestly. Per-site JavaScript is a kill-list that applies while JavaScript is on globally.
- **Long-press context menu.** Pressing and holding a link now offers *Open in new tab*, *Copy link address* and *Share link*; a plain image additionally offers *Download image* (through the system DownloadManager). Non-http targets (`mailto:` and friends) can be copied and shared but never opened or downloaded. For image-anchored links the menu acts on the link — the WebView only reports the link target synchronously, and no async hit-test workaround is used.
- **Edge-swipe tab switching.** A clearly horizontal drag from the left screen edge (moving right) switches to the previous tab; from the right edge (moving left) to the next one — UC-Browser style. The gesture only arms when the touch starts within 24 dp of an edge, the drag exceeds 56 dp while staying dominantly horizontal, and the visible WebView cannot scroll horizontally in that direction, so carousels, sliders and map pans always keep their own swipes; vertical drags are abandoned to the page and pull-to-refresh as before. A new *Edge swipe to switch tabs* setting (on by default) controls it.
- **Password autofill, surfaced honestly.** A new Settings row shows the credential manager currently enabled at the Android system level (or *None set*) and opens the system autofill picker. Zerium itself never stores, reads or transmits passwords (`setSavePassword` stays off); filling is provided entirely by the user's chosen service (Bitwarden, KeePassDX, system providers) through the Android autofill framework, which the WebView supports natively on API 26+.
- **Downloads screen detail.** The downloads list now shows the file size for completed downloads, live progress percentages for running ones, and plain-text failure reasons (out of space / server error / file error) for failed ones.
- **Close incognito in one tap.** The tab switcher toolbar gains a *Close incognito* action that closes every incognito tab at once and reports how many; regular tabs are untouched.
- **GeckoView Phase-0 spike (no user-facing change).** A separate `:gecko-spike` Gradle module boots a real GeckoSession (Mozilla GeckoView 133 from Mozilla's Maven repository) and is compiled in CI by a dedicated non-gating job. The production app still ships on the System WebView; the spike proves the toolchain from the [migration plan](docs/GECKOVIEW_MIGRATION.md) and is documented in `docs/GECKOVIEW_SPIKE.md`.
- **Sync: evaluated, honestly not shipped.** The full assessment lives in `docs/SYNC_EVALUATION.md`: every real sync variant needs a server, the credible options (Firefox Sync protocol, Brave-style chains, a Zerium-hosted service) are each weighed against the project's zero-backend reality, and the honest near-term alternative (local export/import of user data) is on the roadmap instead. Nothing in the app syncs or implies syncing.

### Honest scope
- Per-site JavaScript is a kill-list on top of the global switch: with JavaScript disabled globally, no per-site override can turn it back on (a WebView limit, stated in the panel).
- Per-site desktop memory applies at load time (typed URLs, home tiles, restores). A link click into a remembered host from a non-remembered page keeps the current tab's UA until the next explicit load of that host.
- The edge-swipe inset (24 dp) sits inside the system gesture-navigation exclusion zone, so with system gesture navigation the OS edge zone may take part of the swipe area; the gesture is most reliable with 3-button navigation or a light swipe from slightly further in.
- Context-menu actions act on what the WebView reports synchronously: image-anchored links expose the link, not the image, and JS-driven elements report nothing (no menu).
- Password autofill depends entirely on the user's system service; Zerium only opens the system screen and shows its status.

## [1.5.0] — 2026-09-15

### Added
- **Desktop site, per tab.** The menu gains a checkable *Desktop site* toggle that swaps the tab's user agent to a desktop Chromium string and reloads. The UA is derived at runtime from the device's own WebView engine (current Chromium major in reduced `major.0.0.0` form on a Linux X11 platform token) instead of a hardcoded stale version — old pinned versions now trigger Google's "unsupported browser" walls. Turning it off restores the tab's original mobile UA. Pattern proven in Lightning Browser and EinkBro.
- **Reader view.** *Reader view* re-renders the current page as clean, theme-aware typography with a title, byline and estimated reading time (265 wpm, floored at 1 minute). Extraction uses Mozilla's Readability library (v0.6.0, Apache-2.0, attribution in the asset header) injected via `evaluateJavascript` so page CSP cannot block it; the original DOM is snapshotted on the page itself and restored exactly on toggle-off. The reader never re-fetches anything — paywalled pages show what the WebView already holds — and short or non-article pages get an honest "does not look like an article" message instead of empty content.
- **Translate page.** Translates the current page through Google's `translate.goog` proxy in the same tab (subsequent links stay translated), targeting the device language, with no API key. While on a proxy page the menu offers *View original*, which returns to the stored pre-translation URL (or a best-effort reconstruction when the translation was opened from a link). No personal data leaves the device beyond what the translation service itself sees — same as entering the URL there manually.
- **Print / Save as PDF.** *Print* hands the current page to the Android printing framework (`createPrintDocumentAdapter`), whose destination picker includes Save as PDF. Only offered on loaded pages, per the framework's own guidance.
- **Add to home screen.** Pins a launcher shortcut for the current page with the site's favicon as the icon (captured via `onReceivedIcon`) and the page title as the label. Uses pinned-shortcut APIs (API 26+, no legacy path needed) and degrades to a toast on launchers that refuse pinning.
- **Find bar rebuilt.** Find-in-page moved from a dialog to an inline bar docked under the toolbar: debounced `findAllAsync`, previous/next navigation, a `current/total` counter that updates only when counting finishes (avoiding flicker, with the classic 0-based-ordinal off-by-one handled), and proper keyboard/clear handling. Back, tab switch and tab close all dismiss it and clear matches.
- **HTTPS upgrade for main frames.** Main-frame `http://` navigations are rewritten to `https://` before they leave the browser (local hosts, private LAN ranges and IPv6 literals are skipped). Invalid certificates still raise the existing user decision dialog, so a failed upgrade is always visible rather than silently downgraded.
- **intent:// and external-app links handled properly.** `intent://` URIs are parsed and dispatched; `market://` links fall back to the Play Store web page; every other unhandled scheme now toasts "No app found to open this link" instead of failing silently.
- **Custom search engines.** Settings → *Custom search engines* lets you add engines (name + URL with a `%s` placeholder), set them as default or delete them; they appear in the omnibox search, the start-page search pill and the engine picker. Defaults fall back safely if the selected engine is deleted.
- **Dynamic start-page shortcuts.** The start-page tile grid is no longer hardcoded: by default it blends your most-visited sites from history (one tile per site, search-result pages excluded) with the built-in defaults, or you can define a custom tile list (Settings → *Home screen shortcuts*). Tile markup is HTML-escaped.
- **Automatic filter-list updates.** A new *Keep filter lists updated* setting (on by default) refreshes both the hosts blocklist and the cosmetic element-hiding rules about once a week — or manually via the existing *Update filter lists* row, which now updates both lists. Downloads are validated (size + content marker) and swapped atomically; a failed download can never degrade blocking. Updated lists apply without a restart: the network blocklist reloads on the next Settings visit / app resume, and newly loaded pages pick up the refreshed cosmetic script. Sources: StevenBlack hosts (MIT) upstream and the Zerium project's own cosmetic list on GitHub.

### Changed
- **New browsing controls in Settings.** Web text size (50–200%, applied live to all open tabs), Force enable zoom (rewrites the page's viewport meta at document start, mirroring Firefox Focus / Chrome), media autoplay (blocked by default as before, now configurable), and pull-to-refresh (the gesture from the v1.3.1 fix is now a toggle).
- **Allowlist quick action.** The *Blocked on this page* dialog now has an *Allow this site* button that adds the current host to the blocking allowlist immediately (duplicated entries are detected; the running AdBlocker rebuilds on the spot).

### Honest scope
- Reader view is a snapshot of whatever the WebView already rendered: hard-paywalled pages yield their stub, and infinite-scroll pages capture what was loaded when you toggled it. On sites with a strict CSP the reader's inline stylesheet may be dropped — content still renders, just with default styling.
- HTTPS upgrade covers main frames only; sub-resource upgrades (mixed content) remain the WebView's compatibility-mode decision. Sites without TLS keep working through the existing warning dialog.
- The translate proxy and filter-list updater add two new network contacts (`translate.goog` only when you press Translate; `raw.githubusercontent.com`/StevenBlack only when lists update). Documented in `docs/PRIVACY.md`.
- Reader text extraction by Mozilla Readability is used verbatim; no readability heuristics of our own are layered on top.

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
