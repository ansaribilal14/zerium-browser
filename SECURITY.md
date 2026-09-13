# Security Policy

Zerium Browser is a privacy product: security reports are treated as first-class engineering work, not as reputation management. Thank you for reporting responsibly.

## Supported versions

| Channel | Version | Status |
|---------|---------|--------|
| Stable (version tags) | v1.1.x | ✅ Supported — security fixes land here |
| Rolling (`latest` tag) | current `main` | ✅ Supported — fixes appear here first |
| v1.0.x and older | — | ❌ Unsupported — please update |

## Reporting a vulnerability

**Preferred:** use GitHub's **Private Vulnerability Reporting** (Security tab → "Report a vulnerability"). This keeps the report, discussion and fix coordinated privately and gives you credit automatically.

If private reporting is unavailable, contact the maintainer via the email listed on the [owner profile](https://github.com/ansaribilal14). Please **do not open a public issue** for anything exploitable.

Include what you can of: affected version/commit, reproduction steps, impact assessment, and (if relevant) a minimal proof of concept. We commit to:

1. **Acknowledge** reports within 72 hours.
2. Provide an initial assessment and a fix timeline within 7 days.
3. Fix and release for vulnerabilities within **90 days**, or publish a reasoned exception.
4. Credit reporters in release notes unless anonymity is requested.

## Scope

**In scope:**
- Zerium's own code: request interception, cosmetic filtering, YouTube suppression layer, tab/session storage, download handling, permission prompts, settings and allowlist enforcement.
- The bundled blocklists (e.g. a rule that breaks first-party pages or blocks a security-critical endpoint).
- The build and release pipeline (e.g. artifact tampering, keystore handling).

**Out of scope:**
- Vulnerabilities in the **Android System WebView / Chromium engine itself** — report those to the [Android Security Program](https://source.android.com/docs/security/overview/reports) or [Chromium's VRP](https://bugzilla.chromium.org/). We track and disclose engine-level constraints honestly (e.g. the incognito cookie-jar limitation documented in the README), but we cannot patch the engine.
- Social engineering of users, and issues requiring a compromised device.

## Verifying release artifacts

Every release ships a `SHA256SUMS.txt`:

```bash
sha256sum -c SHA256SUMS.txt        # in the folder with the downloaded APKs
```

Release and debug APKs are signed with the project's release keystore (alias `zerium`). If a signature ever stops matching across versions, treat it as compromised and report immediately.
