<div align="center">

# Zerium Browser

**A free, open-source, privacy-first browser for Android.**

Native ad & tracker blocking · Cosmetic filtering · YouTube ad suppression · No accounts · No telemetry

[![CI — Build, sign and publish](https://github.com/ansaribilal14/zerium-browser/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/ansaribilal14/zerium-browser/actions/workflows/build.yml)
[![Stable release](https://img.shields.io/github/v/release/ansaribilal14/zerium-browser?sort=semver&label=stable&color=2E7D32)](https://github.com/ansaribilal14/zerium-browser/releases/latest)
[![Rolling build](https://img.shields.io/badge/rolling-latest-1565C0)](https://github.com/ansaribilal14/zerium-browser/releases/tag/latest)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Java](https://img.shields.io/badge/Java-17-EE8721?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/github/license/ansaribilal14/zerium-browser?color=6F42C1)](LICENSE)

[Download](#installation) · [Releases](#releases--channels) · [Documentation](#documentation) · [Report a bug](https://github.com/ansaribilal14/zerium-browser/issues/new?template=bug_report.yml) · [Security](SECURITY.md)

</div>

---

## Why Zerium

Zerium is a privacy-first Android browser that treats ad and tracker blocking as a core engine feature, not an afterthought. It is built for people who want a fast browser that respects them by default: no account, no telemetry, no ads served by the browser itself, and full source transparency under GPL-3.0.

The project follows three hard rules. First, **honesty over marketing**: every capability documented here is actually implemented in the code, and the [limitations section](#honest-limitations) is explicit about what the current engine can and cannot do. Second, **blocking must be native and transparent**: the browser ships with a bundled blocklist, counts every blocked request, and shows you the numbers. Third, **the whole product must be reproducible** from this repository with one command or one CI run.

## Feature overview

- **Network-level blocking** — ads, trackers, and malware domains are filtered in `shouldInterceptRequest` using the bundled StevenBlack unified hosts list (~80k domains, MIT licensed) plus ~60 curated URL-pattern rules covering ad exchanges, analytics pixels, and popup networks. Every blocked request is counted per page, per session, and all-time.
- **Cosmetic filtering** — 1,200 sanitized element-hiding selectors (a validated EasyList generic-hide subset, CC-BY-SA-3.0, with curated additions) are injected at document start on supporting WebViews, so ad containers are hidden before the page first paints; a debounced MutationObserver keeps hiding dynamically inserted ads.
- **YouTube ad suppression** — modeled on uBlock Origin's actively maintained YouTube scriptlets: ad structures are pruned from player responses *before the player parses them*, so most ads are never scheduled and never show up (the closest a WebView can get to Brave's aggressive-mode "block" behavior); any remaining skip buttons are clicked the instant they appear; a strictly-scoped fallback finishes unskippable in-stream ads muted and fast-forwarded, then restores your exact playback rate — overlay ads and normal playback are never touched. On by default, with a Settings toggle. See `docs/CONTENT_BLOCKING.md` for the honest scope.
- **Live transparency** — menu shows blocked counts; the padlock icon and connection dialog explain HTTPS status; a per-site allowlist exempts chosen domains.
- **Tabs** — multi-tab browsing with a grid switcher showing live page previews, incognito tabs that skip history and bookmarks, and session restore across restarts.
- **Privacy controls** — third-party cookie blocking (on by default), optional global cookie/JavaScript switches, Do Not Track + Global Privacy Control headers, algorithmic darkening, one-tap data clearing (history, cookies, site storage).
- **Real browser plumbing** — omnibox with smart URL/search detection, six search engines (DuckDuckGo default), bookmarks and history (SQLite), system DownloadManager integration, file upload support, camera/microphone permission prompts, geolocation prompts, SSL error warnings, find-in-page, share targets, full-screen video, Material You dynamic theming with system/light/dark modes.
- **Filter list updates** — one tap in Settings fetches the newest StevenBlack list with integrity validation.

## Releases & channels

Zerium ships on two channels. Both APKs are signed with the same key, and every release includes `SHA256SUMS.txt` for verification.

| Channel | Tag | What it is | Audience |
|---------|-----|------------|----------|
| **Stable** | `vX.Y.Z` (e.g. [`v1.1.0`](https://github.com/ansaribilal14/zerium-browser/releases/tag/v1.1.0)) | Immutable, tagged, versioned release. History in [`CHANGELOG.md`](CHANGELOG.md). | Daily use — recommended |
| **Rolling** | [`latest`](https://github.com/ansaribilal14/zerium-browser/releases/tag/latest) | Recreated on every successful build of `main`; includes the newest fixes. | Testers and contributors |

Each release provides a **signed release APK** (recommended) and a **debug APK** (for development), named `Zerium-v<version>-r<commit>-{release,debug}.apk`.

## Installation

1. Download `Zerium-v*-release.apk` from the [stable release](https://github.com/ansaribilal14/zerium-browser/releases/latest).
2. Optional but recommended: verify integrity with `sha256sum -c SHA256SUMS.txt`.
3. Open the APK and allow installs from your browser or file manager when prompted.
4. Requires **Android 8.0+** (API 26). Universal APK: arm64-v8a, armeabi-v7a, x86, x86_64.

## Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Engine decision matrix — why v1 ships on System WebView and what GeckoView/Chromium-fork tracks would change |
| [`docs/GECKOVIEW_MIGRATION.md`](docs/GECKOVIEW_MIGRATION.md) | Full GeckoView migration assessment — what ports untouched, what gets rewritten (API-by-API), costs, and a phased no-Chromium-fork plan |
| [`docs/CONTENT_BLOCKING.md`](docs/CONTENT_BLOCKING.md) | The three blocking layers, YouTube suppression scope, and why network-level in-stream removal is impossible on WebView |
| [`docs/PRIVACY.md`](docs/PRIVACY.md) | What Zerium does and does not collect (short version: nothing) |
| [`docs/BUILD.md`](docs/BUILD.md) | Toolchain requirements, release signing, CI internals |
| [`docs/RESEARCH_SUMMARY.md`](docs/RESEARCH_SUMMARY.md) | Research basis and third-party license matrix |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Prioritized future work |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history (Keep a Changelog format) |

## Honest limitations

Zerium v1 uses the **Android System WebView** engine. That is a deliberate, documented tradeoff (see `docs/ARCHITECTURE.md`): it lets a small team ship a small, fast, fully CI-buildable browser instead of maintaining a 100 GB Chromium fork. It also brings real constraints, stated plainly (the GeckoView path out of them is assessed in `docs/GECKOVIEW_MIGRATION.md`):

1. **No extension support.** WebView has no WebExtensions API.
2. **In-stream video ads (including YouTube's) cannot be removed at the network level**, because they are served from the same endpoints as the video itself. Zerium therefore suppresses them client-side (player-API pruning + auto-skip + overlay hiding), which removes or shortens most of them; it remains an arms race and some formats can still slip through as YouTube changes.
3. **DNT/GPC headers apply to main-frame requests**; WebView does not expose per-subresource header injection.
4. **Incognito shares the WebView cookie jar** with normal tabs; history and bookmarks are skipped, and true cookie isolation is planned via the WebView Profiles API.
5. Blocking is domain/path based; it is not a full filter-list DSL (no EasyList syntax) in v1.

## Roadmap

1. **GeckoView engine track** — evaluate Mozilla GeckoView as an alternative engine build flavor, which brings real WebExtensions support (including uBlock Origin-class blockers) and per-profile cookie isolation.
2. Per-site toggles (JavaScript, cookies, blocking) with a site panel.
3. HTTPS-first mode with automatic upgrade and downgrade warnings.
4. Custom filter syntax (subset of EasyList element hiding).
5. WebView Profiles API for true incognito isolation (API level permitting).
6. Reproducible release signing documentation.

## Building from source

```bash
./gradlew assembleRelease   # signed via KEYSTORE_FILE / KEYSTORE_PASSWORD env
./gradlew assembleDebug     # no signing required
./gradlew lint
```

Full toolchain requirements are in [`docs/BUILD.md`](docs/BUILD.md). CI builds both APKs on every push to `main`, computes SHA-256 checksums, publishes the rolling `latest` release, and creates a tagged stable release whenever `versionName` changes (`.github/scripts/publish_release.sh`).

## Repository layout

```
app/src/main/java/com/zerium/browser/    Browser engine, UI, blocking, storage
app/src/main/assets/blocklists/          Bundled hosts list + cosmetic selectors
docs/                                    Architecture, blocking scope, privacy, build, research
.github/workflows/build.yml              CI: build, sign, checksums, release publication
.github/scripts/publish_release.sh       Release notes generation + two-channel publishing
CHANGELOG.md                             Version history (Keep a Changelog)
```

## Contributing and community

Contributions are welcome — please start with [CONTRIBUTING.md](CONTRIBUTING.md), which covers the development environment, the Conventional Commits requirement, and the review checklist. Bug reports and feature requests go through the [issue templates](https://github.com/ansaribilal14/zerium-browser/issues/new/choose). Security vulnerabilities are handled privately per [SECURITY.md](SECURITY.md). All participants agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

- Zerium code: **GPL-3.0** (see [`LICENSE`](LICENSE)).
- Bundled hosts list: [StevenBlack/hosts](https://github.com/StevenBlack/hosts), MIT.
- Android, androidx, Material Components: Apache 2.0 / their respective licenses.

Credits and the detailed license matrix: [`docs/RESEARCH_SUMMARY.md`](docs/RESEARCH_SUMMARY.md).
