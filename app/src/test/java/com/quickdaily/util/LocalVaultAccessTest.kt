package com.quickdaily.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalVaultAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun successfulProbePreservesNotesAndRemovesItsTemporaryFile() {
        val vault = temporary.newFolder("vault")
        val note = File(vault, "日记.md").apply { writeText("existing note\n- [ ] task\n") }
        val before = note.readBytes()
        assertTrue(LocalVaultAccess.verify(vault.path))
        assertArrayEquals(before, note.readBytes())
        assertEquals(listOf("日记.md"), vault.list()!!.toList())
    }

    @Test fun missingVaultIsRejectedWithoutCreatingAnEmptyReplacement() {
        val missing = File(temporary.root, "missing-vault")
        assertFalse(LocalVaultAccess.verify(missing.path))
        assertFalse(missing.exists())
    }

    @Test fun aFileCannotBeAcceptedAsAVaultDirectory() {
        val note = temporary.newFile("note.md").apply { writeText("do not modify") }
        assertFalse(LocalVaultAccess.verify(note.path))
        assertEquals("do not modify", note.readText())
    }
}
