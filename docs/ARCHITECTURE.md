# Zerium Architecture

## Product shape

Zerium is a single-activity browser core (`MainActivity`) plus four lightweight secondary activities (Settings, Bookmarks, History, Downloads). All tab state lives in memory (`TabManager`); sessions are serialized to `SharedPreferences` on pause so cold starts restore up to 10 tabs lazily (each tab's URL loads on first activation, not at startup).

## Engine decision: WebView first, recorded honestly

The original project brief targeted a deep Chromium/Cromite fork. That remains the long-term ambition, but it is not shippable from this repository's infrastructure, and shipping something unbuildable would violate the project's honesty rules. The decision matrix that produced the v1 architecture:

| Option | Compatibility | Blocking ceiling | Build cost | CI feasibility | Verdict |
|---|---|---|---|---|---|
| Cromite/Chromium fork | Best (engine-level) | Very high (C++ net stack) | ~100 GB checkout, 16-core/8h+ builds | Not feasible on standard runners (4 cores, 14 GB RAM/disk); requires self-hosted build farm | Postponed, documented |
| GeckoView embed | Very good | High (WebExtensions, uBlock Origin) | Moderate (large AAR, no source build) | Feasible | Roadmap track 1 |
| **System WebView (v1)** | Good (Chromium rendering) | Medium (network + cosmetic, no DSL) | Minimal | ~10 min on free runners | **Shipped** |

WebView was chosen for v1 because it is the only option that satisfies three simultaneous constraints: a real Chromium-derived rendering engine for web compatibility, a fully automated cloud build that a single maintainer can keep green, and zero fork-maintenance burden. The known engine limitations (no extensions, no per-subresource headers, shared cookie jar) are documented in the README rather than hidden. The GeckoView track is scoped as a future build flavor, not a rewrite, because the blocking engine, UI, and storage layers are engine-agnostic by design. The full migration assessment — API mapping, costs, phased plan — lives in [`docs/GECKOVIEW_MIGRATION.md`](GECKOVIEW_MIGRATION.md).

## Module map

- `MainActivity` — browser chrome, tab lifecycle, intent handling (VIEW/SEND/MAIN), full-screen video, file chooser, runtime permissions, session persistence.
- `TabManager` / `Tab` / `TabsAdapter` — tab ownership; each tab wraps its own `WebView` kept alive (hidden, not detached) for state retention.
- `AdBlocker` — network filtering. Loads the hosts list (asset, or a validated updated file in `filesDir`) into a `HashSet`, matches by exact domain plus ancestor walk, plus curated URL-pattern rules. Thread-safe; counters are atomic.
- `CosmeticFilter` — element hiding. Selectors from assets are sanitized with a whitelist regex (no quotes/braces/escapes), embedded as JSON into a self-contained script with rAF-debounced MutationObserver re-application.
- `StartPage` — generates the built-in start page HTML with the user's engine baked in and the all-time blocked counter.
- `BookmarksDB` / `HistoryDB` — SQLite storage, deliberately dependency-free (no Room codegen in CI).
- `Prefs` — typed access to all settings; single source of truth.
- `SettingsActivity` — UI for every pref, list updates with integrity validation (size + sentinel check), allowlist editing.

## Blocking data flow

Request → `shouldInterceptRequest` (IO thread) → `AdBlocker.shouldBlock(url, pageUrl)` → blocked responses return HTTP 404 with an empty body; counters increment per-tab and session. Page finish → counters flushed to the all-time total, cosmetic script injected on the UI thread. The blocklist loads asynchronously at app start; until ready, requests pass through (fail-open is intentional: availability over purity, counted honestly in docs).

## Update integrity

Updated hosts files are accepted only if they exceed 100 KB and contain the `0.0.0.0` sentinel pattern; otherwise the update is rejected and the bundled list remains active. This is a deliberately simple integrity gate appropriate to a plain-text list fetched over HTTPS from the canonical upstream repository.
