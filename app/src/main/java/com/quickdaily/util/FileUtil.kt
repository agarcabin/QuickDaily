package com.quickdaily.util

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

data class FileFingerprint(
    val exists: Boolean,
    val length: Long,
    val sha256: String,
    val lastModified: Long,
    /** Physical fileKey or SAF document identity when the backend exposes one. */
    val identity: String? = null,
) {
    fun hasSameContentAs(other: FileFingerprint?): Boolean =
        other != null && exists == other.exists && length == other.length && sha256 == other.sha256

    fun hasSameIdentityAs(other: FileFingerprint?): Boolean =
        identity != null && other?.identity != null && identity == other.identity

    fun hasSameContentAndIdentityAs(other: FileFingerprint?): Boolean =
        hasSameContentAs(other) &&
            (identity == null || other?.identity == null || identity == other.identity)

    companion object {
        fun fromBytes(bytes: ByteArray, lastModified: Long = 0L, identity: String? = null): FileFingerprint =
            FileFingerprint(
                exists = true,
                length = bytes.size.toLong(),
                sha256 = sha256(bytes),
                lastModified = lastModified,
                identity = identity,
            )

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class FileReadSnapshot(
    val result: ReadResult,
    val fingerprint: FileFingerprint?,
)

/** Test seams for deterministic partial-write and post-write verification tests. */
internal object FileSaveTestHooks {
    @Volatile
    var targetWriter: ((path: String, bytes: ByteArray) -> Unit)? = null

    @Volatile
    var beforeTargetWrite: (() -> Unit)? = null

    @Volatile
    var afterTargetWrite: (() -> Unit)? = null

    @Volatile
    var backupWriter: ((file: File, bytes: ByteArray) -> Unit)? = null

    @Volatile
    var abortAfterJournalStage: FileSaveStage? = null

    fun clear() {
        targetWriter = null
        beforeTargetWrite = null
        afterTargetWrite = null
        backupWriter = null
        abortAfterJournalStage = null
    }
}

/**
 * All user-facing Markdown and attachment writes go through this coordinator.
 * Existing files are copied to a validated recovery backup and then written in
 * place so the path/document identity is not replaced during normal saves.
 */
object FileUtil {
    private const val BACKUP_DIRECTORY = "quickdaily-save-backups"
    private const val JOURNAL_DIRECTORY = "quickdaily-save-journal"

    private val mutationLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun acquirePathMutation(path: String): AutoCloseable {
        val key = if (isSafBackedPath(path)) {
            path.trim()
        } else {
            runCatching { File(path).canonicalPath }
                .getOrElse { File(path).absolutePath }
        }
        val lock = mutationLocks.computeIfAbsent(key) { ReentrantLock() }
        lock.lock()
        return AutoCloseable { lock.unlock() }
    }

    /** Serializes a complete read-modify-write section by canonical file path. */
    fun <T> withPathMutation(path: String, block: () -> T): T {
        val guard = acquirePathMutation(path)
        return try {
            block()
        } finally {
            guard.close()
        }
    }

    fun read(path: String): String {
        return when (val result = readResult(path)) {
            is ReadResult.Success -> result.content
            else -> ""
        }
    }

    fun readOrNull(path: String): String? {
        return when (val result = readResult(path)) {
            is ReadResult.Success -> result.content
            ReadResult.NotFound -> null
            is ReadResult.Error -> null
        }
    }

    /** Compatibility boolean API. A true value means TARGET_VERIFIED was reached. */
    fun write(path: String, content: String): Boolean = saveTextResult(path, content).succeeded

    fun saveTextResult(
        path: String,
        content: String,
        expectedFingerprint: FileFingerprint? = null,
    ): FileSaveResult = saveBytesResult(path, content.toByteArray(Charsets.UTF_8), expectedFingerprint)

    /**
     * Saves bytes with conflict detection, backup validation, in-place writing
     * and post-write byte verification. When [rejectDifferentExisting] is true
     * (attachments), an existing file with different bytes is never overwritten.
     */
    fun saveBytesResult(
        path: String,
        bytes: ByteArray,
        expectedFingerprint: FileFingerprint? = null,
        rejectDifferentExisting: Boolean = false,
    ): FileSaveResult = withPathMutation(path) {
        saveBytesLocked(path, bytes, expectedFingerprint, rejectDifferentExisting)
    }

    /** Compatibility result API retained for callers that only need Boolean-like status. */
    fun writeResult(path: String, content: String): WriteResult {
        val result = saveTextResult(path, content)
        return if (result.succeeded) {
            WriteResult.Success
        } else {
            WriteResult.Error(IllegalStateException(result.diagnostic ?: result.status.name))
        }
    }

    fun readResult(path: String): ReadResult {
        if (isSafBackedPath(path)) {
            val context = StorageContextHolder.get()
                ?: return ReadResult.Error(IllegalStateException("SAF context unavailable"))
            if (!VaultStorage.exists(context, path)) return ReadResult.NotFound
            return VaultStorage.readText(context, path)?.let(ReadResult::Success)
                ?: ReadResult.Error(IllegalStateException("SAF document cannot be read"))
        }
        val file = File(path)
        if (!file.exists()) return ReadResult.NotFound
        return try {
            ReadResult.Success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            ReadResult.Error(e)
        }
    }

    /** Reads content and its fingerprint while sharing the same path lock as mutations. */
    fun readStableSnapshot(path: String, maxAttempts: Int = 3): FileReadSnapshot =
        withPathMutation(path) {
            var lastFingerprint: FileFingerprint? = null
            repeat(maxAttempts.coerceAtLeast(1)) {
                val before = fingerprint(path)
                val result = readResult(path)
                val after = fingerprint(path)
                lastFingerprint = after
                if (before != null && after != null && before.hasSameContentAs(after)) {
                    return@withPathMutation FileReadSnapshot(result, after)
                }
            }
            FileReadSnapshot(
                result = ReadResult.Error(IOException("File changed while reading")),
                fingerprint = lastFingerprint,
            )
        }

    /** Reads a stable content fingerprint for external-edit detection. */
    fun fingerprint(path: String): FileFingerprint? {
        if (isSafBackedPath(path)) {
            return StorageContextHolder.get()?.let { VaultStorage.fingerprint(it, path) }
        }
        val file = File(path)
        if (!file.exists()) return FileFingerprint(false, 0L, "", 0L, null)
        return runCatching {
            val initialLength = file.length()
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val finalLength = file.length()
            if (initialLength != finalLength) return@runCatching null
            val identity = runCatching {
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).fileKey()?.toString()
            }.getOrNull()
            FileFingerprint(
                exists = true,
                length = finalLength,
                sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
                lastModified = file.lastModified(),
                identity = identity,
            )
        }.getOrNull()
    }

    internal fun readBytes(path: String): ByteArray? {
        if (isSafBackedPath(path)) {
            return StorageContextHolder.get()?.let { VaultStorage.readBytes(it, path) }
        }
        return runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    fun exists(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.exists(it, path) } == true
    } else {
        File(path).exists()
    }

    fun isDirectory(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.isDirectory(it, path) } == true
    } else {
        File(path).isDirectory
    }

    /** 返回文件最后修改时间戳；不存在返回 0 */
    fun canWrite(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.canWrite(it, path) } == true
    } else {
        runCatching { File(path).isFile && File(path).canWrite() }.getOrDefault(false)
    }

    fun lastModified(path: String): Long = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.lastModified(it, path) } ?: 0L
    } else {
        runCatching { File(path).lastModified() }.getOrDefault(0L)
    }

    /**
     * Inspects durable save journals after process start. Ambiguous states are
     * deliberately left for a user-visible recovery action; no target is
     * overwritten or deleted automatically.
     */
    fun pendingSaveRecoveries(): List<PendingSaveRecovery> {
        val root = journalRoot()
        if (!root.isDirectory) return emptyList()
        return root.listFiles { file -> file.extension == "state" }
            .orEmpty()
            .mapNotNull { file -> inspectJournal(file) }
    }

    /** Called by Application startup so interrupted writes are discoverable. */
    fun recoverPendingSaves(): List<PendingSaveRecovery> {
        val recoveries = pendingSaveRecoveries()
        recoveries.filter { it.safeState != SafeRecoveryState.RECOVERY_REQUIRED }
            .forEach { deleteJournal(it.targetKey) }
        if (recoveries.isNotEmpty()) {
            runCatching {
                Log.w(
                    "QuickDaily",
                    "save journal inspected count=${recoveries.size} " +
                        "ambiguous=${recoveries.count { it.safeState == SafeRecoveryState.RECOVERY_REQUIRED }}",
                )
            }
        }
        return recoveries
    }

    /**
     * Scans old hidden backup/temp naming patterns without changing any file.
     * The caller must show the result and request an explicit destination before
     * calling [restoreLegacySaveArtifact].
     */
    fun scanLegacySaveArtifacts(rootPath: String): List<LegacySaveArtifact> {
        val result = mutableListOf<LegacySaveArtifact>()
        if (isSafBackedPath(rootPath)) {
            val context = StorageContextHolder.get() ?: return emptyList()
            val parsed = SafVirtualPath.parse(rootPath) ?: return emptyList()
            val root = VaultStorage.documentForPath(context, rootPath) ?: return emptyList()
            scanLegacySaf(context, root, parsed.rootUri, parsed.relativePath, 0, result)
        } else {
            scanLegacyPhysical(File(rootPath), 0, result)
        }
        return result.sortedBy { it.path }
    }

    /** Explicit, non-overwriting recovery copy for a user-selected old artifact. */
    fun restoreLegacySaveArtifact(sourcePath: String, destinationPath: String): FileSaveResult? {
        val bytes = readBytes(sourcePath) ?: return null
        return saveBytesResult(
            path = destinationPath,
            bytes = bytes,
            rejectDifferentExisting = true,
        )
    }

    private fun saveBytesLocked(
        path: String,
        bytes: ByteArray,
        expectedFingerprint: FileFingerprint?,
        rejectDifferentExisting: Boolean,
    ): FileSaveResult {
        val backend = backendFor(path)
        val key = targetKey(path)
        val expected = FileFingerprint.fromBytes(bytes)
        val before = fingerprint(path)
            ?: return failure(
                FileSaveStatus.BACKUP_FAILED,
                backend,
                key,
                before = null,
                expected = expected,
                diagnostic = "fingerprint_unavailable",
            )

        if (expectedFingerprint != null && !before.hasSameContentAndIdentityAs(expectedFingerprint)) {
            return failure(
                FileSaveStatus.CONFLICT,
                backend,
                key,
                before = before,
                expected = expected,
                diagnostic = "precondition_changed",
            )
        }

        if (before.exists && before.hasSameContentAs(expected)) {
            return FileSaveResult(
                status = FileSaveStatus.SAVED,
                backend = backend,
                targetKey = key,
                stage = FileSaveStage.COMPLETED,
                before = before,
                expected = expected,
                after = before,
                identityPreserved = true,
                diagnostic = "already_equal",
            )
        }

        if (rejectDifferentExisting && before.exists) {
            return failure(
                FileSaveStatus.CONFLICT,
                backend,
                key,
                before = before,
                expected = expected,
                diagnostic = "attachment_target_contains_different_bytes",
            )
        }

        val oldBytes = if (before.exists) readBytes(path) else null
        if (before.exists && oldBytes == null) {
            return failure(
                FileSaveStatus.BACKUP_FAILED,
                backend,
                key,
                before = before,
                expected = expected,
                diagnostic = "old_bytes_unreadable",
            )
        }
        if (before.exists && !before.hasSameContentAs(FileFingerprint.fromBytes(oldBytes!!))) {
            return failure(
                FileSaveStatus.CONFLICT,
                backend,
                key,
                before = before,
                expected = expected,
                diagnostic = "read_snapshot_changed",
            )
        }

        val backup = oldBytes?.let(::writeValidatedBackup)
        if (before.exists && backup == null) {
            return failure(
                FileSaveStatus.BACKUP_FAILED,
                backend,
                key,
                before = before,
                expected = expected,
                diagnostic = "backup_validation_failed",
            )
        }

        val journal = JournalRecord(
            targetKey = key,
            path = path,
            stage = FileSaveStage.BACKUP_VERIFIED,
            before = before,
            expected = expected,
            backupPath = backup?.first?.path,
        )
        if (!writeJournal(journal)) {
            return failure(
                FileSaveStatus.BACKUP_FAILED,
                backend,
                key,
                before = before,
                expected = expected,
                backup = backup?.second,
                backupPath = backup?.first?.path,
                diagnostic = "save_journal_unavailable",
            )
        }

        val preWrite = fingerprint(path)
        if (preWrite == null || !preWrite.hasSameContentAndIdentityAs(before)) {
            deleteJournal(key)
            return failure(
                FileSaveStatus.CONFLICT,
                backend,
                key,
                before = preWrite ?: before,
                expected = expected,
                backup = backup?.second,
                backupPath = backup?.first?.path,
                diagnostic = "changed_before_write",
            )
        }

        if (!writeJournal(journal.copy(stage = FileSaveStage.WRITE_STARTED))) {
            deleteJournal(key)
            return failure(
                FileSaveStatus.BACKUP_FAILED,
                backend,
                key,
                before = before,
                expected = expected,
                backup = backup?.second,
                backupPath = backup?.first?.path,
                diagnostic = "save_journal_unavailable_before_write",
            )
        }
        return if (before.exists) {
            saveExistingBytes(
                path = path,
                backend = backend,
                key = key,
                before = before,
                expected = expected,
                bytes = bytes,
                backup = backup!!,
            )
        } else {
            saveNewBytes(
                path = path,
                backend = backend,
                key = key,
                before = before,
                expected = expected,
                bytes = bytes,
            )
        }
    }

    private fun scanLegacyPhysical(
        directory: File,
        depth: Int,
        result: MutableList<LegacySaveArtifact>,
    ) {
        if (!directory.isDirectory || depth > 50) return
        directory.listFiles().orEmpty().forEach { child ->
            if (child.isDirectory) {
                scanLegacyPhysical(child, depth + 1, result)
            } else {
                legacyArtifactKind(child.name)?.let { kind ->
                    result += LegacySaveArtifact(child.path, child.name, kind)
                }
            }
        }
    }

    private fun scanLegacySaf(
        context: android.content.Context,
        directory: DocumentFile,
        treeUri: android.net.Uri,
        relativeDirectory: String,
        depth: Int,
        result: MutableList<LegacySaveArtifact>,
    ) {
        if (depth > 50) return
        directory.listFiles().forEach { child ->
            val name = child.name.orEmpty()
            if (child.isDirectory) {
                scanLegacySaf(
                    context,
                    child,
                    treeUri,
                    listOf(relativeDirectory, name).filter(String::isNotBlank).joinToString("/"),
                    depth + 1,
                    result,
                )
            } else {
                legacyArtifactKind(name)?.let { kind ->
                    val path = SafDocumentPath.forDocumentUri(child.uri, treeUri) ?: return@let
                    result += LegacySaveArtifact(path, name, kind)
                }
            }
        }
    }

    private fun legacyArtifactKind(name: String): LegacySaveArtifactKind? = when {
        name.endsWith(".quickdaily.bak", ignoreCase = true) -> LegacySaveArtifactKind.BACKUP
        name.endsWith(".quickdaily.tmp", ignoreCase = true) -> LegacySaveArtifactKind.TEMPORARY
        else -> null
    }

    private fun saveExistingBytes(
        path: String,
        backend: FileSaveBackend,
        key: String,
        before: FileFingerprint,
        expected: FileFingerprint,
        bytes: ByteArray,
        backup: Pair<File, FileFingerprint>,
    ): FileSaveResult {
        val writeError = runCatching { writeInPlace(path, bytes) }.exceptionOrNull()
        val after = fingerprint(path)
        val verified = writeError == null &&
            after?.hasSameContentAs(expected) == true &&
            identityStable(before, after)
        if (verified) {
            val targetVerifiedJournaled = writeJournal(
                JournalRecord(key, path, FileSaveStage.TARGET_VERIFIED, before, expected, backup.first.path),
            )
            val completedJournaled = targetVerifiedJournaled && writeJournal(
                JournalRecord(key, path, FileSaveStage.COMPLETED, before, expected, backup.first.path),
            )
            if (!targetVerifiedJournaled || !completedJournaled) {
                return FileSaveResult(
                    status = FileSaveStatus.RECOVERY_FAILED,
                    backend = backend,
                    targetKey = key,
                    stage = FileSaveStage.TARGET_VERIFIED,
                    before = before,
                    expected = expected,
                    after = after,
                    backup = backup.second,
                    backupPath = backup.first.path,
                    identityPreserved = identityPreserved(before, after),
                    diagnostic = "target_verified_but_journal_failed",
                )
            }
            deleteJournal(key)
            return FileSaveResult(
                status = FileSaveStatus.SAVED,
                backend = backend,
                targetKey = key,
                stage = FileSaveStage.TARGET_VERIFIED,
                before = before,
                expected = expected,
                after = after,
                backup = backup.second,
                backupPath = backup.first.path,
                identityPreserved = identityPreserved(before, after),
            )
        }

        writeJournal(
            JournalRecord(key, path, FileSaveStage.RECOVERY_STARTED, before, expected, backup.first.path),
        )
        val restoreError = runCatching {
            writeInPlace(path, backup.first.readBytes(), useTestHooks = false)
        }.exceptionOrNull()
        val restored = fingerprint(path)
        if (restoreError == null && restored?.hasSameContentAs(before) == true) {
            deleteJournal(key)
            return FileSaveResult(
                status = FileSaveStatus.RECOVERED,
                backend = backend,
                targetKey = key,
                stage = FileSaveStage.RECOVERY_STARTED,
                before = before,
                expected = expected,
                after = restored,
                backup = backup.second,
                backupPath = backup.first.path,
                identityPreserved = identityPreserved(before, restored),
                exceptionType = (writeError ?: restoreError)?.javaClass?.simpleName,
                diagnostic = if (writeError == null) "post_write_verification_failed" else "write_failed_and_restored",
            )
        }
        return FileSaveResult(
            status = FileSaveStatus.RECOVERY_FAILED,
            backend = backend,
            targetKey = key,
            stage = FileSaveStage.RECOVERY_STARTED,
            before = before,
            expected = expected,
            after = restored,
            backup = backup.second,
            backupPath = backup.first.path,
            identityPreserved = identityPreserved(before, restored),
            exceptionType = (writeError ?: restoreError)?.javaClass?.simpleName,
            diagnostic = "write_failed_and_restore_failed",
        )
    }

    private fun saveNewBytes(
        path: String,
        backend: FileSaveBackend,
        key: String,
        before: FileFingerprint,
        expected: FileFingerprint,
        bytes: ByteArray,
    ): FileSaveResult {
        var createdPhysical = false
        var createdSaf = false
        val context = StorageContextHolder.get()
        try {
            if (backend == FileSaveBackend.SAF) {
                val safContext = context
                    ?: return failure(FileSaveStatus.UNSUPPORTED, backend, key, before, expected, diagnostic = "saf_context_unavailable")
                if (VaultStorage.documentForPath(safContext, path) != null) {
                    deleteJournal(key)
                    return failure(FileSaveStatus.CONFLICT, backend, key, before, expected, diagnostic = "target_created_concurrently")
                }
                if (VaultStorage.createDocumentForPath(safContext, path) == null) {
                    deleteJournal(key)
                    return failure(FileSaveStatus.WRITE_FAILED, backend, key, before, expected, diagnostic = "saf_create_failed")
                }
                createdSaf = true
            } else {
                val target = File(path).absoluteFile
                target.parentFile?.mkdirs()
                if (!target.createNewFile()) {
                    deleteJournal(key)
                    return failure(FileSaveStatus.CONFLICT, backend, key, before, expected, diagnostic = "target_created_concurrently")
                }
                createdPhysical = true
            }

            val error = runCatching { writeInPlace(path, bytes) }.exceptionOrNull()
            val after = fingerprint(path)
            if (error == null && after?.hasSameContentAs(expected) == true) {
                val targetVerifiedJournaled = writeJournal(
                    JournalRecord(key, path, FileSaveStage.TARGET_VERIFIED, before, expected, null),
                )
                val completedJournaled = targetVerifiedJournaled && writeJournal(
                    JournalRecord(key, path, FileSaveStage.COMPLETED, before, expected, null),
                )
                if (!targetVerifiedJournaled || !completedJournaled) {
                    return FileSaveResult(
                        status = FileSaveStatus.RECOVERY_FAILED,
                        backend = backend,
                        targetKey = key,
                        stage = FileSaveStage.TARGET_VERIFIED,
                        before = before,
                        expected = expected,
                        after = after,
                        identityPreserved = false,
                        diagnostic = "new_target_verified_but_journal_failed",
                    )
                }
                deleteJournal(key)
                return FileSaveResult(
                    status = FileSaveStatus.SAVED,
                    backend = backend,
                    targetKey = key,
                    stage = FileSaveStage.TARGET_VERIFIED,
                    before = before,
                    expected = expected,
                    after = after,
                    identityPreserved = false,
                )
            }
            if (createdPhysical) File(path).delete()
            if (createdSaf) context?.let { VaultStorage.deleteDocumentForPath(it, path) }
            deleteJournal(key)
            return failure(
                if (error == null) FileSaveStatus.VERIFY_FAILED else FileSaveStatus.WRITE_FAILED,
                backend,
                key,
                before,
                expected,
                after = after,
                exceptionType = error?.javaClass?.simpleName,
                diagnostic = "new_target_write_failed",
            )
        } catch (error: Exception) {
            if (createdPhysical) File(path).delete()
            if (createdSaf) context?.let { VaultStorage.deleteDocumentForPath(it, path) }
            deleteJournal(key)
            return failure(
                FileSaveStatus.WRITE_FAILED,
                backend,
                key,
                before,
                expected,
                exceptionType = error.javaClass.simpleName,
                diagnostic = "new_target_write_failed",
            )
        }
    }

    private fun writeInPlace(path: String, bytes: ByteArray, useTestHooks: Boolean = true) {
        if (useTestHooks) FileSaveTestHooks.beforeTargetWrite?.invoke()
        val override = if (useTestHooks) FileSaveTestHooks.targetWriter else null
        if (override != null) {
            override(path, bytes)
        } else if (isSafBackedPath(path)) {
            val context = StorageContextHolder.get()
                ?: throw IllegalStateException("SAF context unavailable")
            check(VaultStorage.writeExistingBytes(context, path, bytes)) {
                "SAF in-place write failed"
            }
        } else {
            val target = File(path).absoluteFile
            FileOutputStream(target, false).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
        if (useTestHooks) FileSaveTestHooks.afterTargetWrite?.invoke()
    }

    private fun writeValidatedBackup(bytes: ByteArray): Pair<File, FileFingerprint>? {
        val root = backupRoot()
        if (!root.exists() && !root.mkdirs()) return null
        val suffix = "${System.currentTimeMillis()}-${System.nanoTime()}"
        val backup = File(root, "${FileFingerprint.sha256(bytes).take(24)}-$suffix.bak")
        val temporary = File(root, ".${backup.name}.tmp")
        return runCatching {
            FileSaveTestHooks.backupWriter?.invoke(temporary, bytes) ?: writeDurable(temporary, bytes)
            val copied = temporary.readBytes()
            val fingerprint = FileFingerprint.fromBytes(copied)
            check(fingerprint.hasSameContentAs(FileFingerprint.fromBytes(bytes)))
            runCatching {
                Files.move(
                    temporary.toPath(),
                    backup.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            backup to fingerprint
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    private fun writeDurable(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun failure(
        status: FileSaveStatus,
        backend: FileSaveBackend,
        key: String,
        before: FileFingerprint?,
        expected: FileFingerprint,
        after: FileFingerprint? = null,
        backup: FileFingerprint? = null,
        backupPath: String? = null,
        exceptionType: String? = null,
        diagnostic: String? = null,
    ): FileSaveResult = FileSaveResult(
        status = status,
        backend = backend,
        targetKey = key,
        before = before,
        expected = expected,
        after = after,
        backup = backup,
        backupPath = backupPath,
        exceptionType = exceptionType,
        diagnostic = diagnostic,
    )

    private fun identityPreserved(before: FileFingerprint?, after: FileFingerprint?): Boolean? = when {
        before?.identity == null || after?.identity == null -> null
        else -> before.identity == after.identity
    }

    private fun identityStable(before: FileFingerprint, after: FileFingerprint?): Boolean =
        before.identity == null || after?.identity == null || before.identity == after.identity

    private data class JournalRecord(
        val targetKey: String,
        val path: String,
        val stage: FileSaveStage,
        val before: FileFingerprint,
        val expected: FileFingerprint,
        val backupPath: String?,
    )

    private fun writeJournal(record: JournalRecord): Boolean {
        val root = journalRoot()
        if (!root.exists() && !root.mkdirs()) return false
        val destination = File(root, "${record.targetKey}.state")
        val temporary = File(root, ".${record.targetKey}.state.tmp")
        val written = runCatching {
            val properties = Properties().apply {
                setProperty("targetKey", record.targetKey)
                setProperty("path", record.path)
                setProperty("stage", record.stage.name)
                setProperty("backupPath", record.backupPath.orEmpty())
                putFingerprint(this, "before", record.before)
                putFingerprint(this, "expected", record.expected)
            }
            FileOutputStream(temporary, false).use { output ->
                properties.store(output, "QuickDaily coordinated save")
                output.flush()
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
        }.getOrElse {
            temporary.delete()
            runCatching { Log.w("QuickDaily", "save journal write failed stage=${record.stage.name}") }
            false
        }
        if (written && FileSaveTestHooks.abortAfterJournalStage == record.stage) {
            FileSaveTestHooks.abortAfterJournalStage = null
            throw IllegalStateException("injected process stop after ${record.stage.name}")
        }
        return written
    }

    private fun inspectJournal(file: File): PendingSaveRecovery? {
        return runCatching {
            val properties = Properties()
            file.inputStream().use(properties::load)
            val path = properties.getProperty("path") ?: return@runCatching null
            val targetKey = properties.getProperty("targetKey") ?: file.nameWithoutExtension
            val stage = properties.getProperty("stage")?.let(FileSaveStage::valueOf)
                ?: return@runCatching null
            val before = readFingerprint(properties, "before") ?: return@runCatching null
            val expected = readFingerprint(properties, "expected") ?: return@runCatching null
            val current = fingerprint(path)
            val safeState = when {
                current?.hasSameContentAs(expected) == true -> SafeRecoveryState.COMPLETED
                current?.hasSameContentAs(before) == true -> SafeRecoveryState.NOT_STARTED
                else -> SafeRecoveryState.RECOVERY_REQUIRED
            }
            PendingSaveRecovery(
                targetKey = targetKey,
                path = path,
                stage = stage,
                safeState = safeState,
                targetFingerprint = current,
                expectedFingerprint = expected,
                backupPath = properties.getProperty("backupPath").orEmpty().ifBlank { null },
            )
        }.getOrNull()
    }

    private fun putFingerprint(properties: Properties, prefix: String, fingerprint: FileFingerprint) {
        properties.setProperty("$prefix.exists", fingerprint.exists.toString())
        properties.setProperty("$prefix.length", fingerprint.length.toString())
        properties.setProperty("$prefix.sha256", fingerprint.sha256)
        properties.setProperty("$prefix.lastModified", fingerprint.lastModified.toString())
        properties.setProperty("$prefix.identity", fingerprint.identity.orEmpty())
    }

    private fun readFingerprint(properties: Properties, prefix: String): FileFingerprint? {
        val exists = properties.getProperty("$prefix.exists")?.toBooleanStrictOrNull() ?: return null
        val length = properties.getProperty("$prefix.length")?.toLongOrNull() ?: return null
        val sha256 = properties.getProperty("$prefix.sha256") ?: return null
        val lastModified = properties.getProperty("$prefix.lastModified")?.toLongOrNull() ?: return null
        return FileFingerprint(
            exists = exists,
            length = length,
            sha256 = sha256,
            lastModified = lastModified,
            identity = properties.getProperty("$prefix.identity").orEmpty().ifBlank { null },
        )
    }

    private fun deleteJournal(key: String) {
        File(journalRoot(), "$key.state").delete()
    }

    private fun backendFor(path: String): FileSaveBackend =
        if (isSafBackedPath(path)) FileSaveBackend.SAF else FileSaveBackend.PHYSICAL

    private fun targetKey(path: String): String = FileFingerprint.sha256(
        path.trim().toByteArray(Charsets.UTF_8),
    ).take(32)

    private fun backupRoot(): File = storageRoot(BACKUP_DIRECTORY)

    private fun journalRoot(): File = storageRoot(JOURNAL_DIRECTORY)

    private fun storageRoot(name: String): File {
        val contextRoot = StorageContextHolder.get()?.filesDir
        return if (contextRoot != null) {
            File(contextRoot, name)
        } else {
            File(System.getProperty("java.io.tmpdir"), name)
        }
    }

    private fun isSafBackedPath(path: String): Boolean =
        SafVirtualPath.isSafPath(path) || SafDocumentPath.isPath(path)
}
