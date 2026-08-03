package com.quickdaily

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.core.content.ContextCompat
import com.quickdaily.util.DateUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private const val FLOATING_NOTE_PREFS = "QuickDaily"

enum class FloatingNoteSource {
    SIDEBAR,
    DESKTOP_LAUNCHER,
    HOME_AUTH_FLOW,
    SHORTCUT,
    WIDGET
}

internal fun newFloatingNoteRequestId(): String = UUID.randomUUID().toString()

data class FloatingNoteRequest(
    val source: FloatingNoteSource,
    val prefillText: String = "",
    val returnToHomeAfterClose: Boolean,
    val targetRelativePath: String? = null,
    val displayTitle: String? = null,
    val requestId: String = newFloatingNoteRequestId(),
)

data class FloatingNoteTargetOption(
    val path: String?,
    val title: String,
    val menuTitle: String = title,
)

object FloatingNoteTargetStore {
    fun options(context: Context, currentTargetPath: String?): List<FloatingNoteTargetOption> {
        val currentPath = currentTargetPath
            ?.takeIf { it.isNotBlank() }
            ?.let {
                TaskWidgetConfigStore.customFilePath(
                    context,
                    TaskWidgetConfig(TaskWidgetScope.CUSTOM, it),
                ) ?: it
            }
        val customPaths = (TaskWidgetConfigStore.recentCustomPaths(context) + currentPath.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
        BetaLogger.log(
            "FloatingNote/Targets",
            "options currentPath=${currentPath.orEmpty()} customCount=${customPaths.size} customPaths=${customPaths.joinToString("|")}",
        )
        return listOf(
            FloatingNoteTargetOption(
                path = null,
                title = titleFor(context, null),
                menuTitle = "日记",
            )
        ) + customPaths.map { path ->
            FloatingNoteTargetOption(path, titleFor(context, path))
        }
    }

    fun titleFor(context: Context, targetPath: String?): String {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
        val pageName = targetPath
            ?.takeIf { it.isNotBlank() }
            ?.let { TaskWidgetConfigStore.displayName(it) }
            ?.takeIf { it.isNotBlank() }
            ?: DateUtil.todayStr(dateFormat)
        return "$pageName 速记"
    }
}

internal object FloatingNotePolicy {
    fun shouldApplyPrefill(
        text: String,
        imageCount: Int,
        attachmentCount: Int,
        prefillText: String
    ): Boolean =
        prefillText.isNotBlank() &&
            text.isBlank() &&
            imageCount == 0 &&
            attachmentCount == 0

    fun effectiveReturnHome(
        persistedValue: Boolean?,
        requestValue: Boolean
    ): Boolean = persistedValue ?: requestValue

    fun shouldLoadNewRequest(overlayShowing: Boolean): Boolean = !overlayShowing

    fun targetForRequest(
        hasPersistedDraft: Boolean,
        persistedTargetPath: String?,
        requestTargetPath: String?,
    ): String? = if (hasPersistedDraft) persistedTargetPath else requestTargetPath
}

internal object FloatingNoteEntryPolicy {
    const val PREF_SYSTEM_SIDEBAR_SUPPORT = "system_sidebar_support"
    const val DEFAULT_SYSTEM_SIDEBAR_SUPPORT = false

    fun isSystemSidebarSupportEnabled(context: Context): Boolean =
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_SYSTEM_SIDEBAR_SUPPORT, DEFAULT_SYSTEM_SIDEBAR_SUPPORT)

    fun launchLegacyEditor(
        context: Context,
        source: FloatingNoteSource = FloatingNoteSource.SIDEBAR,
    ) {
        context.startActivity(Intent(context, NoteEditActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("floating_source", source.name)
            putExtra("floating_request_id", newFloatingNoteRequestId())
        })
    }
}

/** Prevents repeated sidebar taps from queueing several asynchronous overlay starts. */
internal object FloatingNoteLaunchGate {
    private val pending = AtomicBoolean(false)

    fun acquire(): Boolean = pending.compareAndSet(false, true)

    fun release() {
        pending.set(false)
    }

    fun isPending(): Boolean = pending.get()
}

/** Compose-friendly state shared by the Activity and Overlay hosts. */
class FloatingNoteEditorState(context: Context) {
    private val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)

    var text by mutableStateOf("")
    val selectedImages = mutableStateListOf<Uri>()
    val pendingAttachments = mutableStateListOf<Uri>()
    var enterToSave by mutableStateOf(prefs.getBoolean("enter_to_save", true))
    var returnToHomeAfterClose by mutableStateOf(false)
    var source by mutableStateOf(FloatingNoteSource.SIDEBAR)
    var targetRelativePath by mutableStateOf<String?>(null)
    var displayTitle by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var selectionStart by mutableStateOf(0)
    var selectionEnd by mutableStateOf(0)

    fun hasContent(): Boolean =
        hasRealContent(text) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()
}

