# Content Blocking

## Two layers

**Layer 1 - Network filtering.** Every resource request passes through `AdBlocker.shouldBlock`. A request is blocked when:

1. Its host matches an entry in the hosts-derived domain set, either exactly or via an ancestor domain (an entry for `example.com` blocks its whole tree; requests to unlisted subdomains of listed domains are therefore also blocked - this is intentionally stronger than hosts-file semantics), or
2. Its URL contains one of the curated pattern rules (e.g. `googlesyndication.com`, `/pagead/`, `doubleclick.net`, `/api/stats/ads`).

Blocked requests receive an empty HTTP 404 response. Requests are counted per page, per session, and all-time; counts are visible in the menu ("Blocked on this page") and in the connection dialog.

**Layer 2 - Cosmetic filtering.** After page load, `CosmeticFilter` injects a script containing sanitized CSS selectors. Matching nodes get `display:none !important` and a `data-zerium-hidden` marker. A MutationObserver re-applies hiding on DOM mutations, debounced through requestAnimationFrame. Selectors are validated against a whitelist regex before injection: only letters, digits, and attribute/class syntax pass; quotes, braces, backslashes, and semicolons are rejected, so a malicious list file cannot inject arbitrary JavaScript.

## Lists

| List | Scope | License | Source |
|---|---|---|---|
| `hosts.txt` (bundled) | Ads, trackers, malware domains (~140k) | MIT | StevenBlack/hosts unified, frozen at build |
| `hosts_updated.txt` (user-fetched) | Same, current | MIT | Fetched over HTTPS from the upstream repo, integrity-gated (size + sentinel) |
| `cosmetic.txt` (bundled) | Generic ad slot selectors | GPL-3.0 (part of Zerium) | Curated in-repo |

## YouTube strategy (client-side suppression, documented honestly)

YouTube serves in-stream ads from the same delivery endpoints as the video itself (`googlevideo.com/videoplayback`), so no network-layer blocker can separate them by host or path. This is why the strategy used by scriptlet-based web blockers (uBlock Origin, AdGuard, Brave's web filtering) is client-side, and Zerium implements the same three layers, injected at document start (before the YouTube player initializes) via `WebViewCompat.addDocumentStartJavaScript`:

1. **Player-API pruning.** `adPlacements`, `adSlots`, `playerAds`, and `adBreaks` are deleted from player JSON before the player consumes it: a setter trap on `window.ytInitialPlayerResponse`, plus `fetch` and `XMLHttpRequest` hooks that prune `/youtubei/` API responses. This prevents most ad slots from ever being scheduled.
2. **Auto-skip watchdog.** A 250 ms watchdog detects the ad UI (`.ytp-ad-player-overlay`, `.ad-showing`), mutes the ad, raises playback rate to finish it, auto-clicks skip buttons and overlay-close buttons, and restores normal playback when the ad UI disappears.
3. **CSS hiding.** Ad overlay and countdown containers are hidden with `!important` rules.

Scope, stated plainly: this is an arms race against a well-funded opponent. The pruning layer is the most durable; UI-level selectors break when YouTube renames classes. Expect most sessions to be ad-free or near-ad-free, with occasional formats slipping through. A settings toggle (`YouTube ad suppression`, on by default) controls all of it. The engine-level endgame (in-request ad separation) belongs to the Chromium/GeckoView track in `docs/ROADMAP.md`.

## Site allowlist

Settings allows per-domain exemptions (one domain per line). The allowlist is checked before blocking; allowlisted hosts are never blocked at the network layer. Editing it takes effect immediately for new requests.

## What blocking cannot do (v1, stated honestly)

- In-stream video ads are handled client-side (see the YouTube strategy above): effective most of the time, not guaranteed always; network-layer removal alone is impossible without breaking playback.
- No filter DSL (EasyList syntax, `##` cosmetic rules with domains, scriptlets). The selector set is generic and curated.
- Fail-open while the list is still loading (a fraction of a second at first launch): a page loaded in that window may fetch some ads. This is intentional; availability beats purity, and it is not hidden.
