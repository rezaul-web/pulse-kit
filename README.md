# PulseKit

Cross-platform performance profiling SDK — Android first, KMP ready.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full specification. This README covers the current scaffold.

## Status: Phase 1 bootstrap ✅

A buildable Kotlin Multiplatform skeleton with the core plugin/event architecture and a working Android sample app.

## Modules

| Module | Type | Purpose |
|---|---|---|
| `pulse-core` | KMP (android + jvm) | Events, `EventBus`, dispatchers, config. No platform APIs. |
| `pulse-plugin` | KMP | `PulsePlugin` / `PluginScope` contracts. |
| `pulse-runtime` | KMP | Public `Pulse` API, `PluginManager`, `expect/actual` platform context. |
| `pulse-android` | Android lib | `PulseAndroid` facade + Choreographer `FpsPlugin`. |
| `sample` | Android app | Compose app that initializes PulseKit and tracks events. |

## Requirements

- **JDK:** The build runs on the JDK bundled with Android Studio (JBR 21), pinned via `org.gradle.java.home` in `gradle.properties`. The compile toolchain (JDK 17) is auto-provisioned by Gradle.
- **Android SDK:** `~/Library/Android/sdk` (compileSdk 35, minSdk 24). Path is in `local.properties`.

## Build & test

```bash
./gradlew :sample:assembleDebug     # build the sample APK
./gradlew :pulse-core:jvmTest       # run core unit tests
./gradlew build                     # everything
```

The debug APK lands at `sample/build/outputs/apk/debug/sample-debug.apk`.

## Integration (current API)

```kotlin
// Application.onCreate()
PulseAndroid.initialize(this) {
    enableBattery = true
}

// anywhere
Pulse.track("checkout_started", mapOf("cart_size" to "3"))
```

## Next (Phase 2 / 3)

Per `ARCHITECTURE.md`: DI graph (Koin), SQLDelight storage, network/memory/startup plugins, the Compose Multiplatform dashboard, and the floating overlay.
