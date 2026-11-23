# Phase 1: read-only technical spike

Small specification for the first netgrip-mobile deliverable. Source: handoff from 21-Nov-2026. All API details come from `internal/server/server.go` in NetGrip. Response bodies marked as unverified are intentional and must not be assumed.

## Goal

Demonstrate the complete read-only flow against a real router: configure, log in, restore the session, read data, display states, and log out.

## Scope

1. Enter the router URL and credentials.
2. Log in with POST /api/login.
3. Restore the session and validate it with GET /api/me.
4. Read GET /api/system and GET /api/clients.
5. Show loading, error, expired session, unreachable router, and rate limit states.
6. Log out with POST /api/logout.
7. Include HTTP mock tests from the start.

## Endpoints

| Endpoint | Method | Relevant responses |
|---|---|---|
| /api/login | POST | 204 + Set-Cookie, 401 for credentials, 429 + Retry-After, 502 when rpcd is unreachable |
| /api/me | GET | 204 for a valid session, 401 without a session |
| /api/logout | POST | Revokes the session and expires cookies |
| /api/system | GET | 200 JSON, body unverified |
| /api/clients | GET | 200 JSON, body unverified |

The login body is `{"username":"...","password":"..."}`. If the username is empty, the backend uses root. The session travels in an HttpOnly cookie. Possible names are `__Secure-netgrip_session` for HTTPS, `netgrip_http_session` for HTTP, and the legacy `netgrip_session`.

## DTO policy

The early scaffold consumed /api/system and /api/clients as `JsonElement`. The
read-only shell replaces that with typed DTOs whose field names, units and
optional values were checked against the NetGrip handlers. The contract, the
tabs and the health rules are documented in `docs/readonly-shell.md`.

## UI states

`Idle`, `Loading`, `Ready`, `Error(message)`, `SessionExpired`, `RouterUnreachable`, `RateLimited(retryAfter)`, and `CertificateConfirmation(host, fingerprint)`. `SessionExpired` forces an explicit return to login. The app must not remain in a loading state indefinitely.

## On-demand router discovery

Android can send the existing NetGrip fleet probe to UDP broadcast port 5141. The socket remains open for at most 3 seconds, accepts bounded JSON beacons, validates protocol version, type, ID, display fields, IPv4 address, port and a timestamp within 60 seconds, and deduplicates by ID, address and port. One pass retains at most 32 routers. No credentials are sent or tested during discovery.

Beacons are unsigned, so every discovered router is labeled "Sin verificar". Selecting one only prepares the login form. Discovered URLs start as `http://<address>:<port>` because the beacon carries no transport scheme. After the user presses Entrar, Android probes HTTPS and then HTTP if needed with a credential-free GET /api/me. System-trusted certificates proceed directly. An unknown certificate requires explicit SHA-256 confirmation before login, is persisted per host, and is rejected if it changes. A pinned host never falls back to HTTP. Manual HTTP and HTTPS entry remains available.

iOS discovery is explicitly unsupported. The iOS UI offers manual entry and never returns simulated routers. iOS supports HTTP and system-trusted HTTPS, but self-signed certificate confirmation is explicitly unsupported.

## Acceptance checks

- G1: `./gradlew :shared:testAndroidHostTest` passes and `./gradlew :androidApp:assembleDebug` generates an APK.
- G2: login, session restoration, reads, error states, and logout work in an Android emulator against a mock.
- G3: with the user's authorization, run a read-only test against a real router. Do not install packages or restart services to test the app.

## Out of scope

Writes, persisted multi-router profiles, additional endpoints, the iOS app, and publication. The main risk in this phase is operational: a mobile error must not be able to lock the user out of the router, so this phase performs no writes.
