# iosApp

Placeholder for the netgrip-mobile iOS target.

The shared code already includes the `iosArm64` and `iosSimulatorArm64` targets and the `mainViewController()` entry point in the `shared` module. The remaining work requires macOS with Xcode:

1. Build the shared framework. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` only works with the Kotlin/Native toolchain, and final packaging needs Xcode.
2. Create the Xcode project that consumes the framework and calls `mainViewController()`.
3. Implement `IosSecureStorage` with Keychain. It is currently a documented stub.
4. Validate on the simulator and a device.

Klibs can be compiled on Linux, but that does not produce a complete iOS app. The decision is recorded in docs/adr/0001-app-nativa-kmp.md.
