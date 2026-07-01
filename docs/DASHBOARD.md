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
| `ui/DashboardTopBars.kt` | `DashboardTopBar` (collapsing `LargeTopAppBar`) and `DetailTopBar` (`TopAppBar` with a "Copy properties" overflow). |
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
- **Selection** is a single `rememberSaveable` `selectedId: String?`. `null` = grid,
  non-null = that panel's detail. Survives configuration changes.

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
Owns the `Scaffold`. Chooses the top bar and body by selection state:

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = { if (selected == null) DashboardTopBar(scrollBehavior, …) else DetailTopBar(selected, …) },
) { padding -> if (selected == null) PanelGrid(…) else PanelDetail(…) }
BackHandler(enabled = selected != null) { selectedId = null }   // detail → grid
```

### Top bars — `DashboardTopBars.kt`
- **`DashboardTopBar`**: `LargeTopAppBar` — logo (`LogoBadge`), title **PulseKit** +
  subtitle **Profiler dashboard**, actions **Reset counters** (`Refresh`) and
  **Close** (`Close` → `onClose` → `finish()`). Collapses on grid scroll via the
  shared `scrollBehavior` (`scrolledContainerColor` gives the elevation tint).
- **`DetailTopBar`**: `TopAppBar` — back arrow, panel title, and a **⋮ overflow**
  with **Copy properties** (writes `label: value` lines to the clipboard via
  `LocalClipboardManager`). Overflow only shows when the panel has data.

> Icons are from **`material-icons-core`** only (`ArrowBack`, `Refresh`, `Close`,
> `MoreVert`) — we deliberately avoid the multi-MB `material-icons-extended`.

### Grid / tiles / detail
- `PanelGrid`: `LazyVerticalGrid(GridCells.Fixed(2))`, spacing/padding from
  `DashboardDefaults`, `items(panels, key = { it.id })`.
- `PanelTile`: fixed-height `Card`, `Badge` + title + summary, `clickable`. Muted
  colors when `!available`.
- `PanelDetail`: vertically scrollable; property rows in a `Card` separated by
  `HorizontalDivider`; values are **monospaced** for scannability. `EmptyPanel` for
  Phase-3 panels.

### Layout constants
All magic numbers live in `private object DashboardDefaults` (columns, spacings,
tile height, badge/card corners). Tune the look in one place.

---

## 6. Navigation — why no nav library

Two destinations (grid ⇄ detail) don't justify `navigation-compose`. We use a
single `rememberSaveable` selection id + `BackHandler`. This:
- survives rotation/process death (saveable),
- adds no dependency,
- keeps the whole flow readable in one function.

If the dashboard grows to many nested screens, revisit this (likely when it moves
to `pulse-compose-ui`).

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

---

## 8. Dependencies added to `pulse-android`

Compose is added here because this is the dashboard's interim home:

```
compose-compiler plugin + android.buildFeatures.compose = true
platform(compose-bom)
androidx.activity:activity-compose          // setContent, enableEdgeToEdge, BackHandler
androidx.compose.foundation:foundation      // LazyVerticalGrid
androidx.compose.material3:material3         // Scaffold, TopAppBar, Card, …
androidx.compose.material:material-icons-core // ArrowBack/Refresh/Close/MoreVert only
androidx.compose.ui:ui-tooling-preview (+ ui-tooling on debug)
```

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

## 11. Known limitations / TODO

- Placeholder panels (API Requests, Crashes, Commit History) have no data — Phase 3.
- Long device model names truncate on the tile (full value shows in detail).
- Compose `@Preview`s are not yet added for the grid/detail (device-only for now).
- Migrates to `pulse-compose-ui` in Phase 3; keep `DashboardModel` UI-free to ease
  that move (only the composables should need porting).
