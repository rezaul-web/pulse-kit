# PulseKit Dashboard — Design & Implementation

> How the on-device debug dashboard is built, why it's built this way, and how to
> extend it. This is the interim, Android-only implementation living in
> `pulse-android`; in **Phase 3** the shared parts migrate to the
> `pulse-compose-ui` Compose-Multiplatform module (see `ARCHITECTURE.md` §7.14).

---

## 1. What it is

A **grid of property panels** (App Info, Device, Session, Events, FPS/Jank, plus
Phase-3 placeholders for API Requests, Crashes, Commit History). Tapping a tile
opens a **detail screen** listing that panel's properties. It is opened from an
ongoing, debug-only **notification** — no per-screen wiring in the host app.

```
Notification (PulseNotification)  ──tap──▶  PulseDashboardActivity
                                                   │
                                              PulseDashboard()  ← stateful host
                                              ┌────────────┴────────────┐
                                          PanelGrid                 PanelDetail
                                        (LargeTopAppBar)            (TopAppBar)
```

Entry point and gating are documented in the **README → "Debug dashboard from a
notification"** section. The notification itself is
`io.pulsekit.android.PulseNotification`.

---

## 2. File map

All under `pulse-android/src/main/kotlin/io/pulsekit/android/`:

| File | Responsibility |
|---|---|
| `ui/PulseDashboardActivity.kt` | Thin `ComponentActivity`. Applies `PulseTheme`, goes edge-to-edge, hosts `PulseDashboard(onClose = ::finish)`. No UI logic. |
| `ui/PulseDashboard*`… `PulseDashboardScreen.kt` | **Stateful host** `PulseDashboard()` (collects live stats, owns selection + `Scaffold`) and the stateless UI: `PanelGrid`, `PanelTile`, `Badge`, `PanelDetail`, `PropertyRow`, `EmptyPanel`. Layout constants in `DashboardDefaults`. |
| `ui/DashboardTopBars.kt` | `DashboardTopBar` (collapsing `LargeTopAppBar`) and `DetailTopBar` (`TopAppBar` with a "Copy properties" overflow). Both use the colorful `primaryContainer`. |
| `ui/DashboardNavKeys.kt` | **Navigation 3** destination keys: `HomeKey`, `PanelKey`, `ApiListKey`, `ApiDetailKey` — `@Serializable` `NavKey`s. |
| `ui/ApiRequestsScreens.kt` | **API Requests** UI: the request list and the tabbed detail (cURL · Request · Response). |
| `ui/DashboardModel.kt` | Domain model (`PulsePanel`, `PulseProperty`, `LiveStats`), the `LiveStats.reduce()` reducer, `buildPanels()`, and the per-panel data providers. |
| `ui/theme/PulseTheme.kt` | Material 3 brand `ColorScheme` (light + dark). No dynamic color. |
| `res/drawable/ic_pulse_dashboard.xml` | The PulseKit logo (used by the notification and the grid top bar). |
| `res/values{,-night}/…` | The `Theme.PulseKit.Dashboard` window theme + color tokens (the pre-Compose window background). |

Separation rule: **`DashboardModel.kt` has no Compose imports** (pure data + Android
SDK reads), so panel data is testable without a UI. Composables never read the
`PackageManager`/`Build` directly — they consume `PulsePanel`.

---

## 3. Data flow

```
Pulse.events : Flow<PulseEvent>
        │  collect (LaunchedEffect in PulseDashboard)
        ▼
LiveStats.reduce(event)  ──▶  var stats: LiveStats   (eventCount, lastEvent, droppedFrames)
                                        │
                                        ▼
                        buildPanels(context, stats) : List<PulsePanel>
                                        │
                                        ▼
                 PanelGrid / PanelDetail  (recompose when stats change)
```

- **`LiveStats`** is the only mutable UI state derived from the event stream. It's
  updated by a **pure reducer** `LiveStats.reduce(PulseEvent)`, so the collection
  site stays logic-free and the reducer is unit-testable.
- **`buildPanels()`** is called on each recomposition. It folds the current
  `stats` snapshot into the panel list. It's cheap; no memoization needed. Static
  reads (App Info, Device) are recomputed but negligible for a debug screen.
- **Navigation** is a Navigation 3 `NavBackStack` of `NavKey`s (`HomeKey` /
  `PanelKey`); `NavDisplay` renders the top entry. Survives process death (§6).

---

## 4. Domain model (`DashboardModel.kt`)

