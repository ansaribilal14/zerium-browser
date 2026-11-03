# Changelog

All notable changes to Zerium Browser are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html): **MAJOR** for engine/UX-breaking changes, **MINOR** for user-facing features, **PATCH** for fixes.

Stable builds are published as immutable releases on version tags (see [Releases](https://github.com/ansaribilal14/zerium-browser/releases)); the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- Local export/import of user data (bookmarks, history, allowlist, per-site settings) — the honest, serverless alternative to sync (see `docs/SYNC_EVALUATION.md`).
- GeckoView engine track (v2.0): Phase-0 spike is in-repo and green in CI; the remaining phases follow `docs/GECKOVIEW_MIGRATION.md` behind the decision gate. WebExtensions support, engine-level blocking, per-profile cookie isolation.
- Spaces follow-ups: per-space colored identities, space-aware start-page tiles.
- Anti-adblock follow-ups: remote rule updates for the detector-script list, a per-site "bypass active" indicator in the Protection sheet, Zerium G port of the same engine.

## [1.15.0] — 2026-11-03

Address-bar correctness, native omnibox suggestions, genuine private sessions and a Chrome-style Normal/Private tab switcher.

### Fixed
- **`about:blank` never poses as a website URL.** Internal pages (`about:*`, `data:*`, `javascript:*`) now show an empty address field with the start-page hint; only real HTTP/HTTPS URLs (host, path, query intact) reach the omnibox. The same classification keeps internal and blank/error pages out of browsing history (previously `about:blank` slipped past the `data:`-only filter).
- **The omnibox never overwrites what you are typing.** Page updates — redirects, back/forward, title changes — no longer clobber focused user text (Chrome behaviour); tab switches close omnibox editing first.
- The tab-switcher **New Tab button works again** (its click handler was lost in the v1.14.0 redesign).

### Added
- **Genuine private sessions.** Previously an incognito WebView had no profile attached and silently shared the default cookie jar — private tabs were not private. Each incognito session now gets one ephemeral engine profile (`incognito_<token>`, WebView multi-profile): all private tabs of the session share it; nothing is shared with normal tabs, Spaces or other sessions. When the last private tab closes, cookies and site storage are wiped and the profile is deleted (WebViews destroyed first; a refused deletion is retried at the next startup, before any profile resolves or WebView exists — same contract as Space profiles). If the WebView provider lacks MULTI_PROFILE support, private tabs are refused with an explanation instead of running on the default profile.
- **Native omnibox suggestions.** Focusing the address field shows the current page (title + URL); typing shows local history suggestions (page title + URL, parameterized and deduplicated SQLite queries), a direct-URL row and a search row for the selected engine. **Remote search suggestions** (DuckDuckGo, Startpage, Brave, Google, Bing, Wikipedia endpoints; custom engines fall back to local) are opt-in via the new **Search Suggestions** setting, whose description states that typed text is sent to the provider. Requests are debounced ~250 ms, stale responses cancelled by a generation counter, results capped at 8, and offline/stale failures silently keep the local rows. Nothing of this runs in a private tab.
- **Chrome-style Normal / Private tab switcher.** The switcher header is now a two-mode selector with live counts. Normal mode shows only the active Space's regular tabs and existing tab groups (Space chip bar unchanged); Private mode shows only private tabs, hides the Space chips and never shows groups. The New Tab button creates a tab in the selected mode, Close All respects it, and opening the switcher lands in the mode of the active tab. Switching modes never touches any session — every WebView stays alive.

### Changed
- The standalone "New incognito" and "Close incognito" switcher buttons are replaced by the mode selector (Close All in Private mode closes all private tabs).
- Grouping rules are unchanged: private tabs can never be grouped or moved into Spaces (`GroupRowPlanner.planPrivate` renders them in their own pane only).

## [1.14.0] — 2026-11-02

Complete Chrome-style redesign of the tab-group UI (grouped-card overview + dedicated group-detail grid).

### Added
- **Grouped-card tab overview.** Every group now renders as one compact group card in the tab grid — colour strip, name/count chip row and up to four member page-preview tiles — instead of the old flat full-width header rows. Ungrouped and incognito cards sit directly in the grid; the "Ungrouped" section header is gone. Dropping a grouped tab on empty grid space ungroups it.
- **Dedicated group-detail grid.** Tapping a group card opens a full detail surface for that group: colour-coded header (back, group chip, overflow menu), a grid of exactly the group's member tabs, and a "New tab in group" action — new tabs created there join that group and the current Space. Dragging a member out onto empty space removes it from the group while the tab (and its WebView) keeps running. The Space chip bar stays reachable; switching Space closes the detail.
- **Group creation dialog** with an editable title and the eight-colour swatch palette, reachable from "Add to group → New group…" on any tab card.
- **"Add to group" tab context menu.** A bare long-press on a tab card now opens the tab's context menu: add to an existing group (or a new one), remove from its group, or the classic move-to-another-Space dialog.

### Changed
- **Group overflow actions** (long-press a group card or the detail header menu) are now: Rename, Group colour (swatch palette), Ungroup all, and **Delete group** — which closes every member tab and removes the group after a confirmation.
- Group cards and grouped tab cards carry their group colour (strip/dot/border); the collapse chevron retired — a group card is the collapsed form, expansion happens in the detail grid. The persisted `collapsed` flag stays in the model for compatibility.

### Fixed
- Incognito tabs can no longer be pulled into a group by dropping them on a same-space card (the drop is rejected with the existing "Incognito tabs don't belong to spaces" toast), and dropping a private tab on a Space chip no longer silently re-scopes it.

### Notes
- Grouping is still metadata only: every path above moves `Tab.groupId` and never recreates a WebView, history, scroll position or the Space's login profile. Spaces are untouched.
- New pure-JVM layer (`GroupRowPlanner`, `GroupOps`) drives the row layout and the drag/drop decisions; unit tests cover the grouped-card rows, search flattening, Space scoping, detail membership, process-death restoration and the incognito guards (27 tests green via the local ecj+JUnit chain).

## [1.13.0] — 2026-11-01

Anti-adblock reliability pass plus a two-menu split for the task bar.

### Fixed
- **Anti-adblock: walls that sit inside theme wrappers are now removed.** Real-world walls (the CHP Ads Block Detector family, as used by medeberiyal.com) are sometimes wrapped by theme containers, so the scanner only looked at direct body children and missed them. Candidate collection now walks a bounded descent (depth 3, 400 nodes) below body children.
- **Anti-adblock: published widgets are caught by their un-randomizable artifacts.** The CHP wall randomizes every class name, but its branding links (`chpadblock.com`, `toolkitspro.com`) and plugin asset paths cannot be randomized — the scanner now walks up from those stable hits (attribute selectors `[href*=…]`, `[src*=…]`) to the wall container itself. This also covers walls whose admin-customized text no longer matches any known phrase.
- **Anti-adblock: static page content can never be removed by descendant text.** Removal now requires structural evidence (fixed/absolute overlay covering a significant area) — a theme wrapper that happens to hold hidden wall markup is never nuked, and an article that merely mentions ad blockers stays intact.
- **Anti-adblock: a proven detector lowers the removal bar.** When a page script is caught attacking the engine's bait elements, weak-text structural overlays are removed without needing "Fix this site".
- Anti-adblock text signals cover the singular wording ("…using an extension to block ads") seen on real sites, and the detector-URL list includes the CHP pro CDN (`chpadblock.com`). Guard suite extended to 28 cases, including the exact medeberiyal.com wall shape (nested, random classes, plugin icon, custom text).

### Changed
- **The bottom task bar and the top menu no longer open the same sheet.** The bottom ⋮ opens a compact quick-actions sheet (new tab / incognito / spaces / downloads in the circular row, then bookmarks, history, media, find, desktop site, reader, translate and settings). The top menu keeps the complete list with the quick-nav row (back / forward / refresh / share) and all page tools and app entries. Both routes share the same handlers.

## [1.12.0] — 2026-11-01

The "tab groups inside Spaces + a real Android browser citizen" release. Two features that make Spaces easier to live in and Zerium easier to live with.

### Added
- **Tab Groups inside Spaces** — Chrome-style drag-and-drop grouping within the active space. Long-press a card and drag: drop it on another tab to start a group, on a group's header or a member to join that group, on the "Ungrouped" header or empty space to ungroup, and on a Space chip to move it to that space. Groups render as coloured header rows (8-colour palette) with a live tab count and a collapse chevron; collapsed groups keep their tabs running. Long-press a header to rename, recolour or ungroup all.
- **Sessions are never recreated for organization** — grouping is pure metadata (`Tab.groupId`): moving tabs between groups never touches the WebView, its history, scroll position or the space's login profile. Groups persist per space, survive session restore, and disappear when their last member leaves.
- **Default browser support** — Settings → Links → "Default browser" requests `RoleManager.ROLE_BROWSER` on Android 10+, so Zerium appears in other apps' link choosers and can be set as the system default. Below Android 10 the row opens the system default-apps screen. The app only requests the role — Android owns the dialog.
- **External links open in** (Settings → Links) — where links handed over by other apps land: *Default space* (default), *Current space*, or *Ask every time* with a space picker. Only http(s) URLs are accepted, so an incoming link can never silently land in an isolated space's cookies.

### Notes
- Quick long-press (no drag) on a tab card keeps the old behaviour: the move-to-space dialog. Incognito tabs stay out of groups, as they stay out of spaces.
- Feature specs live in `docs/issues/` (the repo token cannot open GitHub issues).

## [1.11.0] — 2026-11-01

The Anti-Adblock Bypass release ("Zerium Protection"): websites that detect ad blockers and wall themselves off ("Ads Blocker Detected!!!", full-screen overlays, scroll locks, countdown screens) are handled by a dedicated protection layer — while ad blocking itself stays on.

### Added
- **Anti-adblock bypass layer** — a fourth protection component beside network blocking, cosmetic filtering and YouTube suppression. It runs in-page at document start via `WebViewCompat.addDocumentStartJavaScript`, so countermeasures are in place before any detection script executes.
- **Known detector scripts are blocked at the network level** — a curated rule list (`AntiAdblockRules`) covering specific detector families (CHP Ads Block Detector, blockadblock, killadblock, detectadblock, Admiral/getadmiral, ads-recover, anti-adblock killers). Main-frame navigations are never matched. Walls whose detector script is blocked never arm (their overlay markup stays hidden).
- **Confidence-based wall removal** — full-viewport fixed overlays are scored structurally (coverage, z-index, backdrop/blur, dialog semantics, scroll lock) and by text signals; walls are removed only above a confidence threshold. Elements with randomized class names (like the CHP widget) are matched this way.
- **Detection countermeasures without API spoofing** — hidden bait elements are installed before page scripts run; if a detector tampers with them, they are re-asserted and the removal confidence rises. No global `fetch`/XHR API is ever wrapped or spoofed.
- **Automatic recovery** — a MutationObserver watches for walls injected or revealed seconds after page load (pre-rendered hidden overlays being flipped visible included), with capped re-removal.
- **Zerium Protection sheet** — the "Blocked on this page" menu entry is now a full panel: Ad blocking, Anti-adblock bypass, Cosmetic filtering and Automatic recovery switches, block statistics, allow-this-site, and **Fix this site** — a per-host aggressive mode (lower removal threshold, persisted) for sites the automatic confidence bar does not clear.
- **Safety vetos (by design)** — cookie-consent dialogs, paywall/subscription prompts, login screens (password fields) and CAPTCHA elements are never removed, in any mode. No blanket keyword hiding: the word "ads" alone never removes anything.

### Honest scope
- The engine needs document-start script support (the standard WebView capability check); on the rare older WebView without it, the same engine is injected at page finish instead — late walls are still handled, but countermeasures no longer run before detection scripts.
- Best-effort by design: heavily obfuscated, server-rendered or fingerprint-based walls may not clear. "Fix this site" lowers the threshold for that host but never guarantees success, and it never bypasses paywalls, logins or CAPTCHAs — those are protected by hard vetos.
- The detector-script list is curated by hand and bundled with the app; remote updates are queued on the roadmap.
- Browsing-session protection only: this is not device-level anonymity and not an ad-blocker "fingerprint normalizer".

## [1.10.0] — 2026-11-01

The Zerium Spaces release: tab groups with per-space logins — one isolated space per identity, so the same site can stay signed in with different accounts at the same time.

### Added
- **Zerium Spaces** — a Spaces entry in the menu and a chip bar in the tab switcher list every space with its live tab count. Tap a chip (or a space in the manager sheet) to switch; the switcher shows the active space's tabs, plus incognito tabs, which belong to no space. New tabs open into the active space automatically.
- **Isolated spaces** — creating a space can turn on Isolation: the space then gets its own engine profile through the WebView multi-profile API, with its own cookie jar, site storage (localStorage/IndexedDB) and service workers. Log in to Gmail as user A in one space and user B in another; closing tabs or the app never signs either out, and deleting one space never touches the others.
- **Space management** — the manager sheet supports rename and delete with an explicit confirmation (tabs close and the space's saved logins/site data are erased; other spaces keep their sessions); the default space can be renamed but not deleted or isolated.
- **Move tabs between spaces** — long-press a tab card to move it. Moves that cross an isolation boundary reload the tab with the target space's cookies (the engine cannot migrate a live WebView between cookie jars); moves between non-isolated spaces re-tag instantly.
- **Profile cleanup** — deleting an isolated space deletes its engine profile and data; if the engine refuses (lingering references) the deletion is retried on the next launch before anything touches the profile.
- **Delete browsing data is space-aware** — cookies/site storage now clear across the default jar AND every isolated space's profile.

### Changed
- "Close all" in the tab switcher now closes the current space's tabs (the scope the switcher shows) instead of every space.
- Session persistence saves each tab's space; legacy saved sessions restore into the default space unchanged.

### Honest scope
- Isolation needs a recent WebView provider (the multi-profile feature is checked at runtime). When the device's WebView is too old, spaces still organize tabs but the isolation toggle is disabled up front with a clear message — the browser never pretends cookies are separated when they are not.
- The default space and non-isolated spaces share the default profile: they group tabs without splitting logins, by design.
- The system-level HTTP-auth credential store is not per-profile on this engine; form autofill is delegated to your system credential manager (see the password note in `docs/PRIVACY.md`).
- Incognito tabs stay outside spaces (they skip history and never persist), so they are reachable from every space's switcher.
- The YouTube suppression pipeline, cosmetic filter and blocklists are untouched (21/21 YouTube suite green).

## [1.9.0] — 2026-09-20

The Turbo Downloads release: a complete download manager with an in-page media grabber, plus Jetpack Compose (Material 3) screens and deeper appearance customization.

### Added
- **Turbo download engine** — all downloads now run through Zerium's own manager instead of the system downloader: multi-connection range downloads (up to 8 connections per file where the server supports ranges, with an automatic single-connection fallback when a server silently ignores ranges), pause and resume with persisted partial progress, a bounded queue (3 concurrent), automatic retry with backoff, and honest error mapping (403/404/range/encrypted/storage/network).
- **Background downloads** — a foreground service keeps transfers running while you browse or leave the app; per-task notifications show live progress, speed and ETA with pause/resume/cancel actions, an Open action on completion and Retry on failure (notification permission requested on Android 13+).
- **Media grabber** — a "Media on this page" menu entry lists videos, audio, HLS/DASH playlists, direct file links and blob-backed players detected on the current page by an in-page DOM scanner (document-start injection with a MutationObserver) plus URL-shape network sniffing; each row downloads with one tap.
- **Blob capture** — single-file `blob:` video/audio players are fetched by the page itself and streamed to the app in base64 chunks (512 KB each, 300 MB cap) with live progress in the downloads list.
- **HLS stream downloads** — m3u8 playlists resolve master→highest-bandwidth variant and download segments sequentially into a single `.ts` file with per-segment progress; encrypted playlists are refused with a clear error.
- **Category folders** — finished files land in `Download/Zerium/<Video|Audio|Images|Docs|Archives|APKs|Other>` via MediaStore on Android 10+ (direct paths with a stated app-dir fallback on older devices).
- **Jetpack Compose download manager** — the Downloads screen is rebuilt in Compose (Material 3): live progress with speed/ETA, filter chips (All/Active/Finished), per-task pause/resume/cancel/retry, tap to open; unit tests cover the task/category/registry layer.
- **Appearance screen (Compose)** — theme mode (System/Light/Dark), Material You dynamic color toggle (Android 12+), and six accent colors, with an honest note about when the accent reaches classic screens.
- **Settings** — switches for accelerated downloads and page-media detection, plus an Appearance row.

### Changed
- `ZeriumApp` now honours the dynamic-color preference instead of applying Material You unconditionally.
- The YouTube suppression pipeline, cosmetic filter and blocklists are untouched; the media scanner observes requests without altering the blocking pipeline (21/21 YouTube suite green).

### Honest scope
- MSE/DASH media served as many small encrypted or indexed segments is only partially capturable; DRM (Widevine/FairPlay) streams are never downloadable.
- Multi-connection speedups only materialize when the server supports HTTP ranges and the network allows parallelism.
- Blob capture requires same-origin cooperation from the page and is capped at 300 MB; sites that fragment media across many blob segments cannot be captured whole.
- Torrent downloads are not supported (no engine in-app); tracked in ROADMAP.
- The static accent applies instantly to the Compose screens; classic View screens follow on next launch.

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
