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

The /api/system and /api/clients response bodies had not been captured when this scaffold was created. Phase 1 consumes them as `JsonElement` and displays the raw JSON.

Before adding typed DTOs, capture real router responses and fix the fields with contract tests. Before expanding the API surface, consider publishing a versioned OpenAPI contract from NetGrip.

## UI states

`Idle`, `Loading`, `Ready`, `Error(message)`, `SessionExpired`, `RouterUnreachable`, and `RateLimited(retryAfter)`. `SessionExpired` forces an explicit return to login. The app must not remain in a loading state indefinitely.

## Acceptance checks

- G1: `./gradlew :shared:testAndroidHostTest` passes and `./gradlew :androidApp:assembleDebug` generates an APK.
- G2: login, session restoration, reads, error states, and logout work in an Android emulator against a mock.
- G3: with the user's authorization, run a read-only test against a real router. Do not install packages or restart services to test the app.

## Out of scope

Writes, persisted multi-router profiles, TLS pinning, additional endpoints, the iOS app, and publication. The main risk in this phase is operational: a mobile error must not be able to lock the user out of the router, so this phase performs no writes.