```kotlin
data class PulseProperty(val label: String, val value: String)

data class PulsePanel(
    val id: String,          // stable key (grid item key + selection id)
    val title: String,       // tile + detail title
    val badge: String,       // 2-letter initials on the tile
    val summary: String,     // one-line value on the tile
    val available: Boolean,  // false → muted tile + Phase-3 empty state
    val properties: List<PulseProperty>,  // detail rows (empty when !available)
)

data class LiveStats(val eventCount: Int, val lastEvent: String, val droppedFrames: Int)
fun LiveStats.reduce(event: PulseEvent): LiveStats
```

### Data providers

| Panel | Source | Notes |
|---|---|---|
| App Info | `PackageManager` / `ApplicationInfo` | package, build type (`FLAG_DEBUGGABLE`), target/min SDK, version (`PackageInfoCompat.getLongVersionCode`), first-install/last-update, installer, process (`Application.getProcessName`, API 28+). |
| Device | `android.os.Build` + `DisplayMetrics` | manufacturer, model, device, Android + API, primary ABI, locale, screen. |
| Session | `Pulse.activeSession()` + `stats` | session id, events observed, last event. |
| Events | `stats` | total observed, most recent event type. |
| FPS / Jank | `stats.droppedFrames` | counts `FrameDropped` events from `FpsPlugin`. |
| API Requests / Crashes / Commit History | — | `available = false`; Phase-3 placeholders. |

SDK-gated reads are guarded (`Build.VERSION.SDK_INT`) and package reads are wrapped
in `runCatching`/`try` so a provider can never crash the dashboard.

---

## 5. UI composition

### Host — `PulseDashboard(onClose)`
Owns the live stats and the **Navigation 3 back stack**; `NavDisplay` renders the
top key (see §6). Each destination is a full screen with its own `Scaffold` + top
bar:
- `DashboardGridScreen` — `Scaffold` with the collapsing `DashboardTopBar` +
  `nestedScroll(scrollBehavior)`, body = `PanelGrid`.
- `DashboardDetailScreen` — `Scaffold` with `DetailTopBar`, body = `PanelDetail`.

### Top bars — `DashboardTopBars.kt`
Both bars are **colorful**: `containerColor = primaryContainer`, content =
`onPrimaryContainer` (light blue in light theme, dark blue in dark theme). The bar
fills behind the status bar, so the status bar area is colored too (see §7 for icon
contrast).
- **`DashboardTopBar`**: `LargeTopAppBar` — logo (`LogoBadge`), title **PulseKit** +
  subtitle **Profiler dashboard**, actions **Reset counters** (`Refresh`) and
  **Close** (`Close` → `onClose` → `finish()`). Collapses on grid scroll via the
  shared `scrollBehavior`.
- **`DetailTopBar`**: `TopAppBar` — back arrow, panel title, and a **⋮ overflow**
  with **Copy properties** (writes `label: value` lines to the clipboard via
  `LocalClipboardManager`). Overflow only shows when the panel has data.

> Icons are from **`material-icons-core`** only (`ArrowBack`, `Refresh`, `Close`,
> `MoreVert`) — we deliberately avoid the multi-MB `material-icons-extended`.

### Grid / tiles / detail
- `PanelGrid`: **adaptive** `LazyVerticalGrid(GridCells.Adaptive(minSize = 168.dp))`
  — columns reflow with available width (2 on a phone, more on tablets/landscape).
  Spacing/padding from `DashboardDefaults`, `items(panels, key = { it.id })`.
- `PanelTile`: fixed-height `Card`, `Badge` + title + summary, `clickable`. Muted
  colors when `!available`.
- `PanelDetail`: vertically scrollable; property rows in a `Card` separated by
  `HorizontalDivider`; values are **monospaced** for scannability. `EmptyPanel` for
  Phase-3 panels.

### Layout constants
All magic numbers live in `private object DashboardDefaults` (columns, spacings,
tile height, badge/card corners). Tune the look in one place.

---

## 6. Navigation — Navigation 3 (`NavKey` back stack)

The dashboard uses **Navigation 3** (`androidx.navigation3`, stable `1.1.4`) — the
key-based navigation model. The back stack is a list of `NavKey`s and
[`NavDisplay`] renders the top one.

```kotlin
// DashboardNavKeys.kt
@Serializable data object HomeKey : NavKey
@Serializable data class PanelKey(val panelId: String) : NavKey

// PulseDashboard()
val backStack = rememberNavBackStack(HomeKey)          // survives process death
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },          // predictive back
    entryProvider = entryProvider<NavKey> {
        entry<HomeKey>  { DashboardGridScreen(onOpen = { backStack.add(PanelKey(it.id)) }, …) }
        entry<PanelKey> { key -> DashboardDetailScreen(panel = panels.first { it.id == key.panelId }, …) }
    },
)
```

