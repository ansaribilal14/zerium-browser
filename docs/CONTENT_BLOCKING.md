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

YouTube serves in-stream ads from the same delivery endpoints as the video itself (`googlevideo.com/videoplayback`), so no network-layer blocker can separate them by host or path. Zerium therefore implements the technique used by actively maintained scriptlet-based web blockers — the design is modeled on uBlock Origin's YouTube scriptlets in [uAssets quick-fixes](https://github.com/uBlockOrigin/uAssets/blob/master/filters/quick-fixes.txt), the same class of technique Brave's aggressive mode layers on top of its network filtering. The script (`app/src/main/assets/yt-block.js`) is injected at document start via `WebViewCompat.addDocumentStartJavaScript`, before the YouTube player initializes, and works in four layers:

1. **Prune-before-load — the "block" layer.** Ad structures (`adPlacements`, `adSlots`, `playerAds`, `adBreaks`, heartbeat params, third-party placements) are deep-pruned from every player JSON: the initial `ytInitialPlayerResponse` (setter trap), `/youtubei/v1/player|next|video_details|get_midroll_info|viewer|playlist` fetch responses (cloned, pruned, re-served), and the same over XHR (response rewritten via property definition). Because the player never parses the ad metadata, those ads are never scheduled — nothing renders, there is nothing to skip. This is the closest a WebView can get to Brave's "the ad does not even show up" behavior, and it is the most durable layer.
2. **UI cleanup.** Skip buttons (all known variants) and overlay-close buttons are clicked the moment they appear — MutationObserver-driven with a 500 ms safety net, no artificial delay. The anti-adblock enforcement dialog is hidden and auto-confirmed if YouTube shows it.
3. **In-stream fallback state machine.** For unskippable in-stream ads only: the ad is muted and fast-forwarded (16x). This is strictly scoped to a confirmed in-stream state (`.ad-showing` / `.ad-interrupting` on the player root); playback rate and mute state are captured before any change and restored exactly when the ad state ends. Overlay ads and normal playback are never touched — v1.1.0's watchdog matched the always-present `.ytp-ad-module` container and reset the rate to a hardcoded `1`, which could fast-forward the main video; that defect is fixed in v1.2.0.
4. **CSS hiding.** Ad renderer elements in feeds, search, and the watch page (`ytd-ad-slot-renderer`, `ytd-in-feed-ad-layout-renderer`, `ytd-compact-promoted-video-renderer`, masthead/search/companion ads, overlay and countdown containers, and more) are hidden with `!important` rules.

Techniques evaluated and **deliberately rejected** from the reference implementations:

- **Premium-client masquerade** (uBO's `serverContract` rewrite): rewrites the client user-agent so YouTube's server serves no ads, and hooks `Promise.prototype.then`, `Map.prototype.has`, and `Array.prototype.push` globally. It works, but it is fragile across YouTube releases, misrepresents the client to Google's servers, and hooks global prototypes in a way we are not willing to ship. Revisit only if the pruning layer degrades badly.
- **Network-blocking of ad video segments**: failing the ad's `videoplayback` request tends to stall the player instead of skipping cleanly, because WebView cannot distinguish the ad segment from the content segment at request time.

Scope, stated plainly: this is an arms race against a well-funded opponent. The pruning layer is the most durable; UI-level selectors break when YouTube renames classes. Expect most sessions to be ad-free or near-ad-free, with occasional formats slipping through. A settings toggle (`YouTube ad suppression`, on by default) controls all of it. The engine-level endgame (in-request ad separation) belongs to the Chromium/GeckoView track in `docs/ROADMAP.md`.

## Site allowlist

Settings allows per-domain exemptions (one domain per line). The allowlist is checked before blocking; allowlisted hosts are never blocked at the network layer. Editing it takes effect immediately for new requests.

## What blocking cannot do (v1, stated honestly)

- In-stream video ads are handled client-side (see the YouTube strategy above): effective most of the time, not guaranteed always; network-layer removal alone is impossible without breaking playback.
- No filter DSL (EasyList syntax, `##` cosmetic rules with domains, scriptlets). The selector set is generic and curated.
- Fail-open while the list is still loading (a fraction of a second at first launch): a page loaded in that window may fetch some ads. This is intentional; availability beats purity, and it is not hidden.
