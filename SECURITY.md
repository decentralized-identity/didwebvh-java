# Security Policy

## Supported Versions

Until `1.0.0`, only the latest minor release line receives security fixes.

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅        |
| < 0.1.0 | ❌        |

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Instead, report privately via one of:

1. **GitHub private vulnerability reporting** —
   [Report a vulnerability](https://github.com/decentralized-identity/didwebvh-java/security/advisories/new)
   (preferred).
2. **Email** — `m.reza.maghool@gmail.com` with subject
   `[didwebvh-java security]`. Encrypt sensitive contents with the
   maintainer's GPG key from GitHub if possible.

Please include:

- A description of the issue and its impact.
- Steps to reproduce (proof-of-concept code is very welcome).
- Affected version(s) and, if known, the first affected commit.
- Your preferred handling timeline and credit preference.

## Response Expectations

- **Acknowledgement**: within 3 working days.
- **Initial triage and severity assessment**: within 7 working days.
- **Fix + coordinated disclosure**: target 30 days for high/critical
  issues, 90 days for lower-severity issues. Timelines may be extended
  by mutual agreement when a fix requires a spec clarification.

Fixed vulnerabilities will be announced via a GitHub Security Advisory
and noted in `CHANGELOG.md`. Reporters are credited unless they prefer
to remain anonymous.

## Scope

In scope:

- Any cryptographic, validation, or resolution flaw in
  `didwebvh-core`, `didwebvh-signing-local`, or `didwebvh-wizard`
  that allows forged log entries to be accepted, signed data to be
  replayed or substituted, or a DID to be hijacked.
- Supply-chain issues in the release pipeline (e.g. unsigned
  artifacts, compromised workflow).

Out of scope:

- Vulnerabilities in third-party dependencies that have a public CVE
  and an upstream fix — please file an issue requesting a dependency
  bump instead.
- Attacks requiring a compromised signing key (by design, a
  compromised key compromises the DID).
