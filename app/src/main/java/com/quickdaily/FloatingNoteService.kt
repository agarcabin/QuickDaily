package com.quickdaily

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.quickdaily.ui.theme.LocalFloaterColors
import com.quickdaily.ui.theme.QuickDailyTheme
import com.quickdaily.ui.theme.quickDailyFloaterColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import kotlin.math.roundToInt

internal enum class FloatingNoteRecordingState {
    Idle,
    Recording,
    Finalizing,
}

internal object FloatingNoteRecordingPolicy {
    fun canStart(state: FloatingNoteRecordingState): Boolean =
        state == FloatingNoteRecordingState.Idle

    fun afterStop(state: FloatingNoteRecordingState): FloatingNoteRecordingState =
        if (state == FloatingNoteRecordingState.Recording) {
            FloatingNoteRecordingState.Finalizing
        } else {
            state
        }

    fun afterFinalize(state: FloatingNoteRecordingState): FloatingNoteRecordingState =
        if (state == FloatingNoteRecordingState.Finalizing) {
            FloatingNoteRecordingState.Idle
        } else {
            state
        }
}

class FloatingNoteService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: FloatingNoteComposeView? = null
    private lateinit var state: FloatingNoteEditorState
    private lateinit var viewTreeOwner: FloatingNoteViewTreeOwner
    private val saveUseCase by lazy { FloatingNoteSaveUseCase(applicationContext) }
    private var targetOptions by mutableStateOf<List<FloatingNoteTargetOption>>(emptyList())
    private var windowParams: WindowManager.LayoutParams? = null
    private var windowDragOrigin: FloatingNotePosition? = null
    private var windowDragX = 0f
    private var windowDragY = 0f
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var activeRequestId: String = "service"
    private var closingOverlay = false
    private var recordingStartedAt by mutableStateOf<Long?>(null)
    private var recordingElapsedMs by mutableStateOf(0L)
    private var floatingCoachStep by mutableStateOf<Int?>(null)
    private var appearanceRefreshToken by mutableIntStateOf(0)
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var closeJob: Job? = null
    @Volatile
    private var recordingState = FloatingNoteRecordingState.Idle

    override fun onCreate() {
        super.onCreate()
        FloatingNoteTiming.mark("service_create")
        BetaLogger.init(this)
        OnboardingStore.initialize(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        state = FloatingNoteEditorState(this)
        viewTreeOwner = FloatingNoteViewTreeOwner().also { it.onCreate() }
        createNotificationChannel()
        lifecycleScope.launch {
            while (isActive) {
                recordingStartedAt?.let { recordingElapsedMs = SystemClock.elapsedRealtime() - it }
                delay(250)
            }
        }
        BetaLogger.log("FloatingNote/Service", "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        FloatingNoteTiming.mark("service_start", "action=${intent?.action ?: "none"}")
        when (intent?.action) {
            ACTION_HIDE -> requestClose(intent.getStringExtra(EXTRA_REASON) ?: "user")
            ACTION_START_RECORDING -> startRecording()
            ACTION_REFRESH_APPEARANCE -> refreshAppearance()
            ACTION_REFRESH -> {
                val previousTarget = intent.getStringExtra(EXTRA_PREVIOUS_TARGET_PATH)
                val refreshSource = intent.getStringExtra(EXTRA_SOURCE)
                    ?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() }
                    ?: FloatingNoteSource.SIDEBAR
                val refreshRememberTarget = intent.getBooleanExtra(EXTRA_REMEMBER_TARGET, true)
                FloatingNoteDraftStore.loadInto(
                    this,
                    state,
                    FloatingNoteRequest(
                        refreshSource,
                        returnToHomeAfterClose = false,
                        targetRelativePath = previousTarget,
                        rememberTarget = refreshRememberTarget,
                        sourceBounds = state.sourceBounds,
                    ),
                )
                intent.getStringExtra(EXTRA_TARGET_PATH)?.let { path ->
                    switchTarget(FloatingNoteTargetOption(path, FloatingNoteTargetStore.titleFor(this, path)))
                }
                targetOptions = FloatingNoteTargetStore.options(this, state.targetRelativePath)
                ensureOverlay()
            }
            else -> {
                val request = requestFromIntent(intent)
                activeRequestId = request.requestId
                FloatingNoteTiming.begin(request.requestId, request.source)
                state.source = request.source
                request.sourceBounds?.let { state.sourceBounds = Rect(it) }
                state.rememberTarget = request.rememberTarget
                if (overlayView == null) {
                    FloatingNoteDraftStore.loadInto(this, state, request)
                } else {
                    if (FloatingNoteDraftTargetPolicy.keyFor(this, request.targetRelativePath) !=
                        FloatingNoteDraftTargetPolicy.keyFor(this, state.targetRelativePath)
                    ) {
                        switchTarget(FloatingNoteTargetOption(
                            request.targetRelativePath,
                            request.displayTitle ?: FloatingNoteTargetStore.titleFor(this, request.targetRelativePath),
                        ))
                    }
                    BetaLogger.log(
                        "FloatingNote/Window",
                        "focus existing source=${request.source} target=${request.targetRelativePath.orEmpty()} imeRequestIgnored=true",
                    )
                }
                ensureOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureOverlay() {
        floatingCoachStep = if (OnboardingStore.shouldShowFloatingCoach(this)) {
            OnboardingStore.floatingCoachStep(this)
        } else {
            null
        }
        if (overlayView != null) {
            FloatingNoteLaunchGate.release()
            clampOverlayToScreen()
            BetaLogger.log("FloatingNote/Window", "focus existing source=${state.source}")
            FloatingNoteTiming.mark("focus_existing")
            return
        }

        try {
            closingOverlay = false
            targetOptions = FloatingNoteTargetStore.options(this, state.targetRelativePath)
            val completionPrefs = getSharedPreferences("QuickDaily", MODE_PRIVATE)
            // Launcher starts this service from a user-visible Activity. Starting the
            // foreground service immediately keeps the overlay alive on Android 14+.
            FloatingNoteTiming.mark("foreground_start")
            startForeground(NOTIFICATION_ID, buildNotification())
            FloatingNoteTiming.mark("foreground_ready")
            val view = FloatingNoteComposeView(this) { requestClose("back") }.apply {
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                        requestClose("back")
                        true
                    } else false
                }
                setViewTreeLifecycleOwner(viewTreeOwner)
                setViewTreeSavedStateRegistryOwner(viewTreeOwner)
                setViewTreeViewModelStoreOwner(viewTreeOwner)
                FloatingNoteTiming.mark("compose_content_set_start", "host=overlay")
                setComposeContent {
                    val monetEnabled = completionPrefs.getBoolean("theme_use_monet", true)
                    BetaLogger.log(
                        "FloatingNote/Theme",
                        "source=${state.source} monetEnabled=$monetEnabled accent=${completionPrefs.getString("theme_accent_preset", "blue")} nightMode=${completionPrefs.getString("theme_night_mode", "system")}",
                    )
                    QuickDailyTheme(
                        nightModeOverride = FloatingNoteAppearance.nightMode(this@FloatingNoteService),
                        refreshKey = appearanceRefreshToken,
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalFloaterColors provides quickDailyFloaterColors()) {
                            NoteEditDialog(
                                text = state.text,
                                onTextChange = {
                                    state.text = it
                                    FloatingNoteDraftStore.persistOrClear(this@FloatingNoteService, state)
                                },
                                onSelectionChange = { selection ->
                                    state.selectionStart = selection.start
                                    state.selectionEnd = selection.end
                                    FloatingNoteDraftStore.persistOrClear(this@FloatingNoteService, state)
                                },
                                enterToSave = state.enterToSave,
                                tagAutocomplete = completionPrefs.getBoolean("tag_autocomplete", true),
                                wikilinkAutocomplete = completionPrefs.getBoolean("wikilink_autocomplete", true),
                                title = state.displayTitle.orEmpty(),
                                targetPath = state.targetRelativePath,
                                targetOptions = targetOptions,
                                onTargetChange = { option -> switchTarget(option) },
                                onAddCustomPage = {
                                    openPicker(FloatingNotePickerActivity.MODE_CUSTOM_PAGE)
                                },
                                onRemoveTarget = { path ->
                                    BetaLogger.log("FloatingNote/Selection", "overlay remove path=$path")
                                    if (state.targetRelativePath == path) {
                                        state.targetRelativePath = null
                                        state.displayTitle = FloatingNoteTargetStore.titleFor(this@FloatingNoteService, null)
                                        FloatingNoteDraftStore.persistOrClear(this@FloatingNoteService, state)
                                        if (state.rememberTarget) {
                                            FloatingNoteTargetMemory.remember(this@FloatingNoteService, state.source, null)
                                        }
                                    }
                                    TaskWidgetConfigStore.removeCustomPage(this@FloatingNoteService, path)
                                    targetOptions = FloatingNoteTargetStore.options(
                                        this@FloatingNoteService,
                                        state.targetRelativePath,
                                    )
                                },
                                useInlineTargetMenu = true,
                                onSave = { saveDraft() },
                                onClose = { requestClose("close") },
                                onFullScreen = ::openFullScreen,
                                floatingCoachStep = floatingCoachStep,
                                onFloatingCoachPrevious = {
                                    floatingCoachStep = OnboardingStore.previousFloatingCoach(this@FloatingNoteService)
                                },
                                onFloatingCoachNext = {
                                    floatingCoachStep = OnboardingStore.advanceFloatingCoach(this@FloatingNoteService)
                                },
                                onFloatingCoachSkip = {
                                    OnboardingStore.skipFloatingCoach(this@FloatingNoteService)
                                    floatingCoachStep = null
                                },
                                onHome = { requestClose("home", openHomeAfterClose = true) },
                                imageUris = state.selectedImages,
                                hasAttachments = state.pendingAttachments.isNotEmpty() || state.invalidAttachments.isNotEmpty(),
                                attachmentUris = state.pendingAttachments + state.invalidAttachments,
                                invalidAttachmentUris = state.invalidAttachments,
                                imePolicy = FloatingNoteImePolicy.OverlayInstant,
                                onTiming = { stage, detail ->
                                    val hostView = overlayView
                                    FloatingNoteTiming.mark(
                                        stage,
                                        "host=overlay imePolicy=OverlayInstant requestId=$activeRequestId " +
                                            "source=${state.source} target=${state.targetRelativePath.orEmpty()} " +
                                            "attached=${hostView?.isAttachedToWindow == true} laidOut=${hostView?.isLaidOut == true} " +
                                            "focus=${hostView?.hasFocus() == true} windowFocus=${hostView?.hasWindowFocus() == true} " +
                                            "imeState=${hostView?.let { FloatingNoteImeController.debugState(it) }.orEmpty()} " +
                                            "detail=${detail.orEmpty()}",
                                    )
                                },
                                onPickImages = { openPicker(FloatingNotePickerActivity.MODE_IMAGES) },
                                onPickAttachment = { openPicker(FloatingNotePickerActivity.MODE_ATTACHMENT) },
                                onTakePhoto = { openPicker(FloatingNotePickerActivity.MODE_CAMERA) },
                                onToggleRecording = {
                                    when {
                                        recordingState == FloatingNoteRecordingState.Finalizing -> {
                                            Toast.makeText(
                                                this@FloatingNoteService,
                                                "正在保存上一段录音",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        recorder != null -> stopRecording()
                                        else -> openPicker(FloatingNotePickerActivity.MODE_RECORD_PERMISSION)
                                    }
                                },
                                toolbarOrder = toolbarOrder(),
                                toolbarVisible = toolbarVisible(),
                                recording = recorder != null,
                                recordingDurationMs = recordingElapsedMs,
                                initialSelection = androidx.compose.ui.text.TextRange(
                                    state.selectionStart,
                                    state.selectionEnd,
                                ),
                                onMoveWindowStart = ::beginMoveOverlay,
                                onMoveWindow = ::moveOverlay,
                                onMoveWindowEnd = ::persistWindowPosition,
                                onRemoveImage = { index ->
                                    if (index in state.selectedImages.indices) {
                                        state.selectedImages.removeAt(index)
                                        FloatingNoteDraftStore.persistOrClear(this@FloatingNoteService, state)
                                    }
                                },
                                onRemoveAttachment = { index ->
                                    val all = state.pendingAttachments.toList() + state.invalidAttachments.toList()
                                    all.getOrNull(index)?.let { uri ->
                                        state.pendingAttachments.remove(uri)
                                        state.invalidAttachments.remove(uri)
                                        state.text = state.text.replace("![[${uri}]]", "").replace("\n\n", "\n")
                                        FloatingNoteDraftStore.persistOrClear(this@FloatingNoteService, state)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.88f).toInt()
                .coerceAtLeast(dp(280))
                .coerceAtMost(dm.widthPixels)
            val height = (dm.heightPixels * 0.35f).toInt().coerceAtLeast(dp(220))
            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = FloatingNoteAppearance.alpha(this@FloatingNoteService)
                val fallback = FloatingNotePositionPolicy.defaultPosition(
                    dm.widthPixels,
                    dm.heightPixels,
                    width,
                    height,
                )
                val position = FloatingNotePositionPolicy.clamp(
                    FloatingNotePositionPolicy.load(this@FloatingNoteService, fallback),
                    dm.widthPixels,
                    dm.heightPixels,
                    width,
                    height,
                )
                x = position.x
                y = position.y
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                windowAnimations = 0
            }
            FloatingNoteTiming.mark("window_add_start", "width=$width height=$height")
            windowManager.addView(view, params)
            overlayView = view
            windowParams = params
            FloatingNotePositionPolicy.save(this, FloatingNotePosition(params.x, params.y))
            isWindowShowing = true
            view.post {
                FloatingNoteTiming.mark(
                    "overlay_post_add",
                    "host=overlay attached=${view.isAttachedToWindow} laidOut=${view.isLaidOut} " +
                        "focus=${view.hasFocus()} windowFocus=${view.hasWindowFocus()} " +
                        FloatingNoteImeController.debugState(view),
                )
            }
            FloatingNoteLaunchGate.release()
            FloatingNoteTiming.mark("window_add_end")
            BetaLogger.log("FloatingNote/Window", "show type=TYPE_APPLICATION_OVERLAY width=$width height=$height")
            FloatingNoteTiming.mark("overlay_ready")
            FloatingNoteHandoff.notifyReady(activeRequestId)
        } catch (error: Throwable) {
            BetaLogger.logException("FloatingNote/Window", "add_failed=${error.javaClass.simpleName}", error)
            overlayView = null
            isWindowShowing = false
            FloatingNoteLaunchGate.release()
            Toast.makeText(this, "悬浮窗启动失败，已返回首页", Toast.LENGTH_SHORT).show()
            openHome()
        }
    }

    private fun switchTarget(option: FloatingNoteTargetOption) {
        val oldTarget = state.targetRelativePath
        if (FloatingNoteDraftTargetPolicy.keyFor(this, oldTarget) ==
            FloatingNoteDraftTargetPolicy.keyFor(this, option.path)
        ) return
        if (state.rememberTarget) {
            FloatingNoteTargetMemory.remember(this, state.source, option.path)
        }
        FloatingNoteDraftStore.persistOrClear(this, state)
        FloatingNoteDraftStore.loadInto(
            this,
            state,
            FloatingNoteRequest(
                source = state.source,
                returnToHomeAfterClose = state.returnToHomeAfterClose,
                targetRelativePath = option.path,
                displayTitle = option.title,
                 rememberTarget = state.rememberTarget,
            ),
        )
        targetOptions = FloatingNoteTargetStore.options(this, state.targetRelativePath)
        BetaLogger.log(
            "FloatingNote/Selection",
            "switch old=${oldTarget.orEmpty()} new=${state.targetRelativePath.orEmpty()} restoredTextLength=${state.text.length}",
        )
    }

    private fun requestClose(reason: String, openHomeAfterClose: Boolean = false) {
        if (overlayView == null || closingOverlay || closeJob?.isActive == true) return
        FloatingNoteTiming.mark("close_requested", "reason=$reason target=${state.targetRelativePath.orEmpty()}")
        if (FloatingNoteExitPolicy.shouldDiscard(reason)) {
            FloatingNoteDraftStore.clear(this, state.targetRelativePath)
            completeClose(reason, openHomeAfterClose)
            return
        }
        val saveOnClose = FloatingNoteEntryPolicy.shouldSaveOnClose(this)
        if (!FloatingNoteExitPolicy.shouldSave(reason, saveOnClose, state.hasContent())) {
            FloatingNoteDraftStore.persistOrClear(this, state)
            completeClose(reason, openHomeAfterClose)
            return
        }
        state.isSaving = true
        FloatingNoteDraftStore.persist(this, state)
        closeJob = lifecycleScope.launch {
            val result = try {
                saveUseCase.save(
                    state.text,
                    state.selectedImages.toList(),
                    state.pendingAttachments.toList(),
                    targetRelativePath = state.targetRelativePath,
                )
            } catch (error: Throwable) {
                FloatingNoteSaveResult.Failed(error.message ?: "save failed")
            }
            withContext(Dispatchers.Main) {
                closeJob = null
                state.isSaving = false
                when (result) {
                    FloatingNoteSaveResult.Saved -> {
                        FloatingNoteDraftStore.clear(this@FloatingNoteService, state.targetRelativePath)
                        val openedObsidian = FloatingNoteObsidianLauncher.openAfterSuccessfulSave(
                            this@FloatingNoteService,
                            state.targetRelativePath,
                        )
                        completeClose(reason, openHomeAfterClose && !openedObsidian)
                    }
                    FloatingNoteSaveResult.NoContent -> {
                        FloatingNoteDraftStore.clear(this@FloatingNoteService, state.targetRelativePath)
                        completeClose(reason, openHomeAfterClose)
                    }
                    is FloatingNoteSaveResult.Failed -> {
                        BetaLogger.log("FloatingNote/Close", "save_failed reason=$reason message=${result.message}")
                        Toast.makeText(this@FloatingNoteService, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    private fun completeClose(reason: String, openHomeAfterClose: Boolean) {
        if (openHomeAfterClose) openHome() else hideOverlay(reason, persistDraft = false)
    }
    private fun saveDraft() {
        FloatingNoteTiming.mark("save_requested")
        if (state.isSaving) return
        if (!state.hasContent()) {
            requestClose("save_empty")
            return
        }
        state.isSaving = true
        FloatingNoteDraftStore.persistOrClear(this, state)
        BetaLogger.log(
            "FloatingNote/Save",
            "images=${state.selectedImages.size} attachments=${state.pendingAttachments.size}"
        )
        lifecycleScope.launch {
            FloatingNoteTiming.mark("save_start")
            val result = try {
                saveUseCase.save(
                    state.text,
                    state.selectedImages.toList(),
                    state.pendingAttachments.toList(),
                    targetRelativePath = state.targetRelativePath,
                )
            } catch (error: Throwable) {
                FloatingNoteSaveResult.Failed(error.message ?: "save failed")
            }
            withContext(Dispatchers.Main) {
                FloatingNoteTiming.mark("save_use_case_done", "result=${result::class.simpleName}")
                state.isSaving = false
                when (result) {
                    FloatingNoteSaveResult.Saved -> {
                        FloatingNoteDraftStore.clear(this@FloatingNoteService, state.targetRelativePath)
                        if (!FloatingNoteEntryPolicy.isSystemSidebarSupportEnabled(this@FloatingNoteService)) {
                            Toast.makeText(this@FloatingNoteService, "已保存", Toast.LENGTH_SHORT).show()
                        }
                        FloatingNoteObsidianLauncher.openAfterSuccessfulSave(
                            this@FloatingNoteService,
                            state.targetRelativePath,
                        )
                        hideOverlay("saved", persistDraft = false)
                    }
                    FloatingNoteSaveResult.NoContent -> {
                        FloatingNoteDraftStore.clear(this@FloatingNoteService, state.targetRelativePath)
                        completeClose("saved_empty", state.returnToHomeAfterClose)
                    }
                    is FloatingNoteSaveResult.Failed -> {
                        BetaLogger.log("FloatingNote/Save", "failed=${result.message}")
                        Toast.makeText(this@FloatingNoteService, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openPicker(mode: String) {
        FloatingNoteDraftStore.persistOrClear(this, state)
        if (mode == FloatingNotePickerActivity.MODE_IMAGES ||
            mode == FloatingNotePickerActivity.MODE_ATTACHMENT ||
            mode == FloatingNotePickerActivity.MODE_CUSTOM_PAGE ||
            mode == FloatingNotePickerActivity.MODE_CAMERA ||
            mode == FloatingNotePickerActivity.MODE_RECORD_PERMISSION
        ) {
            // TYPE_APPLICATION_OVERLAY can remain above system pickers and
            // permission surfaces. Remove it before launching the short-lived
            // Activity; the picker restores it on success or cancellation.
            hideOverlay("${mode}_picker", persistDraft = true)
        }
        startActivity(Intent(this, FloatingNotePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(FloatingNotePickerActivity.EXTRA_MODE, mode)
            putExtra(FloatingNotePickerActivity.EXTRA_TARGET_PATH, state.targetRelativePath)
            putExtra(FloatingNoteService.EXTRA_SOURCE, state.source.name)
            putExtra(FloatingNoteService.EXTRA_REMEMBER_TARGET, state.rememberTarget)
        })
        BetaLogger.log("FloatingNote/Window", "picker mode=$mode")
    }

    private fun startRecording() {
        if (!FloatingNoteRecordingPolicy.canStart(recordingState)) {
            if (recordingState == FloatingNoteRecordingState.Finalizing) {
                Toast.makeText(this, "正在保存上一段录音", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (recorder != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请允许录音权限后再录音", Toast.LENGTH_SHORT).show()
            return
        }
        val file = runCatching { CaptureFileUtil.newAudioFile(this) }.getOrNull()
        if (file == null) {
            Toast.makeText(this, "无法创建录音文件", Toast.LENGTH_SHORT).show()
            return
        }
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
        recordingState = FloatingNoteRecordingState.Recording
        recordingStartedAt = SystemClock.elapsedRealtime()
        FloatingNoteDraftStore.persistOrClear(this, state)
    }

    private fun stopRecording(refreshOverlay: Boolean = isWindowShowing) {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        recordingStartedAt = null
        recordingElapsedMs = 0L
        recordingState = FloatingNoteRecordingPolicy.afterStop(recordingState)
        val stopped = runCatching { activeRecorder.stop() }.isSuccess
        runCatching { activeRecorder.reset() }
        runCatching { activeRecorder.release() }
        if (!stopped || file == null) {
            file?.delete()
            recordingState = FloatingNoteRecordingPolicy.afterFinalize(recordingState)
            return
        }
        recordingJob = recordingScope.launch {
            val link = runCatching { EditorMediaUtil.audioLink(this@FloatingNoteService, file) }.getOrNull()
            if (link != null) {
                file.delete()
                FloatingNoteDraftStore.insertLink(this@FloatingNoteService, link, state.targetRelativePath)
                if (refreshOverlay) {
                    withContext(Dispatchers.Main) {
                        if (overlayView != null) {
                            FloatingNoteDraftStore.loadInto(
                                this@FloatingNoteService,
                                state,
                                FloatingNoteRequest(state.source, returnToHomeAfterClose = false, targetRelativePath = state.targetRelativePath, rememberTarget = state.rememberTarget),
                            )
                            targetOptions = FloatingNoteTargetStore.options(this@FloatingNoteService, state.targetRelativePath)
                        }
                    }
                }
            } else if (refreshOverlay) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingNoteService, "录音保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (recordingJob === job) recordingJob = null
                recordingState = FloatingNoteRecordingPolicy.afterFinalize(recordingState)
                if (closingOverlay) recordingScope.cancel()
            }
        }
    }

    private fun beginMoveOverlay() {
        val params = windowParams ?: return
        windowDragOrigin = FloatingNotePosition(params.x, params.y)
        windowDragX = 0f
        windowDragY = 0f
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val params = windowParams ?: return
        val origin = windowDragOrigin ?: FloatingNotePosition(params.x, params.y).also {
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
            params.width,
            params.height,
        )
        params.x = position.x
        params.y = position.y
        overlayView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { error ->
                    BetaLogger.logException("FloatingNote/Window", "move_failed", error)
                }
        }
    }

    private fun persistWindowPosition() {
        windowParams?.let { FloatingNotePositionPolicy.save(this, FloatingNotePosition(it.x, it.y)) }
        windowDragOrigin = null
        windowDragX = 0f
        windowDragY = 0f
    }

    private fun clampOverlayToScreen() {
        val params = windowParams ?: return
        val dm = resources.displayMetrics
        val position = FloatingNotePositionPolicy.clamp(
            FloatingNotePosition(params.x, params.y),
            dm.widthPixels,
            dm.heightPixels,
            params.width,
            params.height,
        )
        if (position.x != params.x || position.y != params.y) {
            params.x = position.x
            params.y = position.y
            overlayView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
        }
        FloatingNotePositionPolicy.save(this, position)
    }

    private fun toolbarOrder(): List<String> {
        val prefs = getSharedPreferences("QuickDaily", MODE_PRIVATE)
        return EditorToolbarPolicy.migrateOrder(
            prefs.getString(EditorToolbarPolicy.PREF_ORDER, null),
            prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0) < EditorToolbarPolicy.CURRENT_SCHEMA_VERSION,
        )
    }

    private fun toolbarVisible(): Set<String> {
        val prefs = getSharedPreferences("QuickDaily", MODE_PRIVATE)
        return if (prefs.contains(EditorToolbarPolicy.PREF_VISIBLE)) {
            EditorToolbarPolicy.readVisible(
                prefs.getString(EditorToolbarPolicy.PREF_VISIBLE, null),
                prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0),
            )
        } else EditorToolbarPolicy.defaultVisible
    }

    private fun openHome() {
        startActivity(MainActivity.editorIntent(this, state.targetRelativePath))
        hideOverlay("home", persistDraft = false)
    }

    private fun openFullScreen() {
        if (closingOverlay) return
        FloatingNoteDraftStore.persistOrClear(this, state)
        if (floatingCoachStep == OnboardingPolicy.FLOATING_COACH_STEP_COUNT - 2) {
            floatingCoachStep = OnboardingStore.advanceFloatingCoach(this)
        }
        val baseTitle = state.displayTitle.orEmpty().ifBlank {
            FloatingNoteTargetStore.titleFor(this, state.targetRelativePath)
        }
        val title = if (baseTitle.endsWith("速录")) baseTitle else "$baseTitle 速录"
        BetaLogger.log(
            "FloatingNote/Fullscreen",
            "open source=" + state.source + " target=" + state.targetRelativePath.orEmpty(),
        )
        startActivity(
            NoteEditActivity.fullScreenIntent(
                context = this,
                source = state.source,
                targetRelativePath = state.targetRelativePath,
                title = title,
                sourceBounds = state.sourceBounds,
            )
        )
        hideOverlay("fullscreen", persistDraft = false)
    }
    private fun hideOverlay(reason: String, persistDraft: Boolean = true) {
        if (closingOverlay) {
            FloatingNoteTiming.mark("hide_ignored", "reason=$reason alreadyClosing=true")
            return
        }
        closingOverlay = true
        FloatingNoteTiming.mark("hide_start", "reason=$reason")
        BetaLogger.log("FloatingNote/Window", "hide reason=$reason")
        stopRecording(refreshOverlay = false)
        persistWindowPosition()
        if (persistDraft) {
            FloatingNoteDraftStore.persistOrClear(this, state)
        }
        val finishHide = {
            overlayView = null
            windowParams = null
            isWindowShowing = false
            FloatingNoteLaunchGate.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            FloatingNoteTiming.mark("hide_end", "reason=$reason")
        }
        overlayView?.let { view ->
            FloatingNoteTiming.mark(
                "ime_hide_request",
                "host=overlay reason=$reason attached=${view.isAttachedToWindow} focus=${view.hasFocus()} " +
                    FloatingNoteImeController.debugState(view),
            )
            FloatingNoteImeController.hide(view) { stage, detail ->
                FloatingNoteTiming.mark(
                    stage,
                    "host=overlay reason=$reason attached=${view.isAttachedToWindow} focus=${view.hasFocus()} " +
                        "${FloatingNoteImeController.debugState(view)} detail=${detail.orEmpty()}",
                )
            }
            view.clearFocus()
            val finishRemoval = {
                FloatingNoteTiming.mark("window_remove_start", "reason=$reason")
                runCatching { windowManager.removeViewImmediate(view) }
                    .onFailure { error ->
                        BetaLogger.logException("FloatingNote/Window", "remove_immediate_failed", error)
                }
                FloatingNoteTiming.mark("window_remove_end", "reason=$reason")
                runCatching { view.disposeComposition() }
                finishHide()
            }
            val animated = if (reason == "back") {
                FloatingNoteExitAnimator.animateToSource(
                    view = view,
                    source = state.source,
                    sourceBounds = state.sourceBounds,
                    onEnd = finishRemoval,
                )
            } else {
                false
            }
            if (!animated) finishRemoval()
        }
            ?: finishHide()
    }

    override fun onDestroy() {
        closeJob?.cancel()
        closeJob = null
        closingOverlay = true
        stopRecording()
        if (recordingJob?.isActive != true) recordingScope.cancel()
        persistWindowPosition()
        overlayView?.let { view ->
            FloatingNoteImeController.hide(view) { stage, detail ->
                FloatingNoteTiming.mark(
                    stage,
                    "host=overlay lifecycle=destroy attached=${view.isAttachedToWindow} focus=${view.hasFocus()} " +
                        "${FloatingNoteImeController.debugState(view)} detail=${detail.orEmpty()}",
                )
            }
            view.clearFocus()
            runCatching { windowManager.removeViewImmediate(view) }
            runCatching { view.disposeComposition() }
        }
        overlayView = null
        windowParams = null
        isWindowShowing = false
        FloatingNoteLaunchGate.release()
        viewTreeOwner.onDestroy()
        BetaLogger.log("FloatingNote/Service", "destroyed")
        super.onDestroy()
    }

    private fun requestFromIntent(intent: Intent?): FloatingNoteRequest {
        val source = intent?.getStringExtra(EXTRA_SOURCE)
            ?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() }
            ?: FloatingNoteSource.SIDEBAR
        return FloatingNoteRequest(
            source = source,
            prefillText = intent?.getStringExtra(EXTRA_PREFILL).orEmpty(),
            returnToHomeAfterClose = intent?.getBooleanExtra(EXTRA_RETURN_HOME, false) ?: false,
            targetRelativePath = intent?.getStringExtra(EXTRA_TARGET_PATH),
            displayTitle = intent?.getStringExtra(EXTRA_DISPLAY_TITLE),
            requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
                ?.takeIf { it.isNotBlank() }
                ?: newFloatingNoteRequestId(),
            rememberTarget = intent?.getBooleanExtra(EXTRA_REMEMBER_TARGET, true) ?: true,
            sourceBounds = intent?.sourceBounds?.let(::Rect),
        )
    }

    private fun buildNotification(): Notification {
        val closeIntent = PendingIntent.getService(
            this,
            1001,
            hideIntent(this, "notification"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_add)
            .setContentTitle("QuickDaily 速记悬浮窗")
            .setContentText("悬浮窗正在运行")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", closeIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "速记悬浮窗", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "QuickDaily 速记悬浮窗运行状态"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshAppearance() {
        appearanceRefreshToken++
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.alpha = FloatingNoteAppearance.alpha(this)
        runCatching { windowManager.updateViewLayout(view, params) }
        BetaLogger.log(
            "FloatingNote/Appearance",
            "refreshed nightMode=${FloatingNoteAppearance.nightMode(this).key} opacity=${FloatingNoteAppearance.percent(this)}",
        )
    }

    companion object {
        private const val CHANNEL_ID = "floating_note"
        private const val NOTIFICATION_ID = 1702
        private const val ACTION_SHOW = "com.quickdaily.action.FLOATING_NOTE_SHOW"
        private const val ACTION_HIDE = "com.quickdaily.action.FLOATING_NOTE_HIDE"
        private const val ACTION_REFRESH = "com.quickdaily.action.FLOATING_NOTE_REFRESH"
        private const val ACTION_REFRESH_APPEARANCE = "com.quickdaily.action.FLOATING_NOTE_REFRESH_APPEARANCE"
        private const val ACTION_START_RECORDING = "com.quickdaily.action.FLOATING_NOTE_START_RECORDING"
        private const val EXTRA_REMEMBER_TARGET = "floating_remember_target"
        private const val EXTRA_SOURCE = "floating_source"
        private const val EXTRA_PREFILL = "floating_prefill"
        private const val EXTRA_RETURN_HOME = "floating_return_home"
        private const val EXTRA_TARGET_PATH = "floating_target_path"
        private const val EXTRA_DISPLAY_TITLE = "floating_display_title"
        private const val EXTRA_REQUEST_ID = "floating_request_id"
        private const val EXTRA_REASON = "floating_reason"
        private const val EXTRA_PREVIOUS_TARGET_PATH = "floating_previous_target_path"

        @Volatile
        var isWindowShowing: Boolean = false

        fun showIntent(context: Context, request: FloatingNoteRequest): Intent =
            Intent(context, FloatingNoteService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_SOURCE, request.source.name)
                putExtra(EXTRA_PREFILL, request.prefillText)
                putExtra(EXTRA_RETURN_HOME, request.returnToHomeAfterClose)
                putExtra(EXTRA_TARGET_PATH, request.targetRelativePath)
                putExtra(EXTRA_DISPLAY_TITLE, request.displayTitle)
                putExtra(EXTRA_REMEMBER_TARGET, request.rememberTarget)
                putExtra(EXTRA_REQUEST_ID, request.requestId)
                request.sourceBounds?.let { setSourceBounds(Rect(it)) }
            }

        fun hideIntent(context: Context, reason: String): Intent =
            Intent(context, FloatingNoteService::class.java).apply {
                action = ACTION_HIDE
                putExtra(EXTRA_REASON, reason)
            }

        fun refreshIntent(
            context: Context,
            previousTargetPath: String? = null,
            source: FloatingNoteSource = FloatingNoteSource.SIDEBAR,
            rememberTarget: Boolean = true,
            selectedTargetPath: String? = null,
        ): Intent = Intent(context, FloatingNoteService::class.java).apply {
            action = ACTION_REFRESH
            putExtra(EXTRA_PREVIOUS_TARGET_PATH, previousTargetPath)
            putExtra(EXTRA_SOURCE, source.name)
            putExtra(EXTRA_REMEMBER_TARGET, rememberTarget)
            putExtra(EXTRA_TARGET_PATH, selectedTargetPath)
        }

        fun refreshAppearanceIntent(context: Context): Intent =
            Intent(context, FloatingNoteService::class.java).apply {
                action = ACTION_REFRESH_APPEARANCE
            }

        fun startRecordingIntent(context: Context): Intent =
            Intent(context, FloatingNoteService::class.java).apply { action = ACTION_START_RECORDING }
    }
}
