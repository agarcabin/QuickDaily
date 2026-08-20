package com.quickdaily

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaActionSound
import android.media.RingtoneManager

internal enum class TaskCompletionSoundMode(val key: String, val label: String) {
    /** The original local feedback sample, now also the new-user default. */
    CLASSIC("classic", "经典"),
    /** Keep the persisted key so existing 1.9.3 users retain their choice. */
    ELECTRONIC("electronic", "木鱼"),
    /** Android 1.9 used MediaActionSound.FOCUS_COMPLETE for this mode. */
    BEEP("beep", "蜂鸣"),
    /** The user's current system notification sound. */
    SYSTEM("system", "系统"),
    SILENT("silent", "静音");

    companion object {
        fun fromKey(key: String?): TaskCompletionSoundMode =
            entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Preference and success gate for task-widget completion feedback. */
internal object TaskCompletionSoundPolicy {
    const val PREF_MODE_KEY = "task_completion_sound_mode"
    const val LEGACY_PREF_KEY = "task_completion_sound"
    val DEFAULT_MODE = TaskCompletionSoundMode.CLASSIC

    fun migrateMode(storedMode: String?, legacyEnabled: Boolean?): TaskCompletionSoundMode =
        if (storedMode != null) {
            TaskCompletionSoundMode.fromKey(storedMode)
        } else if (legacyEnabled == false) {
            TaskCompletionSoundMode.SILENT
        } else {
            DEFAULT_MODE
        }

    fun shouldPlay(mode: TaskCompletionSoundMode, saveSucceeded: Boolean): Boolean =
        mode != TaskCompletionSoundMode.SILENT && saveSucceeded

    internal fun resourceId(mode: TaskCompletionSoundMode): Int? = when (mode) {
        TaskCompletionSoundMode.CLASSIC -> R.raw.task_completion_classic
        TaskCompletionSoundMode.ELECTRONIC -> R.raw.task_completion_electronic
        TaskCompletionSoundMode.BEEP,
        TaskCompletionSoundMode.SYSTEM,
        TaskCompletionSoundMode.SILENT,
        -> null
    }

    internal fun usesSystemSound(mode: TaskCompletionSoundMode): Boolean =
        mode == TaskCompletionSoundMode.BEEP

    internal fun usesSystemNotificationSound(mode: TaskCompletionSoundMode): Boolean =
        mode == TaskCompletionSoundMode.SYSTEM

    internal fun systemNotificationSoundType(mode: TaskCompletionSoundMode): Int? =
        if (usesSystemNotificationSound(mode)) RingtoneManager.TYPE_NOTIFICATION else null

    fun preview(context: Context, mode: TaskCompletionSoundMode) {
        play(context, mode)
    }

    fun play(context: Context, mode: TaskCompletionSoundMode) {
        if (usesSystemSound(mode)) {
            runCatching {
                // Keep the exact Android system sound used by the v1.9 implementation.
                MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE)
            }.onFailure { error ->
                BetaLogger.log(
                    "TaskCompletionSound",
                    "legacy system sound failed exception=${error.javaClass.simpleName}",
                )
            }
            return
        }
        if (usesSystemNotificationSound(mode)) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (uri == null) {
                    BetaLogger.log("TaskCompletionSound", "notification sound unavailable")
                    return@runCatching
                }
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                if (ringtone == null) {
                    BetaLogger.log("TaskCompletionSound", "notification ringtone unavailable uri=$uri")
                    return@runCatching
                }
                ringtone.play()
            }.onFailure { error ->
                // Do not silently substitute another sound when the system sound fails.
                BetaLogger.log(
                    "TaskCompletionSound",
                    "notification sound failed exception=${error.javaClass.simpleName}",
                )
            }
            return
        }
        val resourceId = resourceId(mode) ?: return
        runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(
                context.applicationContext,
                resourceId,
                audioAttributes,
                0,
            ) ?: return@runCatching
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mediaPlayer, _, _ ->
                mediaPlayer.release()
                true
            }
            player.start()
        }.onFailure { error ->
            BetaLogger.log(
                "TaskCompletionSound",
                "local sound failed mode=${mode.key} exception=${error.javaClass.simpleName}",
            )
        }
    }
}
