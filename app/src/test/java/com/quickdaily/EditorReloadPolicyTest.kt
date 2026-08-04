package com.quickdaily

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorReloadPolicyTest {
    @Test
    fun staleReloadFromAIsRejectedAfterBBecomesCurrent() {
        val aReadStarted = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val reader = BlockingReader(aReadStarted, releaseA)
        val requestA = EditorReloadSnapshot(
            generation = 7L,
            target = "A.md",
            absolutePath = "/vault/A.md",
            lastLoadedMtime = 10L,
        )
        val currentB = EditorReloadSnapshot(
            generation = 8L,
            target = "B.md",
            absolutePath = "/vault/B.md",
            lastLoadedMtime = 20L,
        )

        var aContent: String? = null
        val reloadA = Thread {
            aContent = reader.read(requestA)
        }
        reloadA.start()

        assertTrue(aReadStarted.await(1, TimeUnit.SECONDS))
        releaseA.countDown()
        reloadA.join(1_000)

        assertFalse(
            EditorReloadPolicy.canApply(
                request = requestA,
                current = currentB,
                observedMtime = 11L,
            ),
        )
        assertTrue(aContent == "A content")
    }

    @Test
    fun failedReloadDoesNotAdvanceMtimeAndCanRetry() {
        val request = EditorReloadSnapshot(
            generation = 3L,
            target = "A.md",
            absolutePath = "/vault/A.md",
            lastLoadedMtime = 10L,
        )

        assertFalse(EditorReloadPolicy.shouldStart(request, observedMtime = 10L))
        assertTrue(EditorReloadPolicy.shouldStart(request, observedMtime = 11L))
        assertTrue(
            EditorReloadPolicy.canApply(
                request = request,
                current = request,
                observedMtime = 11L,
            ),
        )
    }

    @Test
    fun contentChangeIsDetectedEvenWhenMtimeMovesBackwards() {
        val loaded = com.quickdaily.util.FileFingerprint(
            exists = true,
            length = 4L,
            sha256 = "old",
            lastModified = 200L,
        )
        val changed = loaded.copy(sha256 = "new", lastModified = 100L)
        val request = EditorReloadSnapshot(
            generation = 1L,
            target = "A.md",
            absolutePath = "/vault/A.md",
            lastLoadedMtime = 200L,
            lastLoadedFingerprint = loaded,
        )

        assertTrue(EditorReloadPolicy.shouldStart(request, 100L, changed))
        assertTrue(EditorReloadPolicy.canApply(request, request, 100L, changed))
    }

    @Test
    fun mtimeOnlyChangeDoesNotReloadSameContent() {
        val loaded = com.quickdaily.util.FileFingerprint(true, 4L, "same", 200L)
        val sameContent = loaded.copy(lastModified = 300L)
        val request = EditorReloadSnapshot(
            generation = 1L,
            target = "A.md",
            absolutePath = "/vault/A.md",
            lastLoadedMtime = 200L,
            lastLoadedFingerprint = loaded,
        )

        assertFalse(EditorReloadPolicy.shouldStart(request, 300L, sameContent))
    }

    private class BlockingReader(
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) {
        fun read(request: EditorReloadSnapshot): String {
            if (request.target == "A.md") {
                started.countDown()
                release.await(1, TimeUnit.SECONDS)
            }
            return "A content"
        }
    }
}
