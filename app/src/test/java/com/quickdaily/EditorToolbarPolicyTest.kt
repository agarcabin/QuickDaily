package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarPolicyTest {
    @Test
    fun defaultOrderUsesRecommendedVisibleSequence() {
        assertEquals(
            listOf(
                "task", "heading", "wikilink", "image", "camera", "record", "timestamp", "undo", "redo",
                "indent", "outdent", "move_line_up", "move_line_down", "date_stamp", "list", "ordered_list", "bold",
                "attachment", "cut_line", "strikethrough", "inline_code", "quote", "code_block",
                "horizontal_rule", "markdown_link",
            ),
            EditorToolbarPolicy.defaultOrder.map { it.id },
        )
        assertEquals(
            setOf(
                "task", "heading", "wikilink", "image", "camera", "record", "timestamp", "undo", "redo",
                "indent", "outdent", "move_line_up", "move_line_down", "date_stamp",
            ),
            EditorToolbarPolicy.defaultVisible,
        )
    }

    @Test
    fun orderedListIsSchema6AndHiddenByDefault() {
        assertEquals(6, EditorToolbarPolicy.CURRENT_SCHEMA_VERSION)
        assertTrue(EditorToolbarAction.ORDERED_LIST.id !in EditorToolbarPolicy.defaultVisible)
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
        val oldDefault = setOf(
            "image", "task", "heading", "list", "bold", "attachment", "camera", "record",
            "indent", "outdent", "cut_line", "move_line_up", "move_line_down", "timestamp",
            "date_stamp", "wikilink", "undo", "redo",
        )
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
    fun schema5CustomVisibilitySurvivesTheSchema6Migration() {
        val saved = setOf(
            EditorToolbarAction.IMAGE.id,
            EditorToolbarAction.TASK.id,
            EditorToolbarAction.HEADING.id,
            EditorToolbarAction.LIST.id,
            EditorToolbarAction.BOLD.id,
            EditorToolbarAction.ATTACHMENT.id,
            EditorToolbarAction.CAMERA.id,
            EditorToolbarAction.RECORD.id,
            EditorToolbarAction.INDENT.id,
            EditorToolbarAction.OUTDENT.id,
            EditorToolbarAction.CUT_LINE.id,
            EditorToolbarAction.MOVE_LINE_UP.id,
            EditorToolbarAction.MOVE_LINE_DOWN.id,
            EditorToolbarAction.TIMESTAMP.id,
            EditorToolbarAction.DATE_STAMP.id,
            EditorToolbarAction.WIKILINK.id,
            EditorToolbarAction.UNDO.id,
            EditorToolbarAction.REDO.id,
        )

        val persisted = EditorToolbarPolicy.serializeVisible(saved)
        val afterRestart = EditorToolbarPolicy.readVisible(
            persisted,
            storedSchemaVersion = EditorToolbarPolicy.CURRENT_SCHEMA_VERSION - 1,
        )

        assertEquals(saved, afterRestart)
        assertTrue(EditorToolbarAction.LIST.id in afterRestart)
    }

    @Test
    fun currentSchemaRoundTripKeepsNewToolbarActionsVisible() {
        val enabled = EditorToolbarPolicy.defaultVisible + setOf(
            EditorToolbarAction.LIST.id,
            EditorToolbarAction.QUOTE.id,
            EditorToolbarAction.ORDERED_LIST.id,
        )

        val persisted = EditorToolbarPolicy.serializeVisible(enabled)
        val afterRestart = EditorToolbarPolicy.readVisible(
            persisted,
            storedSchemaVersion = EditorToolbarPolicy.CURRENT_SCHEMA_VERSION,
        )

        assertEquals(enabled, afterRestart)
        assertTrue(EditorToolbarAction.LIST.id in afterRestart)
        assertTrue(EditorToolbarAction.QUOTE.id in afterRestart)
        assertTrue(EditorToolbarAction.ORDERED_LIST.id in afterRestart)
    }

    @Test
    fun moveCanJumpAcrossMultipleRows() {
        val order = EditorToolbarPolicy.defaultOrder
        val moved = EditorToolbarPolicy.move(order, "image", "record")

        assertEquals(
            listOf(
                "task", "heading", "wikilink", "camera", "record", "image", "timestamp", "undo", "redo",
                "indent", "outdent", "move_line_up", "move_line_down", "date_stamp", "list", "ordered_list", "bold", "attachment",
                "cut_line", "strikethrough", "inline_code", "quote", "code_block",
                "horizontal_rule", "markdown_link",
            ),
            moved.map { it.id },
        )
    }
}
