<!-- One logical change per PR. Commit subjects must follow Conventional Commits (see CONTRIBUTING.md). -->

## Summary

<!-- What does this PR change, in 2–4 sentences? -->

## Motivation

<!-- Link the issue: Fixes #NNN. Why is this the right approach over the alternatives? -->

## Type of change

- [ ] Bug fix (`fix`)
- [ ] New feature (`feat`)
- [ ] Performance (`perf`)
- [ ] Documentation (`docs`)
- [ ] CI / release pipeline (`ci`)
- [ ] Refactor with no behavior change (`refactor`)

## Testing

- [ ] `./gradlew assembleDebug` passes locally
- [ ] `./gradlew lint` passes locally
- [ ] Tested on a real device or emulator (state API level and device): **…**
- [ ] Blocking behavior verified on a site affected by this change (if applicable)

## Checklist

- [ ] Commit messages follow Conventional Commits
- [ ] User-facing changes are reflected in `CHANGELOG.md` (Unreleased) and, if needed, the README/docs
- [ ] No new permission, service, network endpoint, or telemetry of any kind
- [ ] The "Honest limitations" section of the README is still accurate after this change
- [ ] Blocklist changes carry source attribution (if applicable)

CI must be green (required check: **Build, sign and publish**) before review.
