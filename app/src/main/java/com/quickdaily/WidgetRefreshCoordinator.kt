package com.quickdaily

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces noisy lifecycle/save refreshes before they reach launcher RemoteViews services. */
object WidgetRefreshCoordinator {
    private const val DEBOUNCE_MS = 400L
    private const val MIN_INTERVAL_MS = 2_000L
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val lastRun = mutableMapOf<String, Long>()
    private val pending = mutableMapOf<String, Runnable>()
    private val runLocks = mutableMapOf<String, Mutex>()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshRead(context: Context, immediate: Boolean = false) =
        request(context, "read", immediate) { QuickDailyReadWidget.refreshNow(context.applicationContext) }

    fun refreshTasks(context: Context, immediate: Boolean = false) =
        request(context, "tasks", immediate) { TaskWidget.refreshNow(context.applicationContext) }

    fun refreshAll(context: Context, immediate: Boolean = false) {
        refreshRead(context, immediate)
        refreshTasks(context, immediate)
    }

    private fun request(context: Context, key: String, immediate: Boolean, work: suspend () -> Unit) {
        synchronized(lock) {
            val replacedPending = pending.remove(key)?.also(handler::removeCallbacks) != null
            val now = System.currentTimeMillis()
            val elapsed = now - (lastRun[key] ?: 0L)
            // "Immediate" means do not add the normal debounce, not that the per-type
            // two-second safety window may be bypassed. This protects launchers that
            // enqueue every RemoteViews update (notably Samsung/Xiaomi launchers).
            val delay = if (immediate && elapsed >= MIN_INTERVAL_MS) 0L
                else maxOf(if (immediate) 0L else DEBOUNCE_MS, MIN_INTERVAL_MS - elapsed)
            BetaLogger.log("WidgetRefresh", "request type=$key immediate=$immediate replacedPending=$replacedPending elapsedSinceRunMs=$elapsed scheduledDelayMs=$delay")
            val runnable = Runnable {
                val startedAt = System.currentTimeMillis()
                synchronized(lock) {
                    pending.remove(key)
                    lastRun[key] = startedAt
                }
                workerScope.launch {
                    BetaLogger.log("WidgetRefresh", "run start type=$key")
                    val runLock = synchronized(lock) { runLocks.getOrPut(key) { Mutex() } }
                    try {
                        runLock.withLock { work() }
                        BetaLogger.log("WidgetRefresh", "run success type=$key durationMs=${System.currentTimeMillis() - startedAt}")
                    } catch (e: Exception) {
                        BetaLogger.log("WidgetRefresh", "run failed type=$key durationMs=${System.currentTimeMillis() - startedAt} exception=${e.javaClass.simpleName} message=${e.message}")
                    }
                }
            }
            pending[key] = runnable
            handler.postDelayed(runnable, delay)
        }
    }
}
