package com.quickdaily

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleFlightOperationsTest {
    @Test fun repeatedSaveJoinsAndDifferentInputCannotOverlap() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val gate = SingleFlightOperations<String, String, Int>(scope)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val writes = AtomicInteger()
            val first = gate.start("diary", "snapshot") { entered.countDown(); release.await(2, TimeUnit.SECONDS); writes.incrementAndGet() }!!
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertSame(first, gate.start("diary", "snapshot") { writes.incrementAndGet() })
            assertNull(gate.start("diary", "new input") { writes.incrementAndGet() })
            release.countDown()
            assertEquals(1, first.await())
            assertEquals(1, writes.get())
        } finally { scope.cancel() }
    }

    @Test fun closingObserverDoesNotCancelWriteAndFailureReleasesSlot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val gate = SingleFlightOperations<String, String, Int>(scope)
            val release = CompletableDeferred<Unit>()
            val operation = gate.start("diary", "first") { release.await(); 7 }!!
            val observer = launch { operation.await() }
            yield()
            observer.cancelAndJoin()
            assertTrue(operation.isActive)
            release.complete(Unit)
            assertEquals(7, operation.await())
            val failed = gate.start("other", "bad") { error("storage failed") }!!
            runCatching { failed.await() }
            withTimeout(1_000) { while (gate.isRunning("other")) yield() }
            assertEquals(9, gate.start("other", "retry") { 9 }!!.await())
        } finally { scope.cancel() }
    }
}
