# Roadmap

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
