package com.quickdaily

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quickdaily.BetaLogger
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.DiaryAppendUtil
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.quickdaily.ui.theme.*
import com.quickdaily.ui.EditorToolbarActions
import com.quickdaily.ui.QuickDailyAutocompleteSurface
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.quickdaily.EditorToolbarAction
import com.quickdaily.EditorToolbarPolicy
import com.quickdaily.EditorMediaUtil
import com.quickdaily.TextIndentPolicy
import com.quickdaily.EditorTextActionPolicy
import com.quickdaily.EditorLinePrefixPolicy
import com.quickdaily.EditorStampPolicy
import com.quickdaily.WikilinkIndexRepository
import com.quickdaily.WikilinkPolicy
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.net.Uri
import com.quickdaily.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import java.io.File
import kotlin.math.roundToInt

internal enum class EditorThumbnailStrategy {
    PlatformThumbnail,
    StreamDecode,
}

internal object EditorThumbnailPolicy {
    fun strategy(sdkInt: Int): EditorThumbnailStrategy =
        if (sdkInt >= Build.VERSION_CODES.Q) {
            EditorThumbnailStrategy.PlatformThumbnail
        } else {
            EditorThumbnailStrategy.StreamDecode
        }

    fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }
}

internal object EditorThumbnailLoader {
    private const val MAX_DIMENSION = 120

    fun load(resolver: android.content.ContentResolver, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(MAX_DIMENSION, MAX_DIMENSION), null)
        } else {
            decodeLegacy(resolver, uri)
        }
    }.getOrNull()

    private fun decodeLegacy(resolver: android.content.ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = EditorThumbnailPolicy.sampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_DIMENSION,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }
}

class NoteEditActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RETURN_TO_HOME = "return_to_home"
        const val EXTRA_TARGET_RELATIVE_PATH = "target_relative_path"
        const val EXTRA_DIALOG_TITLE = "dialog_title"
        const val EXTRA_REMEMBER_TARGET = "floating_remember_target"
        const val EXTRA_FULLSCREEN = "fullscreen_quick_note"

        fun fullScreenIntent(
            context: android.content.Context,
            source: FloatingNoteSource,
            targetRelativePath: String?,
            title: String?,
        ): Intent = Intent(context, NoteEditActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_FULLSCREEN, true)
            putExtra("floating_source", source.name)
            putExtra(EXTRA_TARGET_RELATIVE_PATH, targetRelativePath.orEmpty())
            putExtra(EXTRA_DIALOG_TITLE, title.orEmpty())
            putExtra(EXTRA_REMEMBER_TARGET, false)
        }
    }

    private var noteText by mutableStateOf("")
    private val selectedImages = mutableStateListOf<Uri>()
    private val pendingAttachments = mutableStateListOf<Uri>()
    private var noteAddAnchorIfMissing by mutableStateOf(true)
    private var noteTimestampOrder by mutableStateOf("above")
    private var noteSaveInProgress = false
    private var noteEnterToSave by mutableStateOf(false)
    private var noteTagAutocomplete by mutableStateOf(true)
    private var noteWikilinkAutocomplete by mutableStateOf(true)
    private lateinit var floatingDraft: FloatingNoteEditorState
    private var floatingSource = FloatingNoteSource.WIDGET
    private var rememberTarget = true
    private var fullScreen by mutableStateOf(false)

    private var toolbarOrder by mutableStateOf(EditorToolbarPolicy.defaultOrder.map { it.id })
    private var toolbarVisible by mutableStateOf(EditorToolbarPolicy.defaultVisible)
    private var selectionRange by mutableStateOf(TextRange(0))
    private var pendingCameraFile: File? = null
    private var pendingCameraUri: Uri? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAt by mutableStateOf<Long?>(null)
    private var recordingElapsedMs by mutableStateOf(0L)
    private var returnToHomeAfterClose = false
    private var targetRelativePath by mutableStateOf<String?>(null)
    private var targetOptions by mutableStateOf<List<FloatingNoteTargetOption>>(emptyList())
    private var dialogTitle by mutableStateOf("速记")
    private var windowDragOrigin: FloatingNotePosition? = null
    private var windowDragX = 0f
    private var windowDragY = 0f

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages.addAll(uris)
    }

    private val attachmentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val fileName = ImageUtil.getDisplayName(this, it).ifBlank { "attachment" }
                insertPendingAttachment(it)
                BetaLogger.log("NoteEdit", "attachment selected name=$fileName uri=$it count=${pendingAttachments.size}")
            } catch (_: Exception) {
                insertPendingAttachment(it)
                BetaLogger.log("NoteEdit", "attachment selected without persisted permission uri=$it count=${pendingAttachments.size}")
            }
        }
    }

    private fun insertPendingAttachment(uri: Uri) {
        val next = EditorMediaUtil.insertLink(noteText, selectionRange, "![[${uri}]]")
        noteText = next.text
        selectionRange = next.selection
        pendingAttachments.add(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fullScreen = intent.getBooleanExtra(EXTRA_FULLSCREEN, false)
        BetaLogger.init(this, "NoteEditActivity")
        floatingSource = intent.getStringExtra("floating_source")
            ?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() }
            ?: FloatingNoteSource.WIDGET
        rememberTarget = intent.getBooleanExtra(EXTRA_REMEMBER_TARGET, true)
        val requestId = intent.getStringExtra("floating_request_id")
            ?.takeIf { it.isNotBlank() }
            ?: newFloatingNoteRequestId()
        FloatingNoteTiming.begin(requestId, floatingSource)
        FloatingNoteTiming.mark("activity_create")
        returnToHomeAfterClose = intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false)
        val requestedTarget = intent.getStringExtra(EXTRA_TARGET_RELATIVE_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val requestedDialogTitle = intent.getStringExtra(EXTRA_DIALOG_TITLE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val request = FloatingNoteRequest(
            source = floatingSource,
            prefillText = intent.getStringExtra("prefill_text").orEmpty(),
            returnToHomeAfterClose = returnToHomeAfterClose,
            targetRelativePath = requestedTarget,
            displayTitle = requestedDialogTitle,
            requestId = requestId,
            rememberTarget = rememberTarget,
        )
        floatingDraft = FloatingNoteEditorState(this)
        FloatingNoteDraftStore.loadInto(this, floatingDraft, request)
        noteText = floatingDraft.text
        selectedImages.addAll(floatingDraft.selectedImages)
        pendingAttachments.addAll(floatingDraft.pendingAttachments)
        selectionRange = TextRange(floatingDraft.selectionStart, floatingDraft.selectionEnd)
        targetRelativePath = floatingDraft.targetRelativePath
        dialogTitle = if (fullScreen) {
            requestedDialogTitle ?: floatingDraft.displayTitle
        } else {
            floatingDraft.displayTitle
        } ?: FloatingNoteTargetStore.titleFor(this, targetRelativePath)
        dialogTitle = titleForCurrentMode(dialogTitle)
        targetOptions = FloatingNoteTargetStore.options(this, targetRelativePath)
        BetaLogger.log(
            "FloatingNote/Selection",
            "open source=" + floatingSource + " target=" + targetRelativePath.orEmpty() +
                " title=" + dialogTitle + " options=" + targetOptions.size,
        )
        val prefs = getSharedPreferences("QuickDaily", 0)
        noteAddAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", true)
        noteTimestampOrder = prefs.getString("timestamp_order", "above") ?: "above"
        noteEnterToSave = prefs.getBoolean("enter_to_save", true)
        noteTagAutocomplete = prefs.getBoolean("tag_autocomplete", true)
        noteWikilinkAutocomplete = prefs.getBoolean("wikilink_autocomplete", true)
        toolbarOrder = EditorToolbarPolicy.migrateOrder(
            prefs.getString(EditorToolbarPolicy.PREF_ORDER, null),
            prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0) < EditorToolbarPolicy.CURRENT_SCHEMA_VERSION,
        )
        toolbarVisible = if (prefs.contains(EditorToolbarPolicy.PREF_VISIBLE)) {
            EditorToolbarPolicy.readVisible(
                prefs.getString(EditorToolbarPolicy.PREF_VISIBLE, null),
                prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0),
            )
        } else {
            EditorToolbarPolicy.defaultVisible
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeFloatingEditor(fromBack = true)
            }
        })
        lifecycleScope.launch {

            while (isActive) {
                recordingStartedAt?.let { recordingElapsedMs = SystemClock.elapsedRealtime() - it }
                delay(250)
            }
        }
        val dm = resources.displayMetrics
        val w = if (fullScreen) dm.widthPixels else (dm.widthPixels * 0.88f).toInt()
        val h = if (fullScreen) dm.heightPixels else (dm.heightPixels * 0.35f).toInt()
        if (fullScreen) {
            window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                clearFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
                val lp = attributes
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 0
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.MATCH_PARENT
                lp.dimAmount = 0.0f
                lp.format = android.graphics.PixelFormat.OPAQUE
                lp.alpha = 1.0f
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                attributes = lp
            }
        } else {
            val fallbackPosition = FloatingNotePositionPolicy.defaultPosition(
                dm.widthPixels,
                dm.heightPixels,
                w,
                h,
            )
            val position = FloatingNotePositionPolicy.clamp(
                FloatingNotePositionPolicy.load(this, fallbackPosition),
                dm.widthPixels,
                dm.heightPixels,
                w,
                h,
            )
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
                val lp = attributes
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = position.x
                lp.y = position.y
                lp.width = w
                lp.height = h
                lp.dimAmount = 0.0f
                lp.format = android.graphics.PixelFormat.TRANSLUCENT
                lp.alpha = FloatingNoteAppearance.alpha(this@NoteEditActivity)
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                attributes = lp
            }
        }
        FloatingNoteTiming.mark("activity_content_set_start", "host=activity width=$w height=$h alpha=${FloatingNoteAppearance.alpha(this)}")
        setContent {
            QuickDailyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    CompositionLocalProvider(LocalFloaterColors provides quickDailyFloaterColors()) {
                        NoteEditDialog(
                        text = noteText,
                        onTextChange = { noteText = it },
                        enterToSave = noteEnterToSave,
                        tagAutocomplete = noteTagAutocomplete,
                        wikilinkAutocomplete = noteWikilinkAutocomplete,
                        title = dialogTitle,
                        targetPath = targetRelativePath,
                        targetOptions = targetOptions,
                        onTargetChange = { option ->
                            targetRelativePath = option.path
                            dialogTitle = titleForCurrentMode(option.title)
                            if (rememberTarget) {
                                FloatingNoteTargetMemory.remember(this@NoteEditActivity, floatingSource, option.path)
                            }
                            BetaLogger.log(
                                "FloatingNote/Selection",
                                "selected path=${option.path.orEmpty()} title=${option.title} menuTitle=${option.menuTitle}",
                            )
                        },
                        onAddCustomPage = {
                            customPagePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        },
                        onRemoveTarget = { path ->
                            BetaLogger.log("FloatingNote/Selection", "remove path=$path")
                            if (targetRelativePath == path) {
                                targetRelativePath = null
                                dialogTitle = titleForCurrentMode(FloatingNoteTargetStore.titleFor(this@NoteEditActivity, null))
                                if (rememberTarget) {
                                    FloatingNoteTargetMemory.remember(this@NoteEditActivity, floatingSource, null)
                                }
                            }
                            TaskWidgetConfigStore.removeCustomPage(this@NoteEditActivity, path)
                            targetOptions = FloatingNoteTargetStore.options(this@NoteEditActivity, targetRelativePath)
                        },
                        onSave = ::saveEditor,
                        onClose = { closeFloatingEditor(discard = true) },
                        onTopAction = { if (fullScreen) saveEditor() else closeFloatingEditor(discard = true) },
                        topActionText = if (fullScreen) "保存" else "关闭",
                        fullScreen = fullScreen,
                        onHome = { closeFloatingEditor(openHomeAfterClose = true) },
                        onReturnToFloating = ::shrinkToFloating,
                        imageUris = selectedImages,
                        hasAttachments = pendingAttachments.isNotEmpty(),
                        attachmentUris = pendingAttachments,
                        onPickImages = {
                imagePicker.launch("image/*")
            },
                         onPickAttachment = {
                attachmentPicker.launch(arrayOf("*/*"))
            },
                         onTakePhoto = { requestPhoto() },
                         onToggleRecording = { toggleRecording() },
                         toolbarOrder = toolbarOrder,
                         toolbarVisible = toolbarVisible,
                         recording = recorder != null,
                         recordingDurationMs = recordingElapsedMs,
                         initialSelection = selectionRange,
                         onSelectionChange = { selectionRange = it },
                         onMoveWindowStart = ::beginWindowMove,
                         onMoveWindow = ::moveWindow,
                         onMoveWindowEnd = ::endWindowMove,
                         onFullScreen = ::openFullScreen,
                         onTiming = { stage, detail ->
                             FloatingNoteTiming.mark(
                                 stage,
                                 "host=activity imePolicy=ActivityDefault windowFocus=${window?.decorView?.hasWindowFocus() == true} " +
                                     "detail=${detail.orEmpty()}",
                             )
                         },

                         onRemoveImage = { index -> selectedImages.removeAt(index) }
                        )
                    }
                }
            }
        }

        FloatingNoteTiming.mark("activity_content_set_return", "host=activity")
        window?.decorView?.post {
            val view = window?.decorView ?: return@post
            FloatingNoteTiming.mark(
                "activity_post_content",
                "host=activity attached=${view.isAttachedToWindow} laidOut=${view.isLaidOut} " +
                    "focus=${view.hasFocus()} windowFocus=${view.hasWindowFocus()} ${FloatingNoteImeController.debugState(view)}",
            )
        }
    }

    private fun beginWindowMove() {
        val attrs = window?.attributes ?: return
        windowDragOrigin = FloatingNotePosition(attrs.x, attrs.y)
        windowDragX = 0f
        windowDragY = 0f
    }

    private fun moveWindow(dx: Float, dy: Float) {
        val currentWindow = window ?: return
        val attrs = currentWindow.attributes
        val origin = windowDragOrigin ?: FloatingNotePosition(attrs.x, attrs.y).also {
            windowDragOrigin = it
        }
        windowDragX += dx
        windowDragY += dy
        val dm = resources.displayMetrics
        val position = FloatingNotePositionPolicy.clamp(
            FloatingNotePosition(
                x = origin.x + windowDragX.roundToInt(),
                y = origin.y + windowDragY.roundToInt(),
            ),
            dm.widthPixels,
            dm.heightPixels,
            attrs.width,
            attrs.height,
        )
        if (attrs.x != position.x || attrs.y != position.y) {
            attrs.x = position.x
            attrs.y = position.y
            currentWindow.attributes = attrs
        }
    }

    private fun endWindowMove() {
        val attrs = window?.attributes
        if (attrs != null) {
            FloatingNotePositionPolicy.save(this, FloatingNotePosition(attrs.x, attrs.y))
        }
        windowDragOrigin = null
        windowDragX = 0f
        windowDragY = 0f
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val link = runCatching { EditorMediaUtil.imageLink(this@NoteEditActivity, uri) }.getOrNull()
                withContext(Dispatchers.Main) { if (link != null) insertMediaLink(link) }
            }
        } else {
            file?.delete()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePhotoNow()
        else Toast.makeText(this, "请允许相机权限后再拍照", Toast.LENGTH_SHORT).show()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingNow()
        else Toast.makeText(this, "请允许录音权限后再录音", Toast.LENGTH_SHORT).show()
    }

    private fun insertMediaLink(link: String) {
        val next = EditorMediaUtil.insertLink(noteText, selectionRange, link)
        noteText = next.text
        selectionRange = next.selection
    }

    private fun requestPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhotoNow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun takePhotoNow() {
        val file = runCatching { CaptureFileUtil.newImageFile(this) }.getOrNull()
        if (file == null) {
            Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraFile = file
        pendingCameraUri = CaptureFileUtil.fileUri(this, file)
        pendingCameraUri?.let(cameraLauncher::launch)
    }

    private fun toggleRecording() {
        if (recorder != null) {
            stopRecording()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingNow()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingNow() {
        if (recorder != null) return
        val file = runCatching { CaptureFileUtil.newAudioFile(this) }.getOrNull() ?: return
        val nextRecorder = runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        if (nextRecorder == null) {
            file.delete()
            Toast.makeText(this, "无法开始录音", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = nextRecorder
        recordingFile = file
        recordingStartedAt = SystemClock.elapsedRealtime()
    }

    private fun stopRecording() {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        recordingStartedAt = null
        recordingElapsedMs = 0L
        val stopped = runCatching { activeRecorder.stop() }.isSuccess
        runCatching { activeRecorder.reset() }
        runCatching { activeRecorder.release() }
        if (!stopped || file == null) {
            file?.delete()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val link = runCatching { EditorMediaUtil.audioLink(this@NoteEditActivity, file) }.getOrNull()
            if (link != null) file.delete()
            withContext(Dispatchers.Main) {
                if (link != null) insertMediaLink(link) else Toast.makeText(this@NoteEditActivity, "录音保存失败，临时文件已保留", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val customPagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            BetaLogger.log("FloatingNote/Picker", "custom page picker cancelled")
        }
        val path = uri?.let { TaskWidgetConfigStore.filePathFromUri(this, it) }
        if (path != null) {
            BetaLogger.log("FloatingNote/Picker", "custom page selected uri=$uri path=$path")
            TaskWidgetConfigStore.recordCustomPage(this, path)
            targetRelativePath = path
            dialogTitle = titleForCurrentMode(FloatingNoteTargetStore.titleFor(this, path))
            if (rememberTarget) {
                FloatingNoteTargetMemory.remember(this, floatingSource, path)
            }
            targetOptions = FloatingNoteTargetStore.options(this, targetRelativePath)
        } else if (uri != null) {
            BetaLogger.log("FloatingNote/Picker", "custom page rejected uri=$uri")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FULLSCREEN, false)) {
            BetaLogger.log("FloatingNote/Fullscreen", "activity reused fullscreen request")
            fullScreen = true
            intent.getStringExtra(EXTRA_DIALOG_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.let { dialogTitle = titleForCurrentMode(it) }
            if (intent.hasExtra(EXTRA_TARGET_RELATIVE_PATH)) {
                targetRelativePath = intent.getStringExtra(EXTRA_TARGET_RELATIVE_PATH)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                targetOptions = FloatingNoteTargetStore.options(this, targetRelativePath)
            }
            applyFullScreenWindow()
        }
        // singleInstance 会复用当前编辑器。不要重新初始化 noteText，避免重复点击入口时丢草稿。
        if (intent.hasExtra(EXTRA_RETURN_TO_HOME)) {
            returnToHomeAfterClose = intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false)
        }
        BetaLogger.log("Lifecycle", "NoteEditActivity reused")
    }

    private fun syncFloatingDraft() {
        floatingDraft.text = noteText
        floatingDraft.selectedImages.clear()
        floatingDraft.selectedImages.addAll(selectedImages)
        floatingDraft.pendingAttachments.clear()
        floatingDraft.pendingAttachments.addAll(pendingAttachments)
        floatingDraft.source = floatingSource
        floatingDraft.rememberTarget = rememberTarget
        floatingDraft.targetRelativePath = targetRelativePath
        floatingDraft.displayTitle = dialogTitle
        floatingDraft.selectionStart = selectionRange.start
        floatingDraft.selectionEnd = selectionRange.end
    }

    private fun persistDraftAndFinish(openHomeAfterClose: Boolean = false) {
        syncFloatingDraft()
        FloatingNoteDraftStore.persistOrClear(this, floatingDraft)
        finishEditor(openHomeAfterClose)
    }

    private fun titleForCurrentMode(baseTitle: String): String {
        val normalized = withoutQuickRecordSuffix(baseTitle)
        return if (fullScreen) "$normalized \u901f\u5F55" else normalized
    }

    private fun withoutQuickRecordSuffix(title: String): String {
        var normalized = title.trim()
        while (normalized.endsWith("\u901f\u5F55")) {
            normalized = normalized.removeSuffix("\u901f\u5F55").trimEnd()
        }
        return normalized.ifBlank { "\u901f\u8bb0" }
    }

    private fun openFullScreen() {
        if (fullScreen || noteSaveInProgress) return
        BetaLogger.log(
            "FloatingNote/Fullscreen",
            "activity expand source=" + floatingSource + " target=" + targetRelativePath.orEmpty(),
        )
        syncFloatingDraft()
        FloatingNoteDraftStore.persist(this, floatingDraft)
        val title = dialogTitle
            .takeIf { it.isNotBlank() }
            ?.let { "${withoutQuickRecordSuffix(it)} \u901f\u5F55" }
        title?.let { dialogTitle = it }
        fullScreen = true
        setIntent(
            fullScreenIntent(
                context = this,
                source = floatingSource,
                targetRelativePath = targetRelativePath,
                title = title,
            )
        )
        applyFullScreenWindow()
    }

    private fun shrinkToFloating() {
        if (!fullScreen || noteSaveInProgress) return
        BetaLogger.log(
            "FloatingNote/Fullscreen",
            "activity shrink source=" + floatingSource + " target=" + targetRelativePath.orEmpty(),
        )
        syncFloatingDraft()
        dialogTitle = withoutQuickRecordSuffix(dialogTitle)
        fullScreen = false
        floatingDraft.displayTitle = dialogTitle
        FloatingNoteDraftStore.persist(this, floatingDraft)
        applyFloatingWindow()
    }

    private fun applyFullScreenWindow() {
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            clearFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            val lp = attributes
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 0
            lp.y = 0
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.dimAmount = 0.0f
            lp.format = android.graphics.PixelFormat.OPAQUE
            lp.alpha = 1.0f
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            attributes = lp
        }
    }

    private fun applyFloatingWindow() {
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * 0.88f).toInt()
        val h = (dm.heightPixels * 0.35f).toInt()
        val fallbackPosition = FloatingNotePositionPolicy.defaultPosition(
            dm.widthPixels,
            dm.heightPixels,
            w,
            h,
        )
        val position = FloatingNotePositionPolicy.clamp(
            FloatingNotePositionPolicy.load(this, fallbackPosition),
            dm.widthPixels,
            dm.heightPixels,
            w,
            h,
        )
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            val lp = attributes
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = position.x
            lp.y = position.y
            lp.width = w
            lp.height = h
            lp.dimAmount = 0.0f
            lp.format = android.graphics.PixelFormat.TRANSLUCENT
            lp.alpha = FloatingNoteAppearance.alpha(this@NoteEditActivity)
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            attributes = lp
        }
    }

    private fun requestActivityImeHide() {
        val view = window?.decorView ?: return
        val manager = getSystemService(InputMethodManager::class.java)
        val token = view.windowToken
        FloatingNoteTiming.mark(
            "ime_hide_request",
            "host=activity managerAvailable=${manager != null} token=${token != null} " +
                "focus=${view.hasFocus()} ${FloatingNoteImeController.debugState(view)}",
        )
        val accepted = token != null && manager?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS) == true
        FloatingNoteTiming.mark(
            "ime_hide_called",
            "host=activity accepted=$accepted managerAvailable=${manager != null} token=${token != null} " +
                "focus=${view.hasFocus()} ${FloatingNoteImeController.debugState(view)}",
        )
        view.post {
            FloatingNoteTiming.mark(
                "ime_hide_post",
                "host=activity attached=${view.isAttachedToWindow} focus=${view.hasFocus()} " +
                    "${FloatingNoteImeController.debugState(view)}",
            )
        }
    }

    private fun finishEditor(openHomeAfterClose: Boolean = false) {
        FloatingNoteTiming.mark("hide_start", "host=activity")
        stopRecording()
        requestActivityImeHide()
        if (returnToHomeAfterClose || openHomeAfterClose) {
            startActivity(MainActivity.editorIntent(this, targetRelativePath))
        }
        finish()
        FloatingNoteTiming.mark("hide_end", "host=activity")
    }

    private fun saveEditor() {
        if (hasRealContent(noteText) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()) {
            appendToDiary(noteText.trim())
        } else {
            FloatingNoteDraftStore.clear(this, targetRelativePath)
            finishEditor()
        }
    }

    private fun closeFloatingEditor(
        openHomeAfterClose: Boolean = false,
        discard: Boolean = false,
        fromBack: Boolean = false,
    ) {
        if (noteSaveInProgress) return
        if (discard) {
            FloatingNoteDraftStore.clear(this, targetRelativePath)
            finishEditor(openHomeAfterClose)
            return
        }
        val hasContent = hasRealContent(noteText) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()
        BetaLogger.log("FloatingNote/Exit", "activity exit fromBack=$fromBack saveOnClose=${FloatingNoteEntryPolicy.shouldSaveOnClose(this)} hasContent=$hasContent")
        if (FloatingNoteEntryPolicy.shouldSaveOnClose(this) && hasContent) {
            appendToDiary(noteText.trim(), openHomeAfterClose)
        } else {
            persistDraftAndFinish(openHomeAfterClose)
        }
    }
    private fun appendToDiary(text: String, openHomeAfterClose: Boolean = false) {
        stopRecording()
        if (noteSaveInProgress) return
        if (!hasRealContent(text) && selectedImages.isEmpty() && pendingAttachments.isEmpty()) {
            FloatingNoteDraftStore.clear(this, targetRelativePath)
            finishEditor(openHomeAfterClose)
            return
        }
        noteSaveInProgress = true
        syncFloatingDraft()
        FloatingNoteDraftStore.persist(this, floatingDraft)
        lifecycleScope.launch {
            when (val result = FloatingNoteSaveUseCase(this@NoteEditActivity).save(
                text,
                selectedImages.toList(),
                pendingAttachments.toList(),
                targetRelativePath = targetRelativePath,
            )) {
                FloatingNoteSaveResult.Saved -> {
                    selectedImages.clear()
                    pendingAttachments.clear()
                    FloatingNoteDraftStore.clear(this@NoteEditActivity, targetRelativePath)
                    Toast.makeText(this@NoteEditActivity, "已保存", Toast.LENGTH_SHORT).show()
                    finishEditor(openHomeAfterClose)
                }
                FloatingNoteSaveResult.NoContent -> {
                    FloatingNoteDraftStore.clear(this@NoteEditActivity, targetRelativePath)
                    finishEditor(openHomeAfterClose)
                }
                is FloatingNoteSaveResult.Failed -> {
                    noteSaveInProgress = false
                    BetaLogger.log("NoteEdit", "save failed=" + result.message)
                    Toast.makeText(this@NoteEditActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            closeFloatingEditor(fromBack = true)
            return true
        }
        return super.onTouchEvent(event)
    }
    override fun onDestroy() {
        FloatingNoteTiming.mark("activity_destroy")
        super.onDestroy()
    }
}

internal fun hasRealContent(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    // 任务标记前缀（同时检查有无尾随空格，因为 .trim() 会去掉末尾空格）
    val taskPrefixes = listOf("- [ ] ", "- [x] ", "- [X] ", "- [ ]", "- [x]", "- [X]")
    for (prefix in taskPrefixes) {
        if (trimmed.startsWith(prefix)) {
            val rest = trimmed.removePrefix(prefix).trim()
            return rest.isNotBlank()
        }
    }
    return true
}

@Composable
fun NoteEditDialog(
    text: String,
    onTextChange: (String) -> Unit,
    enterToSave: Boolean,
    tagAutocomplete: Boolean = true,
    wikilinkAutocomplete: Boolean = true,
    title: String = "速记",
    targetPath: String? = null,
    targetOptions: List<FloatingNoteTargetOption> = emptyList(),
    onTargetChange: (FloatingNoteTargetOption) -> Unit = {},
    onAddCustomPage: () -> Unit = {},
    onRemoveTarget: (String) -> Unit = {},
    useInlineTargetMenu: Boolean = false,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onTopAction: () -> Unit = onClose,
    topActionText: String = "关闭",
    fullScreen: Boolean = false,
    onHome: () -> Unit,
    onReturnToFloating: () -> Unit = {},
    imageUris: List<Uri>,
    hasAttachments: Boolean,
    attachmentUris: List<Uri>,
    invalidAttachmentUris: List<Uri> = emptyList(),
    imePolicy: FloatingNoteImePolicy = FloatingNoteImePolicy.ActivityDefault,
    onPickImages: () -> Unit,
    onPickAttachment: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    toolbarOrder: List<String> = EditorToolbarPolicy.defaultOrder.map { it.id },
    toolbarVisible: Set<String> = EditorToolbarPolicy.defaultVisible,
    recording: Boolean = false,
    recordingDurationMs: Long = 0L,
    initialSelection: TextRange? = null,
    onSelectionChange: (TextRange) -> Unit = {},
    onMoveWindowStart: () -> Unit = {},
    onMoveWindow: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onMoveWindowEnd: () -> Unit = {},
    onRemoveImage: (Int) -> Unit,
    onRemoveAttachment: (Int) -> Unit = {},
    onTiming: (stage: String, detail: String?) -> Unit = { _, _ -> },
    onFullScreen: () -> Unit = {},
) {
    val floater = LocalFloaterColors.current
    val dim = LocalAppDimensions.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    var focusRequested by remember { mutableStateOf(false) }
    var imeShowRequested by remember { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var wikilinkPopupDismissKey by remember { mutableStateOf<String?>(null) }
    var tfv by remember { mutableStateOf(TextFieldValue(text, initialSelection ?: TextRange(text.length))) }
    var autoIndentState by remember { mutableStateOf<EditorAutoIndentState?>(null) }
    var stampToggleState by remember { mutableStateOf(EditorStampToggleState()) }
    val localUndoStack = remember { mutableStateListOf<String>() }
    val localRedoStack = remember { mutableStateListOf<String>() }
    var lastUndoTime by remember { mutableLongStateOf(0L) }
    val neCtx = LocalContext.current
    val neView = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    var recentWikilinks by remember { mutableStateOf(WikilinkRecentStore.load(neCtx)) }

    fun recordUndo(previousText: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        localRedoStack.clear()
        if (force || now - lastUndoTime > 1500) {
            localUndoStack.add(previousText)
            if (localUndoStack.size > 50) localUndoStack.removeAt(0)
            lastUndoTime = if (force) 0L else now
        }
    }

    fun applyTextChange(
        newValue: TextFieldValue,
        forceUndo: Boolean = false,
        invalidateStamp: Boolean = true,
    ) {
        val oldText = tfv.text
        if (oldText == newValue.text) {
            tfv = newValue
            return
        }
        if (invalidateStamp) stampToggleState = stampToggleState.clear()
        recordUndo(oldText, forceUndo)
        tfv = newValue
        onTextChange(newValue.text)
        onSelectionChange(newValue.selection)
    }

    LaunchedEffect(Unit) {
        // A service-hosted ComposeView may not have attached the BasicTextField node
        // when the first composition side effect runs. Wait for a frame and tolerate
        // a vendor focus failure instead of taking down the overlay process.
        onTiming("editor_effect_start", "imePolicy=$imePolicy")
        withFrameNanos { }
        onTiming("editor_frame_ready", "imePolicy=$imePolicy")
        if (!focusRequested) {
            focusRequested = true
            onTiming("focus_request", "imePolicy=$imePolicy")
            runCatching {
                val accepted = focusRequester.requestFocus()
                onTiming("focus_result", "accepted=$accepted imePolicy=$imePolicy")
            }.onFailure { onTiming("focus_failed", "imePolicy=$imePolicy error=${it.javaClass.simpleName}") }
        }
    }
    LaunchedEffect(density, imeInsets) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .filter { it }
            .take(1)
            .collect {
                onTiming("ime_visible", "imePolicy=$imePolicy bottom=${imeInsets.getBottom(density)}")
            }
    }
    LaunchedEffect(text, initialSelection) {
        if (text != tfv.text || (initialSelection != null && tfv.selection != initialSelection)) {
            stampToggleState = stampToggleState.clear()
            autoIndentState = null
            tfv = TextFieldValue(text, initialSelection ?: TextRange(text.length))
        }
    }
    val tagVaultPath = neCtx.getSharedPreferences("QuickDaily", 0).getString("vault_path", "") ?: ""
    val wikilinkIndex by WikilinkIndexRepository.indexState.collectAsStateWithLifecycle()
    val completionIndex = wikilinkIndex.takeIf {
        it.rootPath == tagVaultPath && it.indexed && it.tagsIndexed && it.error == null
    }
    val noteTagList = completionIndex?.tags.orEmpty()
    val tagCompletion = remember(tfv.text, tfv.selection, noteTagList, tagAutocomplete, completionIndex?.rootPath) {
        if (!tagAutocomplete || completionIndex == null) return@remember Triple(false, "", 0)
        val currentText = tfv.text
        val cursor = tfv.selection.start
        if (cursor > 0 && cursor <= currentText.length) {
            val before = currentText.substring(0, cursor)
            val hashPos = before.lastIndexOf('#')
            if (hashPos >= 0) {
                val after = before.substring(hashPos + 1)
                val wordBefore = hashPos > 0 && (currentText[hashPos - 1].isLetterOrDigit() || currentText[hashPos - 1] == '_')
                if (!wordBefore && (after.isEmpty() || (after[0] != ' ' && !after.all { it == '#' }))) {
                    val prefix = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }
                    val finished = prefix in noteTagList && (after.length == prefix.length || after.length > prefix.length && (!after[prefix.length].isLetterOrDigit() && after[prefix.length] != '#'))
                    if (!finished) return@remember Triple(true, prefix, hashPos)
                }
            }
        }
        Triple(false, "", 0)
    }
    val (tagActive2, tagPrefix2, tagHashPos2) = tagCompletion
    val noteMatchingTags = remember(tagActive2, tagPrefix2, noteTagList) {
        if (!tagActive2) emptyList() else if (tagPrefix2.isEmpty()) {
            val recent = com.quickdaily.util.RecentTags.get(neCtx)
            (recent + noteTagList.filterNot { it in recent }).take(3)
        } else noteTagList.filter { it.contains(tagPrefix2, ignoreCase = true) }.take(8)
    }
    val latestTextFieldValue by rememberUpdatedState(tfv)
    val noteSelectTag: (String) -> Unit = remember(tagHashPos2) {
        { tag ->
            val currentValue = latestTextFieldValue
            val currentText = currentValue.text
            val cursor = currentValue.selection.start
            val needSpaceBefore = tagHashPos2 > 0 && currentText[tagHashPos2 - 1] != ' ' && currentText[tagHashPos2 - 1] != '\n'
            val prefix = if (needSpaceBefore) " #" else "#"
            val newText = currentText.substring(0, tagHashPos2) + prefix + tag + " " + currentText.substring(cursor)
            val newCursor = tagHashPos2 + prefix.length + tag.length + 1
            applyTextChange(TextFieldValue(newText, TextRange(newCursor)), forceUndo = true)
            com.quickdaily.util.RecentTags.record(neCtx, tag)
        }
    }

    LaunchedEffect(tagVaultPath) {
        WikilinkIndexRepository.ensureIndexed(neCtx, tagVaultPath)
    }
    LaunchedEffect(wikilinkIndex.error) {
        wikilinkIndex.error?.let { message ->
            Toast.makeText(neCtx, message, Toast.LENGTH_SHORT).show()
        }
    }
    val wikilinkTrigger = remember(tfv.text, tfv.selection, wikilinkIndex, wikilinkAutocomplete) {
        if (!wikilinkAutocomplete || tagVaultPath.isBlank() || wikilinkIndex.rootPath != tagVaultPath || wikilinkIndex.error != null) {
            null
        } else {
            WikilinkPolicy.trigger(tfv.text, tfv.selection.start)
        }
    }
    val noteMatchingWikilinks = remember(wikilinkIndex.candidates, wikilinkTrigger, recentWikilinks) {
        val trigger = wikilinkTrigger ?: return@remember emptyList<WikilinkCandidate>()
        if (trigger.query.isBlank()) {
            recentWikilinks.take(WikilinkCandidatePolicy.DEFAULT_LIMIT)
        } else {
            WikilinkPolicy.filterWikilinkCandidates(wikilinkIndex.candidates, trigger.query)
        }
    }
    val aliasCounts = remember(wikilinkIndex.aliases) {
        wikilinkIndex.aliases.groupingBy { it.alias }.eachCount()
    }
    val wikilinkTriggerKey = wikilinkTrigger?.let { "${it.start}:${it.replaceEnd}:${it.query}" }

    fun selectWikilink(candidate: WikilinkCandidate) {
        val currentTrigger = wikilinkTrigger ?: return
        val replacement = candidate.insertionText
        val newText = tfv.text.substring(0, currentTrigger.start) + replacement + tfv.text.substring(currentTrigger.replaceEnd)
        applyTextChange(TextFieldValue(newText, TextRange(currentTrigger.start + replacement.length)), forceUndo = true)
        WikilinkRecentStore.record(neCtx, candidate)
        recentWikilinks = listOf(candidate) + recentWikilinks.filterNot { it.stableKey == candidate.stableKey }
            .take(9)
    }

    fun applyTextAction(result: EditorTextActionResult, invalidateStamp: Boolean = true) {
        result.clipboardText?.let { clipboardManager.setText(AnnotatedString(it)) }
        if (invalidateStamp) stampToggleState = stampToggleState.clear()
        applyTextChange(
            TextFieldValue(result.text, result.selection),
            forceUndo = true,
            invalidateStamp = invalidateStamp,
        )
    }

    fun saveOrClose() {
        onTiming("enter_action", "hasTagCandidates=${noteMatchingTags.isNotEmpty()}")
        if (noteMatchingTags.isNotEmpty()) {
            noteSelectTag(noteMatchingTags.first())
        } else if (noteMatchingWikilinks.isNotEmpty()) {
            selectWikilink(noteMatchingWikilinks.first())
        } else if (tfv.text.isNotBlank() || imageUris.isNotEmpty() || hasAttachments) {
            onSave()
        } else {
            onClose()
        }
    }

    fun handleToolbarAction(action: EditorToolbarAction) {
        when (action) {
            EditorToolbarAction.IMAGE -> onPickImages()
            EditorToolbarAction.CAMERA -> onTakePhoto()
            EditorToolbarAction.RECORD -> onToggleRecording()
            EditorToolbarAction.ATTACHMENT -> onPickAttachment()
            EditorToolbarAction.INDENT -> {
                val result = TextIndentPolicy.indent(tfv.text, tfv.selection)
                applyTextChange(TextFieldValue(result.text, result.selection), forceUndo = true)
            }
            EditorToolbarAction.OUTDENT -> {
                val result = TextIndentPolicy.outdent(tfv.text, tfv.selection)
                applyTextChange(TextFieldValue(result.text, result.selection), forceUndo = true)
            }
            EditorToolbarAction.CUT_LINE -> applyTextAction(
                EditorTextActionPolicy.cutLine(tfv.text, tfv.selection)
            )
            EditorToolbarAction.MOVE_LINE_UP -> applyTextAction(
                EditorTextActionPolicy.moveLineUp(tfv.text, tfv.selection)
            )
            EditorToolbarAction.MOVE_LINE_DOWN -> applyTextAction(
                EditorTextActionPolicy.moveLineDown(tfv.text, tfv.selection)
            )
            EditorToolbarAction.TIMESTAMP -> {
                val (result, nextState) = stampToggleState.toggle(
                    text = tfv.text,
                    selection = tfv.selection,
                    action = EditorStampAction.TIMESTAMP,
                    insertion = EditorStampPolicy.toolbarTimestampInsertion(),
                )
                stampToggleState = nextState
                applyTextAction(result, invalidateStamp = false)
            }
            EditorToolbarAction.DATE_STAMP -> {
                val (result, nextState) = stampToggleState.toggle(
                    text = tfv.text,
                    selection = tfv.selection,
                    action = EditorStampAction.DATE_STAMP,
                    insertion = EditorStampPolicy.dateInsertion(),
                )
                stampToggleState = nextState
                applyTextAction(result, invalidateStamp = false)
            }
            EditorToolbarAction.WIKILINK -> applyTextAction(
                EditorTextActionPolicy.insert(tfv.text, tfv.selection, "[[")
            )
            EditorToolbarAction.TASK,
            EditorToolbarAction.HEADING,
            EditorToolbarAction.LIST,
            EditorToolbarAction.ORDERED_LIST -> applyTextAction(
                EditorLinePrefixPolicy.apply(tfv.text, tfv.selection, action)
            )
            EditorToolbarAction.BOLD -> {
                val t = tfv.text
                val c = tfv.selection.start
                if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                    applyTextChange(TextFieldValue(t.substring(0, c - 2) + t.substring(c + 2), TextRange(c - 2)), forceUndo = true)
                } else {
                    applyTextChange(TextFieldValue(t.substring(0, c) + "****" + t.substring(c), TextRange(c + 2)), forceUndo = true)
                }
            }
            EditorToolbarAction.STRIKETHROUGH -> applyTextAction(
                EditorTextActionPolicy.toggleDelimiter(tfv.text, tfv.selection, "~~")
            )
            EditorToolbarAction.INLINE_CODE -> applyTextAction(
                EditorTextActionPolicy.toggleDelimiter(tfv.text, tfv.selection, "`")
            )
            EditorToolbarAction.QUOTE -> applyTextAction(
                EditorLinePrefixPolicy.apply(tfv.text, tfv.selection, EditorToolbarAction.QUOTE)
            )
            EditorToolbarAction.CODE_BLOCK -> applyTextAction(
                EditorLinePrefixPolicy.apply(tfv.text, tfv.selection, EditorToolbarAction.CODE_BLOCK)
            )
            EditorToolbarAction.HORIZONTAL_RULE -> applyTextAction(
                EditorTextActionPolicy.horizontalRule(tfv.text, tfv.selection)
            )
            EditorToolbarAction.MARKDOWN_LINK -> applyTextAction(
                EditorTextActionPolicy.markdownLink(tfv.text, tfv.selection)
            )
            EditorToolbarAction.UNDO -> {
                if (localUndoStack.isNotEmpty()) {
                    val cur = tfv.text
                    localRedoStack.add(cur)
                    if (localRedoStack.size > 50) localRedoStack.removeAt(0)
                    val prev = localUndoStack.removeAt(localUndoStack.lastIndex)
                    applyTextChange(TextFieldValue(prev, TextRange(prev.length)))
                    lastUndoTime = 0L
                    BetaLogger.log("Toolbar", "undo")
                }
            }
            EditorToolbarAction.REDO -> {
                if (localRedoStack.isNotEmpty()) {
                    val cur = tfv.text
                    localUndoStack.add(cur)
                    if (localUndoStack.size > 50) localUndoStack.removeAt(0)
                    val next = localRedoStack.removeAt(localRedoStack.lastIndex)
                    applyTextChange(TextFieldValue(next, TextRange(next.length)))
                    lastUndoTime = 0L
                    BetaLogger.log("Toolbar", "redo")
                }
            }
        }
    }

    if (fullScreen) {
        FullScreenNoteEditSurface(
            title = title,
            targetPath = targetPath,
            targetOptions = targetOptions,
            targetMenuExpanded = targetMenuExpanded,
            onTargetMenuExpandedChange = { targetMenuExpanded = it },
            textFieldValue = tfv,
            onTextChange = ::applyTextChange,
            onSave = onSave,
            onSaveOrClose = ::saveOrClose,
            onHome = onHome,
            onReturnToFloating = onReturnToFloating,
            onTargetChange = onTargetChange,
            onAddCustomPage = onAddCustomPage,
            onRemoveTarget = onRemoveTarget,
            imageUris = imageUris,
            attachmentUris = attachmentUris,
            invalidAttachmentUris = invalidAttachmentUris,
            onRemoveImage = onRemoveImage,
            onRemoveAttachment = onRemoveAttachment,
            onPickImages = onPickImages,
            onPickAttachment = onPickAttachment,
            onTakePhoto = onTakePhoto,
            onToggleRecording = onToggleRecording,
            toolbarOrder = toolbarOrder,
            toolbarVisible = toolbarVisible,
            recording = recording,
            recordingDurationMs = recordingDurationMs,
            canUndo = localUndoStack.isNotEmpty(),
            canRedo = localRedoStack.isNotEmpty(),
            onToolbarAction = ::handleToolbarAction,
            enterToSave = enterToSave,
            tagActive = tagActive2,
            matchingTags = noteMatchingTags,
            onTagSelected = noteSelectTag,
            wikilinkTriggerKey = wikilinkTriggerKey,
            wikilinkLoading = wikilinkIndex.loading,
            matchingWikilinks = noteMatchingWikilinks,
            aliasCounts = aliasCounts,
            wikilinkPopupDismissKey = wikilinkPopupDismissKey,
            onDismissWikilinkPopup = { wikilinkPopupDismissKey = wikilinkTriggerKey },
            onWikilinkSelected = ::selectWikilink,
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = floater.background,
        shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(dim.radiusXl),
        shadowElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(if (fullScreen) 16.dp else 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.wrapContentWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            TextButton(onClick = onHome) {
                                Text("编辑页", color = floater.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    // The overlay is intentionally narrow on phones. Let the
                                    // title yield space to the selector instead of pushing the
                                    // selector outside the centered header row.
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                onMoveWindowStart()
                                                neView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            },
                                            onDragCancel = onMoveWindowEnd,
                                            onDrag = { change, amount ->
                                                change.consume()
                                                onMoveWindow(amount.x, amount.y)
                                            },
                                            onDragEnd = onMoveWindowEnd,
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    title.ifBlank { "速记" },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = floater.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { targetMenuExpanded = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "选择记录页面",
                                    tint = floater.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            if (!fullScreen) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable(onClick = onFullScreen),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "全屏",
                                        tint = floater.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.wrapContentWidth(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            TextButton(onClick = onTopAction) {
                                Text(topActionText, color = floater.onBackgroundVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

            if (targetMenuExpanded && !useInlineTargetMenu) {
                Dialog(onDismissRequest = { targetMenuExpanded = false }) {
                    TargetSelectionSurface(
                        modifier = Modifier.fillMaxWidth(0.86f),
                        targetPath = targetPath,
                        targetOptions = targetOptions,
                        onTargetChange = onTargetChange,
                        onAddCustomPage = onAddCustomPage,
                        onRemoveTarget = onRemoveTarget,
                        onDismiss = { targetMenuExpanded = false },
                    )
                }
            }



            // ── 内容区（撑满剩余空间，将工具栏推到最下方）──
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
            // Thumbnail preview
            if (imageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(imageUris) { index, uri ->
                        Box(modifier = Modifier.size(60.dp)) {
                            val ctx = LocalContext.current
                            val thumbBitmap by produceState<Bitmap?>(
                                initialValue = null,
                                key1 = uri,
                            ) {
                                value = withContext(Dispatchers.IO) {
                                    EditorThumbnailLoader.load(ctx.contentResolver, uri)
                                }
                            }
                            thumbBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, "删除", tint = Color.Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            if (attachmentUris.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    attachmentUris.forEachIndexed { index, uri ->
                        val invalid = uri in invalidAttachmentUris
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (invalid) "附件失效：${uri.lastPathSegment ?: uri}" else "附件：${uri.lastPathSegment ?: uri}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (invalid) MaterialTheme.colorScheme.error else floater.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { onRemoveAttachment(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "移除附件", tint = floater.onBackgroundVariant)
                            }
                        }
                    }
                }
            }
            BasicTextField(value = tfv, onValueChange = { newTfv ->
                    val oldText = tfv.text
                    val newText = newTfv.text
                    val insertedNewlines = newText.count { it == '\n' } - oldText.count { it == '\n' }
                    if (enterToSave && insertedNewlines > 0 && newText.length == oldText.length + 1) {
                        // IMEs that commit Enter as a newline still use the same explicit action.
                        autoIndentState = null
                        saveOrClose()
                    } else {
                        val result = EditorAutoIndentPolicy.apply(
                            previous = tfv,
                            proposed = newTfv,
                            state = autoIndentState,
                        )
                        autoIndentState = result.state
                        if (oldText != result.value.text) {
                            stampToggleState = stampToggleState.clear()
                            recordUndo(oldText)
                        }
                        tfv = result.value
                        onTextChange(result.value.text)
                        onSelectionChange(result.value.selection)
                    }
            },
                keyboardOptions = KeyboardOptions(
                    imeAction = if (enterToSave) {
                        androidx.compose.ui.text.input.ImeAction.Done
                    } else {
                        androidx.compose.ui.text.input.ImeAction.Default
                    }
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (noteMatchingTags.isNotEmpty()) noteSelectTag(noteMatchingTags.first())
                    else if (noteMatchingWikilinks.isNotEmpty()) selectWikilink(noteMatchingWikilinks.first())
                    else if (enterToSave) saveOrClose()
                }),
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = floater.onBackground),
                cursorBrush = SolidColor(FloatingCursorPolicy.colorFor(floater.background)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        onTiming("focus_state", "focused=${it.isFocused} imePolicy=$imePolicy")
                        if (it.isFocused) {
                            onTiming("focus_acquired", "imePolicy=$imePolicy")
                            if (!imeShowRequested) {
                                imeShowRequested = true
                                val keyboardControllerAvailable = keyboardController != null
                                onTiming(
                                    "ime_show_request",
                                    "imePolicy=$imePolicy keyboardControllerAvailable=$keyboardControllerAvailable " +
                                        "attached=${neView.isAttachedToWindow} ${FloatingNoteImeController.debugState(neView)}",
                                )
                                if (imePolicy == FloatingNoteImePolicy.OverlayInstant) {
                                    FloatingNoteImeController.show(neView, onTiming)
                                } else {
                                    keyboardController?.show()
                                    onTiming(
                                        "ime_show_called",
                                        "imePolicy=$imePolicy keyboardControllerAvailable=$keyboardControllerAvailable " +
                                            "attached=${neView.isAttachedToWindow} ${FloatingNoteImeController.debugState(neView)}",
                                    )
                                    neView.post {
                                        onTiming(
                                            "ime_show_post",
                                            "imePolicy=$imePolicy attached=${neView.isAttachedToWindow} " +
                                                "focus=${neView.hasFocus()} ${FloatingNoteImeController.debugState(neView)}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Escape &&
                            wikilinkTriggerKey != null
                        ) {
                            wikilinkPopupDismissKey = wikilinkTriggerKey
                            true
                        } else if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            noteMatchingTags.isNotEmpty()
                        ) {
                            noteSelectTag(noteMatchingTags.first())
                            true
                        } else if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            noteMatchingWikilinks.isNotEmpty()
                        ) {
                            selectWikilink(noteMatchingWikilinks.first())
                            true
                        } else if (enterToSave &&
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            saveOrClose()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("写点什么...", color = floater.onBackgroundDim, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    inner() })
            } // end content Column (weight)
            // ── Tag autocomplete row ──
            if (tagActive2 && noteMatchingTags.isNotEmpty()) {
                QuickDailyAutocompleteSurface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 56.dp),
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(noteMatchingTags, key = { _, tag -> tag }) { _, tag ->
                            TextButton(
                                onClick = { noteSelectTag(tag) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.bodySmall, color = floater.primary)
                            }
                        }
                    }
                }
            }

            if (wikilinkTrigger != null &&
                (wikilinkIndex.loading || noteMatchingWikilinks.isNotEmpty()) &&
                wikilinkTriggerKey != wikilinkPopupDismissKey
            ) {
                QuickDailyAutocompleteSurface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                ) {
                    if (wikilinkIndex.loading) {
                        Text(
                            "正在建立双链索引…",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = floater.onBackgroundDim,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        ) {
                            items(noteMatchingWikilinks, key = { it.stableKey }) { candidate ->
                                TextButton(
                                    onClick = { selectWikilink(candidate) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start,
                                    ) {
                                        Text(
                                            candidate.displayText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = floater.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (candidate.alias != null && aliasCounts[candidate.alias] ?: 0 > 1) {
                                            Text(
                                                candidate.targetPath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = floater.onBackgroundDim,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 共享内容工具栏；保存按钮固定在右侧 ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EditorToolbarActions(
                    order = toolbarOrder,
                    visible = toolbarVisible,
                    tint = floater.primary,
                    modifier = Modifier.weight(1f),
                    buttonSize = 40.dp,
                    compact = true,
                    recording = recording,
                    recordingDurationMs = recordingDurationMs,
                    enabled = { action ->
                        when (action) {
                            EditorToolbarAction.UNDO -> localUndoStack.isNotEmpty()
                            EditorToolbarAction.REDO -> localRedoStack.isNotEmpty()
                            else -> true
                        }
                    },
                    onAction = ::handleToolbarAction,
                )
                TextButton(
                    onClick = {
                        val t = tfv.text
                        if (t.isNotBlank() || imageUris.isNotEmpty() || hasAttachments) onSave() else onClose()
                        BetaLogger.log("Toolbar", "save")
                    },
                    modifier = Modifier.width(48.dp).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text("保存", color = floater.primary, style = MaterialTheme.typography.labelMedium)
                }
            } // end toolbar Row
        }

        if (targetMenuExpanded && useInlineTargetMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable { targetMenuExpanded = false },
            )
            TargetSelectionSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f),
                targetPath = targetPath,
                targetOptions = targetOptions,
                onTargetChange = onTargetChange,
                onAddCustomPage = onAddCustomPage,
                onRemoveTarget = onRemoveTarget,
                onDismiss = { targetMenuExpanded = false },
            )
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun FullScreenNoteEditSurface(
    title: String,
    targetPath: String?,
    targetOptions: List<FloatingNoteTargetOption>,
    targetMenuExpanded: Boolean,
    onTargetMenuExpandedChange: (Boolean) -> Unit,
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onSaveOrClose: () -> Unit,
    onTargetChange: (FloatingNoteTargetOption) -> Unit,
    onAddCustomPage: () -> Unit,
    onRemoveTarget: (String) -> Unit,
    imageUris: List<Uri>,
    attachmentUris: List<Uri>,
    invalidAttachmentUris: List<Uri>,
    onRemoveImage: (Int) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onPickImages: () -> Unit,
    onPickAttachment: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleRecording: () -> Unit,
    toolbarOrder: List<String>,
    toolbarVisible: Set<String>,
    recording: Boolean,
    recordingDurationMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolbarAction: (EditorToolbarAction) -> Unit,
    enterToSave: Boolean,
    tagActive: Boolean,
    matchingTags: List<String>,
    onTagSelected: (String) -> Unit,
    wikilinkTriggerKey: String?,
    wikilinkLoading: Boolean,
    matchingWikilinks: List<WikilinkCandidate>,
    aliasCounts: Map<String, Int>,
    wikilinkPopupDismissKey: String?,
    onDismissWikilinkPopup: () -> Unit,
    onWikilinkSelected: (WikilinkCandidate) -> Unit,
    onHome: () -> Unit,
    onReturnToFloating: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardVisible = WindowInsets.isImeVisible
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    var toolbarPage by remember { mutableIntStateOf(0) }
    var toolbarPageCount by remember { mutableIntStateOf(1) }
    var autoIndentState by remember { mutableStateOf<EditorAutoIndentState?>(null) }
    val motionPolicy = LocalQuickDailyMotion.current

    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) toolbarPage = 0
    }

    val toolbarArrowRotation by animateFloatAsState(
        targetValue = when {
            keyboardVisible -> 0f
            toolbarPage > 0 -> 90f
            else -> 270f
        },
        animationSpec = if (motionPolicy.reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "fullscreenToolbarKeyboardPageArrow",
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onHome,
                            modifier = Modifier.widthIn(min = 64.dp),
                        ) {
                            Text("\u7f16\u8f91\u9875", maxLines = 1)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title.ifBlank { "\u901f\u8bb0" },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        IconButton(
                            onClick = { onTargetMenuExpandedChange(true) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "\u9009\u62e9\u8bb0\u5f55\u9875\u9762",
                            )
                        }
                        IconButton(
                            onClick = onReturnToFloating,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = "\u7f29\u56de\u60ac\u6d6e\u7a97",
                            )
                        }
                        TextButton(
                            onClick = onSave,
                            modifier = Modifier.widthIn(min = 56.dp),
                        ) {
                            Text("\u4fdd\u5b58", maxLines = 1)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        EditorToolbarActions(
                            order = toolbarOrder,
                            visible = toolbarVisible,
                            tint = MaterialTheme.colorScheme.primary,
                            enabled = { action ->
                                when (action) {
                                    EditorToolbarAction.UNDO -> canUndo
                                    EditorToolbarAction.REDO -> canRedo
                                    else -> true
                                }
                            },
                            recording = recording,
                            recordingDurationMs = recordingDurationMs,
                            buttonSize = 48.dp,
                            page = toolbarPage,
                            onPageChanged = { toolbarPage = it },
                            onPageCountChanged = { toolbarPageCount = it },
                            onAction = onToolbarAction,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (keyboardVisible) {
                                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                            } else if (toolbarPageCount > 1) {
                                toolbarPage = if (toolbarPage == 0) 1 else 0
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (keyboardVisible) "\u5173\u95ed\u952e\u76d8" else if (toolbarPage > 0) "\u8fd4\u56de\u7b2c\u4e00\u9875\u5de5\u5177" else "\u6253\u5f00\u7b2c\u4e8c\u9875\u5de5\u5177",
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = toolbarArrowRotation },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                ) {
                    if (imageUris.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(imageUris) { index, uri ->
                                Box(modifier = Modifier.size(60.dp)) {
                                    val bitmap by produceState<Bitmap?>(
                                        initialValue = null,
                                        key1 = uri,
                                    ) {
                                        value = withContext(Dispatchers.IO) {
                                            EditorThumbnailLoader.load(context.contentResolver, uri)
                                        }
                                    }
                                    bitmap?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveImage(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "\u79fb\u9664\u56fe\u7247",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (attachmentUris.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            attachmentUris.forEachIndexed { index, uri ->
                                val invalid = uri in invalidAttachmentUris
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 40.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (invalid) "\u9644\u4ef6\u4e0d\u53ef\u7528\uff1a${uri.lastPathSegment ?: uri}" else "\u9644\u4ef6\uff1a${uri.lastPathSegment ?: uri}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(onClick = { onRemoveAttachment(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "\u79fb\u9664\u9644\u4ef6")
                                    }
                                }
                            }
                        }
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val insertedNewlines = newValue.text.count { it == '\n' } - textFieldValue.text.count { it == '\n' }
                            if (enterToSave && insertedNewlines > 0 && newValue.text.length == textFieldValue.text.length + 1) {
                                autoIndentState = null
                                onSaveOrClose()
                            } else {
                                val result = EditorAutoIndentPolicy.apply(
                                    previous = textFieldValue,
                                    proposed = newValue,
                                    state = autoIndentState,
                                )
                                autoIndentState = result.state
                                onTextChange(result.value)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (enterToSave) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSaveOrClose() }),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .defaultMinSize(minHeight = 400.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    false
                                } else if (event.key == Key.Escape && wikilinkTriggerKey != null) {
                                    onDismissWikilinkPopup()
                                    true
                                } else if (event.key == Key.Enter && matchingTags.isNotEmpty()) {
                                    onTagSelected(matchingTags.first())
                                    true
                                } else if (event.key == Key.Enter && matchingWikilinks.isNotEmpty()) {
                                    onWikilinkSelected(matchingWikilinks.first())
                                    true
                                } else if (enterToSave && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                    onSaveOrClose()
                                    true
                                } else {
                                    false
                                }
                            },
                        decorationBox = { inner ->
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    "\u5199\u70b9\u4ec0\u4e48...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            if (tagActive && matchingTags.isNotEmpty()) {
                QuickDailyAutocompleteSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 56.dp),
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(matchingTags, key = { _, tag -> tag }) { _, tag ->
                            TextButton(
                                onClick = { onTagSelected(tag) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (wikilinkTriggerKey != null &&
                (wikilinkLoading || matchingWikilinks.isNotEmpty()) &&
                wikilinkTriggerKey != wikilinkPopupDismissKey
            ) {
                QuickDailyAutocompleteSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                ) {
                    if (wikilinkLoading) {
                        Text(
                            "\u6b63\u5728\u52a0\u8f7d\u9875\u9762...",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                        ) {
                            items(matchingWikilinks, key = { it.stableKey }) { candidate ->
                                TextButton(
                                    onClick = { onWikilinkSelected(candidate) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start,
                                    ) {
                                        Text(
                                            candidate.displayText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (candidate.alias != null && (aliasCounts[candidate.alias] ?: 0) > 1) {
                                            Text(
                                                candidate.targetPath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (targetMenuExpanded) {
        Dialog(onDismissRequest = { onTargetMenuExpandedChange(false) }) {
            TargetSelectionSurface(
                modifier = Modifier.fillMaxWidth(0.86f),
                targetPath = targetPath,
                targetOptions = targetOptions,
                onTargetChange = onTargetChange,
                onAddCustomPage = onAddCustomPage,
                onRemoveTarget = onRemoveTarget,
                onDismiss = { onTargetMenuExpandedChange(false) },
            )
        }
    }
}

@Composable
private fun TargetSelectionSurface(
    modifier: Modifier,
    targetPath: String?,
    targetOptions: List<FloatingNoteTargetOption>,
    onTargetChange: (FloatingNoteTargetOption) -> Unit,
    onAddCustomPage: () -> Unit,
    onRemoveTarget: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val floater = LocalFloaterColors.current
    val dim = LocalAppDimensions.current
    val motionPolicy = LocalQuickDailyMotion.current

    Surface(
        modifier = modifier.animateContentSize(animationSpec = motionPolicy.spatialSpec()),
        color = floater.background,
        shape = RoundedCornerShape(dim.radiusXl),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "记录到",
                style = MaterialTheme.typography.titleMedium,
                color = floater.onBackground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(
                    targetOptions,
                    key = { option -> option.path ?: "today" },
                ) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable {
                                onDismiss()
                                onTargetChange(option)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (targetPath == option.path) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "当前页面",
                                tint = floater.primary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                        Text(
                            option.menuTitle,
                            color = floater.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (option.path != null) {
                            IconButton(onClick = { onRemoveTarget(option.path) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除页面记录",
                                    tint = floater.onBackgroundVariant,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = {
                    onDismiss()
                    onAddCustomPage()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加自定义页面", color = floater.primary)
            }
        }
    }
}


