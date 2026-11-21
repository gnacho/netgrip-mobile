# NetGrip Mobile

[Leer en castellano](README.es.md)

NetGrip Mobile is a native mobile client for [NetGrip](https://github.com/gnacho/netgrip), an OpenWrt management panel. It uses Kotlin Multiplatform and Compose Multiplatform. Android is the first target, with iOS targets already configured.

## Screenshot

<img src="docs/images/netgrip-mobile-dashboard.png" alt="NetGrip Mobile dashboard on Android with simulated router and client data" width="360">

*Simulated data for the screenshot.*

## Status

This is the phase 1 read-only scaffold. The Android host tests and debug APK build pass. Emulator tests and validation against a real router are still pending.

See [docs/fase-1-spike.md](docs/fase-1-spike.md) for the scope and acceptance checks.

## Requirements

- JDK 17
- Android Studio with support for AGP 9.5
- Android SDK platform 37
- Gradle 9.7.0 through the wrapper

If `gradlew` is not executable, run `chmod +x gradlew`.

## Project structure

```text
shared/       shared API, session, encrypted storage, view models, and Compose UI
androidApp/   Android entry point, MainActivity and manifest
iosApp/       documented placeholder, requires a Mac
docs/         ADRs and phase 1 specification
```

The `shared` module uses the `com.android.kotlin.multiplatform.library` plugin from AGP 9. The Android app uses the Kotlin support built into AGP, so it does not apply `org.jetbrains.kotlin.android`.

## Main commands

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:assembleDebug
```

The first command runs the `commonTest` suite with Ktor MockEngine. The second builds the debug APK.

## Security

- Store the encrypted session token instead of the password by default. Android uses Android Keystore and iOS will use Keychain.
- A 401 response marks the session as expired and returns to the login screen. A 429 response respects `Retry-After`.
- The app does not accept every TLS certificate. Per-router certificate fingerprint support is planned for phase 2.

See [ADR 0002](docs/adr/0002-sesiones-y-secretos.md) and [ADR 0003](docs/adr/0003-tls-huella-por-router.md) for details.

## License

AGPL-3.0. See [LICENSE](LICENSE). If code from luci-mobile is reused in the future, its GPL-3.0 notices will be retained. See [ADR 0001](docs/adr/0001-app-nativa-kmp.md).
