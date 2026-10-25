package com.quickdaily

import org.junit.Assert.*
import org.junit.Test

class ObsidianRulesTest {
    @Test fun missingFieldsResetToObsidianDefaultsAndEmptyFolderIsRoot() {
        val old = ObsidianRules("Daily", "YYYY/MM/DD", "template.md", "assets", true, "absolute")
        val daily = ObsidianRulesParser.daily("{}", old).rules
        assertEquals("", daily.diaryFolder)
        assertEquals("YYYY-MM-DD", daily.dateFormat)
        assertEquals("", daily.templatePath)
        assertEquals(ObsidianRules(), ObsidianRulesParser.app("{}", daily).rules)
        assertEquals("", ObsidianRulesParser.daily("""{"folder":""}""", old).rules.diaryFolder)
    }

    @Test fun invalidFieldsPreserveOnlyTheirPreviousValues() {
        val old = ObsidianRules(attachmentFolder = "old", markdownLinks = true, linkPathFormat = "relative")
        val result = ObsidianRulesParser.app("""{"attachmentFolderPath":"../outside","useMarkdownLinks":false,"newLinkFormat":"future"}""", old)
        assertEquals("old", result.rules.attachmentFolder)
        assertFalse(result.rules.markdownLinks)
        assertEquals("relative", result.rules.linkPathFormat)
        assertEquals(listOf("attachmentFolderPath", "newLinkFormat"), result.invalidFields)
    }

    @Test fun malformedDocumentDoesNotProduceAnEmptySnapshot() {
        listOf("[]", "{", "null").forEach { raw -> assertTrue(runCatching { ObsidianRulesParser.app(raw, ObsidianRules()) }.isFailure) }
    }

    @Test fun fourAttachmentLocationsUseTheActualNoteDirectory() {
        assertEquals("", ObsidianLinkPolicy.attachmentDirectory("/", "Daily/2026/today.md"))
        assertEquals("assets", ObsidianLinkPolicy.attachmentDirectory("assets", "Daily/2026/today.md"))
        assertEquals("Daily/2026", ObsidianLinkPolicy.attachmentDirectory(".", "Daily/2026/today.md"))
        assertEquals("Daily/2026/attachments", ObsidianLinkPolicy.attachmentDirectory("./attachments", "Daily/2026/today.md"))
        assertEquals("attachments", ObsidianLinkPolicy.attachmentDirectory("./attachments", "today.md"))
        assertNull(ObsidianLinkPolicy.attachmentDirectory("../../outside", "today.md"))
    }

    @Test fun generatedLinksRoundTripAcrossBothSyntaxesAndAllPathFormats() {
        val note = "Daily/2026/today.md"
        val target = "assets/照片 (1)+100%.jpg"
        for (markdown in listOf(true, false)) for (format in listOf("relative", "absolute", "shortest")) {
            val rules = ObsidianRules(markdownLinks = markdown, linkPathFormat = format)
            val path = ObsidianLinkPolicy.path(note, target, format, listOf(target))
            val link = ObsidianLinkPolicy.link(note, target, rules, knownPaths = listOf(target))
            assertTrue(link.contains(if (markdown) ObsidianLinkPolicy.encode(path) else path))
            assertTrue(ObsidianLinkPolicy.candidates(if (markdown) ObsidianLinkPolicy.encode(path) else path,
                note, !markdown, listOf(target)).contains(target))
        }
    }

    @Test fun shortestPathDisambiguatesDuplicateNames() {
        val paths = listOf("assets/a.jpg", "archive/a.jpg", "assets/b.jpg")
        assertEquals("assets/a.jpg", ObsidianLinkPolicy.path("Daily/today.md", paths[0], "shortest", paths))
        assertEquals("b.jpg", ObsidianLinkPolicy.path("Daily/today.md", paths[2], "shortest", paths))
        assertEquals("assets/b.jpg", ObsidianLinkPolicy.path("Daily/today.md", paths[2], "shortest"))
    }

    @Test fun normalizationCannotEscapeTheVaultAndEncodingPreservesPlus() {
        assertNull(ObsidianLinkPolicy.normalize("../secret"))
        assertEquals("assets/a.jpg", ObsidianLinkPolicy.normalize("Daily/../assets/a.jpg"))
        assertEquals(emptyList<String>(), ObsidianLinkPolicy.candidates("../../secret.jpg", "Daily/today.md", false))
        assertEquals("a+b %20.jpg", ObsidianLinkPolicy.decode("a+b%20%2520.jpg"))
        assertEquals("../a%20%281%29%2B.jpg", ObsidianLinkPolicy.encode("../a (1)+.jpg"))
    }

    @Test fun ordinaryNoteLinksFollowSyntaxAndKeepAliases() {
        assertEquals("[Alias](../notes/Page.md)", ObsidianLinkPolicy.link("Daily/today.md", "notes/Page.md",
            ObsidianRules(markdownLinks = true, linkPathFormat = "relative"), false, "Alias"))
        assertEquals("[[notes/Page|Alias]]", ObsidianLinkPolicy.link("Daily/today.md", "notes/Page.md",
            ObsidianRules(linkPathFormat = "absolute"), false, "Alias"))
    }

    @Test fun completionUsesTheIndexedExtensionAndKeepsDotsInNoteNames() {
        val paths = listOf("notes/Page.MD", "notes/report.md.md")
        assertEquals("notes/Page.MD", ObsidianLinkPolicy.noteTarget("notes/Page", paths))
        assertEquals("notes/report.md.md", ObsidianLinkPolicy.noteTarget("notes/report.md", paths))
        assertEquals("notes/Other.md", ObsidianLinkPolicy.noteTarget("notes/Other", null))
    }
}
