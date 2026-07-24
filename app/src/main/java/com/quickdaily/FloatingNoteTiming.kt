package com.quickdaily

import android.os.SystemClock

/** Monotonic timing markers for Overlay startup, IME and save/close latency analysis. */
internal object FloatingNoteTiming {
    fun mark(stage: String, detail: String? = null) {
        val suffix = detail?.let { " $it" }.orEmpty()
        BetaLogger.log(
            "FloatingNote/Timing",
            "stage=$stage elapsedRealtime=${SystemClock.elapsedRealtime()}$suffix"
        )
    }
}
