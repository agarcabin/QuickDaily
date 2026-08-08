package com.quickdaily

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.core.content.ContextCompat
import com.quickdaily.util.DateUtil
import com.quickdaily.util.VaultPathUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private const val FLOATING_NOTE_PREFS = "QuickDaily"

enum class FloatingNoteSource {
    SIDEBAR,
    DESKTOP_LAUNCHER,
    HOME_AUTH_FLOW,
    SHORTCUT,
    TILE,
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
    val rememberTarget: Boolean = true,
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
    fun displayTitleForRequest(
        hasPersistedDraft: Boolean,
        persistedTitle: String?,
        requestedTitle: String?,
        fallbackTitle: String,
    ): String = requestedTitle
        ?.takeIf { it.isNotBlank() }
        ?: persistedTitle
            ?.takeIf { hasPersistedDraft && it.isNotBlank() }
        ?: fallbackTitle

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
        rememberedTargetPath: String? = null,
        rememberTarget: Boolean = true,
    ): String? = if (hasPersistedDraft) persistedTargetPath else requestTargetPath
        ?: if (rememberTarget) rememberedTargetPath else null
}

internal object FloatingNoteEntryPolicy {
    const val PREF_SYSTEM_SIDEBAR_SUPPORT = "system_sidebar_support"
    const val DEFAULT_SYSTEM_SIDEBAR_SUPPORT = false
    const val PREF_KEEP_DRAFT_ON_CLOSE = "floating_note_keep_draft_on_close"
    const val PREF_SAVE_ON_CLOSE = "floating_note_save_on_close"
    const val DEFAULT_SAVE_ON_CLOSE = true
    const val DEFAULT_KEEP_DRAFT_ON_CLOSE = false

    fun shouldSaveOnClose(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        return when {
            prefs.contains(PREF_SAVE_ON_CLOSE) -> prefs.getBoolean(PREF_SAVE_ON_CLOSE, DEFAULT_SAVE_ON_CLOSE)
            prefs.contains(PREF_KEEP_DRAFT_ON_CLOSE) -> !prefs.getBoolean(
                PREF_KEEP_DRAFT_ON_CLOSE,
                DEFAULT_KEEP_DRAFT_ON_CLOSE,
            )
            else -> DEFAULT_SAVE_ON_CLOSE
        }
    }


    fun keepDraftOnClose(context: Context): Boolean = !shouldSaveOnClose(context)

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
    val invalidAttachments = mutableStateListOf<Uri>()
    var enterToSave by mutableStateOf(prefs.getBoolean("enter_to_save", true))
    var returnToHomeAfterClose by mutableStateOf(false)
    var source by mutableStateOf(FloatingNoteSource.SIDEBAR)
    var rememberTarget by mutableStateOf(true)
    var targetRelativePath by mutableStateOf<String?>(null)
    var displayTitle by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var selectionStart by mutableStateOf(0)
    var selectionEnd by mutableStateOf(0)

    fun hasContent(): Boolean =
        hasRealContent(text) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty() || invalidAttachments.isNotEmpty()
}

internal object FloatingNoteDraftTargetPolicy {
    const val DEFAULT_TARGET_KEY = "default-diary"

    fun keyFor(context: Context, targetRelativePath: String?): String =
        keyForPath(
            targetRelativePath,
            context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
                .getString("vault_path", "")
                .orEmpty(),
        )

