package com.quickdaily

import android.os.SystemClock

/** Monotonic timing markers for Overlay startup, IME and save/close latency analysis. */
internal object FloatingNoteTiming {
    @Volatile
    private var requestContext: String = "requestId=unknown source=unknown"

    fun begin(requestId: String, source: FloatingNoteSource) {
        requestContext = "requestId=$requestId source=$source"
        mark("request_start")
    }

    fun mark(stage: String, detail: String? = null) {
        val suffix = detail?.let { " $it" }.orEmpty()
        BetaLogger.log(
            "FloatingNote/Timing",
            "stage=$stage elapsedRealtime=${SystemClock.elapsedRealtime()} $requestContext$suffix"
        )
    }
}