object FloatingNoteDraftStore {
    private const val KEY_TEXT = "floating_draft_text"
    private const val KEY_IMAGES = "floating_draft_images"
    private const val KEY_ATTACHMENTS = "floating_draft_attachments"
    private const val KEY_SOURCE = "floating_draft_source"
    private const val KEY_RETURN_HOME = "floating_draft_return_home"
    private const val KEY_TARGET_PATH = "floating_draft_target_path"
    private const val KEY_DISPLAY_TITLE = "floating_draft_display_title"
    private const val KEY_SELECTION_START = "floating_draft_selection_start"
    private const val KEY_SELECTION_END = "floating_draft_selection_end"
    private const val SEPARATOR = "\u001f"

    fun loadInto(context: Context, state: FloatingNoteEditorState, request: FloatingNoteRequest) {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        val persistedText = prefs.getString(KEY_TEXT, "").orEmpty()
        val persistedImages = readUris(prefs.getString(KEY_IMAGES, null))
        val persistedAttachments = readUris(prefs.getString(KEY_ATTACHMENTS, null))
        val hasPersistedDraft = persistedText.isNotBlank() || persistedImages.isNotEmpty() || persistedAttachments.isNotEmpty()
        state.text = persistedText
        state.selectedImages.clear()
        state.pendingAttachments.clear()
        state.selectedImages.addAll(persistedImages)
        state.pendingAttachments.addAll(persistedAttachments)
        state.source = prefs.getString(KEY_SOURCE, null)
            ?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() }
            ?: request.source
        val persistedReturnHome = if (prefs.contains(KEY_RETURN_HOME)) {
            prefs.getBoolean(KEY_RETURN_HOME, false)
        } else {
            null
        }
        state.returnToHomeAfterClose = FloatingNotePolicy.effectiveReturnHome(
            persistedReturnHome,
            request.returnToHomeAfterClose
        )
        val persistedTargetPath = prefs.getString(KEY_TARGET_PATH, null)
        state.targetRelativePath = FloatingNotePolicy.targetForRequest(
            hasPersistedDraft = hasPersistedDraft,
            persistedTargetPath = persistedTargetPath,
            requestTargetPath = request.targetRelativePath,
        )
        state.displayTitle = if (hasPersistedDraft) {
            prefs.getString(KEY_DISPLAY_TITLE, null)
        } else {
            request.displayTitle ?: FloatingNoteTargetStore.titleFor(context, state.targetRelativePath)
        }
        state.enterToSave = prefs.getBoolean("enter_to_save", true)
        val selection = TextRange(
            prefs.getInt(KEY_SELECTION_START, state.text.length).coerceIn(0, state.text.length),
            prefs.getInt(KEY_SELECTION_END, state.text.length).coerceIn(0, state.text.length),
        )
        state.selectionStart = selection.start
        state.selectionEnd = selection.end

