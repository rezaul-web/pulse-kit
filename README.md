# PulseKit

Cross-platform performance profiling SDK — Android first, KMP ready.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full specification. This README covers the current scaffold.

## Status: Phase 1 bootstrap ✅

A buildable Kotlin Multiplatform skeleton with the core plugin/event architecture and a working Android sample app.

## Modules

| Module | Type | Purpose |
|---|---|---|
| `pulse-core` | KMP (android + jvm + **ios**) | Events, `EventBus`, dispatchers, config, data models. No platform APIs. |
| `pulse-plugin` | KMP (android + jvm + **ios**) | `PulsePlugin` / `PluginScope` contracts. |
| `pulse-runtime` | KMP (android + jvm + **ios**) | Public `Pulse` API, `PluginManager`, `expect/actual` platform context. Builds a `PulseKit` iOS framework. |
| `iosApp` | SwiftUI sample | Calls the shared core on iOS (`Pulse` + `FrameAggregator`). See [`iosApp/README.md`](iosApp/README.md). |
| `pulse-android` | Android lib | `PulseAndroid` facade, Choreographer `FpsPlugin`, notification launcher + Compose dashboard. |
| `pulse-network` | Android lib | `PulseOkHttpInterceptor` — captures HTTP calls for the API Requests inspector. |
| `sample` | Android app | Compose app that initializes PulseKit, tracks events, makes demo API calls, and requests the notification permission. |

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

## Using PulseKit in another project

Publish to your local Maven and depend on it by coordinate:

```bash
./gradlew publishToMavenLocal            # publishes io.pulsekit:*:0.1.0
```
```kotlin
// consumer app (compileSdk 36, AGP ≥ 8.9.1, minSdk 24)
dependencies {
    implementation("io.pulsekit:pulse-android:0.1.0")
    implementation("io.pulsekit:pulse-network:0.1.0") // optional, OkHttp capture
}
```
Full instructions (Maven Local, composite build, prebuilt AAR, integration & requirements):
see [`docs/INTEGRATION.md`](docs/INTEGRATION.md).

## Integration (current API)

```kotlin
// Application.onCreate()
PulseAndroid.initialize(this) {
    enableBattery = true
}

// anywhere
Pulse.track("checkout_started", mapOf("cart_size" to "3"))

// capture HTTP for the API Requests inspector — one line on your OkHttp client
val client = OkHttpClient.Builder()
    .addInterceptor(PulseOkHttpInterceptor())
    .build()
```

## Debug dashboard from a notification

When PulseKit is initialized in a **debug build**, it automatically posts an ongoing
notification. Tapping it (or its **Dashboard** action) opens `PulseDashboardActivity`.
No per-screen wiring is required — integrating the SDK is enough.

- **Own task, same APK.** The dashboard uses `taskAffinity` + `singleTask` + its own
  icon/label, so it appears as a **separate card in Recents** (feels like a separate app)
  while staying in the same process — which is what lets it read the live event bus,
  prefs, and DB. It is *not* a separate installable APK. (This mirrors how
  `powerplay-fieldapp`'s `DebugActivity` behaves.)
- **Gating.** Controlled by `notificationEnabled` (default `true`) and only shown in
  debuggable builds (respects `debugOnly`).
- **Android 13+ permission.** Posting a notification requires the runtime
  `POST_NOTIFICATIONS` permission. `PulseAndroid.initialize()` runs in
  `Application.onCreate` — before the app can hold that permission — so the sample
  requests it in `MainActivity` and then calls:

  ```kotlin
  PulseAndroid.showDashboardNotification(this) // re-post once the permission is granted
  ```

  If the permission is denied, the SDK silently no-ops (the host owns the prompt).

`PulseDashboardActivity` is a Compose screen: an **adaptive grid of property panels**
under a colorful collapsing top bar, each opening a detail view on tap via
**Navigation 3** (`NavKey` back stack). App Info, Device, Session, Events,
**FPS/Jank** (frame-time sparkline), **Memory** (heap sampling), **Startup** (waterfall),
**API Requests** (OkHttp capture), **Crashes** (uncaught + handled, survives relaunch),
**Commit History** (build-time git provenance), and **Recompositions** (a Compose
heatmap via `Modifier.pulseRecomposeHeatmap("tag")`) are all live. Themed for light/dark via the
brand palette (no dynamic color — a dev tool should look identical everywhere), with
the status bar tuned to match the colored bar. In Phase 3 the shared parts migrate
to `pulse-compose-ui` (Compose Multiplatform).

**Full design & "how to add a panel":** see [`docs/DASHBOARD.md`](docs/DASHBOARD.md)
(also `ARCHITECTURE.md` §7.0/§7.14).

## Next (Phase 2 / 3)

Per `ARCHITECTURE.md`: DI graph (Koin), SQLDelight storage, network/memory/startup plugins, the Compose Multiplatform dashboard, and the floating overlay.
