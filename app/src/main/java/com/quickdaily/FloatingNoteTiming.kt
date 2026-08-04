package com.quickdaily

import android.os.SystemClock

/** Monotonic timing markers for Overlay startup, IME and save/close latency analysis. */
internal object FloatingNoteTiming {
    @Volatile
    private var requestContext: String = "requestId=unknown source=unknown"

    private val lock = Any()
    private var requestStartedAtElapsed = 0L
    private var requestStartedAtWallClock = 0L
    private var previousStageAtElapsed = 0L
    private var sequence = 0L
    private val stageCounts = mutableMapOf<String, Int>()

    fun begin(requestId: String, source: FloatingNoteSource) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWallClock = System.currentTimeMillis()
        val context = "requestId=$requestId source=$source"
        val reused = synchronized(lock) {
            if (requestStartedAtElapsed > 0L && requestContext == context) {
                true
            } else {
                requestContext = context
                requestStartedAtElapsed = nowElapsed
                requestStartedAtWallClock = nowWallClock
                previousStageAtElapsed = nowElapsed
                sequence = 0L
                stageCounts.clear()
                false
            }
        }
        mark(
            if (reused) "request_context_reused" else "request_start",
            "requestBeginWallClockMillis=$requestStartedAtWallClock source=$source reused=$reused",
        )
    }

    fun mark(stage: String, detail: String? = null) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWallClock = System.currentTimeMillis()
        val event = synchronized(lock) {
            sequence += 1L
            val count = (stageCounts[stage] ?: 0) + 1
            stageCounts[stage] = count
            val elapsed = requestStartedAtElapsed
                .takeIf { it > 0L }
                ?.let { elapsedMs(nowElapsed, it).toString() }
                ?: "unknown"
            val delta = previousStageAtElapsed
                .takeIf { it > 0L }
                ?.let { elapsedMs(nowElapsed, it).toString() }
                ?: "unknown"
            previousStageAtElapsed = nowElapsed
            "sequence=$sequence stage=$stage elapsedRealtime=$nowElapsed " +
                "elapsedSinceRequestMs=$elapsed deltaSincePreviousMs=$delta stageCount=$count " +
                "wallClockMillis=$nowWallClock requestBeginWallClockMillis=$requestStartedAtWallClock " +
                "$requestContext thread=${Thread.currentThread().name.replace(' ', '_')}"
        }
        val suffix = detail?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        BetaLogger.log("FloatingNote/Timing", event + suffix)
    }

    /** Keeps reported durations meaningful if a callback arrives with a stale timestamp. */
    internal fun elapsedMs(now: Long, startedAt: Long): Long = (now - startedAt).coerceAtLeast(0L)
}