        // A new prefill is accepted only when no persisted draft exists.
        if (FloatingNotePolicy.shouldApplyPrefill(
                state.text,
                state.selectedImages.size,
                state.pendingAttachments.size,
                request.prefillText
            )
        ) {
            state.text = request.prefillText
            state.selectionStart = state.text.length
            state.selectionEnd = state.text.length
        }
        persist(context, state)
    }

    fun persist(context: Context, state: FloatingNoteEditorState) {
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEXT, state.text)
            .putString(KEY_IMAGES, writeUris(state.selectedImages))
            .putString(KEY_ATTACHMENTS, writeUris(state.pendingAttachments))
            .putString(KEY_SOURCE, state.source.name)
            .putBoolean(KEY_RETURN_HOME, state.returnToHomeAfterClose)
            .putString(KEY_TARGET_PATH, state.targetRelativePath)
            .putString(KEY_DISPLAY_TITLE, state.displayTitle)
            .putInt(KEY_SELECTION_START, state.selectionStart.coerceIn(0, state.text.length))
            .putInt(KEY_SELECTION_END, state.selectionEnd.coerceIn(0, state.text.length))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TEXT)
            .remove(KEY_IMAGES)
            .remove(KEY_ATTACHMENTS)
            .remove(KEY_SOURCE)
            .remove(KEY_RETURN_HOME)
            .remove(KEY_TARGET_PATH)
            .remove(KEY_DISPLAY_TITLE)
            .remove(KEY_SELECTION_START)
            .remove(KEY_SELECTION_END)
            .apply()
    }

    fun addImages(context: Context, uris: List<Uri>) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false))
        state.selectedImages.addAll(uris)
        persist(context, state)
    }

    fun addAttachment(context: Context, uri: Uri) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false))
        state.text = if (state.text.isBlank()) "![[${uri}]]" else "${state.text}\n![[${uri}]]"
        state.pendingAttachments.add(uri)
        persist(context, state)
    }

    fun setTarget(context: Context, path: String) {
        TaskWidgetConfigStore.recordCustomPage(context, path)
        val state = FloatingNoteEditorState(context)
        loadInto(
            context,
            state,
            FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false),
        )
        state.targetRelativePath = path
        state.displayTitle = FloatingNoteTargetStore.titleFor(context, path)
        persist(context, state)
    }

    fun updateSelection(context: Context, selection: TextRange) {
        val state = FloatingNoteEditorState(context)
        loadInto(
            context,
            state,
            FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false),
        )
        state.selectionStart = selection.start.coerceIn(0, state.text.length)
        state.selectionEnd = selection.end.coerceIn(0, state.text.length)
        persist(context, state)
    }

    fun insertLink(context: Context, link: String) {
        val state = FloatingNoteEditorState(context)
        loadInto(
            context,
            state,
            FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false),
        )
        val next = EditorMediaUtil.insertLink(
            state.text,
            TextRange(state.selectionStart, state.selectionEnd),
            link,
        )
        state.text = next.text
        state.selectionStart = next.selection.start
        state.selectionEnd = next.selection.end
        persist(context, state)
    }

    private fun readUris(raw: String?): List<Uri> = raw.orEmpty()
        .split(SEPARATOR)
        .filter { it.isNotBlank() }
        .map(Uri::parse)

    private fun writeUris(uris: List<Uri>): String = uris.joinToString(SEPARATOR) { it.toString() }
}

interface FloatingNoteController {
    fun showOrFocus(request: FloatingNoteRequest, onReady: (() -> Unit)? = null): Boolean
    fun hide(reason: String = "user")
    fun isShowing(): Boolean
}

internal object FloatingNoteHandoff {
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    fun register(requestId: String, callback: () -> Unit) {
        callbacks[requestId] = callback
    }

    fun cancel(requestId: String) {
        callbacks.remove(requestId)
    }

    fun notifyReady(requestId: String) {
        callbacks.remove(requestId)?.let { callback ->
            runCatching { callback() }
                .onFailure { error ->
                    BetaLogger.log(
                        "FloatingNote/Handoff",
                        "ready_callback_failed requestId=$requestId error=${error.javaClass.simpleName}",
                    )
                }
        }
    }
}

object FloatingNoteControllerProvider {
    fun forContext(context: Context): FloatingNoteController = object : FloatingNoteController {
        private val appContext = context.applicationContext

        override fun showOrFocus(request: FloatingNoteRequest, onReady: (() -> Unit)?): Boolean {
            if (!Settings.canDrawOverlays(appContext)) {
                BetaLogger.log("FloatingNote/Permission", "permission_denied source=${request.source}")
                return false
            }
            if (FloatingNoteService.isWindowShowing) {
                BetaLogger.log("FloatingNote/Window", "focus existing source=${request.source}")
                onReady?.invoke()
                return true
            }
            if (!FloatingNoteLaunchGate.acquire()) {
                BetaLogger.log("FloatingNote/Service", "show suppressed source=${request.source} pending=true")
                return true
            }
            onReady?.let { FloatingNoteHandoff.register(request.requestId, it) }
            return try {
                ContextCompat.startForegroundService(
                    appContext,
                    FloatingNoteService.showIntent(appContext, request)
                )
                BetaLogger.log(
                    "FloatingNote/Service",
                    "show source=${request.source} requestId=${request.requestId}",
                )
                true
            } catch (error: Throwable) {
                FloatingNoteHandoff.cancel(request.requestId)
                FloatingNoteLaunchGate.release()
                BetaLogger.log("FloatingNote/Service", "start_failed=${error.javaClass.simpleName}")
                false
            }
        }

        override fun hide(reason: String) {
            runCatching {
                appContext.startService(FloatingNoteService.hideIntent(appContext, reason))
            }
        }

        override fun isShowing(): Boolean = FloatingNoteService.isWindowShowing
    }
}
