# Building Zerium

## Requirements

- JDK 17 (Temurin recommended; the CI workflow pins it)
- Android SDK: platform 34, build-tools 34.x (`sdkmanager "platforms;android-34" "build-tools;34.0.0"`)
- Gradle is not required locally; the wrapper (8.7) downloads itself on first run

## Commands

```bash
./gradlew assembleDebug     # fastest check, installable
./gradlew assembleRelease   # signed release
```

Release signing reads environment variables:

- `KEYSTORE_FILE` - path to a PKCS12 keystore (defaults to `app/release.keystore`)
- `KEYSTORE_PASSWORD` (default `zerium`), `KEY_ALIAS` (default `zerium`), `KEY_PASSWORD`

A personal-use keystore with password `zerium` and alias `zerium` is provisioned by the maintainer and injected into CI through the `KEYSTORE_B64` repository secret (base64 of the PKCS12 file). Builds stay signature-stable across runs. Treat the released APK as signed by a hobby-grade key: fine for personal and F-Droid-style distribution, regenerate before any Play Store release.

## CI (GitHub Actions)

`.github/workflows/build.yml` on every push to `main`:

1. Sets up JDK 17 + Gradle cache.
2. Decodes `KEYSTORE_B64` (or generates an ephemeral keystore when unset, with a warning in the log).
3. Runs `assembleRelease assembleDebug` with `--no-daemon --stacktrace`.
4. Uploads both APKs as workflow artifacts (30-day retention).
5. Publishes/refreshes the rolling `latest` GitHub release with both APKs.
6. Sends a Telegram status message when `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` secrets are present.

Repository secrets used: `KEYSTORE_B64`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.

## Local troubleshooting

- First build downloads ~500 MB of dependencies; be patient or pre-warm with `./gradlew help`.
- `Unable to locate Android SDK`: create `local.properties` with `sdk.dir=/path/to/android-sdk`.
- Keystore errors on release builds: the signing config resolves `KEYSTORE_FILE` or falls back to `app/release.keystore`; either provide the file or build debug.
