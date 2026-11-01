# Roadmap

## Follow-ups (queued)

- Anti-adblock: remote updates for the detector-script list, a per-site "bypass active" indicator in the Protection sheet, Zerium G port of the same engine.
- Spaces follow-ups: per-space colored identities, space-aware start-page tiles; local data export/import.

## Shipped in v1.13.0 (2026-11)

- Anti-adblock reliability pass: walls nested inside theme wrappers are reached by a bounded descent below body children; published widgets (CHP Ads Block Detector family) are caught by their un-randomizable artifacts (branding hrefs, plugin asset paths) via ancestor walking from attribute-selector hits, covering walls whose admin-customized text matches no known phrase; removal now requires structural evidence so static content and theme wrappers are never nuked by descendant text; a proven bait-tampering detector lowers the removal bar automatically; singular wordings and the CHP pro CDN added to the signal/URL lists. Guard suite: 28 cases including the exact medeberiyal.com wall shape.
- Two-menu split: the bottom task bar's menu is now a compact quick-actions sheet (tabs, spaces, destinations, common toggles) while the top menu keeps the full list — no more duplicate sheets.

## Shipped in v1.12.0 (2026-11)

- Tab groups inside Spaces: Chrome-style drag-and-drop grouping within the active space. Pure metadata on the tab model (`Tab.groupId`), so moving tabs between groups never recreates the WebView, its history, scroll position or the space's login profile. Colored collapsible group headers (8-colour palette, rename/recolor/ungroup on long-press), persistence per space, incognito tabs excluded. Spec: `docs/issues/tab-groups-inside-spaces.md`.
- Android browser integration: exported browser activity with `ACTION_VIEW` + `category.BROWSABLE` http/https intent filters; Settings → Links with a `RoleManager.ROLE_BROWSER` default-browser request (Android 10+) and an "External links open in" choice (default space / current space / ask every time with a picker); incoming http(s) links open as a new tab in a legitimate space — never silently into an isolated space's cookies. Spec: `docs/issues/android-browser-integration.md`.

## Shipped in v1.11.0 (2026-11)

- Anti-adblock bypass ("Zerium Protection"): a dedicated layer for sites that detect ad blockers and wall themselves off. Known detector scripts (CHP Ads Block Detector, blockadblock, killadblock, detectadblock, Admiral, ads-recover, anti-adblock killers) are blocked from a curated list (non-main-frame only); the in-page engine runs at document start, installs bait countermeasures (no API spoofing), removes walls above a structural + text confidence threshold, and watches with a MutationObserver for late walls. Safety vetos keep consent dialogs, paywalls, logins and CAPTCHAs untouched. "Zerium Protection" panel with the four toggles plus a per-site "Fix this site" aggressive mode.

## Shipped in v1.10.0 (2026-11)

- Zerium Spaces: tab groups with per-space logins. Isolated spaces map to WebView multi-profile engine profiles (own cookie jar, site storage, service workers); non-isolated spaces organize tabs on the shared default profile. Space switcher chips + manager sheet (switch/rename/delete with data wipe), move-tab-between-spaces (cross-isolation moves reload with the target space's cookies), space-aware "Delete browsing data" and a runtime capability check that disables isolation honestly on older WebView providers.

## Shipped in v1.6.0 (2026-09)

- Site settings panel: per-site JavaScript, per-site blocking exemption and per-site desktop-mode memory (the per-site desktop *memory* piece of the old "site panel" item; per-site cookies are honestly impossible on the WebView engine and are not offered).
- Long-press context menu on links and images (open in new tab, copy, share, download image).
- Edge-swipe tab switching (UC-style) with strict anti-hijack conditions and a Settings toggle.
- Password autofill surfaced honestly: system autofill framework status + one tap to the system picker; Zerium stores nothing.
- Downloads screen shows sizes, live progress and failure reasons.
- "Close incognito" bulk action in the tab switcher.
- GeckoView Phase-0 spike module (`:gecko-spike`) compiled in CI as a separate non-gating job; see `docs/GECKOVIEW_SPIKE.md`.
- Sync evaluated honestly and *not* shipped; the reasoning and the serverless alternative are documented in `docs/SYNC_EVALUATION.md`.

## Shipped in v1.5.0 (2026-09)

- HTTPS-first main-frame upgrades with the existing certificate warning as the downgrade path.
- Reader view (Mozilla Readability), translate page (translate.goog), print/Save-as-PDF, add-to-home-screen.
- Desktop-site per-tab toggle with a runtime-derived desktop user agent.
- Custom search engines, dynamic start-page shortcuts, text size / force zoom / autoplay / pull-to-refresh controls.
- Automatic weekly filter-list refresh (hosts + cosmetic), validated and applied without restart.

## v1.7 - near term

- Local export/import of user data (bookmarks, history, allowlist, per-site settings, engines) — the honest serverless alternative to sync; see `docs/SYNC_EVALUATION.md`.
- Import/export of settings, allowlist, bookmarks.
- WebView Profiles API evaluation for true incognito cookie isolation.
- Domain-qualified cosmetic rules and a first-party filter DSL.

## v2.0 - engine track

- GeckoView Phase-0 spike is in-repo and green in CI (`:gecko-spike`, `docs/GECKOVIEW_SPIKE.md`); the remaining phases follow `docs/GECKOVIEW_MIGRATION.md` behind the decision gate (startup/memory/blocking-ceiling measurements on device).
- If migrated: WebExtensions support becomes possible, including uBlock Origin-class filtering for users who want maximum blocking.
- Decision gate: keep dual-engine flavors or migrate fully, based on measurement.
- Tab grouping (folders/colored groups in the switcher): deliberately deferred to the engine track — the current switcher shows live previews and a Close-incognito bulk action; grouping adds persistent group state across restore/fullscreen/switch flows for little workflow gain at the current scale. Re-evaluated once the switcher is rebuilt on the new engine.

## Later

- Chromium/Cromite fork track revisited if/when build infrastructure (self-hosted runner with 16+ cores, 200+ GB disk) becomes available; the rebase operations cost is documented in `docs/ARCHITECTURE.md` and remains the deciding factor.

## Download-manager track (opened in v1.9.0)

- BitTorrent/magnet support: needs an in-app engine decision (library size vs. APK weight for a WebView-class browser); evaluate libtorrent4j and a native-service design before committing.
- Per-site download rules and download-location picker.
- DASH (mpd) segment assembly beyond the current playlist handling; encrypted HLS (AES-128) via a key-fetch path if legally unencumbered use cases demand it.
