# Roadmap

## Shipped in v1.5.0 (2026-09)

- HTTPS-first main-frame upgrades with the existing certificate warning as the downgrade path.
- Reader view (Mozilla Readability), translate page (translate.goog), print/Save-as-PDF, add-to-home-screen.
- Desktop-site per-tab toggle with a runtime-derived desktop user agent.
- Custom search engines, dynamic start-page shortcuts, text size / force zoom / autoplay / pull-to-refresh controls.
- Automatic weekly filter-list refresh (hosts + cosmetic), validated and applied without restart.

## v1.1 - near term

- Site panel: per-site toggles for JavaScript, cookies, blocking, desktop mode (per-site memory for desktop mode shipped in v1.5.0 as a per-tab toggle; per-site memory remains).
- WebView Profiles API evaluation for true incognito cookie isolation.
- HTTPS-first upgrades with downgrade warnings.
- Long-press image/link context menu (open in new tab, download, share).

## v1.2 - blocking depth

- Domain-qualified cosmetic rules and a first-party filter DSL.
- Pinned list snapshots with versioned release notes per list update.
- Import/export of settings, allowlist, bookmarks.

## v2.0 - engine track

- GeckoView build flavor behind the same UI/storage layers; WebExtensions support becomes possible, including uBlock Origin-class filtering for users who want maximum blocking.
- Decision gate: keep dual-engine flavors or migrate fully, based on measurement (startup, memory, blocking ceiling, maintenance).

## Later

- Chromium/Cromite fork track revisited if/when build infrastructure (self-hosted runner with 16+ cores, 200+ GB disk) becomes available; the rebase operations cost is documented in `docs/ARCHITECTURE.md` and remains the deciding factor.
