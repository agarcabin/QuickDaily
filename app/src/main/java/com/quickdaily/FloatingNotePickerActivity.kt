package com.quickdaily

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Short-lived bridge because a Service cannot own Activity Result launchers. */
class FloatingNotePickerActivity : ComponentActivity() {
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) FloatingNoteDraftStore.addImages(this, uris)
        refreshOverlayAndFinish()
    }

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            FloatingNoteDraftStore.addAttachment(this, it)
        }
        refreshOverlayAndFinish()
    }

    private val pagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val path = uri?.let { TaskWidgetConfigStore.filePathFromUri(this, it) }
        if (path != null) {
            FloatingNoteDraftStore.setTarget(this, path)
        }
        refreshOverlayAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_ATTACHMENT -> attachmentPicker.launch(arrayOf("*/*"))
            MODE_CUSTOM_PAGE -> pagePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
            else -> imagePicker.launch("image/*")
        }
    }

    private fun refreshOverlayAndFinish() {
        runCatching { startService(FloatingNoteService.refreshIntent(this)) }
        finish()
    }

    companion object {
        const val EXTRA_MODE = "picker_mode"
        const val MODE_IMAGES = "images"
        const val MODE_ATTACHMENT = "attachment"
        const val MODE_CUSTOM_PAGE = "custom_page"
    }
}
