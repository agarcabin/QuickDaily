package com.quickdaily

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps transparent launch dispatchers alive until the overlay window is actually attached. */
internal class FloatingNoteHandoffWaiter(
    private val activity: ComponentActivity,
    private val requestId: String,
    private val timeoutMs: Long = 320L,
) {
    private var finished = false
    private var timeoutJob: Job? = null

    fun start(started: Boolean) {
        if (finished) return
        if (!started) {
            finish("not_started")
            return
        }
        FloatingNoteTiming.mark("handoff_wait_start", "requestId=$requestId timeoutMs=$timeoutMs")
        timeoutJob = activity.lifecycleScope.launch {
            delay(timeoutMs)
            finish("timeout")
        }
    }

    fun ready() {
        finish("ready")
    }

    fun cancel() {
        if (finished) return
        finished = true
        timeoutJob?.cancel()
        FloatingNoteHandoff.cancel(requestId)
    }

    private fun finish(reason: String) {
        if (finished) return
        finished = true
        timeoutJob?.cancel()
        FloatingNoteHandoff.cancel(requestId)
        FloatingNoteTiming.mark("handoff_finish", "requestId=$requestId reason=$reason")
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }
}
