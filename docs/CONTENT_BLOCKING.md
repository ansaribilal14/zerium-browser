# Content Blocking

## Two layers

**Layer 1 - Network filtering.** Every resource request passes through `AdBlocker.shouldBlock`. A request is blocked when:

1. Its host matches an entry in the hosts-derived domain set, either exactly or via an ancestor domain (an entry for `example.com` blocks its whole tree; requests to unlisted subdomains of listed domains are therefore also blocked - this is intentionally stronger than hosts-file semantics), or
2. Its URL contains one of the ~60 curated pattern rules. They cover Google's ad/measurement endpoints (`googlesyndication.com`, `/pagead/`, `doubleclick.net`, `imasdk.googleapis.com`, GA4/GTM loaders), social and analytics pixels (Meta, TikTok, X, Pinterest, LinkedIn, Bing/Clarity, Hotjar, Yandex), header-bidding exchanges (PubMatic, Rubicon, Criteo, Index, Sovrn and peers), native/content-ad and popup networks (Taboola, Outbrain, MGID, RevContent, PopAds, PropellerAds, Adsterra and peers), and YouTube internals (`/api/stats/`, `get_midroll_info`, `/ptracking?`).

Blocked requests receive an empty HTTP 404 response. Requests are counted per page, per session, and all-time; counts are visible in the menu ("Blocked on this page") and in the connection dialog.

**Layer 2 - Cosmetic filtering.** `CosmeticFilter` injects a script containing sanitized CSS selectors. On WebViews that support document-start scripting the script is injected via `WebViewCompat.addDocumentStartJavaScript` on every http(s) origin, so ad containers are hidden *before the first paint*; the page-finish injection remains as a fallback for older WebView versions. Matching nodes get `display:none !important` and a `data-zerium-hidden` marker. A MutationObserver re-applies hiding on DOM mutations, debounced through requestAnimationFrame. For performance with the large selector set, selectors are queried in comma-joined batches (one engine pass per batch instead of one pass per selector); if a batch fails to parse as a whole (e.g. a `:has()` on an old engine), it is retried selector by selector. Selectors are validated against a whitelist regex before injection: only letters, digits, and attribute/class syntax pass; quotes, braces, backslashes, and semicolons are rejected, so a malicious list file cannot inject arbitrary JavaScript.

## Lists

| List | Scope | License | Source |
|---|---|---|---|
| `hosts.txt` (bundled) | Ads, trackers, malware domains (~80k entries) | MIT | StevenBlack/hosts unified, frozen at build |
| `hosts_updated.txt` (app-fetched) | Same, current | MIT | Fetched over HTTPS from the upstream repo, integrity-gated (size + sentinel) |
| `cosmetic.txt` (bundled) | 1,200 sanitized generic element-hiding selectors (id/class/attribute mix, ad-related rules prioritized) | CC-BY-SA-3.0 (EasyList subset) + GPL-3.0 curated additions | EasyList generic-hide rules, sanitized and capped; attribution + license in the file header |
| `cosmetic_updated.txt` (app-fetched) | Same, current | CC-BY-SA-3.0 + GPL-3.0 | Fetched over HTTPS from the Zerium repo, integrity-gated (size + `##` marker), atomically swapped |

Since v1.5.0 both lists refresh themselves automatically about once a week (Settings toggle, on by default) or on demand via *Update filter lists*; downloads are validated before they replace the previous file and apply to newly loaded pages without a restart.

## YouTube strategy (client-side suppression, documented honestly)

YouTube serves in-stream ads from the same delivery endpoints as the video itself (`googlevideo.com/videoplayback`), so no network-layer blocker can separate them by host or path. Zerium therefore implements the technique used by actively maintained scriptlet-based web blockers — the design is modeled on uBlock Origin's YouTube scriptlets in [uAssets quick-fixes](https://github.com/uBlockOrigin/uAssets/blob/master/filters/quick-fixes.txt), the same class of technique Brave's aggressive mode layers on top of its network filtering. The script (`app/src/main/assets/yt-block.js`) is injected at document start via `WebViewCompat.addDocumentStartJavaScript`, before the YouTube player initializes, and works in four layers:

