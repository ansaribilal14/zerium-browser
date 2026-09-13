# Privacy

## Defaults (what you get with zero configuration)

- Ads and trackers blocked at the network layer (StevenBlack unified list + pattern rules).
- Third-party cookies blocked. First-party cookies allowed (required for logins).
- JavaScript enabled (disabling it globally breaks a large share of the modern web; the switch exists for users who want it).
- Do Not Track and Sec-GPC headers sent on main-frame requests.
- No analytics, no crash reporting, no remote config, no accounts. The app makes network calls for exactly three things: the pages you visit, downloads, and the filter-list update you explicitly trigger in Settings.

## Data handling

- History and bookmarks live in local SQLite databases inside the app sandbox. Clearing is one tap and is unrecoverable.
- Settings persist in `SharedPreferences` in the app sandbox.
- Incognito tabs skip history, bookmarks, and the all-time blocked counter, and `savePassword` is disabled globally. Known limitation (stated in the README): the system WebView shares one cookie jar, so incognito cookies are not yet isolated from normal tabs; closing data or clearing cookies in Settings affects the jar globally. True per-profile isolation is the first item of the privacy roadmap (WebView Profiles API / GeckoView track).
- The session-restore snapshot (up to 10 URLs) is stored locally only and is never synced anywhere.

## Signals that may be ignored by websites

DNT and GPC are opt-in signals by design; many sites ignore them. Zerium sends them on the main document request. This is disclosed rather than oversold: they are courtesy signals, not protection.

## Permissions the app can request at runtime

- Camera and microphone: only when a website asks via the WebView media APIs, always behind an explicit dialog.
- Location: only when a site prompts, behind an explicit dialog.
- Storage: none. Downloads go through the system DownloadManager, which owns its storage access.
