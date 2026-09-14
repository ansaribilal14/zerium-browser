# Research Summary and Decision Log

Condensed findings from the pre-build research phase of this project (full exploration of the Android privacy-browser ecosystem, completed before any implementation).

## Ecosystem survey

- **Cromite** (Bromite successor): Chromium fork with adblocking patches, per-site toggles, strong defaults. Gold standard for engine-level privacy, but a fork means permanent upstream tracking: rebasing a patch stack across Chromium's ~6-week release train is an operations commitment larger than the browser itself. Building Chromium needs ~100 GB source, 16+ cores, 8+ hours; standard GitHub-hosted runners (4 vCPU, 14 GB RAM, 14 GB disk) cannot host it without self-hosted infrastructure.
- **Brave**: proof that a browser can ship native network+cosmetic filtering at engine level (adblock-rust, Chromium net stack integration). Its differentiators (BAT/ads/rewards) were explicitly out of scope for this project per the product brief.
- **adblock-rust**: high-performance Rust filter engine (the one Brave uses). Integrating it requires either a Chromium fork or a JNI bridge against a custom net stack; neither is reachable from a WebView-based v1. Reference value for the future engine track.
- **ungoogled-chromium**: patch philosophy (de-Googling without new features) informed Zerium's default choices, even though the codebase itself is not reused.
- **WebView-based browsers (Lightning/Foxhusk lineage, Privacy Browser)**: prove the category is viable, but most ship weak blocking (hosts-file only, no counters, no updates) and dated UI. That combination - strong blocking, live counters, modern Material 3 - is the gap Zerium v1 targets.

## License matrix (shipped artifacts)

| Component | License | Compatibility with GPL-3.0 app |
|---|---|---|
| Zerium code | GPL-3.0 | Base license |
| Android SDK / androidx / Material | Apache-2.0 / BSD / MIT mix | Compatible |
| StevenBlack hosts | MIT | Compatible (attribution kept) |
| EasyList (sanitized generic-hide subset, `blocklists/cosmetic.txt`) | CC-BY-SA-3.0 | Compatible with attribution + share-alike on the list file itself (attribution and license are carried in the file header) |
| Mozilla Readability (`assets/readability.js`, v0.6.0) | Apache-2.0 | Compatible (license header retained in the asset) |
| Android System WebView | System component (not redistributed) | N/A - invoked, not shipped |

Deliberately excluded: any BAT/crypto/ads code, any analytics SDK. EasyList-family lists were excluded in v1.0/v1.1 (the curated selector set was first-party GPL code); from v1.3.0 a sanitized, validated subset of EasyList's generic element-hiding rules is bundled with attribution under CC-BY-SA-3.0, whose terms (attribution + share-alike applying to the list file) are satisfied in the file header and this matrix.

## Decision log

1. **Base (v1): system WebView** - the only option satisfying real-Chromium rendering + one-maintainer CI + zero fork burden. Chromium fork track stays documented as the long-term engine ambition; GeckoView is the intermediate step with extension support.
2. **Language: Java over Kotlin** - removes the Kotlin/AGP version matrix from CI failure surface for a solo-maintained project; no language runtime in the shipped APK.
3. **Blocking: hosts + patterns + sanitized cosmetic selectors** - honest ceiling for WebView; no fake "uBlock-compatible" claims.
4. **minSdk 26** - covers Android 8+ (mid-90s percent of devices), lets adaptive icons and modern WebView APIs stand in for compat shims.
5. **No R8 minification in v1** - reproducibility and debuggability beat ~30% APK size at this stage.
6. **Fail-open blocking during list load** - availability over purity; disclosed in docs.
