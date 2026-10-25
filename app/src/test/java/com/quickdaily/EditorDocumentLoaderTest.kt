package com.quickdaily

import com.quickdaily.util.FileFingerprint
import com.quickdaily.util.FileReadSnapshot
import com.quickdaily.util.ReadResult
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

class EditorDocumentLoaderTest {
    private fun snapshot(result: ReadResult) = FileReadSnapshot(result, FileFingerprint(false, 0, "", 0))

    @Test fun missingTodayCanUseTemplate() {
        val loaded = EditorDocumentLoader.load(true, { snapshot(ReadResult.NotFound) }, { "# Today\n" })
        assertEquals("# Today\n", loaded.rawContent)
    }

    @Test fun existingContentIsNotReplacedByATemplate() {
        val loaded = EditorDocumentLoader.load(true, { snapshot(ReadResult.Success("existing note")) }, { error("must not read template") })
        assertEquals("existing note", loaded.rawContent)
    }

    @Test fun unreadableFileIsAnErrorRatherThanAnEmptyEditableDiary() {
        try {
            EditorDocumentLoader.load(true, { snapshot(ReadResult.Error(SecurityException("revoked"))) }, { error("must not fall back to template") })
            fail("must fail the load")
        } catch (error: IOException) {
            assertTrue(error.cause is SecurityException)
        }
    }

    @Test fun missingCustomFileDoesNotSilentlyCreateABlankDocument() {
        try {
            EditorDocumentLoader.load(false, { snapshot(ReadResult.NotFound) }, { "unused" })
            fail("custom target missing")
        } catch (_: IOException) { }
    }

    @Test fun blockedSafReadDoesNotPreventLoadingADifferentLocalPath() {
        val loader = BoundedLoader(timeoutMs = 150, capacity = 2)
        val release = CountDownLatch(1)
        try {
            try {
                loader.load("editor/saf-old", "config") { release.await(3, TimeUnit.SECONDS); "old" }
                fail("must time out")
            } catch (_: TimeoutException) { }
            assertEquals("local diary", loader.load("editor/local-path", "config") { "local diary" })
        } finally { release.countDown() }
    }
}
