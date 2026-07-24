package com.quickdaily

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.quickdaily.ui.theme.LocalFloaterColors
import com.quickdaily.ui.theme.QuickDailyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingNoteService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: FloatingNoteComposeView? = null
    private lateinit var state: FloatingNoteEditorState
    private lateinit var viewTreeOwner: FloatingNoteViewTreeOwner
    private val saveUseCase by lazy { FloatingNoteSaveUseCase(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        FloatingNoteTiming.mark("service_create")
        BetaLogger.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        state = FloatingNoteEditorState(this)
        viewTreeOwner = FloatingNoteViewTreeOwner().also { it.onCreate() }
        createNotificationChannel()
        BetaLogger.log("FloatingNote/Service", "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        FloatingNoteTiming.mark("service_start", "action=${intent?.action ?: "none"}")
        when (intent?.action) {
            ACTION_HIDE -> hideOverlay(intent.getStringExtra(EXTRA_REASON) ?: "user")
            ACTION_REFRESH -> {
                FloatingNoteDraftStore.loadInto(
                    this,
                    state,
                    FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false)
                )
                ensureOverlay()
            }
            else -> {
                val request = requestFromIntent(intent)
                FloatingNoteDraftStore.loadInto(this, state, request)
                ensureOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureOverlay() {
        if (overlayView != null) {
            BetaLogger.log("FloatingNote/Window", "focus existing source=${state.source}")
            FloatingNoteTiming.mark("focus_existing")
            return
        }

        try {
            // Launcher starts this service from a user-visible Activity. Starting the
            // foreground service immediately keeps the overlay alive on Android 14+.
            FloatingNoteTiming.mark("foreground_start")
            startForeground(NOTIFICATION_ID, buildNotification())
            FloatingNoteTiming.mark("foreground_ready")
            val view = FloatingNoteComposeView(this) { finishFromClose() }.apply {
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                        finishFromClose()
                        true
                    } else false
                }
                setViewTreeLifecycleOwner(viewTreeOwner)
                setViewTreeSavedStateRegistryOwner(viewTreeOwner)
                setViewTreeViewModelStoreOwner(viewTreeOwner)
                FloatingNoteTiming.mark("compose_content_set")
                setComposeContent {
                    QuickDailyTheme {
                        androidx.compose.runtime.CompositionLocalProvider(LocalFloaterColors provides com.quickdaily.ui.theme.FloaterColors()) {
                            NoteEditDialog(
                                text = state.text,
                                onTextChange = {
                                    state.text = it
                                    FloatingNoteDraftStore.persist(this@FloatingNoteService, state)
                                },
                                enterToSave = state.enterToSave,
                                onSave = { saveDraft() },
                                onClose = { finishFromClose() },
                                onHome = { openHome() },
                                imageUris = state.selectedImages,
                                hasAttachments = state.pendingAttachments.isNotEmpty(),
                                attachmentUris = state.pendingAttachments,
                                onTiming = { stage, detail -> FloatingNoteTiming.mark(stage, detail) },
                                onPickImages = { openPicker(FloatingNotePickerActivity.MODE_IMAGES) },
                                onPickAttachment = { openPicker(FloatingNotePickerActivity.MODE_ATTACHMENT) },
                                onRemoveImage = { index ->
                                    if (index in state.selectedImages.indices) {
                                        state.selectedImages.removeAt(index)
                                        FloatingNoteDraftStore.persist(this@FloatingNoteService, state)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.88f).toInt().coerceAtLeast(dp(280))
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
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (dm.heightPixels * 0.25f).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            FloatingNoteTiming.mark("window_add_start", "width=$width height=$height")
            windowManager.addView(view, params)
            overlayView = view
            isWindowShowing = true
            FloatingNoteTiming.mark("window_add_end")
            BetaLogger.log("FloatingNote/Window", "show type=TYPE_APPLICATION_OVERLAY width=$width height=$height")
        } catch (error: Throwable) {
            BetaLogger.log("FloatingNote/Window", "add_failed=${error.javaClass.simpleName}")
            overlayView = null
            isWindowShowing = false
            Toast.makeText(this, "悬浮窗启动失败，已返回首页", Toast.LENGTH_SHORT).show()
            openHome()
        }
    }

    private fun saveDraft() {
        FloatingNoteTiming.mark("save_requested")
        if (state.isSaving) return
        if (!state.hasContent()) {
            finishFromClose()
            return
        }
        state.isSaving = true
        FloatingNoteDraftStore.persist(this, state)
        lifecycleScope.launch {
            FloatingNoteTiming.mark("save_start")
            val result = saveUseCase.save(
                state.text,
                state.selectedImages.toList(),
                state.pendingAttachments.toList()
            )
            withContext(Dispatchers.Main) {
                FloatingNoteTiming.mark("save_use_case_done", "result=${result::class.simpleName}")
                state.isSaving = false
                when (result) {
                    FloatingNoteSaveResult.Saved -> {
                        FloatingNoteDraftStore.clear(this@FloatingNoteService)
                        Toast.makeText(this@FloatingNoteService, "已保存", Toast.LENGTH_SHORT).show()
                        hideOverlay("saved", persistDraft = false)
                    }
                    FloatingNoteSaveResult.NoContent -> finishFromClose()
                    is FloatingNoteSaveResult.Failed -> {
                        BetaLogger.log("FloatingNote/Save", "failed=${result.message}")
                        Toast.makeText(this@FloatingNoteService, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openPicker(mode: String) {
        FloatingNoteDraftStore.persist(this, state)
        startActivity(Intent(this, FloatingNotePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(FloatingNotePickerActivity.EXTRA_MODE, mode)
        })
        BetaLogger.log("FloatingNote/Window", "picker mode=$mode")
    }

    private fun finishFromClose() {
        FloatingNoteDraftStore.persist(this, state)
        if (state.returnToHomeAfterClose) openHome() else hideOverlay("close")
    }

    private fun openHome() {
        FloatingNoteDraftStore.persist(this, state)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        hideOverlay("home")
    }

    private fun hideOverlay(reason: String, persistDraft: Boolean = true) {
        FloatingNoteTiming.mark("hide_start", "reason=$reason")
        BetaLogger.log("FloatingNote/Window", "hide reason=$reason")
        if (persistDraft) {
            FloatingNoteDraftStore.persist(this, state)
        }
        overlayView?.let { view ->
            runCatching { view.disposeComposition() }
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        isWindowShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        FloatingNoteTiming.mark("hide_end", "reason=$reason")
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { view.disposeComposition() }
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        isWindowShowing = false
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
            returnToHomeAfterClose = intent?.getBooleanExtra(EXTRA_RETURN_HOME, false) ?: false
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

    companion object {
        private const val CHANNEL_ID = "floating_note"
        private const val NOTIFICATION_ID = 1702
        private const val ACTION_SHOW = "com.quickdaily.action.FLOATING_NOTE_SHOW"
        private const val ACTION_HIDE = "com.quickdaily.action.FLOATING_NOTE_HIDE"
        private const val ACTION_REFRESH = "com.quickdaily.action.FLOATING_NOTE_REFRESH"
        private const val EXTRA_SOURCE = "floating_source"
        private const val EXTRA_PREFILL = "floating_prefill"
        private const val EXTRA_RETURN_HOME = "floating_return_home"
        private const val EXTRA_REASON = "floating_reason"

        @Volatile
        var isWindowShowing: Boolean = false

        fun showIntent(context: Context, request: FloatingNoteRequest): Intent =
            Intent(context, FloatingNoteService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_SOURCE, request.source.name)
                putExtra(EXTRA_PREFILL, request.prefillText)
                putExtra(EXTRA_RETURN_HOME, request.returnToHomeAfterClose)
            }

        fun hideIntent(context: Context, reason: String): Intent =
            Intent(context, FloatingNoteService::class.java).apply {
                action = ACTION_HIDE
                putExtra(EXTRA_REASON, reason)
            }

        fun refreshIntent(context: Context): Intent =
            Intent(context, FloatingNoteService::class.java).apply { action = ACTION_REFRESH }
    }
}
