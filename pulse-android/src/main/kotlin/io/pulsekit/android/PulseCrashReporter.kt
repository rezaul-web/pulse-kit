package io.pulsekit.android

import android.annotation.SuppressLint
import android.content.Context
import io.github.aakira.napier.Napier
import io.pulsekit.core.CrashReport
import io.pulsekit.runtime.Pulse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Captures crashes for the dashboard's **Crashes** panel.
 *
 * - Installs a **chaining** `Thread.UncaughtExceptionHandler`: it records the fatal
 *   crash, then delegates to the previously-installed handler, so it never swallows
 *   Crashlytics/Play or the system default.
 * - Because a fatal crash kills the process (wiping the in-memory store), each report
 *   is **persisted to a file synchronously** and re-loaded on the next launch — so a
 *   crash from the previous session is visible after relaunch.
 * - Also records non-fatal/handled exceptions via [PulseAndroid.recordException].
 */
internal object PulseCrashReporter {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private const val FILE_NAME = "pulsekit_crashes.json"
    private const val MAX_ENTRIES = 50
    private val seq = AtomicLong(0)

    @SuppressLint("StaticFieldLeak") // application context only
    private lateinit var appContext: Context

    fun install(context: Context) {
        appContext = context.applicationContext

        // Seed the live list with anything persisted from previous sessions.
        Pulse.restoreCrashes(load())

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(throwable, thread, fatal = true)
            } catch (t: Throwable) {
                // The crash reporter must never become the cause of a crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Record a crash / handled exception, persisting it and pushing it to the store. */
    fun record(throwable: Throwable, thread: Thread = Thread.currentThread(), fatal: Boolean) {
        if (!::appContext.isInitialized) return
        val report = CrashReport(
            id = "${System.currentTimeMillis()}-${seq.incrementAndGet()}",
            timestampMs = System.currentTimeMillis(),
            type = throwable::class.java.simpleName ?: "Throwable",
            message = throwable.message ?: "",
            threadName = thread.name,
            stackTrace = throwable.stackTraceToString(),
            fatal = fatal,
            sessionId = Pulse.activeSession()?.value,
        )
        val updated = (listOf(report) + load()).take(MAX_ENTRIES)
        persist(updated)
        Pulse.recordCrash(report)
    }

    private fun file(): File = File(appContext.filesDir, FILE_NAME)

    private fun load(): List<CrashReport> = try {
        val f = file()
        if (f.exists()) json.decodeFromString<List<CrashReport>>(f.readText()) else emptyList()
    } catch (e: Exception) {
        Napier.w { "Failed to load crash reports: ${e.message}" }
        emptyList()
    }

    private fun persist(reports: List<CrashReport>) {
        try {
            file().writeText(json.encodeToString(reports))
        } catch (e: Exception) {
            Napier.w { "Failed to persist crash reports: ${e.message}" }
        }
    }
}
