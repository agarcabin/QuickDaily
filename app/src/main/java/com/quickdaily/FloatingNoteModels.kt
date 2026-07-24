package com.quickdaily

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

private const val FLOATING_NOTE_PREFS = "QuickDaily"

enum class FloatingNoteSource {
    SIDEBAR,
    DESKTOP_LAUNCHER,
    HOME_AUTH_FLOW,
    SHORTCUT,
    WIDGET
}

data class FloatingNoteRequest(
    val source: FloatingNoteSource,
    val prefillText: String = "",
    val returnToHomeAfterClose: Boolean
)

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
}

internal object FloatingNoteEntryPolicy {
    const val PREF_SYSTEM_SIDEBAR_SUPPORT = "system_sidebar_support"

    fun isSystemSidebarSupportEnabled(context: Context): Boolean =
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_SYSTEM_SIDEBAR_SUPPORT, false)

    fun launchLegacyEditor(context: Context) {
        context.startActivity(Intent(context, NoteEditActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
    }
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
    var isSaving by mutableStateOf(false)

    fun hasContent(): Boolean =
        hasRealContent(text) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()
}

object FloatingNoteDraftStore {
    private const val KEY_TEXT = "floating_draft_text"
    private const val KEY_IMAGES = "floating_draft_images"
    private const val KEY_ATTACHMENTS = "floating_draft_attachments"
    private const val KEY_SOURCE = "floating_draft_source"
    private const val KEY_RETURN_HOME = "floating_draft_return_home"
    private const val SEPARATOR = "\u001f"

    fun loadInto(context: Context, state: FloatingNoteEditorState, request: FloatingNoteRequest) {
        val prefs = context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE)
        state.text = prefs.getString(KEY_TEXT, "").orEmpty()
        state.selectedImages.clear()
        state.pendingAttachments.clear()
        state.selectedImages.addAll(readUris(prefs.getString(KEY_IMAGES, null)))
        state.pendingAttachments.addAll(readUris(prefs.getString(KEY_ATTACHMENTS, null)))
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
        state.enterToSave = prefs.getBoolean("enter_to_save", true)

        // A new prefill is accepted only when no persisted draft exists.
        if (FloatingNotePolicy.shouldApplyPrefill(
                state.text,
                state.selectedImages.size,
                state.pendingAttachments.size,
                request.prefillText
            )
        ) {
            state.text = request.prefillText
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
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FLOATING_NOTE_PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TEXT)
            .remove(KEY_IMAGES)
            .remove(KEY_ATTACHMENTS)
            .remove(KEY_SOURCE)
            .remove(KEY_RETURN_HOME)
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

    private fun readUris(raw: String?): List<Uri> = raw.orEmpty()
        .split(SEPARATOR)
        .filter { it.isNotBlank() }
        .map(Uri::parse)

    private fun writeUris(uris: List<Uri>): String = uris.joinToString(SEPARATOR) { it.toString() }
}

interface FloatingNoteController {
    fun showOrFocus(request: FloatingNoteRequest): Boolean
    fun hide(reason: String = "user")
    fun isShowing(): Boolean
}

object FloatingNoteControllerProvider {
    fun forContext(context: Context): FloatingNoteController = object : FloatingNoteController {
        private val appContext = context.applicationContext

        override fun showOrFocus(request: FloatingNoteRequest): Boolean {
            if (!Settings.canDrawOverlays(appContext)) {
                BetaLogger.log("FloatingNote/Permission", "permission_denied source=${request.source}")
                return false
            }
            return try {
                ContextCompat.startForegroundService(
                    appContext,
                    FloatingNoteService.showIntent(appContext, request)
                )
                BetaLogger.log("FloatingNote/Service", "show source=${request.source}")
                true
            } catch (error: Throwable) {
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
