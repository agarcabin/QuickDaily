package com.quickdaily

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Short-lived bridge because a Service cannot own Activity Result launchers. */
class FloatingNotePickerActivity : ComponentActivity() {
    private var pendingCameraFile: File? = null
    private var pendingCameraUri: Uri? = null
    private var activeTargetPath: String? = null
    private var activeSource: FloatingNoteSource = FloatingNoteSource.SIDEBAR
    private var rememberTarget = true

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            refreshOverlayAndFinish()
            return@registerForActivityResult
        }
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error ->
                BetaLogger.logException("FloatingNote/Picker", "image_permission_failed uri=$uri", error)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val links = EditorImageInsertPolicy.processInSelectionOrder(uris) { uri ->
                runCatching { EditorMediaUtil.imageLink(this@FloatingNotePickerActivity, uri) }
                    .onFailure { error ->
                        BetaLogger.logException("FloatingNote/Picker", "image_process_failed uri=$uri", error)
                    }
                    .getOrNull()
            }
            val successfulLinks = links.filterNotNull()
            withContext(Dispatchers.Main) {
                if (successfulLinks.isNotEmpty()) {
                    FloatingNoteDraftStore.insertLinks(this@FloatingNotePickerActivity, successfulLinks, activeTargetPath)
                }
                BetaLogger.log(
                    "FloatingNote/Picker",
                    "images selected=${uris.size} inserted=${successfulLinks.size} target=${activeTargetPath.orEmpty()}",
                )
                if (successfulLinks.size < uris.size) {
                    Toast.makeText(this@FloatingNotePickerActivity, "部分图片保存失败，请检查图片存储路径", Toast.LENGTH_LONG).show()
                }
                refreshOverlayAndFinish()
            }
        }
    }

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            FloatingNoteDraftStore.addAttachment(this, it, activeTargetPath)
        }
        refreshOverlayAndFinish()
    }

    private val pagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val path = uri?.let { TaskWidgetConfigStore.filePathFromUri(this, it) }
        if (path != null) {
            BetaLogger.log("FloatingNote/Picker", "overlay custom page selected uri=$uri path=$path")
            FloatingNoteDraftStore.setTarget(this, path)
            refreshOverlayAndFinish(path)
        } else if (uri == null) {
            BetaLogger.log("FloatingNote/Picker", "overlay custom page picker cancelled")
            refreshOverlayAndFinish()
        } else {
            BetaLogger.log("FloatingNote/Picker", "overlay custom page rejected uri=$uri")
            refreshOverlayAndFinish()
        }
    }

    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (!success || uri == null) {
            file?.delete()
            refreshOverlayAndFinish()
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val link = runCatching { EditorMediaUtil.imageLink(this@FloatingNotePickerActivity, uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (link != null) {
                    FloatingNoteDraftStore.insertLink(this@FloatingNotePickerActivity, link, activeTargetPath)
                } else {
                    Toast.makeText(this@FloatingNotePickerActivity, "照片保存失败", Toast.LENGTH_SHORT).show()
                }
                file?.delete()
                refreshOverlayAndFinish()
            }
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePhotoNow() else {
            Toast.makeText(this, "请允许相机权限后再拍照", Toast.LENGTH_SHORT).show()
            refreshOverlayAndFinish()
        }
    }

    private val recordPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startService(FloatingNoteService.startRecordingIntent(this))
        } else {
            Toast.makeText(this, "请允许录音权限后再录音", Toast.LENGTH_SHORT).show()
        }
        refreshOverlayAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeSource = intent.getStringExtra(EXTRA_SOURCE)?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() }
            ?: FloatingNoteSource.SIDEBAR
        BetaLogger.init(this, "FloatingNotePickerActivity")
        rememberTarget = intent.getBooleanExtra(EXTRA_REMEMBER_TARGET, true)
        activeTargetPath = intent.getStringExtra(EXTRA_TARGET_PATH)
        val mode = intent.getStringExtra(EXTRA_MODE)
        BetaLogger.log("FloatingNote/Picker", "open mode=${mode.orEmpty()}")
        when (mode) {
            MODE_ATTACHMENT -> attachmentPicker.launch(arrayOf("*/*"))
            MODE_CUSTOM_PAGE -> pagePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
            MODE_CAMERA -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhotoNow()
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
            MODE_RECORD_PERMISSION -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startService(FloatingNoteService.startRecordingIntent(this))
                refreshOverlayAndFinish()
            } else {
                recordPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> imagePicker.launch(arrayOf("image/*"))
        }
    }

    private fun takePhotoNow() {
        val file = runCatching { CaptureFileUtil.newImageFile(this) }.getOrNull()
        if (file == null) {
            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show()
            refreshOverlayAndFinish()
            return
        }
        pendingCameraFile = file
        val uri = CaptureFileUtil.fileUri(this, file)
        pendingCameraUri = uri
        cameraPicker.launch(uri)
    }

    private fun refreshOverlayAndFinish(selectedTargetPath: String? = null) {
        runCatching {
            startService(
                FloatingNoteService.refreshIntent(
                    this,
                    previousTargetPath = activeTargetPath,
                    selectedTargetPath = selectedTargetPath,
                    source = activeSource,
                    rememberTarget = rememberTarget,
                )
            )
        }
        finish()
    }
    companion object {
        const val EXTRA_MODE = "picker_mode"
        const val EXTRA_TARGET_PATH = "picker_target_path"
        const val MODE_IMAGES = "images"
        const val MODE_ATTACHMENT = "attachment"
        const val MODE_CUSTOM_PAGE = "custom_page"
        const val MODE_CAMERA = "camera"
        const val MODE_RECORD_PERMISSION = "record_permission"
        const val EXTRA_SOURCE = "floating_source"
        const val EXTRA_REMEMBER_TARGET = "floating_remember_target"
    }
}
