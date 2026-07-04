# PulseKit — Master Architecture & Build Specification

> **Cross-Platform Performance Profiling SDK — Android First, KMP Ready**
>
> **Status:** Pre-development blueprint · **Spec version:** 1.1 · **Target SDK version:** `0.1.0` → `1.0.0`
> **Coordinates:** `io.pulsekit:<module>:<version>` (e.g. `io.pulsekit:pulse-android:0.1.0`)

---

## 0. How to Use This Document (Read First)

This is the **single source of truth** for building PulseKit. It is written to be handed to an AI coding agent (Claude Code) or a human engineer and executed phase-by-phase without further clarification.

**Reading order for an implementer:**
1. §1–§3 — Understand the *why* and the non-negotiable constraints.
2. §4 — Internalize the module dependency graph. **Never** violate it.
3. §5 — The public API contract. This is frozen from `0.1.0` and must not break.
4. §6 — The plugin & event contracts. Every feature is built against these.
5. §7 — Per-module specifications with acceptance criteria.
6. §8 — Execution plan (phases, milestones, definition-of-done).
7. §9–§12 — Tooling: Gradle, CI/CD, testing, publishing.

**Golden rules (enforced at every milestone):**
- `commonMain` contains **zero** platform/Android imports. Enforced by CI (see §11.4).
- The core never knows about a concrete feature. Features register as plugins.
- Everything crosses module boundaries as **Flow of immutable events** or **interfaces**, never concrete classes.
- Every public symbol has KDoc. Every feature has unit tests. No exceptions merge to `main`.
- Debug-only by default. In release builds the SDK is a **no-op shell** unless explicitly forced.

---

## 1. Vision & Scope

PulseKit is an **open-source developer toolkit** for monitoring application performance during **development and testing** — not an end-user analytics product.

**In scope, two complementary pillars:**
1. **Performance profiling** — real-time, on-device insight into FPS/jank, memory, network, startup, ANR/crash, battery, and Compose recomposition behavior.
2. **Debug inspection** — on-device panels that any app needs regardless of its domain: **App Info** (version/build/device metadata), **API Requests** (network inspector), **Crashes** (uncaught-exception reports), **Commit History** (git provenance of the running build), plus storage inspectors (SharedPreferences/DB) and screen tracking.

Both pillars are surfaced through a single Compose Multiplatform dashboard and a floating debug overlay.

**Generic vs app-specific (design line):** PulseKit ships only **app-agnostic** inspectors — features whose implementation is identical across every app. Domain panels that depend on the host's business logic (analytics semantics, experiments/feature-flags, roles & permissions, design-system component galleries) are **explicitly out of scope for the SDK**; apps build those against the `PulsePlugin` contract (§6). See §7.0 for the panel-by-panel classification.

**Explicitly out of scope:** Remote telemetry, cloud dashboards, user identity, ad/attribution tracking, any network egress of collected data. All data is local; export is user-initiated only.

**Design posture:** Android is production-quality on day one; the KMP core is architected so iOS/Desktop/JVM/Web can be added *without touching the public API or the core*.

---

## 2. Primary Goals & Success Criteria

| Goal | Concrete, testable criterion |
|---|---|
| Minimal setup | A working integration in ≤ 3 lines; zero required config. |
| Low overhead | Added memory < 10 MB; CPU < 2% steady-state; startup cost < 30 ms; no measurable jank from the SDK itself. |
| Modular | Each module publishes independently; removing any feature module still compiles the app. |
| Extensible | A third party can add a feature via `PulsePlugin` + events without forking. |
| Great DX | One-call init, live dashboard, movable overlay, one-tap export. |
| Safe by default | Disabled in release; no data leaves device; export is explicit. |

These criteria are the **Definition of Done** for `v1.0` and are asserted by the benchmark/leak suites (§11.5).

---

## 3. Non-Functional Requirements (Hard Constraints)

### 3.1 Performance budget
- **Memory:** < 10 MB resident attributable to PulseKit.
- **CPU:** < 2% average on a mid-tier device during normal use.
- **Startup:** < 30 ms added to cold start (measured, gated in benchmark CI).
- **Frame impact:** SDK work never runs on the frame-critical path; sampling is throttled/batched.

### 3.2 Threading model
| Concern | Dispatcher |
|---|---|
| UI (dashboard, overlay) | `Main` |
| Storage (SQLDelight I/O) | `IO` |
| Analytics / aggregation | `Default` |
| Event collection (Choreographer, interceptors) | Capture on caller thread, **hand off immediately** to a channel; never block. |

**The main thread must never be blocked by PulseKit.** All aggregation, serialization, and persistence are off-main. A central `PulseDispatchers` abstraction (injectable for tests) is the only source of dispatchers.