1. **Prune-before-load — the "block" layer.** Ad structures are deep-pruned from every player JSON: the initial `ytInitialPlayerResponse` (setter trap), and `/youtubei/v1/player|next|video_details|get_midroll_info|viewer|playlist|get_watch|ssap` responses over fetch (cloned, pruned, re-served) and XHR. Two v1.4.0 hardenings: endpoint matching no longer requires a query string (a bare `/youtubei/v1/player` used to slip through), and ad **renderer**-level keys (`adSlotRenderer`, `adBreakAdRenderer`, `adPlacementRenderer`, `inVideoAdCta`) are pruned alongside the container keys, so ad schedules nested under containers whose names we do not know are removed too. The XHR interception is order-independent: instance-level `response`/`responseText` getters installed at `open()` prune on access, so a page listener registered before `send()` can no longer read raw ad data first (the v1.2.0 listener ran after the page's). Because the player never parses the ad metadata, those ads are never scheduled — nothing renders, there is nothing to skip. This is the closest a WebView can get to Brave's "the ad does not even show up" behavior, and it is the most durable layer.
2. **UI cleanup.** Skip buttons (all known variants) and overlay-close buttons are clicked the moment they appear — MutationObserver-driven with a 500 ms safety net, no artificial delay. The anti-adblock enforcement dialog is hidden and auto-confirmed if YouTube shows it.
3. **In-stream fallback state machine.** For unskippable in-stream ads only: the ad is muted and fast-forwarded (16x). This is strictly scoped to a confirmed in-stream state (`.ad-showing` / `.ad-interrupting` on the player root); playback rate and mute state are captured before any change and restored exactly when the ad state ends. Overlay ads and normal playback are never touched — v1.1.0's watchdog matched the always-present `.ytp-ad-module` container and reset the rate to a hardcoded `1`, which could fast-forward the main video; that defect is fixed in v1.2.0. This layer is also what carries **SSAP mid-rolls** (see the honest-scope note below): ads stitched into the stream by the server cannot be pruned client-side, so the confirmed-ad state is muted and fast-forwarded instead. Additionally, when present the web "network machine" experiment flags (`all_web_enable_network_machine`, `all_web_network_machine_raw_request`) are switched off client-side — a config toggle only, mirroring uBO's current quick-fixes; no client identity, user agent, or header is ever changed.
4. **CSS hiding.** Ad renderer elements in feeds, search, and the watch page (`ytd-ad-slot-renderer`, `ytd-in-feed-ad-layout-renderer`, `ytd-compact-promoted-video-renderer`, masthead/search/companion ads, overlay and countdown containers, and more) are hidden with `!important` rules.

Techniques evaluated and **deliberately rejected** from the reference implementations:

- **Premium-client masquerade** (uBO's `serverContract` rewrite): rewrites the client user-agent so YouTube's server serves no ads, and hooks `Promise.prototype.then`, `Map.prototype.has`, and `Array.prototype.push` globally. It works, but it is fragile across YouTube releases, misrepresents the client to Google's servers, and hooks global prototypes in a way we are not willing to ship. Revisit only if the pruning layer degrades badly.
- **Network-blocking of ad video segments**: failing the ad's `videoplayback` request tends to stall the player instead of skipping cleanly, because WebView cannot distinguish the ad segment from the content segment at request time.

Scope, stated plainly: this is an arms race against a well-funded opponent. The pruning layer is the most durable; UI-level selectors break when YouTube renames classes. Expect most sessions to be ad-free or near-ad-free, with occasional formats slipping through. A settings toggle (`YouTube ad suppression`, on by default) controls all of it. The engine-level endgame (in-request ad separation) belongs to the Chromium/GeckoView track in `docs/ROADMAP.md`.

**SSAP mid-rolls, stated plainly.** YouTube is rolling out server-side ad placement (SSAP): the mid-roll ad segments are stitched into the video stream itself and delivered from the same `videoplayback` endpoints as the content. No client-side blocker — uBO, Brave on web, or Zerium — can remove those segments from an already-stitched stream without masquerading as a different client and forcing a stream reload (uBO's `serverContract` approach, which we deliberately reject; see above). What Zerium does instead: client-side *scheduled* mid-rolls are pruned and never appear at all, and any SSAP-stitched ad that does reach the player hits the confirmed-ad watchdog and is muted and fast-forwarded through — playback of the actual video resumes at its real speed immediately after.

## Site allowlist

Settings allows per-domain exemptions (one domain per line). The allowlist is checked before blocking; allowlisted hosts are never blocked at the network layer. Editing it takes effect immediately for new requests.

## What blocking cannot do (v1, stated honestly)

- In-stream video ads are handled client-side (see the YouTube strategy above): effective most of the time, not guaranteed always; network-layer removal alone is impossible without breaking playback.
- No filter DSL (EasyList syntax with domain-scoped rules, scriptlets, procedural filters). The cosmetic set is generic element-hiding only: rules are validated against the runtime whitelist regex and cannot carry per-domain conditions or code.
- Fail-open while the list is still loading (a fraction of a second at first launch): a page loaded in that window may fetch some ads. This is intentional; availability beats purity, and it is not hidden.
