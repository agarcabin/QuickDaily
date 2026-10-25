package com.quickdaily

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** One active operation per target; an identical request joins it instead of writing twice. */
internal class SingleFlightOperations<K, I, T>(private val scope: CoroutineScope, private val capacity: Int = 4) {
    private data class Entry<I, T>(val input: I, val result: Deferred<T>)
    private val active = mutableMapOf<K, Entry<I, T>>()

    @Synchronized
    fun isRunning(key: K): Boolean = active.containsKey(key)

    @Synchronized
    fun start(key: K, input: I, work: suspend () -> T): Deferred<T>? {
        active[key]?.let { return if (it.input == input) it.result else null }
        if (active.size >= capacity) return null
        val result = scope.async(start = CoroutineStart.LAZY) { work() }
        active[key] = Entry(input, result)
        result.invokeOnCompletion {
            synchronized(this) {
                if (active[key]?.result === result) active.remove(key)
            }
        }
        result.start()
        return result
    }
}
