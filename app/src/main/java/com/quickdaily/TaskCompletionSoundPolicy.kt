package com.quickdaily

/** Preference and success gate for task-widget completion feedback. */
internal object TaskCompletionSoundPolicy {
    const val PREF_KEY = "task_completion_sound"
    const val DEFAULT_ENABLED = true

    fun shouldPlay(enabled: Boolean, saveSucceeded: Boolean): Boolean = enabled && saveSucceeded
}
