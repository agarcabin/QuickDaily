package com.quickdaily

internal object FloatingNoteExitPolicy {
    fun shouldSave(reason: String, saveOnClose: Boolean, hasContent: Boolean): Boolean =
        reason != "close" && saveOnClose && hasContent

    fun shouldDiscard(reason: String): Boolean = reason == "close"
}
