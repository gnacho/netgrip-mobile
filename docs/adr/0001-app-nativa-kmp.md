# ADR 0001: New native app with Kotlin Multiplatform

Date: 2026-11-22
Status: accepted

## Context

NetGrip already exposes its own JSON REST HTTP API, so a mobile app does not need to use LuCI RPC. luci-mobile (Flutter, GPL-3.0, approximately 71,600 lines of Dart) is coupled to LuCI, UCI, and ubus. Porting it would be a rewrite, not a conversion. I already have Kotlin experience and another KMP project in production, dootix-mobile.

## Decision

Create a new native app named netgrip-mobile with Kotlin Multiplatform and Compose Multiplatform, in a new repository rather than a fork. Android comes first, with iOS targets prepared from the start. luci-mobile is only a functional, UX, and security reference.

Rejected alternatives: PWA/TWA, which does not provide multi-router profiles, secure storage, or native integration, and a Flutter fork of luci-mobile, which would add another language to the ecosystem, retain an architecture coupled to LuCI, and create GPL derivative obligations.

## Consequences

- The project uses AGPL-3.0. GPLv3 permits combining GPL-3.0 code from luci-mobile into an AGPL-3.0 project while retaining its notices if needed. The intention is not to copy code.
- iOS: klibs compile on Linux, but the linked framework, Xcode, and .ipa require a Mac. The iOS app is not part of the first deliverable.
- AGP 9: the shared module uses `com.android.kotlin.multiplatform.library` and the app uses the Kotlin support built into AGP. Gradle is pinned to 9.7.0, the maximum fully supported by KGP 2.4.21.
