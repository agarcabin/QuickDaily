package com.quickdaily

import org.junit.Assert.*
import org.junit.Test

class ObsidianConfigFolderPolicyTest {
    @Test fun turningOffCustomConfigUsesTheDefaultWithoutErasingTheSavedChoice() {
        val saved = ".obsidian-mobile"
        assertEquals(".obsidian", ObsidianConfigFolderPolicy.effectiveName(false, saved))
        assertEquals(saved, ObsidianConfigFolderPolicy.effectiveName(true, saved))
        assertEquals(".obsidian", ObsidianConfigFolderPolicy.effectiveName(true, "../outside"))
    }

    @Test fun selectedConfigDirectoryMustBeADirectChildOfTheCurrentVault() {
        assertEquals(".obsidian", ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/vault/.obsidian"))
        assertEquals(".obsidian-mobile", ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/vault/.obsidian-mobile"))
        assertNull(ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/other/.obsidian"))
        assertNull(ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/vault-copy/.obsidian"))
        assertNull(ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/vault/notes/.obsidian"))
        assertNull(ObsidianConfigFolderPolicy.fromLocalSelection("/vault", "/vault"))
    }

    @Test fun safRelativeSelectionsFollowTheSameConfigDirectoryRules() {
        assertEquals(".obsidian", ObsidianConfigFolderPolicy.fromRelativePath(".obsidian"))
        assertEquals(".手机配置", ObsidianConfigFolderPolicy.fromRelativePath(".手机配置"))
        listOf("", ".", "..", "../.obsidian", "notes/.obsidian", "notes", "/.obsidian", ".obsidian\\child", ".obsidian:bad", ".obsidian\u0000").forEach {
            assertNull("Invalid selection: $it", ObsidianConfigFolderPolicy.fromRelativePath(it))
        }
    }
}