    internal fun keyForPath(targetRelativePath: String?, vaultPath: String): String {
        val normalized = targetRelativePath?.trim()?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_TARGET_KEY
        val canonical = VaultPathUtil.resolveTarget(vaultPath, normalized)
        val relative = canonical?.let { VaultPathUtil.relativePath(vaultPath, it) }
        return (relative ?: canonical ?: normalized).replace('\\', '/')
    }
}
object FloatingNoteDraftStore {
    private const val KEY_PREFIX = "floating_draft_v2_"
    private const val FIELD_TEXT = "text"
    private const val FIELD_IMAGES = "images"
    private const val FIELD_ATTACHMENTS = "attachments"
    private const val FIELD_SOURCE = "source"
    private const val FIELD_RETURN_HOME = "return_home"
    private const val FIELD_TARGET_PATH = "target_path"
    private const val FIELD_DISPLAY_TITLE = "display_title"
    private const val FIELD_SELECTION_START = "selection_start"
    private const val FIELD_SELECTION_END = "selection_end"
    private const val LEGACY_KEY_TEXT = "floating_draft_text"
    private const val LEGACY_KEY_IMAGES = "floating_draft_images"
    private const val LEGACY_KEY_ATTACHMENTS = "floating_draft_attachments"
    private const val LEGACY_KEY_SOURCE = "floating_draft_source"
    private const val LEGACY_KEY_RETURN_HOME = "floating_draft_return_home"
    private const val LEGACY_KEY_TARGET_PATH = "floating_draft_target_path"
    private const val LEGACY_KEY_DISPLAY_TITLE = "floating_draft_display_title"
    private const val LEGACY_KEY_SELECTION_START = "floating_draft_selection_start"
    private const val LEGACY_KEY_SELECTION_END = "floating_draft_selection_end"
    private const val SEPARATOR = "\u001f"

    fun loadInto(context: Context, state: FloatingNoteEditorState, request: FloatingNoteRequest) {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        val targetKey = FloatingNoteDraftTargetPolicy.keyFor(context, request.targetRelativePath)
        val stored = read(context, targetKey)
        val persistedText = stored?.text.orEmpty()
        val persistedImages = stored?.images.orEmpty()
        val persistedAttachments = stored?.attachments.orEmpty()
        val hasPersistedDraft = stored?.hasContent == true
        state.text = persistedText
        state.selectedImages.clear()
        state.pendingAttachments.clear()
        state.invalidAttachments.clear()
        persistedImages.forEach { uri ->
            if (isUriReadable(context, uri)) state.selectedImages.add(uri)
            else state.invalidAttachments.add(uri)
        }
        persistedAttachments.forEach { uri ->
            if (isUriReadable(context, uri)) state.pendingAttachments.add(uri)
            else state.invalidAttachments.add(uri)
        }
        state.source = request.source
        state.rememberTarget = request.rememberTarget
        state.returnToHomeAfterClose = FloatingNotePolicy.effectiveReturnHome(
            stored?.returnHome,
            request.returnToHomeAfterClose,
        )
        state.targetRelativePath = FloatingNotePolicy.targetForRequest(
            hasPersistedDraft = hasPersistedDraft,
            persistedTargetPath = stored?.targetPath,
            requestTargetPath = request.targetRelativePath,
            rememberedTargetPath = if (request.rememberTarget) {
                FloatingNoteTargetMemory.load(context, request.source)
            } else {
                null
            },
            rememberTarget = request.rememberTarget,
        )
        state.displayTitle = FloatingNotePolicy.displayTitleForRequest(
            hasPersistedDraft = hasPersistedDraft,
            persistedTitle = stored?.displayTitle,
            requestedTitle = request.displayTitle,
            fallbackTitle = FloatingNoteTargetStore.titleFor(context, state.targetRelativePath),
        )
        state.enterToSave = prefs.getBoolean("enter_to_save", true)
        val selection = TextRange(
            stored?.selectionStart?.coerceIn(0, state.text.length) ?: state.text.length,
            stored?.selectionEnd?.coerceIn(0, state.text.length) ?: state.text.length,
        )
        state.selectionStart = selection.start
        state.selectionEnd = selection.end
        if (FloatingNotePolicy.shouldApplyPrefill(
                state.text,
                state.selectedImages.size,
                state.pendingAttachments.size + state.invalidAttachments.size,
                request.prefillText,
            )
        ) {
            state.text = request.prefillText
            state.selectionStart = state.text.length
            state.selectionEnd = state.text.length
        }
        BetaLogger.log(
            "FloatingNote/Draft",
            "load targetKey=$targetKey hasDraft=$hasPersistedDraft textLength=${state.text.length} " +
                "images=${state.selectedImages.size} attachments=${state.pendingAttachments.size} " +
                "invalidAttachments=${state.invalidAttachments.size}",
        )
    }

