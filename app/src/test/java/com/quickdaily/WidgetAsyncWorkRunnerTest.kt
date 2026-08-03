package com.quickdaily

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAsyncWorkRunnerTest {
    @Test
    fun blockingIoRunsAfterReceiverReturnsAndFinishesAfterWork() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val refreshed = AtomicBoolean(false)

        WidgetAsyncWorkRunner.launch(
            finishable = WidgetAsyncFinishable { finished.countDown() },
        ) {
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            refreshed.set(true)
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
        assertFalse(refreshed.get())

        release.countDown()
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertTrue(refreshed.get())
    }

    @Test
    fun exceptionStillFinishesPendingResult() {
        val finished = CountDownLatch(1)

        WidgetAsyncWorkRunner.launch(
            finishable = WidgetAsyncFinishable { finished.countDown() },
        ) {
            throw IllegalStateException("fake io failure")
        }

        assertTrue(finished.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun quickNoteWidgetUpdateRunsOffBroadcastMainThread() {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val workerName = AtomicReference<String>()

        QuickNoteWidgetUpdatePolicy.launch(
            finishable = WidgetAsyncFinishable { finished.countDown() },
        ) {
            workerName.set(Thread.currentThread().name)
            started.countDown()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertFalse(workerName.get().equals("main", ignoreCase = true))
    }
}
