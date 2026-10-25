package com.quickdaily

import com.quickdaily.util.FileFingerprint
import com.quickdaily.util.FileSaveBackend
import com.quickdaily.util.FileSaveResult
import com.quickdaily.util.FileSaveStatus
import org.junit.Assert.*
import org.junit.Test

class SaveRecoveryPolicyTest {
    @Test fun activityAndServiceUseTheSameReceiptForTrimmedInput() {
        val service = FloatingNoteSaveRequest(" note \n", emptyList(), emptyList(), null, "vault", "vault/Daily/today.md")
        assertEquals(service.signature, service.copy(text = "note").signature)
    }

    @Test fun deliveredWriteIsRecognisedAfterWindowOrProcessLoss() {
        val saved = FileFingerprint.fromBytes("saved".toByteArray())
        assertEquals(ReceiptDecision.SAVED, ReceiptPolicy.decide("missing", saved.sha256, saved, true))
        assertEquals(ReceiptDecision.READY, ReceiptPolicy.decide("missing", saved.sha256, saved, false))
    }

    @Test fun unchangedTargetCanRetryButExternalChangesRequireVerification() {
        val before = FileFingerprint.fromBytes("before".toByteArray())
        val external = FileFingerprint.fromBytes("external".toByteArray())
        assertEquals(ReceiptDecision.READY, ReceiptPolicy.decide(before.sha256, "expected", before, true))
        assertEquals(ReceiptDecision.READY, ReceiptPolicy.decide("missing", "expected", FileFingerprint(false, 0, "", 0), true))
        assertEquals(ReceiptDecision.UNCERTAIN, ReceiptPolicy.decide(before.sha256, "expected", external, true))
    }

    @Test fun nonPermissionFailuresAreNotPresentedAsPermissionErrors() {
        fun result(status: FileSaveStatus, diagnostic: String? = null, exception: String? = null) =
            FileSaveResult(status, FileSaveBackend.SAF, "key", diagnostic = diagnostic, exceptionType = exception).failureMessage()
        assertEquals(UiText.Resource(R.string.qd_save_filename_changed), result(FileSaveStatus.WRITE_FAILED, "saf_created_name_mismatch"))
        assertEquals(UiText.Resource(R.string.qd_save_verification_failed), result(FileSaveStatus.VERIFY_FAILED))
        assertEquals(UiText.Resource(R.string.qd_editor_save_failed_permission), result(FileSaveStatus.WRITE_FAILED, exception = "SecurityException"))
        assertEquals(UiText.Resource(R.string.qd_save_recovery_required), result(FileSaveStatus.RECOVERY_FAILED))
    }
}
