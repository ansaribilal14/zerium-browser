# Contributing to Zerium Browser

First off, thank you for considering a contribution. Zerium is a small, focused browser, and every contribution — code, documentation, blocklist curation, bug reports — is reviewed against the project's founding principles.

## Code of conduct

By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Report unacceptable behavior through GitHub's private contact options on the maintainer profile.

## Project principles

Contributions are evaluated against three non-negotiable rules that also govern the maintainer:

1. **Honesty over marketing.** Every documented capability must actually exist in code, and limitations must stay documented. A PR that quietly weakens the "Honest limitations" section will be rejected even if the code is perfect.
2. **Native, transparent blocking.** Blocking happens in the browser's own network stack and rendering pipeline, counts every blocked request, and never ships a proprietary list.
3. **Reproducible product.** The APK must remain buildable from this repository with one Gradle command locally or one CI run. No step may depend on a maintainer's private machine.

Additionally: **no telemetry, no analytics, no new permissions** without explicit prior discussion in an issue.

## Development environment

| Tool | Version |
|------|---------|
| JDK | 17 (Temurin recommended) |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.7 (wrapper provided) |
| Android SDK | Platform 34, minSdk 26 |
| Language | Java (no Kotlin in the main source set yet) |

```bash
git clone https://github.com/ansaribilal14/zerium-browser.git
cd zerium-browser
./gradlew assembleDebug        # debug build, no signing required
./gradlew lint                 # Android lint
```

Full toolchain details, including release signing, are in [`docs/BUILD.md`](docs/BUILD.md).

## Repository map

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/zerium/browser/` | All browser code: engine, UI, blocking, storage |
| `app/src/main/assets/blocklists/` | Bundled hosts list + cosmetic selectors |
| `docs/` | Architecture decisions, blocking scope, privacy policy, build guide |
| `.github/workflows/build.yml` | CI: build, sign, checksums, release publication |
| `.github/scripts/publish_release.sh` | Release notes generation + two-channel publishing |

## Reporting bugs and suggesting features

Please use the [issue templates](.github/ISSUE_TEMPLATE) — they exist because the maintainers need the same information every time. Before filing a blocking-related bug, check [`docs/CONTENT_BLOCKING.md`](docs/CONTENT_BLOCKING.md): it explains which formats are suppressed client-side and why some can slip through.

## Commit message convention

Zerium uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/). Release notes and changelogs are derived from commit subjects, so well-formed messages are a hard requirement, not a nicety.

```
<type>(<optional scope>): <imperative summary, max ~72 chars>

<optional body: motivation, approach, tradeoffs>
```

| Type | Use for |
|------|---------|
| `feat` | New user-facing feature (`feat(tabs): add incognito session restore`) |
| `fix` | Bug fix (`fix(settings): row weight misuse made titles zero-width`) |
| `perf` | Performance improvement |
| `docs` | Documentation only |
| `ci` | Workflow / release pipeline changes |
| `refactor` | No behavior change |
| `chore` | Tooling, editorconfig, housekeeping |
| `security` | Vulnerability fix or hardening |

A release tag (`vX.Y.Z`) is only created when `versionName` in `app/build.gradle` changes; CI derives both the tag and the release notes from history automatically.

## Pull request process

1. Fork, create a topic branch from `main` (e.g. `fix/settings-row-layout`).
2. Keep one logical change per PR; rebase rather than merge.
3. Verify `./gradlew assembleDebug` and `./gradlew lint` pass locally.
4. Fill in the PR template completely, including the device you tested on.
5. CI must be green before review. The required check is **Build, sign and publish**.

**Review checklist** (maintainers will verify): behavior matches the description; docs and changelog updated for user-facing changes; no new permission, service, or network endpoint; blocklist changes include source attribution; limitations section still accurate.

## Blocklist policy

- The hosts list ships from [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT) and is refreshed upstream, not hand-edited here.
- Cosmetic selectors and URL-pattern rules are curated: each rule must hide or block an actual ad/tracker surface, must not break first-party page functionality, and carries a comment when the purpose is not obvious.
- If a site breaks, open a bug report with the URL and a screenshot before proposing removal of a rule.

## Licensing

By contributing, you agree that your contributions are licensed under the **GNU General Public License v3.0**, the project's license. Third-party data sources must remain under licenses compatible with bundling (MIT/Apache-2.0/CC0 preferred) and must be credited in `docs/RESEARCH_SUMMARY.md`.
