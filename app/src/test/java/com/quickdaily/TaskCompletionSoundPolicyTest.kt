package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.media.RingtoneManager

class TaskCompletionSoundPolicyTest {
    @Test
    fun soundModesKeepTheRequestedDisplayOrder() {
        assertEquals(
            listOf("classic", "electronic", "beep", "system", "silent"),
            TaskCompletionSoundMode.entries.map { it.key },
        )
        assertEquals("系统", TaskCompletionSoundMode.SYSTEM.label)
    }

    @Test
    fun soundPlaysOnlyForAudibleModesAfterSuccessfulSave() {
        TaskCompletionSoundMode.entries
            .filter { it != TaskCompletionSoundMode.SILENT }
            .forEach { mode ->
                assertTrue(TaskCompletionSoundPolicy.shouldPlay(mode, saveSucceeded = true))
                assertFalse(TaskCompletionSoundPolicy.shouldPlay(mode, saveSucceeded = false))
            }
        assertFalse(TaskCompletionSoundPolicy.shouldPlay(TaskCompletionSoundMode.SILENT, saveSucceeded = true))
    }

    @Test
    fun legacyBooleanMigratesWithoutChangingExistingChoice() {
        assertEquals(TaskCompletionSoundMode.CLASSIC, TaskCompletionSoundPolicy.migrateMode(null, true))
        assertEquals(TaskCompletionSoundMode.SILENT, TaskCompletionSoundPolicy.migrateMode(null, false))
        assertEquals(TaskCompletionSoundMode.CLASSIC, TaskCompletionSoundPolicy.migrateMode("classic", false))
    }

    @Test
    fun removedModesMigrateToClassic() {
        listOf("urgent", "crisp", "retro", "unknown").forEach { key ->
            assertEquals(TaskCompletionSoundMode.CLASSIC, TaskCompletionSoundMode.fromKey(key))
        }
    }

    @Test
    fun audibleModesMapToResourcesOrTheLegacySystemSound() {
        assertEquals(R.raw.task_completion_classic, TaskCompletionSoundPolicy.resourceId(TaskCompletionSoundMode.CLASSIC))
        assertEquals(R.raw.task_completion_electronic, TaskCompletionSoundPolicy.resourceId(TaskCompletionSoundMode.ELECTRONIC))
        assertTrue(TaskCompletionSoundPolicy.usesSystemSound(TaskCompletionSoundMode.BEEP))
        assertEquals(null, TaskCompletionSoundPolicy.resourceId(TaskCompletionSoundMode.BEEP))
        assertTrue(TaskCompletionSoundPolicy.usesSystemNotificationSound(TaskCompletionSoundMode.SYSTEM))
        assertEquals(
            RingtoneManager.TYPE_NOTIFICATION,
            TaskCompletionSoundPolicy.systemNotificationSoundType(TaskCompletionSoundMode.SYSTEM),
        )
        assertEquals(null, TaskCompletionSoundPolicy.resourceId(TaskCompletionSoundMode.SYSTEM))
        assertFalse(TaskCompletionSoundPolicy.usesSystemNotificationSound(TaskCompletionSoundMode.CLASSIC))
        assertFalse(TaskCompletionSoundPolicy.usesSystemSound(TaskCompletionSoundMode.CLASSIC))
        assertEquals(null, TaskCompletionSoundPolicy.resourceId(TaskCompletionSoundMode.SILENT))
    }
}
