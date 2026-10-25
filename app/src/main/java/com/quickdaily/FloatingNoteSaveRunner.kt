package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileFingerprint
import com.quickdaily.util.FileUtil
import com.quickdaily.util.VaultPathUtil
import com.quickdaily.util.VaultStoragePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal data class FloatingNoteSaveRequest(
    val text: String,
    val images: List<Uri>,
    val attachments: List<Uri>,
    val target: String?,
    val vault: String,
    val path: String,
    val rules: ObsidianRules? = null,
) {
    fun matches(text: String, images: List<Uri>, attachments: List<Uri>, target: String?): Boolean =
        this.text == text && this.images == images && this.attachments == attachments && this.target == target
    val signature: String get() = FileFingerprint.sha256(
        listOf(text.trim(), images.joinToString("\u0000"), attachments.joinToString("\u0000"), path)
            .joinToString("\u0001").toByteArray(Charsets.UTF_8),
    )

    companion object {
        fun capture(context: Context, text: String, images: List<Uri>, attachments: List<Uri>, target: String?): FloatingNoteSaveRequest {
            val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
            val vault = VaultStoragePrefs.current(context).rootPath
            val rules = ObsidianConfigSync.rules(context, vault)
            val path = if (target.isNullOrBlank()) {
                val relative = VaultPathUtil.dailyRelativePath(rules.diaryFolder, DateUtil.todayStr(rules.dateFormat))
                VaultPathUtil.resolveTarget(vault, relative.trimStart('/')).orEmpty()
            } else VaultPathUtil.resolveTarget(vault, target).orEmpty()
            return FloatingNoteSaveRequest(text, images.toList(), attachments.toList(), target, vault, path, rules)
        }
    }
}

/** The write owns its lifetime. Closing a window cancels only the UI observer, never half a write. */
internal object FloatingNoteSaveRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = SingleFlightOperations<String, FloatingNoteSaveRequest, FloatingNoteSaveResult>(scope)

    fun isRunning(path: String): Boolean = operations.isRunning(path)

    fun start(context: Context, request: FloatingNoteSaveRequest): Deferred<FloatingNoteSaveResult> {
        val app = context.applicationContext
        if (request.path.isBlank()) return CompletableDeferred(FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_target_unavailable)))
        return operations.start(request.path, request) {
            try {
                FloatingNoteSaveUseCase(app).save(request.text, request.images, request.attachments, request.target, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                BetaLogger.logException("FloatingNote/Save", "save_use_case_failed", error)
                FloatingNoteSaveResult.Failed(UiText.Resource(
                    if (error is SecurityException) R.string.qd_editor_save_failed_permission else R.string.qd_editor_save_failed_generic,
                ))
            }
        } ?: CompletableDeferred(FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_save_still_running)))
    }
}

/** Durable receipt closes the write-success/UI-delivery gap, including after process death. */
internal class FloatingNoteSaveReceipts(context: Context) {
    private val prefs = context.getSharedPreferences("QuickDailySaveReceipts", Context.MODE_PRIVATE)
    private fun key(path: String) = FileFingerprint.sha256(path.toByteArray(Charsets.UTF_8))

    fun reconcile(path: String, signature: String): ReceiptDecision {
        val key = key(path)
        val expected = prefs.getString("$key.expected", null) ?: return ReceiptDecision.READY
        val before = prefs.getString("$key.before", null)
        val current = FileUtil.fingerprint(path) ?: return ReceiptDecision.UNCERTAIN
        return ReceiptPolicy.decide(before, expected, current, prefs.getString("$key.input", null) == signature)
    }

    fun prepare(path: String, signature: String, before: FileFingerprint, expected: FileFingerprint): Boolean {
        val key = key(path)
        return prefs.edit().putString("$key.before", if (before.exists) before.sha256 else "missing")
            .putString("$key.expected", expected.sha256).putString("$key.input", signature).commit()
    }

    fun acknowledge(path: String) {
        val key = key(path)
        prefs.edit().remove("$key.before").remove("$key.expected").remove("$key.input").apply()
    }
}

internal enum class ReceiptDecision { READY, SAVED, UNCERTAIN }

internal object ReceiptPolicy {
    fun decide(before: String?, expected: String, current: FileFingerprint, sameInput: Boolean): ReceiptDecision = when {
        current.exists && current.sha256 == expected -> if (sameInput) ReceiptDecision.SAVED else ReceiptDecision.READY
        (current.exists && current.sha256 == before) || (!current.exists && before == "missing") -> ReceiptDecision.READY
        else -> ReceiptDecision.UNCERTAIN
    }
}