### 3.3 Security & privacy
- No data collection unless a session is active and a plugin is enabled.
- No network egress. Ever. (CI check: no `INTERNET`-using code in SDK modules beyond the *user's own* OkHttp interceptor, which only observes.)
- Export requires an explicit API call or UI action.
- Sensitive network data (headers/bodies) is **redactable** via a configurable redaction policy (default: redact `Authorization`, `Cookie`, `Set-Cookie`).

### 3.4 Reliability
- The SDK must **never crash the host app.** Every plugin boundary is wrapped in a supervised scope; a failing plugin is disabled and logged, not propagated.
- Release builds default to a no-op implementation with near-zero footprint.

---

## 4. Architecture

### 4.1 High-level flow

```
                        +------------------+
                        |    Sample App    |
                        +------------------+
                                 |
                         Pulse.initialize()
                                 |
                    +---------------------------+
                    |     pulse-runtime         |  (public API, DI graph, lifecycle)
                    +---------------------------+
                                 |
                    +---------------------------+
                    |     pulse-core (KMP)      |  (config, session, events, plugin mgr)
                    +---------------------------+
                     /      |       |        \
                 network  memory  startup  analytics ...  (feature plugins)
                     \       \      |       /
                      \       \     |      /
                       +-------------------------+
                       |      pulse-android      |  (Android runtime wiring)
                       +-------------------------+
                                    |
        ---------------------------------------------------------
        |         |         |          |            |            |
       UI        FPS       ANR      Battery       Logs        Storage
```

### 4.2 Module dependency graph (STRICT — direction of arrows = "depends on")

```
pulse-runtime ──▶ pulse-core ◀── pulse-plugin
      │                ▲              ▲
      │                │              │
      ▼                │              │
 feature modules ──────┘              │
 (network, memory, startup,          │
  fps, anr, logs, battery) ──────────┘
      │
      ▼
 pulse-storage ──▶ pulse-core
 pulse-export  ──▶ pulse-core, pulse-storage
 pulse-compose-ui ──▶ pulse-core (+ reads via interfaces only)
 pulse-android ──▶ runtime + android feature actuals
 pulse-ios / pulse-desktop ──▶ runtime + platform actuals (future)
```

**Rules:**
- `pulse-core` and `pulse-plugin` depend on **nothing** internal except kotlinx (coroutines/serialization).
- Feature modules depend on `pulse-core` + `pulse-plugin` only — **never on each other**.
- `pulse-compose-ui` reads data exclusively through read-only interfaces exposed by core/storage — it never reaches into a feature module.
- Only platform modules (`pulse-android`, etc.) may reference platform SDKs.

CI enforces this with a dependency-rule check (§11.4).

### 4.3 Clean Architecture layering (within each module)
- **Domain:** entities, value objects, plugin/event interfaces. Pure Kotlin.
- **Data:** SQLDelight, serializers, repositories (implement domain interfaces).
- **Presentation:** Compose UI, view-state, overlay (only in UI/platform modules).

Dependencies point inward. Domain knows nothing of data/presentation.

---

## 5. Public API Contract (FROZEN from 0.1.0)

This surface is **stable and SemVer-protected.** Additive changes only within a major version.

```kotlin
// --- Entry point (in pulse-runtime) ---
object Pulse {

    /** Initialize PulseKit. Safe to call once; subsequent calls are no-ops (logged). */
    fun initialize(context: PlatformContext, config: PulseConfig.() -> Unit = {})

    /** Start a new profiling session. Returns the active session id. */
    fun startSession(): SessionId

    /** End the current session (flushes to storage). */
    fun endSession()

    /** Emit a custom, user-defined event into the active session. */
    fun track(event: PulseEvent)

    /** Register a custom feature plugin at runtime. */
    fun registerPlugin(plugin: PulsePlugin)

    /** Export the given (or current) session in the requested format. */
    suspend fun export(format: ExportFormat, session: SessionId? = null): ExportResult

    /** Read-only stream access for embedding UI or custom tooling. */
    val events: Flow<PulseEvent>
    val sessions: Flow<List<SessionSummary>>

    /** Tear down all plugins and release resources. */
    fun shutdown()
}
```

### 5.1 Configuration DSL

```kotlin
class PulseConfig {
    var debugOnly: Boolean = true          // no-op in release unless false
    var enableFPS: Boolean = true
    var enableMemory: Boolean = true
    var enableCompose: Boolean = true
    var enableNetwork: Boolean = true        // API Requests inspector
    var enableStartup: Boolean = true
    var enableBattery: Boolean = false
    var enableAnr: Boolean = true
    var enableLogs: Boolean = true

    // --- Generic debug-inspection panels (app-agnostic) ---
    var enableAppInfo: Boolean = true        // App Info panel
    var enableCrash: Boolean = true          // Crashes panel (uncaught-exception capture)
    var enableCommitHistory: Boolean = true  // Commit History panel (build provenance; see §6.5)

    var overlayEnabled: Boolean = true      // Android floating bubble
    var maxSessionsRetained: Int = 20
    var samplingIntervalMs: Long = 1_000
    var redaction: RedactionPolicy = RedactionPolicy.Default
}
```

### 5.2 Example integration (the "3-line" promise)

```kotlin
// Android Application.onCreate()
Pulse.initialize(this) {
    enableBattery = true
}
```

`PlatformContext` is an `expect` type: `actual typealias PlatformContext = android.content.Context` on Android; a lightweight holder on other platforms.

---

## 6. Core Contracts

### 6.1 Plugin interface (`pulse-plugin`)

```kotlin
interface PulsePlugin {
    val name: String
    val version: String get() = "0.1.0"

    /** Called once when the plugin is registered and enabled. Must not block. */
    fun initialize(scope: PluginScope)

    /** Called on shutdown or when the plugin is disabled. Must release all resources. */
    fun shutdown()
}

/** Everything a plugin is allowed to touch — no direct core access. */
interface PluginScope {
    val dispatchers: PulseDispatchers
    val eventSink: EventSink            // emit events
    val config: ReadOnlyConfig
    val logger: PulseLogger
    fun launch(block: suspend CoroutineScope.() -> Unit)  // supervised, auto-cancelled on shutdown
}
```

### 6.2 Event model (`pulse-core`)

All cross-cutting communication is an immutable, serializable `PulseEvent`.

```kotlin
@Serializable
sealed interface PulseEvent {
    val timestampMs: Long
    val sessionId: SessionId
}

// Representative built-in events (each feature adds its own subtypes):
@Serializable data class ScreenOpened(...) : PulseEvent
@Serializable data class ApiCallStarted(...) : PulseEvent          // API Requests
@Serializable data class ApiCallCompleted(...) : PulseEvent        // API Requests
@Serializable data class FrameDropped(...) : PulseEvent
@Serializable data class MemoryUpdated(...) : PulseEvent
@Serializable data class AppInfoCaptured(val info: AppInfo, val device: DeviceInfo) : PulseEvent   // App Info
@Serializable data class CrashCaptured(val report: CrashReport) : PulseEvent                       // Crashes (fatal)
@Serializable data class ExceptionOccurred(val throwable: ThrowableInfo, val fatal: Boolean) : PulseEvent  // non-fatal by default
@Serializable data class CustomEvent(val name: String, val attributes: Map<String, String>) : PulseEvent
```

- Events flow through a single hot `SharedFlow` owned by the session manager.
- Backpressure strategy: buffered channel with `DROP_OLDEST` on overflow (profiling data is lossy-tolerant; never block the producer).
- **Fatal crashes bypass `DROP_OLDEST`.** A `CrashCaptured` is persisted synchronously before the default handler re-throws, so the last event of a dying process is never dropped (see §7.18).

### 6.3 Session model

```kotlin
@Serializable
data class Session(
    val id: SessionId,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val device: DeviceInfo,
    val appInfo: AppInfo,
    // Aggregated rollups are stored; raw events stream to storage as they arrive.
)
```

Each app launch = one session. Every session is fully exportable (§10).

### 6.4 Lifecycle & registration

`pulse-runtime` owns:
- The Koin dependency graph.
- The `PluginManager`: registers enabled plugins (from config flags + `registerPlugin`), initializes them under a supervised scope, and coordinates `shutdown()`.
- Lifecycle binding (Android `ProcessLifecycleOwner`; expect/actual elsewhere) to auto start/stop sessions.

### 6.5 Build-time provenance (not an event)

Some inspector data is **static per build** and cannot be produced by the running process — most importantly **Commit History**, since the app cannot run `git`. This data is captured at **build time** by the Gradle plugin (§7.16) and embedded as a generated, read-only resource that the SDK loads at startup.

```kotlin
@Serializable
data class BuildProvenance(
    val branch: String,                 // e.g. "feature/pulsekit-phase2"
    val commit: String,                 // HEAD sha (short + full)
    val commitTimeMs: Long,
    val dirty: Boolean,                 // uncommitted changes at build time
    val buildTimeMs: Long,
    val ciBuildNumber: String?,         // if built on CI
    val history: List<CommitRecord>,    // feature-branch log (bounded, see below)
)

@Serializable
data class CommitRecord(
    val sha: String, val author: String, val timeMs: Long, val subject: String,
)
```

- **Commit History** shows `history`: the commits on the current branch since it diverged from the base branch (`main`), computed at build time as `git log <base>..HEAD`. Bounded (default cap: 100 commits) to keep the artifact small.
- Provenance is exposed as a static query (not the event `Flow`); it feeds the App Info and Commit History panels and is included in every export so a bug report always says *exactly which code* produced it.
- If git is unavailable at build time (e.g. a source tarball), the plugin emits an empty-but-valid provenance record and logs a warning — never fails the build.

---

## 7. Module Specifications

### 7.0 Debug inspector panels: generic (in-SDK) vs app-specific (out of scope)

PulseKit's inspector surface is modeled on common in-app debug menus. The dividing line is **domain-independence**: a panel ships in the SDK only if its implementation is identical for every app.

| Panel | Classification | Where it lives |
|---|---|---|
| **App Info** | Generic | `pulse-appinfo` (§7.17) |
| **API Requests** | Generic ✅ *implemented* | `pulse-network` (§7.4) |
| **Crashes** | Generic ✅ *implemented* | `pulse-crash` (§7.18) |
| **Commit History** | Generic (build-time) ✅ *implemented* | `pulse-provenance` + gradle-plugin (§7.19, §7.16) |
| **Shared Preferences** | Generic | `pulse-storage-inspect` (§7.20) |
| **Database** | Generic (viewer) | `pulse-storage-inspect` (§7.20) |
| **Screens** | Generic | `ScreenOpened` events via `pulse-runtime` |
| **Clear Data** | Generic (utility) | `pulse-android` overlay action |
| **Analytics** | App-specific | Host app, via `PulsePlugin` |
| **Experiments** / feature flags | App-specific | Host app, via `PulsePlugin` |
| **Roles & Permissions** | App-specific | Host app, via `PulsePlugin` |
| **Components** (design system) | App-specific | Host app, via `PulsePlugin` |

App-specific panels are **first-class citizens of the plugin API** — the SDK gives every app the extension points (§6.1) and dashboard slots to build them, it just doesn't ship their business logic.

Each spec lists **Purpose**, **Key APIs**, **Platform**, **Dependencies**, and **Acceptance criteria (Done when…)**.

### 7.1 `pulse-core` (KMP · commonMain)
- **Purpose:** Config, session manager, event bus, plugin manager contract, dispatchers, `DeviceInfo`/`AppInfo` expect types.
- **Platform:** common (+ expect/actual for device info).
- **Depends on:** kotlinx-coroutines, kotlinx-serialization, Napier.
- **Done when:** No Android imports; event bus + session lifecycle unit-tested with Turbine; `PulseDispatchers` injectable.

### 7.2 `pulse-plugin` (KMP)
- **Purpose:** `PulsePlugin`, `PluginScope`, `EventSink`, `ReadOnlyConfig` interfaces.
- **Done when:** Pure interfaces, fully documented, zero implementation logic.

### 7.3 `pulse-runtime` (KMP + platform actuals)
- **Purpose:** Public `Pulse` object, DI graph (Koin), `PluginManager`, lifecycle coordination.
- **Done when:** `Pulse.initialize` is idempotent; failing plugin never crashes host (test: register a throwing plugin, assert app survives).

### 7.4 `pulse-network` (Android/OkHttp now; Ktor/KMP in Phase 3) — *powers the **API Requests** panel* ✅
- **Purpose:** Capture request/response line, headers, body, duration, status; grouping & filtering; redaction.
- **Key API:** `PulseOkHttpInterceptor(maxBodyBytes, redactHeaders)` (Android). `PulseKtorPlugin` (KMP) is Phase 3.
- **Status (implemented):** the interceptor records each round-trip as an immutable `NetworkTransaction` (in `pulse-core`) via `Pulse.recordNetwork(...)`, held by an in-memory `NetworkRecorder` and exposed as `Pulse.network: StateFlow<List<NetworkTransaction>>`. It reads the request body from a copy and *peeks* the response (never consuming it); default headers `Authorization`/`Cookie`/`Set-Cookie`/`Proxy-Authorization` are redacted; bodies are capped (256 KB) and truncated with a marker. The dashboard renders a request list → tabbed detail (**cURL · Request · Response**) with JSON pretty-printing. See `docs/DASHBOARD.md` §11.
- **Phase 3:** SQLDelight-backed persistence, Ktor-client capture, and `ApiCallStarted`/`ApiCallCompleted` event emission.

### 7.5 `pulse-memory` (Android now; KMP later) ✅ *implemented*
- **Purpose:** Heap used/max, native heap, allocation spikes, (future) large-bitmap detection & pressure warnings.
- **Status:** `MemoryPlugin` samples JVM + native heap on `samplingIntervalMs` off the frame path, recording `MemorySample`s into a rolling window (`Pulse.memory`). Panel shows current/native/max/peak + a used-heap sparkline. GC counts / bitmap hooks are follow-ups.

### 7.6 `pulse-startup` (Android) ✅ *implemented*
- **Purpose:** Waterfall of process start → `Application.onCreate` → first activity → first frame.
- **Status:** `PulseStartup` captures the timeline (process start via `Process.getStartUptimeMillis`, `onCreate`, first-activity resume, first frame via a decor-view `post`) into `StartupMetric` (`Pulse.startup`). Panel renders total time-to-first-frame + a phase waterfall. `markInteractive()` (TTI) and the `Initializer` are follow-ups.

### 7.7 `pulse-fps` (Android) ✅ *implemented*
- **Purpose:** `Choreographer`-driven FPS: current/avg, worst frame, dropped frames, jank %.
- **Status:** `FpsPlugin` feeds each frame into `FrameAggregator` — a **primitive `DoubleArray` ring buffer** so `record()` is allocation-free on the main thread — and publishes a throttled `FpsSnapshot` (~2×/sec) to `Pulse.fps`. Emits `FrameDropped` for over-budget frames. Panel shows the stats + a frame-time sparkline with the 16.67 ms budget line.

### 7.8 `pulse-anr` (Android)
- **Purpose:** Watchdog thread pings main; if unresponsive beyond threshold, capture main-thread stack + context.
- **Done when:** Detects a synthetic 6s block in tests; captures stacktrace + device/memory snapshot; never false-positives on backgrounded app.

### 7.9 `pulse-logs` (KMP)
- **Purpose:** Structured, filterable in-app log stream (bridged from Napier); search & level filters for the dashboard.
- **Done when:** Logs are captured into the session and queryable by level/tag/text.

### 7.10 `pulse-battery` (Android · off by default)
- **Purpose:** Wake locks, network-activity frequency, polling patterns, estimated CPU; warn on inefficiency.
- **Done when:** Emits advisory warnings; strictly opt-in.

### 7.11 `pulse-compose-ui` (Compose Multiplatform)
- **Purpose:** The dashboard. Sections: Overview, Performance, Network (API Requests), Memory, Startup, Logs, **App Info**, **Crashes**, **Commit History**, Settings. Dark mode, search, timeline filters. App-specific plugins (§7.0) can contribute their own sections through a dashboard-slot API.
- **Consumes:** read-only interfaces + `Flow`s only.
- **Done when:** Renders live data from a fake repository in previews; no dependency on any feature module.

### 7.12 `pulse-storage` (KMP · SQLDelight)
- **Purpose:** Persist sessions + events. Entities: `Session`, `AppSnapshot`, `NetworkLog`, `FrameEvent`, `MemorySnapshot`, `CrashReport`, `StartupMetric`, `TimelineEvent`, `BuildProvenance`/`CommitRecord`.
- **Done when:** Schema migrations defined; writes on IO dispatcher; retention prunes to `maxSessionsRetained`; queries exposed as `Flow`.

### 7.13 `pulse-export` (KMP)
- **Purpose:** Export sessions as JSON, CSV, ZIP (bundle), HTML report. (Markdown/PDF: future.)
- **Key API:** `sealed interface ExportFormat { JSON, CSV, ZIP, HTML }`, `ExportResult(path/bytes)`.
- **Done when:** Round-trips a session to JSON and back; ZIP bundles logs+network+metrics.

### 7.14 `pulse-android` (Android)
- **Purpose:** Wire Android actuals, the floating overlay (movable/expandable bubble), Android DI, and the **dashboard entry points**.
- **Entry points (two, both zero-integration):** the app gets these for free from `PulseAndroid.initialize()`, no per-screen wiring.
  1. **Notification launcher** — an *ongoing, debug-only* notification on its own low-importance channel (`pulsekit.dashboard`); tapping it (or its "Dashboard" action) fires a `PendingIntent` into `PulseDashboardActivity`. Gated by `PulseConfig.notificationEnabled` + debug-build detection (`ApplicationInfo.FLAG_DEBUGGABLE`, respecting `debugOnly`). On Android 13+ it no-ops if `POST_NOTIFICATIONS` is not granted (the host owns that prompt); `PulseAndroid.showDashboardNotification(context)` re-posts it after the grant. Mirrors the persistent dev-tools notification pattern.
     - **Own task, not a separate APK.** `PulseDashboardActivity` uses `taskAffinity="io.pulsekit.dashboard"` + `launchMode="singleTask"` + its own icon/label, so it opens as a **separate Recents card** and *feels* like a distinct app — while remaining in the **same APK/process/UID**, which is what lets it read the live event bus, prefs, and DB. A genuinely separate installable APK would be sandboxed under a different UID and could only reach that data over IPC (exported debug-only `ContentProvider`/bound `Service`); out of scope unless a single inspector app must attach to many host apps.
  2. **Floating overlay** — the movable/expandable bubble (below).
- **Done when:** Integrating the SDK auto-posts the dashboard notification in a debug build, and tapping it opens the dashboard; overlay draggable + expandable in the sample app; requests overlay permission gracefully. *(Notification launcher implemented in Phase 1. `PulseDashboardActivity` is now a Compose grid of property panels — App Info, Device, Session, Events, FPS live; API Requests, Crashes, Commit History as Phase-3 placeholders — each opening a detail screen on tap, themed light/dark with the brand palette. This lives in `pulse-android` for now; in Phase 3 the shared parts migrate to `pulse-compose-ui`. Full design & extension guide: `docs/DASHBOARD.md`.)*

### 7.15 Future platform modules
- `pulse-ios`, `pulse-desktop` — actuals only; **must not** require changing core or public API.
- **iOS core targets — done ✅.** `pulse-core`, `pulse-plugin`, and `pulse-runtime` now
  declare `iosArm64` / `iosSimulatorArm64` / `iosX64` and **compile for iOS** (Kotlin/Native)
  with iOS `actual`s: `ioDispatcher = Dispatchers.Default`, `PlatformContext` placeholder,
  `nowMs()` via `kotlin.system.getTimeMillis()`. This validates that `commonMain` (event bus,
  session, config, and all data models — network/crash/provenance/fps/memory/startup/recomposition)
  is genuinely portable **without any change to the public `Pulse` API**. Still Android-only:
  the collectors (FPS/memory/network/crash/startup) and the Compose dashboard — those are the
  remaining iOS work (CADisplayLink, URLSession, `NSSetUncaughtExceptionHandler`, Compose
  Multiplatform UI), none of which touch the shared core.

### 7.16 Support modules
- `sample/` — reference Android app exercising every feature.
- `benchmark/` — macrobenchmark + microbenchmark for startup/FPS/memory budgets.
- `gradle-plugin/` — convenience plugin that (a) auto-applies the OkHttp interceptor / build config, and (b) **captures build provenance** (§6.5): at build time it runs `git log <base>..HEAD`, branch, HEAD sha, dirty flag, and build/CI metadata, then generates a `BuildProvenance` resource embedded in the app. Configurable base branch (default `main`) and history cap (default 100). Degrades to an empty record if git is absent; never fails the build.
- `documentation/`, `website/` — Dokka + MkDocs sources.

---

### Inspection & diagnostics modules (generic debug panels · §7.0)

### 7.17 `pulse-appinfo` (KMP core + platform actual) — *powers the **App Info** panel*
- **Purpose:** One-shot + on-demand snapshot of app and device identity: package name, version name/code, build type (debug/release), applicationId, min/target/compile SDK, install source, first-install & last-update time, process uptime, locale, and `DeviceInfo` (model, manufacturer, OS version, screen, ABI, total/available RAM & storage).
- **Key API:** `AppInfo` / `DeviceInfo` expect types (already used by `Session`, §6.3); emits `AppInfoCaptured` at session start.
- **Platform:** common contract; `actual` reads `PackageManager`/`Build`/`ActivityManager` on Android.
- **Done when:** Panel renders a live snapshot; values match `adb`/system truth on a real device; zero PII beyond standard build metadata; included verbatim in every export.

### 7.18 `pulse-crash` (Android now; KMP later) — *powers the **Crashes** panel* ✅ *implemented*
> **Status:** `PulseCrashReporter` (in `pulse-android`) installs a chaining uncaught handler, records fatal + handled (`PulseAndroid.recordException`) exceptions as `CrashReport`s (in `pulse-core`), persists them to `filesDir/pulsekit_crashes.json` synchronously, and reloads on next launch (fatal crash survives relaunch — verified). Exposed as `Pulse.crashes`. See `docs/DASHBOARD.md` §12.
- **Purpose:** Capture uncaught exceptions (and, on Android, native-adjacent signals where feasible) as durable `CrashReport`s with stacktrace, thread, the current session id, a bounded breadcrumb trail (recent events), and an `AppInfo`/`DeviceInfo`/`BuildProvenance` snapshot for full context.
- **Key API:** installs a chaining `Thread.UncaughtExceptionHandler` that **delegates to the previously-installed handler** (never swallows Crashlytics/Play). `Pulse.track(ExceptionOccurred(..., fatal = false))` records non-fatal/handled exceptions.
- **Reliability:** the fatal path persists synchronously (§6.2) on a dedicated single-thread writer before re-throwing; must not itself throw. On next launch the report is available in the panel and flagged as "from previous session."
- **Done when:** A synthetic uncaught throw produces a persisted `CrashReport` visible after relaunch; the prior handler is still invoked; the SDK never becomes the crash's root cause.

### 7.19 `pulse-provenance` (Android now; KMP later) — *powers the **Commit History** panel* ✅ *implemented*
> **Status:** the sample's Gradle build generates `assets/pulsekit_provenance.json` (git branch/HEAD/dirty + last 20 commits) at build time; `PulseProvenance` (in `pulse-android`) reads it into `BuildProvenance` (in `pulse-core`), exposed as `Pulse.provenance`. The generation task moves into a reusable Gradle plugin (§7.16) later. See `docs/DASHBOARD.md` §12.
- **Purpose:** Runtime reader for the `BuildProvenance` resource generated by the Gradle plugin (§6.5, §7.16). No git at runtime — pure resource load + parse.
- **Key API:** `Pulse.provenance(): BuildProvenance`; drives the Commit History list (branch, HEAD, dirty flag, `git log base..HEAD`) and enriches App Info + every export.
- **Done when:** Panel lists the feature-branch commits baked into the running build; a fresh commit → rebuild updates the list; missing resource degrades to an empty, non-crashing state.

### 7.20 `pulse-storage-inspect` (KMP core + Android actual) — *powers the **Shared Preferences** & **Database** panels* — *(post-0.1)*
- **Purpose:** Read-only (optionally editable in debug) inspection of the host app's key-value stores and SQLite/SQLDelight databases: list stores/tables, browse rows, run scoped queries. The **viewer is generic**; the data is the app's.
- **Reliability:** never writes unless the developer explicitly toggles edit mode; respects the redaction policy (§3.3) for values.
- **Done when:** Panels enumerate the sample app's prefs and one DB; edits are gated behind an explicit debug toggle.

---

## 8. Execution Plan

### Phase 1 — Bootstrap
- KMP project; module skeletons per §4/§7; `gradle/libs.versions.toml` (§9).
- Root build conventions (convention plugins in `build-logic/`).
- CI skeleton: build + ktlint/detekt + the `commonMain`-purity check.
- **DoD:** `./gradlew build` green; empty modules publish locally to `mavenLocal`.

### Phase 2 — Core
- Implement `pulse-core`, `pulse-plugin`, `pulse-runtime` public API (§5, §6).
- Event bus, session manager, plugin manager, dispatchers, config DSL.
- **DoD:** `Pulse.initialize`/`startSession`/`track`/`shutdown` fully unit-tested; no Android deps in core; throwing-plugin isolation test passes.

### Phase 3 — Android UI, Metrics & Generic Inspectors
- Performance: `pulse-fps`, `pulse-memory`, `pulse-network` (API Requests), `pulse-startup`.
- Generic debug inspectors (§7.0): `pulse-appinfo` (App Info), `pulse-crash` (Crashes), `pulse-provenance` + `gradle-plugin` provenance capture (Commit History).
- `pulse-storage` (SQLDelight) + `pulse-export` (JSON/CSV/ZIP).
- `pulse-compose-ui` dashboard (incl. App Info / Crashes / Commit History sections) + `pulse-android` overlay.
- Sample app wiring.
- **DoD:** Live dashboard shows real FPS/memory/network/startup **and** App Info, Crashes (survives a forced uncaught throw across relaunch), and the feature-branch Commit History from the sample app; export produces a valid session file including provenance; overhead budgets met in benchmark.

### Phase 4 — CI/CD & Publishing
- GitHub Actions: build+test matrix, Dokka, MkDocs → GitHub Pages, Maven Central publish on tag.
- Signing + Sonatype config; per-module independent publication.
- **DoD:** A `v0.1.0` tag publishes all artifacts to Maven Central staging and deploys docs.

### Later roadmap (post-0.1)
- **v0.2:** Compose recomposition metrics, timeline view, custom plugins, settings/filters/dark mode polish, `pulse-storage-inspect` (SharedPreferences + Database panels, §7.20).
- **v0.3:** ANR, battery, historical session comparison, session viewer. *(Crash reporting, App Info, and Commit History land earlier — in Phase 3 — as generic inspectors.)*
- **Not planned in-SDK:** app-specific panels (Analytics, Experiments, Roles & Permissions, Components) — these are supported *as plugins* (§7.0), not shipped.
- **v1.0:** Frozen API, full docs, benchmark gate, Maven Central GA, release automation, SemVer.

**Milestone review loop:** after each version, audit architecture vs §3 constraints, log technical debt, refactor before proceeding.

---

## 9. Technology Stack & Version Catalog

| Concern | Choice |
|---|---|
| Language | Kotlin (KMP) |
| UI | Compose Multiplatform |
| Concurrency | Coroutines, Flow, StateFlow |
| Serialization | kotlinx.serialization |
| DI | Koin |
| Storage | SQLDelight |
| Networking | OkHttp (Android), Ktor Client (KMP) |
| Logging | Napier |
| Testing | Kotest, Turbine, MockK, JUnit |
| Docs & CI | Dokka, MkDocs, GitHub Actions, Maven Central |

`gradle/libs.versions.toml` is the sole place versions are declared; all modules reference `libs.*`. Pin versions at bootstrap and upgrade deliberately.

---

## 10. Storage & Export

- **Storage:** SQLDelight schema per §7.12; all writes off-main; retention pruning; reactive `Flow` queries feed the dashboard.
- **Export formats:** JSON (full fidelity), CSV (tabular per entity), ZIP (bundle of all artifacts for a session), HTML (human-readable report). Export is always explicit and local.

---

## 11. Testing, Quality & CI

### 11.1 Test pyramid
- **Unit:** every module (Kotest + Turbine + MockK).
- **Integration:** runtime + plugins + storage end-to-end.
- **Instrumentation / Compose UI:** dashboard and overlay on Android.
- **Benchmark / stress / leak:** required to merge (startup, FPS, memory budgets; LeakCanary-style checks in the sample).

### 11.2 Static quality
- ktlint + detekt; KDoc required on public symbols (detekt rule).
- API binary-compatibility validator (`binary-compatibility-validator`) guards the frozen surface (§5).

### 11.3 CI matrix (GitHub Actions)
- Jobs: `build`, `test`, `lint`, `api-check`, `dependency-rules`, `common-purity`, `benchmark` (nightly), `docs`, `publish` (tag-triggered).

### 11.4 `commonMain` purity & dependency-rule checks
- A CI step fails the build if any `commonMain` source imports `android.*`/`java.*`-only APIs.
- A module-graph assertion fails if a feature module depends on another feature module or on a platform module.

### 11.5 Budget gates
- Benchmark job asserts: startup delta < 30 ms, added memory < 10 MB, no SDK-induced jank. Regressions fail CI.

---

## 12. Publishing & Distribution

- **Source:** GitHub.
- **Artifacts:** Maven Central via Sonatype, group `io.pulsekit`, each module independently versioned/publishable.
- **Docs:** GitHub Pages — Dokka (API reference) + MkDocs (guides).
- **Automation:** GitHub Actions builds, tests, generates docs, and publishes on Git tags (`vX.Y.Z`). SemVer strictly; breaking changes documented in `CHANGELOG.md` and never shipped in a minor/patch.

---

## 13. Lead Architect Directives (Enforced Every Milestone)

1. **Architecture:** Clean Architecture + SOLID; modules strictly independent per §4.2.
2. **KMP:** No Android deps in `commonMain`; `expect/actual` only when strictly necessary.
3. **Design:** Interfaces over implementations; composition over inheritance; design for iOS/Desktop from day one.
4. **Quality:** KDoc every public API; unit-test every feature; readability before micro-optimization.
5. **Git/CI:** Small, logically-scoped commits; every module independently publishable; no undocumented breaking changes.
6. **Review loop:** After each milestone, review architecture, record tech debt, refactor before advancing.

---

## 14. Open Decisions (Resolve Before or During Phase 1)

These are the only ambiguities in this spec. Defaults are proposed; confirm at bootstrap.

| # | Decision | Proposed default |
|---|---|---|
| 1 | Min Android SDK / target SDK | min 24, target latest stable |
| 2 | Kotlin / Compose MP / AGP versions | latest stable trio at bootstrap, pinned in catalog |
| 3 | Overlay permission UX (SYSTEM_ALERT_WINDOW) | prompt lazily on first overlay show; degrade to in-app screen if denied |
| 4 | Network body capture size cap | 256 KB, truncate with marker |
| 5 | Default retained sessions | 20 |
| 6 | License | Apache-2.0 |
| 7 | Package root | `io.pulsekit` |
| 8 | Commit History base branch + cap (§6.5) | base `main`, cap 100 commits, both overridable in the Gradle plugin DSL |
| 9 | Crash handler policy (§7.18) | chain to existing handler; capture fatal + opt-in non-fatal; persist synchronously |

---

*End of specification. This document is authoritative; where code and this document disagree, update one to match the other in the same change.*
