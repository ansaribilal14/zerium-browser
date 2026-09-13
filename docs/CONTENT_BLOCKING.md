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

## Site allowlist

Settings allows per-domain exemptions (one domain per line). The allowlist is checked before blocking; allowlisted hosts are never blocked at the network layer. Editing it takes effect immediately for new requests.

## What blocking cannot do (v1, stated honestly)

- In-stream video ads (YouTube and similar) are served from the same delivery endpoints as the media itself; network-level blocking cannot remove them without breaking playback. Zerium blocks their telemetry endpoints and hides page-level ad containers.
- No filter DSL (EasyList syntax, `##` cosmetic rules with domains, scriptlets). The selector set is generic and curated.
- Fail-open while the list is still loading (a fraction of a second at first launch): a page loaded in that window may fetch some ads. This is intentional; availability beats purity, and it is not hidden.
