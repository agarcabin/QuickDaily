package com.quickdaily

import org.junit.Assert.*
import org.junit.Test

class ObsidianConfigSyncPolicyTest {
    private val previous = ObsidianSyncState(
        ObsidianRules("Daily", "YYYY/MM/DD", "templates/daily.md", "assets", true, "absolute"),
        dailyLoaded = true, appLoaded = true, dailyStatus = "ok", appStatus = "ok",
    )

    @Test fun appSettingsDoNotDependOnDailyNotesBeingConfigured() {
        val result = ObsidianSyncPolicy.merge(ObsidianSyncState(ObsidianRules()), null,
            """{"useMarkdownLinks":true,"newLinkFormat":"relative","attachmentFolderPath":"./images"}""", "daily", "app")
        assertFalse(result.dailyLoaded)
        assertTrue(result.appLoaded)
        assertEquals("relative", result.rules.linkPathFormat)
        assertEquals("./images", result.rules.attachmentFolder)
        assertEquals("unavailable", result.dailyStatus)
        assertEquals("ok", result.appStatus)
    }

    @Test fun badDailyJsonDoesNotPreventValidAppSettings() {
        val result = ObsidianSyncPolicy.merge(previous, "{", "{}", "custom-daily", "app")
        assertEquals(previous.rules.diaryFolder, result.rules.diaryFolder)
        assertEquals(previous.rules.templatePath, result.rules.templatePath)
        assertEquals("invalid_json", result.dailyStatus)
        assertEquals("ok", result.appStatus)
        assertEquals("/", result.rules.attachmentFolder)
        assertFalse(result.rules.markdownLinks)
        assertEquals("shortest", result.rules.linkPathFormat)
    }

    @Test fun unavailableAndCorruptFilesRetainTheLastValidSnapshot() {
        val result = ObsidianSyncPolicy.merge(previous, null, "[]", "daily", "app")
        assertEquals(previous.rules, result.rules)
        assertTrue(result.dailyLoaded && result.appLoaded)
        assertEquals("unavailable", result.dailyStatus)
        assertEquals("invalid_json", result.appStatus)
    }

    @Test fun clearingTemplateAndResettingDefaultsAreSuccessfulUpdates() {
        val result = ObsidianSyncPolicy.merge(previous, """{"folder":"","template":""}""", "{}", "daily", "app")
        assertEquals(ObsidianRules(), result.rules)
        assertEquals("ok", result.dailyStatus)
        assertEquals("ok", result.appStatus)
    }

    @Test fun booleanStringsAndUnknownFormatsAreNotSilentlyCoerced() {
        val result = ObsidianSyncPolicy.merge(previous, "{}", """{"useMarkdownLinks":"false","newLinkFormat":"future"}""", "daily", "app")
        assertTrue(result.rules.markdownLinks)
        assertEquals("absolute", result.rules.linkPathFormat)
        assertEquals("invalid_fields", result.appStatus)
        assertEquals("ok", result.dailyStatus)
    }

    @Test fun oneSaveKeepsItsImmutableRuleSnapshotDuringRefresh() {
        val captured = previous.rules
        val refreshed = ObsidianSyncPolicy.merge(previous, """{"folder":"New"}""", """{"newLinkFormat":"relative"}""", "daily", "app")
        assertEquals("Daily", captured.diaryFolder)
        assertEquals("absolute", captured.linkPathFormat)
        assertEquals("New", refreshed.rules.diaryFolder)
        assertEquals("relative", refreshed.rules.linkPathFormat)
    }
}
