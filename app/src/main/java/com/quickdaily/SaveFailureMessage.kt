package com.quickdaily

import com.quickdaily.util.FileSaveResult
import com.quickdaily.util.FileSaveStatus

internal fun FileSaveResult.failureMessage(): UiText = UiText.Resource(when {
    exceptionType == "SecurityException" -> R.string.qd_editor_save_failed_permission
    status == FileSaveStatus.RECOVERY_FAILED -> R.string.qd_save_recovery_required
    diagnostic == "saf_created_name_mismatch" -> R.string.qd_save_filename_changed
    status == FileSaveStatus.CONFLICT -> R.string.qd_editor_external_change
    status == FileSaveStatus.RECOVERED -> R.string.qd_editor_save_incomplete
    status == FileSaveStatus.BACKUP_FAILED -> R.string.qd_save_backup_failed
    status == FileSaveStatus.VERIFY_FAILED -> R.string.qd_save_verification_failed
    diagnostic == "saf_create_failed" -> R.string.qd_save_create_failed
    else -> R.string.qd_editor_save_failed_generic
})