    fun persist(context: Context, state: FloatingNoteEditorState) {
        val targetKey = FloatingNoteDraftTargetPolicy.keyFor(context, state.targetRelativePath)
        val prefix = prefixFor(targetKey)
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(prefix + FIELD_TEXT, state.text)
            .putString(prefix + FIELD_IMAGES, writeUris(state.selectedImages))
            .putString(prefix + FIELD_ATTACHMENTS, writeUris(state.pendingAttachments + state.invalidAttachments))
            .putString(prefix + FIELD_SOURCE, state.source.name)
            .putBoolean(prefix + FIELD_RETURN_HOME, state.returnToHomeAfterClose)
            .putString(prefix + FIELD_TARGET_PATH, state.targetRelativePath)
            .putString(prefix + FIELD_DISPLAY_TITLE, state.displayTitle)
            .putInt(prefix + FIELD_SELECTION_START, state.selectionStart.coerceIn(0, state.text.length))
            .putInt(prefix + FIELD_SELECTION_END, state.selectionEnd.coerceIn(0, state.text.length))
            .apply()
        BetaLogger.log("FloatingNote/Draft", "persist targetKey=$targetKey textLength=${state.text.length}")
    }

    fun persistOrClear(context: Context, state: FloatingNoteEditorState) {
        if (state.hasContent()) persist(context, state) else clear(context, state.targetRelativePath)
    }

