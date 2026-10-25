package com.quickdaily

import com.quickdaily.util.CreatedSafTarget
import com.quickdaily.util.FileFingerprint
import com.quickdaily.util.FileSaveStatus
import com.quickdaily.util.FileSaveTestHooks
import com.quickdaily.util.FileUtil
import com.quickdaily.util.SafeRecoveryState
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises the real new-file coordinator against provider behaviours, without Android stubs. */
class SafNewFileSaveTest {
    private val path = "saf://fixture-${UUID.randomUUID()}/Daily/today.md"
    private data class Doc(val id: String, var bytes: ByteArray = byteArrayOf())
    private val documents = mutableMapOf<String, Doc>()
    private val writes = mutableListOf<String>()
    private var sequence = 0
    private var deleteAllowed = true

    @Before fun backend() {
        FileSaveTestHooks.fingerprintReader = { target ->
            documents[target]?.let { FileFingerprint.fromBytes(it.bytes, identity = it.id) }
                ?: FileFingerprint(false, 0, "", 0)
        }
        FileSaveTestHooks.targetWriter = { target, bytes ->
            writes.add(target)
            documents.getValue(target).bytes = bytes.copyOf()
        }
        FileSaveTestHooks.safCreator = { create("today.md") }
    }

    @After fun cleanup() {
        FileSaveTestHooks.clear()
        val key = FileFingerprint.sha256(path.toByteArray(Charsets.UTF_8)).take(32)
        File(System.getProperty("java.io.tmpdir"), "quickdaily-save-journal/$key.state").delete()
    }

    private fun create(name: String, linkRequestedPath: Boolean = name == "today.md"): CreatedSafTarget {
        val uri = "safdoc://created-${++sequence}"
        val document = Doc(uri)
        documents[uri] = document
        if (linkRequestedPath) documents[path] = document
        return CreatedSafTarget(uri, name, delete = {
            if (!deleteAllowed) false else {
                documents.entries.removeAll { it.value === document }
                true
            }
        }, exists = { documents[uri] != null })
    }

    @Test fun writesAndVerifiesTheReturnedUriInsteadOfReopeningByName() {
        val result = FileUtil.saveTextResult(path, "中文🙂\n")
        assertEquals(FileSaveStatus.SAVED, result.status)
        assertEquals(listOf("safdoc://created-1"), writes)
        assertEquals("中文🙂\n", documents.getValue(path).bytes.toString(Charsets.UTF_8))
    }

    @Test fun providerAppendedTxtIsRejectedAndOnlyReturnedDocumentIsRemoved() {
        FileSaveTestHooks.safCreator = { create("today.md.txt") }
        repeat(2) {
            val result = FileUtil.saveTextResult(path, "note")
            assertEquals(FileSaveStatus.WRITE_FAILED, result.status)
            assertEquals("saf_created_name_mismatch", result.diagnostic)
        }
        assertTrue(writes.isEmpty())
        assertTrue(documents.isEmpty())
        assertTrue(FileUtil.pendingSaveRecoveries().none { it.path == path })
    }

    @Test fun failedCleanupRetainsActualUriAndBlocksRepeatedCreation() {
        deleteAllowed = false
        FileSaveTestHooks.safCreator = { create("today.md (1).txt") }
        val result = FileUtil.saveTextResult(path, "note")
        assertEquals(FileSaveStatus.RECOVERY_FAILED, result.status)
        val pending = FileUtil.pendingSaveRecoveries().single { it.path == path }
        assertEquals("safdoc://created-1", pending.createdPath)
        assertEquals(SafeRecoveryState.RECOVERY_REQUIRED, pending.safeState)
        assertEquals(FileSaveStatus.RECOVERY_FAILED, FileUtil.saveTextResult(path, "retry").status)
        assertEquals(1, sequence)
    }

    @Test fun concurrentCreatorIsNotOverwrittenOrDeleted() {
        FileSaveTestHooks.safCreator = {
            val created = create("today.md", linkRequestedPath = false)
            documents[path] = Doc("external-id", "external content".toByteArray())
            created
        }
        assertEquals(FileSaveStatus.CONFLICT, FileUtil.saveTextResult(path, "local").status)
        assertTrue(writes.isEmpty())
        assertEquals("external content", documents.getValue(path).bytes.toString(Charsets.UTF_8))
        assertFalse(documents.containsKey("safdoc://created-1"))
    }

    @Test fun failureBeforeFirstByteCleansUpTheOwnedEmptyDocument() {
        FileSaveTestHooks.targetWriter = { _, _ -> throw IOException("provider unavailable") }
        assertEquals(FileSaveStatus.WRITE_FAILED, FileUtil.saveTextResult(path, "note").status)
        assertTrue(documents.isEmpty())
    }

    @Test fun unknownPartialDataIsKeptForRecovery() {
        FileSaveTestHooks.targetWriter = { target, _ ->
            documents.getValue(target).bytes = "unknown partial".toByteArray()
            throw IOException("interrupted")
        }
        assertEquals(FileSaveStatus.RECOVERY_FAILED, FileUtil.saveTextResult(path, "expected").status)
        assertEquals("unknown partial", documents.getValue(path).bytes.toString(Charsets.UTF_8))
        assertEquals(SafeRecoveryState.RECOVERY_REQUIRED, FileUtil.pendingSaveRecoveries().single { it.path == path }.safeState)
    }
}
