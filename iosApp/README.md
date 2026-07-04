# PulseKit iOS sample

A minimal SwiftUI app that calls PulseKit's Kotlin Multiplatform core through an
iOS framework — proving `commonMain` (the `Pulse` API, session manager, and the
`FrameAggregator` logic) runs natively on iOS with **no change to the public API**.

## Run it

```bash
# 1. Build the KMP framework for the simulator (arm64)
./gradlew :pulse-runtime:linkDebugFrameworkIosSimulatorArm64

# 2. Generate the Xcode project (project.yml → .xcodeproj)
brew install xcodegen        # once
cd iosApp && xcodegen generate

# 3. Build + run on a booted simulator (Apple Silicon → arm64)
xcrun simctl boot "iPhone 17" ; open -a Simulator
xcodebuild -project PulseKitSample.xcodeproj -scheme PulseKitSample \
  -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath build ONLY_ACTIVE_ARCH=YES ARCHS=arm64 EXCLUDED_ARCHS=x86_64
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/PulseKitSample.app
xcrun simctl launch booted io.pulsekit.sample.ios
```

Or just open `PulseKitSample.xcodeproj` in Xcode and press Run.

## What it shows
- `PulseIosKt.startPulseOnIos()` → initializes PulseKit, returns the session id.
- `Pulse.shared.track(name:attributes:)` → tracks a custom event.
- `FrameAggregator(...).record(...).publish()` → FPS/jank computed by the shared core.

## Notes
- The framework (`PulseKit`, static) is built by `:pulse-runtime` and exports
  `pulse-core` / `pulse-plugin`, so `Pulse`, `FrameAggregator`, `FpsSnapshot`, and the
  data models are visible from Swift.
- Only the **shared core** runs on iOS today. Native collectors (CADisplayLink,
  URLSession, crash handler) and a Compose-Multiplatform dashboard are the remaining
  iOS work — none of which touches the shared code. See `ARCHITECTURE.md` §7.15.
- `project.yml` is committed; the generated `.xcodeproj` and `build/` are not.
