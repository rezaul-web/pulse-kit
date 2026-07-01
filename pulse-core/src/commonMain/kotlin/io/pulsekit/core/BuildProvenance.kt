package io.pulsekit.core

import kotlinx.serialization.Serializable

/** One commit in the build's [BuildProvenance.history]. */
@Serializable
data class CommitRecord(
    val sha: String,
    val author: String,
    val timeMs: Long,
    val subject: String,
)

/**
 * Git/build provenance of the running binary, captured at **build time** (the app
 * can't run `git` at runtime) and read back from a generated resource. Powers the
 * dashboard's **Commit History** panel so a bug report always says exactly which
 * code produced it. See `ARCHITECTURE.md` §6.5.
 */
@Serializable
data class BuildProvenance(
    val branch: String = "",
    val commit: String = "",
    val commitTimeMs: Long = 0,
    val dirty: Boolean = false,
    val buildTimeMs: Long = 0,
    val history: List<CommitRecord> = emptyList(),
) {
    val isEmpty: Boolean get() = commit.isEmpty() && history.isEmpty()

    companion object {
        val EMPTY = BuildProvenance()
    }
}
