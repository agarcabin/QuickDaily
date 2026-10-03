package com.quickdaily

import com.quickdaily.util.FileSaveStatus
import com.quickdaily.util.FileSaveStage
import com.quickdaily.util.FileSaveTestHooks
import com.quickdaily.util.FileUtil
import com.quickdaily.util.LegacySaveArtifactKind
import com.quickdaily.util.SafeRecoveryState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSaveCoordinatorTest {
    private val files = mutableListOf<File>()

    @After
    fun clearHooksAndFixtures() {
        FileSaveTestHooks.clear()
        files.forEach { it.delete() }
    }

    @Test
    fun existingFileIsWrittenInPlaceWithVerifiedBackup() {
        val file = fixture("before")
        val before = FileUtil.fingerprint(file.path)

        val result = FileUtil.saveTextResult(file.path, "after", before)

        assertEquals(FileSaveStatus.SAVED, result.status)
        assertEquals("after", file.readText())
        assertNotNull(result.backupPath)
        assertTrue(File(result.backupPath!!).isFile)
        assertTrue(result.identityPreserved != false)
    }

    @Test
    fun newFileIsCreatedAndVerified() {
        val file = fixture("missing")
        assertTrue(file.delete())

        val result = FileUtil.saveTextResult(file.path, "中文🙂\n")

        assertEquals(FileSaveStatus.SAVED, result.status)
        assertEquals("中文🙂\n", file.readText())
        assertTrue(result.backupPath == null)
    }

    @Test
    fun partialWriteIsRestoredFromBackupAndKeepsDraftRequired() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        FileSaveTestHooks.targetWriter = { path, bytes ->
            FileOutputStream(path, false).use { it.write(bytes, 0, bytes.size / 2) }
            throw IOException("injected partial write")
        }

        val result = FileUtil.saveTextResult(file.path, "new content", before)

        assertEquals(FileSaveStatus.RECOVERED, result.status)
        assertTrue(result.keepDraft)
        assertEquals("original", file.readText())
    }

    @Test
    fun postWriteHashMismatchIsRestored() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        FileSaveTestHooks.targetWriter = { path, _ ->
            File(path).writeText("wrong")
        }

        val result = FileUtil.saveTextResult(file.path, "expected", before)

        assertEquals(FileSaveStatus.RECOVERED, result.status)
        assertEquals("original", file.readText())
    }

    @Test
    fun backupFailureDoesNotModifyTarget() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        FileSaveTestHooks.backupWriter = { _, _ -> throw IOException("injected backup failure") }

        val result = FileUtil.saveTextResult(file.path, "new", before)

        assertEquals(FileSaveStatus.BACKUP_FAILED, result.status)
        assertEquals("original", file.readText())
    }

    @Test
    fun stalePreconditionStopsExternalOverwrite() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        file.writeText("external")

        val result = FileUtil.saveTextResult(file.path, "local", before)

        assertEquals(FileSaveStatus.CONFLICT, result.status)
        assertEquals("external", file.readText())
    }

    @Test
    fun samePathSavesAreSerializedAndSecondStaleSaveConflicts() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstHook = AtomicBoolean(true)
        FileSaveTestHooks.beforeTargetWrite = {
            if (firstHook.compareAndSet(true, false)) {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        var firstResult: com.quickdaily.util.FileSaveResult? = null
        var secondResult: com.quickdaily.util.FileSaveResult? = null
        val first = Thread { firstResult = FileUtil.saveTextResult(file.path, "first", before) }
        val second = Thread { secondResult = FileUtil.saveTextResult(file.path, "second", before) }

        first.start()
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        second.start()
        Thread.sleep(100)
        assertTrue(second.isAlive)
        releaseFirst.countDown()
        first.join(1_000)
        second.join(1_000)

        assertEquals(FileSaveStatus.SAVED, firstResult?.status)
        assertEquals(FileSaveStatus.CONFLICT, secondResult?.status)
        assertEquals("first", file.readText())
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
    }

    @Test
    fun interruptedJournalIsDiscoveredWithoutOverwritingTarget() {
        val file = fixture("original")
        val before = FileUtil.fingerprint(file.path)
        FileSaveTestHooks.abortAfterJournalStage = FileSaveStage.WRITE_STARTED

        var interrupted = false
        try {
            FileUtil.saveTextResult(file.path, "new", before)
        } catch (_: IllegalStateException) {
            interrupted = true
        }

        assertTrue(interrupted)
        val pending = FileUtil.pendingSaveRecoveries().single { it.path == file.path }
        assertEquals(FileSaveStage.WRITE_STARTED, pending.stage)
        assertEquals(SafeRecoveryState.NOT_STARTED, pending.safeState)
        assertEquals("original", file.readText())
        FileUtil.recoverPendingSaves()
        assertTrue(FileUtil.pendingSaveRecoveries().none { it.path == file.path })
    }

    @Test
    fun legacyArtifactsAreReadOnlyScannedAndRequireExplicitRestoreDestination() {
        val directory = kotlin.io.path.createTempDirectory("quickdaily-legacy").toFile()
        val backup = File(directory, ".2026-09-29.md.123.quickdaily.bak").also { it.writeText("old") }
        val temporary = File(directory, ".2026-09-29.md.123.quickdaily.tmp").also { it.writeText("partial") }
        val destination = File(directory, "restored.md")
        try {
            val artifacts = FileUtil.scanLegacySaveArtifacts(directory.path)
            assertEquals(2, artifacts.size)
            assertTrue(artifacts.any { it.kind == LegacySaveArtifactKind.BACKUP })
            assertTrue(artifacts.any { it.kind == LegacySaveArtifactKind.TEMPORARY })
            assertFalse(destination.exists())

            val result = FileUtil.restoreLegacySaveArtifact(backup.path, destination.path)

            assertEquals(FileSaveStatus.SAVED, result?.status)
            assertEquals("old", destination.readText())
            assertTrue(backup.exists())
            assertTrue(temporary.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun fixture(content: String): File =
        kotlin.io.path.createTempFile("quickdaily-save", ".md").toFile().also {
            it.writeText(content)
            files += it
        }
}
