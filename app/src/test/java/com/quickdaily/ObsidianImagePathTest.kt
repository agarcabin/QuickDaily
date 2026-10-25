package com.quickdaily

import com.quickdaily.markdown.MdLine
import com.quickdaily.markdown.parseLines
import com.quickdaily.util.VaultPathUtil
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ObsidianImagePathTest {
    @Test fun imageParserRetainsSyntaxAndParenthesesInOldPaths() {
        val parsed = parseLines("![photo](../assets/a (1).jpg)\n![[a (1).jpg|100]]\n![](../assets/a%20%281%29.jpg)")
        assertEquals(MdLine.Image("../assets/a (1).jpg", "photo"), parsed[0])
        assertEquals(MdLine.Image("a (1).jpg", "100", wiki = true), parsed[1])
        assertEquals(MdLine.Image("../assets/a%20%281%29.jpg", ""), parsed[2])
    }

    @Test fun relativeAndEncodedImagePathsResolveToTheActualFile() {
        val root = Files.createTempDirectory("qd-image-path").toFile()
        try {
            val asset = root.resolve("assets/照片 (1)+100%.jpg").apply { requireNotNull(parentFile).mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val link = ObsidianLinkPolicy.link("Daily/today.md", "assets/${asset.name}",
                ObsidianRules(markdownLinks = true, linkPathFormat = "relative"))
            val image = parseLines(link).single() as MdLine.Image
            val candidates = ObsidianLinkPolicy.candidates(image.path, "Daily/today.md", image.wiki)
            val resolved = candidates.mapNotNull { VaultPathUtil.resolve(root.path, it) }
            assertEquals(asset.canonicalPath, resolved.single())
            assertArrayEquals(byteArrayOf(1, 2, 3), java.io.File(resolved.single()).readBytes())
        } finally {
            check(root.canonicalFile.parentFile == java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile)
            check(root.name.startsWith("qd-image-path"))
            root.deleteRecursively()
        }
    }

    @Test fun plainVaultPathsAndUniqueWikilinksRemainReadable() {
        val paths = listOf("assets/a.jpg", "archive/a.jpg", "assets/unique.jpg")
        assertEquals("assets/a.jpg", ObsidianLinkPolicy.candidates("assets/a.jpg", "Daily/today.md", true, paths).first())
        assertTrue(ObsidianLinkPolicy.candidates("unique.jpg", "Daily/today.md", true, paths).contains("assets/unique.jpg"))
        assertFalse(ObsidianLinkPolicy.candidates("a.jpg", "Daily/today.md", true, paths).contains("assets/a.jpg"))
    }
}
