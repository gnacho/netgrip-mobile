# ADR 0003: Per-router TLS fingerprints

Date: 2026-11-22
Status: accepted, implementation in phase 2

## Context

NetGrip is often served on the LAN over HTTP or HTTPS with a self-signed certificate. Accepting any certificate removes TLS protection.

## Decision

- Never accept every certificate.
- Phase 1 (spike): test over LAN HTTP or valid HTTPS. The Android manifest explicitly and deliberately sets `usesCleartextTraffic="true"` during the spike.
- Phase 2: when connecting to a self-signed HTTPS endpoint for the first time, show the certificate fingerprint and ask for explicit confirmation. Store the fingerprint per router and warn if it changes. Also define the behavior when the same router switches between HTTP and HTTPS, because NetGrip issues cookies with different names.

## Consequences

No phase 1 screen claims certificate validation yet. The phase 2 acceptance check includes real tests for self-signed certificates and scheme changes.
