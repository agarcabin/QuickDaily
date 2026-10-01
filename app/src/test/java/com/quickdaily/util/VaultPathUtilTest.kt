package com.quickdaily.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VaultPathUtilTest {
    @Test
    fun resolvesOnlyPathsInsideTheVault() {
        val vault = File(System.getProperty("java.io.tmpdir"), "quickdaily-vault").canonicalPath

        assertEquals(
            File(vault, "Projects${File.separator}出差.md").canonicalPath,
            VaultPathUtil.resolve(vault, "Projects/出差.md"),
        )
        assertNull(VaultPathUtil.resolve(vault, "../outside.md"))
    }

    @Test
    fun convertsAbsolutePathBackToVaultRelativePath() {
        val vault = File(System.getProperty("java.io.tmpdir"), "quickdaily-vault").canonicalPath
        val file = File(vault, "Projects${File.separator}出差.md").canonicalPath

        assertEquals("Projects/出差.md", VaultPathUtil.relativePath(vault, file))
    }

    @Test
    fun keepsAnAbsoluteTargetOutsideTheVault() {
        val vault = File(System.getProperty("java.io.tmpdir"), "quickdaily-vault").canonicalPath
        val external = File(System.getProperty("java.io.tmpdir"), "external-page.md").canonicalPath

        assertEquals(external, VaultPathUtil.resolveTarget(vault, external))
    }

    @Test
    fun safRelativePathsRejectPhysicalAbsolutePaths() {
        assertEquals("Daily/2026-08-31.md", SafVirtualPath.normalizeRelative("Daily/2026-08-31.md"))
        assertNull(SafVirtualPath.normalizeRelative("/storage/home/Obsidian/2026-08-31.md"))
        assertNull(SafVirtualPath.normalizeRelative("C:/notes/2026-08-31.md"))
    }
}
