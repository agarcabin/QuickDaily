package com.quickdaily

import android.app.AlertDialog
import android.content.Context
import android.view.WindowManager

internal fun showFloatingNoteRecovery(
    context: Context,
    message: UiText,
    overlay: Boolean = false,
    onKeepDraft: () -> Unit,
    onVerifiedRetry: (() -> Unit)? = null,
): AlertDialog? {
    val builder = AlertDialog.Builder(context)
        .setMessage(message.resolve(context))
        .setPositiveButton(R.string.qd_save_keep_draft_close) { _, _ -> onKeepDraft() }
        .setNegativeButton(R.string.qd_save_continue_editing, null)
    if (onVerifiedRetry != null) {
        builder.setNeutralButton(R.string.qd_save_verified_retry) { _, _ -> onVerifiedRetry() }
    }
    val dialog = builder.create()
    if (overlay) dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    return runCatching { dialog.show(); dialog }.getOrElse {
        android.widget.Toast.makeText(context, message.resolve(context), android.widget.Toast.LENGTH_LONG).show()
        null
    }
}
