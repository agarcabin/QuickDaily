package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarPolicyTest {
    @Test
    fun defaultOrderUsesRecommendedVisibleSequence() {
        assertEquals(
            listOf(
                "image", "task", "heading", "wikilink", "camera", "record", "timestamp", "undo", "redo",
                "list", "bold", "attachment", "indent", "outdent", "cut_line", "move_line_up",
                "move_line_down", "date_stamp",
            ),
            EditorToolbarPolicy.defaultOrder.map { it.id },
        )
        assertEquals(
            setOf("image", "task", "heading", "wikilink", "camera", "record", "timestamp", "undo"),
            EditorToolbarPolicy.defaultVisible,
        )
    }

    @Test
    fun orderMigrationRemovesUnknownsAndAppendsNewActions() {
        val order = EditorToolbarPolicy.parseOrder("undo,unknown,image")
        assertEquals("undo", order.first())
        assertEquals("image", order[1])
        assertTrue(order.contains("camera"))
        assertTrue(order.contains("record"))
        assertTrue(order.contains("wikilink"))
    }

    @Test
    fun legacyDefaultOrderUsesRecommendedSequence() {
        val oldDefault = "image,task,heading,list,bold,attachment,camera,record,indent,outdent,cut_line,move_line_up,move_line_down,timestamp,date_stamp,wikilink,undo,redo"
        assertEquals(
            EditorToolbarPolicy.defaultOrder.map { it.id },
            EditorToolbarPolicy.migrateOrder(oldDefault, legacy = true),
        )
    }

    @Test
    fun visibilityMigrationCanHideEveryAction() {
        assertEquals(setOf("image", "record"), EditorToolbarPolicy.parseVisible("image,record"))
        assertEquals(emptySet<String>(), EditorToolbarPolicy.parseVisible(""))
    }

    @Test
    fun legacyDefaultVisibilityUsesRecommendedSequence() {
        val oldDefault = EditorToolbarAction.entries.map { it.id }.toSet()
        assertEquals(EditorToolbarPolicy.defaultVisible, EditorToolbarPolicy.migrateVisible(oldDefault, legacy = true))
        val migrated = EditorToolbarPolicy.migrateVisible(setOf("image", "record"), legacy = true)
        assertEquals(setOf("image", "record"), migrated)
        assertEquals(setOf("image", "record"), EditorToolbarPolicy.migrateVisible(setOf("image", "record"), legacy = false))
    }

    @Test
    fun hiddenActionsDoNotConsumeVisiblePositions() {
        val order = listOf(
            EditorToolbarAction.IMAGE,
            EditorToolbarAction.TASK,
            EditorToolbarAction.HEADING,
        )

        val positions = EditorToolbarPolicy.visiblePositions(order, setOf("task", "heading"))
        assertEquals(null, positions["image"])
        assertEquals(1, positions["task"])
        assertEquals(2, positions["heading"])
    }

    @Test
    fun moveCanJumpAcrossMultipleRows() {
        val order = EditorToolbarPolicy.defaultOrder
        val moved = EditorToolbarPolicy.move(order, "image", "record")

        assertEquals(
            listOf(
                "task", "heading", "wikilink", "camera", "record", "image", "timestamp", "undo", "redo", "list",
                "bold", "attachment", "indent", "outdent", "cut_line", "move_line_up",
                "move_line_down", "date_stamp",
            ),
            moved.map { it.id },
        )
    }
}
