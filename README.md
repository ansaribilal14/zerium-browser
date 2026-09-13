# Zerium Browser

**A free, open-source privacy browser for Android. Native ad and tracker blocking, no accounts, no analytics, no nonsense.**

[![Build Zerium](https://github.com/ansaribilal14/zerium-browser/actions/workflows/build.yml/badge.svg)](https://github.com/ansaribilal14/zerium-browser/actions/workflows/build.yml)

Download the newest APK from the **[latest release](https://github.com/ansaribilal14/zerium-browser/releases/tag/latest)**. Every push to `main` is built automatically by GitHub Actions; the `latest` release always points at the newest successful build.

---

## What Zerium is

Zerium is a privacy-first Android browser that treats ad and tracker blocking as a core engine feature, not an afterthought. It is built for people who want a fast browser that respects them by default: no account, no telemetry, no ads served by the browser itself, and full source transparency under GPL-3.0.

The project follows three hard rules. First, honesty over marketing: every capability documented here is actually implemented in the code, and the limitations section below is explicit about what the current engine can and cannot do. Second, blocking must be native and transparent: the browser ships with a bundled blocklist, counts every blocked request, and shows you the numbers. Third, the whole product must be reproducible from this repository with one command or one CI run.

### Implemented features

- **Network-level blocking**: ads, trackers, and malware domains are filtered in `shouldInterceptRequest` using the bundled StevenBlack unified hosts list (~140k domains, MIT licensed) plus a curated URL-pattern rule set. Every blocked request is counted per page, per session, and all-time.
- **Cosmetic filtering**: leftover ad boxes and banners are hidden by a curated element-hiding list injected into every page, with a debounced MutationObserver for dynamically inserted ads.
- **YouTube ad suppression**: the same client-side technique scriptlet-based blockers use on the web - player-API responses are pruned of `adPlacements`/`adSlots`/`playerAds` before the player sees them (document-start script, fetch + XHR hooks, setter trap on `ytInitialPlayerResponse`), unskippable ad segments are muted and fast-forwarded, skip and overlay-close buttons are auto-clicked, and ad containers are CSS-hidden. See `docs/CONTENT_BLOCKING.md` for the honest scope of this approach.
- **Live transparency**: menu shows blocked counts; the padlock icon and connection dialog explain HTTPS status; a site allowlist exempts chosen domains.
- **Tabs**: multi-tab browsing with a grid switcher, incognito tabs that skip history and bookmarks, and session restore across restarts (up to 10 tabs).
- **Privacy controls**: third-party cookie blocking (on by default), optional global cookie/JavaScript switches, Do Not Track + Global Privacy Control headers, optional algorithmic darkening, one-tap data clearing (history, cookies, site storage).
- **Real browser plumbing**: omnibox with smart URL/search detection, six search engines (DuckDuckGo default), bookmarks and history (SQLite), system DownloadManager integration, file upload support, camera/microphone permission prompts, geolocation prompts, SSL error warnings, find-in-page, share targets, full-screen video, Material You dynamic theming with system/light/dark modes.
- **Filter list updates**: one tap in Settings fetches the newest StevenBlack list with basic integrity validation.

### Honest limitations

Zerium v1.0.0 uses the **Android System WebView** engine. That is a deliberate, documented tradeoff (see `docs/ARCHITECTURE.md`): it lets a small team ship a small, fast, fully CI-buildable browser instead of maintaining a 100 GB Chromium fork. It also brings real constraints, stated plainly:

1. **No extension support.** WebView has no WebExtensions API.
2. **In-stream video ads (including YouTube's) cannot be removed at the network level**, because they are served from the same endpoints as the video itself. Zerium therefore suppresses them client-side (player-API pruning + auto-skip + overlay hiding), which removes or shortens most of them; it remains an arms race and some formats can still slip through as YouTube changes.
3. **DNT/GPC headers apply to main-frame requests**; WebView does not expose per-subresource header injection.
4. **Incognito shares the WebView cookie jar** with normal tabs; history and bookmarks are skipped, and true cookie isolation is planned via the WebView Profiles API.
5. Blocking is domain/path based; it is not a full filter-list DSL (no EasyList syntax) in v1.

### Roadmap

1. **GeckoView engine track**: evaluate Mozilla GeckoView as an alternative engine build flavor, which brings real WebExtensions support (including uBlock Origin-class blockers) and per-profile cookie isolation.
2. Per-site toggles (JS, cookies, blocking) with a site panel.
3. HTTPS-first mode with automatic upgrade and downgrade warnings.
4. Custom filter syntax (subset of EasyList element hiding).
5. WebView Profiles API for true incognito isolation (API level permitting).
6. Reproducible release signing documentation.

### Building

```bash
./gradlew assembleRelease        # signed with a keystore from KEYSTORE_FILE env or app/release.keystore
./gradlew assembleDebug
```

Full toolchain requirements and CI details are in `docs/BUILD.md`. The GitHub Actions workflow builds both APKs on every push and attaches them to the rolling `latest` release.

### Repository layout

```
app/src/main/java/com/zerium/browser/   Browser engine, UI, blocking, storage
app/src/main/assets/blocklists/         Bundled hosts list + cosmetic selectors
docs/                                   Architecture, blocking, privacy, build, research
.github/workflows/build.yml             CI: build, release, Telegram notify
```

### License

- Zerium code: **GPL-3.0** (see `LICENSE`).
- Bundled hosts list: [StevenBlack/hosts](https://github.com/StevenBlack/hosts), MIT.
- Android, androidx, Material Components: Apache 2.0 / their respective licenses.

Credits and detailed license matrix: `docs/RESEARCH_SUMMARY.md`.