Key points / gotchas (from wiring this up):
- `entry` needs **no import** — it's a member of the `entryProvider<NavKey> { }`
  receiver scope. (Importing `androidx.navigation3.runtime.entry` fails to resolve.)
- `NavDisplay(onBack = …)` is `() -> Unit` (not `(Int) -> Unit`).
- `rememberNavBackStack` needs the keys to be `@Serializable NavKey` → the module
  applies the `kotlin.serialization` plugin.
- Navigation 3 requires **compileSdk 36** and **AGP ≥ 8.9.1** (we bumped to AGP
  8.9.3 / compileSdk 36; `targetSdk` stays 35).

Opening a panel = `backStack.add(PanelKey(id))`; going back = `removeLastOrNull()`
(also driven by system/predictive back through `onBack`). Adding a new nested
screen later is just another `NavKey` + `entry<>` — no refactor.

---

## 7. Theming (`ui/theme/PulseTheme.kt`)

- Material 3 `lightColorScheme` / `darkColorScheme` from the **sky-blue-on-slate
  brand palette** (matches `ic_pulse_dashboard`). Values mirror the sample app's
  `PulseKitTheme` so the two look consistent.
- **No Material You dynamic color** — a developer tool should look identical on
  every device and wallpaper. (The sample app *does* use dynamic color; the
  dashboard intentionally does not.)
- Light/dark follows the system via `isSystemInDarkTheme()`. The pre-Compose window
  background comes from `Theme.PulseKit.Dashboard` (`res/values{,-night}`), so there's
  no flash of the wrong color at launch.
- **Status bar contrast.** The colored top bar (`primaryContainer`) extends behind
  the status bar. `PulseTheme` sets, via a `SideEffect` on the window's
  `WindowInsetsController`, `isAppearanceLightStatusBars = !darkTheme` (and the same
  for the nav bar). That's correct because `primaryContainer` is *light* in the light
  theme (→ dark icons) and *dark* in the dark theme (→ light icons). This is what
  fixed the previously-unreadable status bar.

---

## 8. Dependencies added to `pulse-android`

Compose is added here because this is the dashboard's interim home:

```
compose-compiler plugin + kotlin-serialization plugin
android.buildFeatures.compose = true
platform(compose-bom)
androidx.activity:activity-compose            // setContent, enableEdgeToEdge
androidx.compose.foundation:foundation        // LazyVerticalGrid
androidx.compose.material3:material3           // Scaffold, TopAppBar, Card, …
androidx.compose.material:material-icons-core  // ArrowBack/Refresh/Close/MoreVert only
androidx.compose.ui:ui-tooling-preview (+ ui-tooling on debug)
androidx.navigation3:navigation3-runtime       // NavKey, rememberNavBackStack, entryProvider
androidx.navigation3:navigation3-ui            // NavDisplay
org.jetbrains.kotlinx:kotlinx-serialization-json  // @Serializable NavKeys
```

Build requirements bumped for Navigation 3: **AGP 8.9.3**, **compileSdk 36**
(both set in `gradle/libs.versions.toml`; Gradle stays 8.11.1).

---

## 9. How to add a new panel

**A. Placeholder panel (no data yet):** add an entry to `buildPanels()`:

```kotlin
PulsePanel(
    id = "battery", title = "Battery", badge = "Bt",
    summary = "Phase 3", available = false, properties = emptyList(),
)
```

That's it — it renders as a muted tile with the Phase-3 empty state.

**B. Real panel with data:**
1. Add a private provider in `DashboardModel.kt` returning `List<PulseProperty>`:
   ```kotlin
   private fun batteryProperties(context: Context): List<PulseProperty> { … }
   ```
2. Add the `PulsePanel` to `buildPanels()` with `available = true`, a `summary`
   (short live value), and `properties = batteryProperties(context)`.
3. If it reflects live events, extend `LiveStats` + `LiveStats.reduce()` and read
   the new field in the provider (thread the `stats` through as the existing panels do).
4. Guard any SDK-version-specific or throwing reads (`Build.VERSION.SDK_INT`,
   `runCatching`) so the provider can't crash the dashboard.

No UI code changes are required — the grid and detail are fully data-driven.

---

## 10. Verifying on a device/emulator

