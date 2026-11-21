# ADR 0002: Sessions and secrets

Date: 2026-11-22
Status: accepted

## Context

NetGrip authenticates with POST /api/login and returns the session in an HttpOnly cookie (`netgrip_http_session` over HTTP and `__Secure-netgrip_session` over HTTPS. The legacy `netgrip_session` is accepted but no longer issued). Login has rate limiting through 429 with `Retry-After`, and authenticated routes return 401 without a valid session. Backend session revocation is in memory and does not survive a process restart.

## Decision

- Store the encrypted session token and not the password by default. Android uses AES/GCM with a key in Android Keystore. iOS will use Keychain, pending implementation and validation on a Mac. `IosSecureStorage` is currently a stub.
- Restore a session by validating it with GET /api/me before accepting it.
- On a 401 response, mark the session as expired and explicitly return to login without retry loops.
- On a 429 response, respect `Retry-After` without aggressive retries.
- Managing multiple cookies by scheme (HTTP/HTTPS) and multi-router profiles with primary and alternative URLs will arrive in phase 2. SessionManager already indexes by profile name.

## Consequences

Saving the password for automatic login is a separate user decision and is not implemented in phase 1.
