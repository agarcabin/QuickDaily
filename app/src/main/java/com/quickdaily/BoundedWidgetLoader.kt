package com.quickdaily

import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A timed-out provider keeps its slot until it really returns. No unbounded IO-thread retries. */
internal class BoundedLoader(private val timeoutMs: Long = 5_000, capacity: Int = 4) {
    private data class Entry(val input: Any, val result: CompletableFuture<Any>)
    private val active = mutableMapOf<String, Entry>()
    private val executor = ThreadPoolExecutor(0, capacity, 30, TimeUnit.SECONDS, SynchronousQueue()) { runnable ->
        Thread(runnable, "quickdaily-widget-io").apply { isDaemon = true }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> load(key: String, input: Any, work: () -> T): T {
        val entry = synchronized(active) {
            active[key]?.let {
                check(it.input == input) { "widget_previous_read_pending" }
                return@synchronized it
            }
            val created = Entry(input, CompletableFuture())
            active[key] = created
            try {
                executor.execute {
                    try {
                        created.result.complete(work())
                    } catch (error: Throwable) {
                        created.result.completeExceptionally(error)
                    } finally {
                        synchronized(active) { if (active[key] === created) active.remove(key) }
                    }
                }
            } catch (error: Exception) {
                active.remove(key)
                throw error
            }
            created
        }
        return entry.result.get(timeoutMs, TimeUnit.MILLISECONDS) as T
    }
}

internal object BoundedWidgetLoader {
    private val loader = BoundedLoader()

    fun <T : Any> load(key: String, config: Any, work: () -> WidgetLoadResult<T>): WidgetLoadResult<T> = try {
        loader.load(key, config, work)
    } catch (error: Exception) {
        WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_refresh_failed), exception = error)
    }
}