```bash
./gradlew :sample:assembleDebug
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
adb shell pm grant io.pulsekit.sample android.permission.POST_NOTIFICATIONS
adb shell monkey -p io.pulsekit.sample -c android.intent.category.LAUNCHER 1
# open the notification shade and tap "PulseKit" to launch the dashboard

adb shell cmd uimode night yes   # test dark mode
adb shell cmd uimode night no    # back to light
```

The dashboard opens in its own task (`taskAffinity="io.pulsekit.dashboard"`), so it
shows as a separate Recents card — see `ARCHITECTURE.md` §7.14.

---

## 11. API Requests inspector (network capture)

The **API Requests** panel is a full feature (not a placeholder): a list of captured
HTTP calls → a tabbed detail (**cURL · Request · Response**).

### Capture pipeline
```
OkHttp call
   │  PulseOkHttpInterceptor  (module :pulse-network)
   │    • reads request body from a COPY (okio.Buffer)
   │    • peeks the response body (never consumes the real stream)
   │    • redacts Authorization/Cookie/… headers
   ▼
Pulse.recordNetwork(NetworkTransaction)          (pulse-runtime)
   ▼
InMemoryNetworkRecorder  (pulse-core) — newest-first, capped ring buffer, atomic update
   ▼
Pulse.network : StateFlow<List<NetworkTransaction>>
   ▼
Dashboard: collectAsState → API Requests tile count → list → detail
```

### Model — `NetworkTransaction` (pulse-core, `NetworkTransaction.kt`)
Immutable, `@Serializable`: method, url (`path`/`host` helpers), request & response
headers/body/content-type/sizes, `statusCode`, `protocol`, `durationMs`, `startedAtMs`,
and `error` (non-null when the call failed before a response). `toCurl()` renders the
request as a runnable `curl`. Bodies are captured up to a cap (256 KB) and may be
`<stream>`/truncated; sensitive headers show as `██`.

### UI — `ApiRequestsScreens.kt`
- `ApiRequestsListScreen`: `LazyColumn` of rows, each a colored **status pill**
  (2xx green, 3xx amber, 4xx/5xx & failures red), method, path, host, duration.
  Empty state prompts to add the interceptor.
- `ApiTransactionScreen`: a `TabRow` with **cURL / Request / Response**. Bodies are
  monospaced and JSON is pretty-printed (`kotlinx.serialization`). The **Response tab
  leads with the response data (body)**, then status and headers. Tab content is
  wrapped in a `SelectionContainer`, so the user can select and copy any text (a plain
  tap does nothing — no accidental copies); the top bar has a Share action that copies
  the whole cURL command.
- Navigation: the API tile pushes `ApiListKey`; a row pushes `ApiDetailKey(id)`.
  Both look the transaction up from the collected `Pulse.network` list by `id`.

### Integrating in a host app
One line — add the interceptor to your client; everything else is automatic:
```kotlin
OkHttpClient.Builder().addInterceptor(PulseOkHttpInterceptor()).build()
```
`PulseOkHttpInterceptor(maxBodyBytes = …, redactHeaders = …)` is configurable. The
sample's `SampleApi` hits public endpoints — `httpbin.org` (GET, and a POST that
carries an `Authorization` header to demonstrate redaction) and
`jsonplaceholder.typicode.com` (GET/POST), plus a 404.

> Storage is in-memory for now (cleared on `Pulse.shutdown()`); the SQLDelight-backed
> store + Ktor-client capture arrive in Phase 3 (`ARCHITECTURE.md` §7.4).

## 12. Crashes & Commit History panels

Both are full features, wired like Network (model in `pulse-core`, capture/read in
`pulse-android`, screens + NavKeys in the dashboard).

### Crashes (`PulseCrashReporter`, `CrashesScreens.kt`)
- `PulseCrashReporter.install()` sets a **chaining** `Thread.UncaughtExceptionHandler`:
  it records the fatal crash, then delegates to the previously-installed handler (never
  swallows Crashlytics/Play).
- A fatal crash kills the process, so each report is **persisted to a file**
  (`filesDir/pulsekit_crashes.json`) synchronously and **re-loaded on next launch** →
  the crash is visible after relaunch (verified: FATAL survives a forced crash).
- Non-fatal/handled exceptions: `PulseAndroid.recordException(throwable)`.
- `Pulse.crashes: StateFlow<List<CrashReport>>`. UI: list with FATAL (red) / LOGGED
  (amber) pills → detail with the full, selectable stack trace.

