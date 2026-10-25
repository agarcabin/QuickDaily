package com.quickdaily

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class BoundedLoaderTest {
    @Test fun timedOutProviderIsNotRestartedByRepeatedRefreshes() {
        val loader = BoundedLoader(timeoutMs = 150, capacity = 2)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        try {
            repeat(2) {
                try {
                    loader.load("widget", "config") { calls.incrementAndGet(); release.await(2, TimeUnit.SECONDS); "content" }
                    fail("Expected timeout")
                } catch (_: TimeoutException) { }
            }
            assertEquals(1, calls.get())
            assertEquals("other", loader.load("other widget", "config") { "other" })
            try {
                loader.load("widget", "new config") { "wrong" }
                fail("Must not reuse the previous configuration")
            } catch (_: IllegalStateException) { }
        } finally { release.countDown() }
    }

    @Test fun failedReadDoesNotPoisonOtherWidgets() {
        val loader = BoundedLoader(timeoutMs = 1_000)
        assertTrue(runCatching { loader.load("bad", "config") { error("broken provider") } }.isFailure)
        assertEquals("ok", loader.load("good", "config") { "ok" })
    }
}
