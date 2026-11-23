# ADR 0003: Per-router TLS fingerprints

Date: 2026-11-22
Status: accepted, implemented on Android

## Context

NetGrip is often served on the LAN over HTTP or HTTPS with a self-signed certificate. Accepting any certificate removes TLS protection.

## Decision

- Never accept every certificate. The Android manifest explicitly and deliberately sets `usesCleartextTraffic="true"` for HTTP-only routers.
- Before sending credentials, Android probes HTTPS without a session cookie. It tries HTTP only when HTTPS does not respond. A certificate failure never triggers an HTTP downgrade.
- Certificates trusted by the system work without a prompt. An unknown certificate is rejected during the handshake and its SHA-256 fingerprint is shown for explicit confirmation. Only after confirmation is the fingerprint encrypted with the existing `SecureStorage` and used for that normalized host.
- A pinned host never falls back to HTTP. A different certificate is rejected before any HTTP request or credential is sent, including during login and session restoration.
- iOS keeps supporting HTTP and system-trusted HTTPS. Self-signed certificate confirmation is explicitly unsupported there and no trust-all handler is installed.

## Consequences

Scheme selection and credential gating have common tests. Android host tests also start local TLS servers with temporary self-signed certificates whose CN does not match the host, verifying unknown, accepted and changed certificates without contacting a real router. Changing or removing a pinned certificate requires an explicit recovery flow in a later phase.
