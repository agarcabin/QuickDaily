package com.quickdaily

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Coalesces noisy lifecycle/save refreshes before they reach launcher RemoteViews services. */
object WidgetRefreshCoordinator {
    private const val DEBOUNCE_MS = 400L
    private const val MIN_INTERVAL_MS = 2_000L
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val lastRun = mutableMapOf<String, Long>()
    private val pending = mutableMapOf<String, Runnable>()
    private val running = mutableSetOf<String>()
    private val rerun = mutableSetOf<String>()
    private val receiverCompletions = mutableMapOf<String, MutableList<() -> Unit>>()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Protect initial/manual refresh while the receiver is gone, with an independent 8s limit. */
    internal fun holdReceiver(kind: String, finish: () -> Unit) {
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var callback: () -> Unit
        val timeout = Runnable { callback() }
        callback = {
            if (completed.compareAndSet(false, true)) {
                handler.removeCallbacks(timeout)
                synchronized(lock) { receiverCompletions[kind]?.remove(callback) }
                runCatching(finish)
            }
        }
        synchronized(lock) { receiverCompletions.getOrPut(kind) { mutableListOf() }.add(callback) }
        handler.postDelayed(timeout, 8_000)
    }

    fun refreshRead(context: Context, immediate: Boolean = false) =
        request(context, "read", immediate) { QuickDailyReadWidget.refreshNow(context.applicationContext) }

    fun refreshTasks(context: Context, immediate: Boolean = false) =
        request(context, "tasks", immediate) { TaskWidget.refreshNow(context.applicationContext) }

    fun refreshAll(context: Context, immediate: Boolean = false) {
        refreshRead(context, immediate)
        refreshTasks(context, immediate)
        refreshQuickNote(context, immediate)
    }

    fun refreshQuickNote(context: Context, immediate: Boolean = false) =
        request(context, "quicknote", immediate) { QuickNoteWidget.refreshNow(context.applicationContext) }

    private fun request(context: Context, key: String, immediate: Boolean, work: suspend () -> Unit) {
        synchronized(lock) {
            if (key in running) {
                rerun.add(key)
                return
            }
            val replacedPending = pending.remove(key)?.also(handler::removeCallbacks) != null
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - (lastRun[key] ?: 0L)
            // "Immediate" means do not add the normal debounce, not that the per-type
            // two-second safety window may be bypassed. This protects launchers that
            // enqueue every RemoteViews update (notably Samsung/Xiaomi launchers).
            val delay = if (immediate && elapsed >= MIN_INTERVAL_MS) 0L
                else maxOf(if (immediate) 0L else DEBOUNCE_MS, MIN_INTERVAL_MS - elapsed)
            BetaLogger.log("WidgetRefresh", "request type=$key immediate=$immediate replacedPending=$replacedPending elapsedSinceRunMs=$elapsed scheduledDelayMs=$delay")
            val runnable = Runnable {
                val startedAt = SystemClock.elapsedRealtime()
                synchronized(lock) {
                    pending.remove(key)
                    if (!running.add(key)) {
                        rerun.add(key)
                        return@Runnable
                    }
                    lastRun[key] = startedAt
                }
                workerScope.launch {
                    BetaLogger.log("WidgetRefresh", "run start type=$key")
                    try {
                        work()
                        BetaLogger.log("WidgetRefresh", "run success type=$key durationMs=${SystemClock.elapsedRealtime() - startedAt}")
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (e: Exception) {
                        BetaLogger.log("WidgetRefresh", "run failed type=$key durationMs=${SystemClock.elapsedRealtime() - startedAt} exception=${e.javaClass.simpleName}")
                    } finally {
                        val (again, completions) = synchronized(lock) {
                            running.remove(key)
                            val again = rerun.remove(key)
                            again to if (again) emptyList() else receiverCompletions.remove(key).orEmpty().toList()
                        }
                        if (again) request(context, key, false, work)
                        completions.forEach { it() }
                    }
                }
            }
            pending[key] = runnable
            handler.postDelayed(runnable, delay)
        }
    }
}
