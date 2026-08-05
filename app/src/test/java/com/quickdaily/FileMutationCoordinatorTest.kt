package com.quickdaily

import com.quickdaily.util.FileUtil
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileMutationCoordinatorTest {
    @Test
    fun editorSnapshotWaitsForWidgetMutationAndPairsNewContentWithItsFingerprint() {
        val file = kotlin.io.path.createTempFile("quickdaily-widget-toggle", ".md").toFile()
        file.writeText("- [ ] task", Charsets.UTF_8)
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val readerFinished = CountDownLatch(1)
        var snapshot: com.quickdaily.util.FileReadSnapshot? = null

        val writer = Thread {
            FileUtil.withPathMutation(file.path) {
                writerEntered.countDown()
                releaseWriter.await(2, TimeUnit.SECONDS)
                FileUtil.write(file.path, "- [X] task")
            }
        }
        val reader = Thread {
            snapshot = FileUtil.readStableSnapshot(file.path)
            readerFinished.countDown()
        }

        try {
            writer.start()
            assertTrue(writerEntered.await(1, TimeUnit.SECONDS))
            reader.start()
            assertFalse(readerFinished.await(100, TimeUnit.MILLISECONDS))

            releaseWriter.countDown()
            assertTrue(readerFinished.await(1, TimeUnit.SECONDS))
            writer.join(1_000)
            reader.join(1_000)

            val loaded = snapshot
            assertNotNull(loaded)
            assertEquals("- [X] task", (loaded!!.result as com.quickdaily.util.ReadResult.Success).content)
            assertTrue(loaded.fingerprint?.hasSameContentAs(FileUtil.fingerprint(file.path)) == true)
        } finally {
            releaseWriter.countDown()
            writer.join(1_000)
            reader.join(1_000)
            file.delete()
        }
    }

    @Test
    fun equivalentPathsAreSerializedAcrossReadModifyWriteSections() {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val order = Collections.synchronizedList(mutableListOf<String>())
        val canonicalPath = "build/test-fixtures/../test-fixtures/coordinated.md"
        val equivalentPath = "build/test-fixtures/coordinated.md"

        val first = Thread {
            FileUtil.withPathMutation(canonicalPath) {
                order += "first-start"
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
                order += "first-end"
            }
        }
        val second = Thread {
            FileUtil.withPathMutation(equivalentPath) {
                order += "second"
                secondEntered.countDown()
            }
        }

        first.start()
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        second.start()
        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        first.join(1_000)
        second.join(1_000)

        assertEquals(listOf("first-start", "first-end", "second"), order)
    }
}