    fun clear(context: Context, targetRelativePath: String? = null) {
        val targetKey = FloatingNoteDraftTargetPolicy.keyFor(context, targetRelativePath)
        val prefix = prefixFor(targetKey)
        val editor = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE).edit()
        listOf(
            FIELD_TEXT, FIELD_IMAGES, FIELD_ATTACHMENTS, FIELD_SOURCE, FIELD_RETURN_HOME,
            FIELD_TARGET_PATH, FIELD_DISPLAY_TITLE, FIELD_SELECTION_START, FIELD_SELECTION_END,
        ).forEach { editor.remove(prefix + it) }
        if (targetKey == FloatingNoteDraftTargetPolicy.DEFAULT_TARGET_KEY) {
            listOf(
                LEGACY_KEY_TEXT, LEGACY_KEY_IMAGES, LEGACY_KEY_ATTACHMENTS, LEGACY_KEY_SOURCE,
                LEGACY_KEY_RETURN_HOME, LEGACY_KEY_TARGET_PATH, LEGACY_KEY_DISPLAY_TITLE,
                LEGACY_KEY_SELECTION_START, LEGACY_KEY_SELECTION_END,
            ).forEach(editor::remove)
        }
        editor.apply()
        BetaLogger.log("FloatingNote/Draft", "clear targetKey=$targetKey")
    }

    fun addImages(context: Context, uris: List<Uri>, targetRelativePath: String? = null) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(
            FloatingNoteSource.SIDEBAR,
            returnToHomeAfterClose = false,
            targetRelativePath = targetRelativePath,
        ))
        uris.forEach { uri ->
            if (isUriReadable(context, uri)) state.selectedImages.add(uri)
            else state.invalidAttachments.add(uri)
        }
        persist(context, state)
    }

    fun addAttachment(context: Context, uri: Uri, targetRelativePath: String? = null) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(
            FloatingNoteSource.SIDEBAR,
            returnToHomeAfterClose = false,
            targetRelativePath = targetRelativePath,
        ))
        state.text = if (state.text.isBlank()) "![[${uri}]]" else "${state.text}\n![[${uri}]]"
        if (isUriReadable(context, uri)) state.pendingAttachments.add(uri) else state.invalidAttachments.add(uri)
        persist(context, state)
    }

    fun setTarget(context: Context, path: String) {
        TaskWidgetConfigStore.recordCustomPage(context, path)
    }

    fun updateSelection(context: Context, selection: TextRange, targetRelativePath: String? = null) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(
            FloatingNoteSource.SIDEBAR,
            returnToHomeAfterClose = false,
            targetRelativePath = targetRelativePath,
        ))
        state.selectionStart = selection.start.coerceIn(0, state.text.length)
        state.selectionEnd = selection.end.coerceIn(0, state.text.length)
        persist(context, state)
    }

    fun insertLink(context: Context, link: String, targetRelativePath: String? = null) {
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(
            FloatingNoteSource.SIDEBAR,
            returnToHomeAfterClose = false,
            targetRelativePath = targetRelativePath,
        ))
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

    fun insertLinks(context: Context, links: List<String>, targetRelativePath: String? = null) {
        if (links.isEmpty()) return
        val state = FloatingNoteEditorState(context)
        loadInto(context, state, FloatingNoteRequest(
            FloatingNoteSource.SIDEBAR,
            returnToHomeAfterClose = false,
            targetRelativePath = targetRelativePath,
        ))
        val next = EditorMediaUtil.insertLinks(
            state.text,
            TextRange(state.selectionStart, state.selectionEnd),
            links,
        )
        state.text = next.text
        state.selectionStart = next.selection.start
        state.selectionEnd = next.selection.end
        persist(context, state)
        BetaLogger.log(
            "FloatingNote/Draft",
            "insertLinks targetKey=${FloatingNoteDraftTargetPolicy.keyFor(context, targetRelativePath)} count=${links.size} textLength=${state.text.length}",
        )
    }

    private data class StoredDraft(
        val text: String,
        val images: List<Uri>,
        val attachments: List<Uri>,
        val source: FloatingNoteSource?,
        val returnHome: Boolean?,
        val targetPath: String?,
        val displayTitle: String?,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        val hasContent: Boolean
            get() = hasRealContent(text) || images.isNotEmpty() || attachments.isNotEmpty()
    }

    private fun read(context: Context, targetKey: String): StoredDraft? {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        val prefix = prefixFor(targetKey)
        val isV2 = prefs.contains(prefix + FIELD_TEXT) ||
            prefs.contains(prefix + FIELD_IMAGES) || prefs.contains(prefix + FIELD_ATTACHMENTS)
        if (isV2) {
            return StoredDraft(
                text = prefs.getString(prefix + FIELD_TEXT, "").orEmpty(),
                images = readUris(prefs.getString(prefix + FIELD_IMAGES, null)),
                attachments = readUris(prefs.getString(prefix + FIELD_ATTACHMENTS, null)),
                source = prefs.getString(prefix + FIELD_SOURCE, null)?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() },
                returnHome = if (prefs.contains(prefix + FIELD_RETURN_HOME)) prefs.getBoolean(prefix + FIELD_RETURN_HOME, false) else null,
                targetPath = prefs.getString(prefix + FIELD_TARGET_PATH, null),
                displayTitle = prefs.getString(prefix + FIELD_DISPLAY_TITLE, null),
                selectionStart = prefs.getInt(prefix + FIELD_SELECTION_START, 0),
                selectionEnd = prefs.getInt(prefix + FIELD_SELECTION_END, 0),
            )
        }
        if (targetKey != FloatingNoteDraftTargetPolicy.DEFAULT_TARGET_KEY ||
            (!prefs.contains(LEGACY_KEY_TEXT) && !prefs.contains(LEGACY_KEY_IMAGES) && !prefs.contains(LEGACY_KEY_ATTACHMENTS))
        ) return null
        return StoredDraft(
            text = prefs.getString(LEGACY_KEY_TEXT, "").orEmpty(),
            images = readUris(prefs.getString(LEGACY_KEY_IMAGES, null)),
            attachments = readUris(prefs.getString(LEGACY_KEY_ATTACHMENTS, null)),
            source = prefs.getString(LEGACY_KEY_SOURCE, null)?.let { runCatching { FloatingNoteSource.valueOf(it) }.getOrNull() },
            returnHome = if (prefs.contains(LEGACY_KEY_RETURN_HOME)) prefs.getBoolean(LEGACY_KEY_RETURN_HOME, false) else null,
            targetPath = prefs.getString(LEGACY_KEY_TARGET_PATH, null),
            displayTitle = prefs.getString(LEGACY_KEY_DISPLAY_TITLE, null),
            selectionStart = prefs.getInt(LEGACY_KEY_SELECTION_START, 0),
            selectionEnd = prefs.getInt(LEGACY_KEY_SELECTION_END, 0),
        ).also { BetaLogger.log("FloatingNote/Draft", "migrate legacy draft targetKey=$targetKey") }
    }

    private fun readUris(raw: String?): List<Uri> = raw.orEmpty()
        .split(SEPARATOR)
        .filter { it.isNotBlank() }
        .map(Uri::parse)

    private fun writeUris(uris: List<Uri>): String = uris.joinToString(SEPARATOR) { it.toString() }

    private fun prefixFor(targetKey: String): String =
        KEY_PREFIX + Base64.encodeToString(targetKey.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP) + "_"

    private fun isUriReadable(context: Context, uri: Uri): Boolean = runCatching {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { java.io.File(it).canRead() } == true
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
            else -> true
        }
    }.getOrDefault(false)
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
            FloatingNoteTiming.begin(request.requestId, request.source)
            FloatingNoteTiming.mark(
                "launch_dispatch_start",
                "host=controller source=${request.source} target=${request.targetRelativePath.orEmpty()} " +
                    "permission=${Settings.canDrawOverlays(appContext)} windowShowing=${FloatingNoteService.isWindowShowing}",
            )
            if (!Settings.canDrawOverlays(appContext)) {
                FloatingNoteTiming.mark("launch_dispatch_blocked", "host=controller reason=permission_denied")
                BetaLogger.log("FloatingNote/Permission", "permission_denied source=${request.source}")
                return false
            }
            if (FloatingNoteService.isWindowShowing) {
                runCatching {
                    appContext.startService(FloatingNoteService.showIntent(appContext, request))
                    FloatingNoteTiming.mark("launch_dispatch_return", "host=controller mode=focus_existing")
                }.onFailure { error ->
                    FloatingNoteTiming.mark("launch_dispatch_error", "host=controller mode=focus_existing error=${error.javaClass.simpleName}")
                    BetaLogger.log("FloatingNote/Service", "focus_update_failed=${error.javaClass.simpleName}")
                }
                BetaLogger.log(
                    "FloatingNote/Window",
                    "focus existing source=${request.source} target=${request.targetRelativePath.orEmpty()} imeRequestIgnored=true",
                )
                onReady?.invoke()
                return true
            }
            if (!FloatingNoteLaunchGate.acquire()) {
                FloatingNoteTiming.mark("launch_dispatch_blocked", "host=controller reason=pending_gate")
                BetaLogger.log("FloatingNote/Service", "show suppressed source=${request.source} pending=true")
                return true
            }
            onReady?.let { FloatingNoteHandoff.register(request.requestId, it) }
            return try {
                FloatingNoteTiming.mark("service_dispatch_start", "host=controller")
                ContextCompat.startForegroundService(
                    appContext,
                    FloatingNoteService.showIntent(appContext, request)
                )
                FloatingNoteTiming.mark("service_dispatch_return", "host=controller")
                BetaLogger.log(
                    "FloatingNote/Service",
                    "show source=${request.source} requestId=${request.requestId}",
                )
                true
            } catch (error: Throwable) {
                FloatingNoteTiming.mark("service_dispatch_error", "host=controller error=${error.javaClass.simpleName}")
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

internal object FloatingNoteTargetMemory {
    private const val PREF_PREFIX = "floating_note_last_target_"
    private const val DEFAULT_DIARY_SENTINEL = "__quickdaily_default_diary__"

    fun keyFor(source: FloatingNoteSource): String = PREF_PREFIX + source.name.lowercase()

    fun load(context: Context, source: FloatingNoteSource): String? {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(keyFor(source))) return null
        return prefs.getString(keyFor(source), null)
            ?.takeUnless { it == DEFAULT_DIARY_SENTINEL }
            ?.takeIf { it.isNotBlank() }
    }

    fun remember(context: Context, source: FloatingNoteSource, targetRelativePath: String?) {
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(source), targetRelativePath?.takeIf { it.isNotBlank() } ?: DEFAULT_DIARY_SENTINEL)
            .apply()
        BetaLogger.log(
            "FloatingNote/Targets",
            "remember source=$source target=${targetRelativePath.orEmpty()}",
        )
    }

}
internal object FloatingNoteAppearance {
    const val PREF_OPACITY = "floating_note_opacity"
    const val DEFAULT_OPACITY_PERCENT = 97

    fun percent(context: Context): Int = context
        .getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        .getInt(PREF_OPACITY, DEFAULT_OPACITY_PERCENT)
        .coerceIn(0, 100)

    fun alpha(context: Context): Float = percent(context) / 100f
}
