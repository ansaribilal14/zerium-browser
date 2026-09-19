# GeckoView Phase-0 Spike

Phase 0 of the [GeckoView migration plan](GECKOVIEW_MIGRATION.md) is now in
the repository. Its purpose is narrow and concrete: prove the toolchain and
the basic engine wiring before any user-facing commitment is made.

## What exists

A separate Gradle module, `:gecko-spike`, contains a minimal single-activity
GeckoView harness:

- `gecko-spike/build.gradle` — pins `org.mozilla.geckoview:geckoview`
  (133 / Firefox 133, full build timestamp for reproducibility; 140+ currently pulls androidx.core 1.16.0 which would require AGP 8.6+ — not worth disturbing the release toolchain for a spike) from Mozilla's own
  Maven repository (`maven.mozilla.org`), which is added to the project
  repository list for this module only.
- `gecko-spike/src/main/java/.../SpikeActivity.java` — creates a
  `GeckoRuntime`, opens a `GeckoSession`, wires progress and content
  delegates, loads a page, and manages session lifecycle (`setActive` on
  start/stop, `close` on destroy). Everything is programmatic: no resources,
  no support libraries, no Zerium code — the spike shows the bare engine cost.

CI runs it as a separate, non-gating job (`gecko-spike`) that builds
`:gecko-spike:assembleDebug`. A green job proves the dependency resolves,
compiles under JDK 17 / AGP 8.5, and produces an installable APK.

## What it deliberately is not

- **It is not the shipped browser.** The production `:app` module keeps
  building on the Android System WebView exactly as before. It cannot depend
  on the spike module; the release pipeline builds `:app` only, and the
  spike APK is unsigned and unpublished.
- **It is not a product decision.** Phase 0 answers feasibility questions
  (dependency, boot, delegates, lifecycle). It does not touch the four hard
  problems identified in the migration assessment: `shouldInterceptRequest`
  parity for the existing blocklists, APK size (~85–105 MB), startup cost,
  and the port of every WebView touchpoint listed in `GECKOVIEW_MIGRATION.md`.

## How to build it locally

```bash
./gradlew :gecko-spike:assembleDebug
adb install -r gecko-spike/build/outputs/apk/debug/gecko-spike-debug.apk
```

The spike appears in the launcher as *Zerium Gecko Spike*, separate from the
real Zerium Browser.

## Decision gate (unchanged from the migration plan)

Phase 0 staying green in CI for a while, plus measurements on a real device
(startup, memory, blocking ceiling via `WebExtension`/`WebRequestFilter`),
feed the go/no-go decision for Phase 1. The decision criteria are listed in
`GECKOVIEW_MIGRATION.md` and are not relaxed by this spike.
