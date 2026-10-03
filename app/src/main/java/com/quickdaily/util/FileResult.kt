package com.quickdaily.util

/** 文件读取结果 */
sealed class ReadResult {
    data class Success(val content: String) : ReadResult()
    data object NotFound : ReadResult()
    data class Error(val exception: Exception) : ReadResult()
}

/** 文件写入结果 */
sealed class WriteResult {
    data object Success : WriteResult()
    data class Error(val exception: Exception) : WriteResult()
}

/** Storage backend used by the coordinated save pipeline. */
enum class FileSaveBackend {
    PHYSICAL,
    SAF,
}

/** Durable phases written to the save journal before destructive work starts. */
enum class FileSaveStage {
    BACKUP_VERIFIED,
    WRITE_STARTED,
    TARGET_VERIFIED,
    COMPLETED,
    RECOVERY_STARTED,
}

/** Final state of one coordinated file transaction. */
enum class FileSaveStatus {
    SAVED,
    CONFLICT,
    BACKUP_FAILED,
    WRITE_FAILED,
    VERIFY_FAILED,
    RECOVERED,
    RECOVERY_FAILED,
    UNSUPPORTED,
}

/**
 * Structured result shared by diary, editor, widget and attachment writes.
 * A caller may clear its draft only when [succeeded] is true.
 */
data class FileSaveResult(
    val status: FileSaveStatus,
    val backend: FileSaveBackend,
    val targetKey: String,
    val stage: FileSaveStage? = null,
    val before: FileFingerprint? = null,
    val expected: FileFingerprint? = null,
    val after: FileFingerprint? = null,
    val backup: FileFingerprint? = null,
    val backupPath: String? = null,
    val identityPreserved: Boolean? = null,
    val exceptionType: String? = null,
    val diagnostic: String? = null,
) {
    val succeeded: Boolean
        get() = status == FileSaveStatus.SAVED

    val keepDraft: Boolean
        get() = !succeeded
}

/** Result of inspecting a journal left by a killed process or an interrupted write. */
data class PendingSaveRecovery(
    val targetKey: String,
    val path: String,
    val stage: FileSaveStage,
    val safeState: SafeRecoveryState,
    val targetFingerprint: FileFingerprint?,
    val expectedFingerprint: FileFingerprint,
    val backupPath: String?,
)

enum class SafeRecoveryState {
    COMPLETED,
    NOT_STARTED,
    RECOVERY_REQUIRED,
}

enum class LegacySaveArtifactKind {
    BACKUP,
    TEMPORARY,
}

/** Read-only discovery result for artifacts left by pre-coordinator versions. */
data class LegacySaveArtifact(
    val path: String,
    val displayName: String,
    val kind: LegacySaveArtifactKind,
)