### Commit History (`PulseProvenance`, `CommitHistoryScreen.kt`)
- **Build time**: the sample's `build.gradle.kts` has a `generatePulseProvenance` task
  that runs `git` (branch, HEAD, dirty, last 20 commits via `ProcessBuilder`) and writes
  `assets/pulsekit_provenance.json`. It's wired before `merge*Assets` and degrades to an
  empty record if git is unavailable — never fails the build.
- **Runtime**: `PulseProvenance.load()` reads that asset into a `BuildProvenance`, exposed
  as `Pulse.provenance`. UI: a header (branch + DIRTY chip + HEAD + build time) over the
  commit list (sha · subject · author · time).
- This is the interim home for the provenance-generation logic; §7.16 moves it into a
  reusable Gradle plugin so any app gets it without copying the task.

### Wiring
- `PulseAndroid.initialize()` installs the crash handler (`enableCrash`) and loads
  provenance (`enableCommitHistory`).
- NavKeys: `CrashListKey` / `CrashDetailKey` / `CommitHistoryKey`; the grid routes the
  `crashes` and `commits` tiles to them via `destinationFor()`.

## 13. Performance metrics (FPS · Memory · Startup)

Three dedicated metric screens with charts, backed by lightweight collectors.

### FPS / Jank (`FpsPlugin` → `FrameAggregator`)
- `FpsPlugin` posts a Choreographer callback; each frame calls `Pulse.recordFrame(ms)`.
- **`FrameAggregator` uses a primitive `DoubleArray` ring buffer**, so `record()` is
  **allocation-free on the main thread** (the architecture's hard perf rule). A throttled
  loop (~2×/sec) calls `publish()` which recomputes an `FpsSnapshot` (fps, avg, worst
  frame, jank %, dropped, recent frame durations) into `Pulse.fps`.
- Screen: big current-fps number + a frame-time sparkline (with the 16.67 ms budget as a
  dashed threshold line) + detail stats.

### Memory (`MemoryPlugin`)
- Samples `Runtime` (used/max heap) + `Debug.getNativeHeapAllocatedSize()` every
  `samplingIntervalMs` in the plugin's supervised scope (off the frame path), recording
  `MemorySample`s into a rolling window (`Pulse.memory`).
- Screen: current used + a used-heap sparkline (shows the GC sawtooth) + used/native/max/peak.

### Startup (`PulseStartup`)
- Captures process start (`Process.getStartUptimeMillis`), `Application.onCreate`, first
  activity resume, and first frame (a `post` on the first resumed activity's decor view),
  into a `StartupMetric` (`Pulse.startup`), captured once per process.
- Screen: total time-to-first-frame + a phase waterfall (proportional bars).

### Charts
Charts are native Compose `Canvas` micro-viz in the existing Material 3 palette
(single primary-accent line, dashed error-color threshold) — see `Sparkline` in
`MetricsScreens.kt`. NavKeys: `FpsKey` / `MemoryKey` / `StartupKey`; gated by
`enableFPS` / `enableMemory` / `enableStartup`.

## 14. Compose recomposition heatmap (`Recompositions` panel)

An opt-in overlay + panel for spotting recomposition hotspots.

- **`Modifier.pulseRecomposeHeatmap(tag)`** (`RecomposeHeatmap.kt`) — a
  `ModifierNodeElement` that forces `update()` to run on every recomposition (the
  recompose-highlighter trick: `equals()` returns `false`). Each call increments a heat
  counter, draws a border that lerps **blue → red** with the count, and cools back down
  after ~1.5 s. It also reports the recomposition to `Pulse.recordRecomposition(tag)`.
- **Store:** `RecompositionRecorder` (pulse-core) keeps per-tag counts, exposed as
  `Pulse.recompositions`. The **Recompositions** panel lists tags hottest-first with a
  heat swatch + `N×` count.
- **Runtime limitation:** counts exist only where the modifier is applied — there's no
  runtime hook for *every* composable (that needs Compose compiler metrics). So it's
  opt-in per composable.
- **Demo:** the sample's "Recomposition heatmap demo" screen shows a thrashing ticker
  (hot), a stable label (never recomposes → no border, absent from the panel), and a
  button that recomposes with its parent scope — a real "unstable lambda" smell the
  heatmap exposes.

## 15. Known limitations / TODO

- Long device model names truncate on the tile (full value shows in detail).
- Compose `@Preview`s are not yet added for the grid/detail (device-only for now).
- Migrates to `pulse-compose-ui` in Phase 3; keep `DashboardModel` UI-free to ease
  that move (only the composables should need porting).
