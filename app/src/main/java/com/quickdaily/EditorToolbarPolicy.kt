package com.quickdaily

/** Stable IDs shared by the full editor and the floating editor toolbar. */
enum class EditorToolbarAction(val id: String, val label: String) {
    IMAGE("image", "图片"),
    TASK("task", "任务"),
    HEADING("heading", "标题"),
    LIST("list", "列表"),
    BOLD("bold", "加粗"),
    ATTACHMENT("attachment", "附件"),
    CAMERA("camera", "拍照"),
    RECORD("record", "录音"),
    INDENT("indent", "Tab缩进"),
    OUTDENT("outdent", "Tab反缩进"),
    CUT_LINE("cut_line", "剪切行"),
    MOVE_LINE_UP("move_line_up", "上移行"),
    MOVE_LINE_DOWN("move_line_down", "下移行"),
    TIMESTAMP("timestamp", "时间戳"),
    DATE_STAMP("date_stamp", "日期戳"),
    WIKILINK("wikilink", "双链[[]]"),
    UNDO("undo", "撤销"),
    REDO("redo", "重做");

    companion object {
        fun fromId(id: String): EditorToolbarAction? =
            entries.firstOrNull { it.id == id }
    }
}

enum class HomeEntryMode(val key: String, val label: String) {
    OVERLAY("overlay", "悬浮窗"),
    EDITOR("editor", "编辑页面");

    companion object {
        fun fromKey(key: String?): HomeEntryMode =
            entries.firstOrNull { it.key == key } ?: OVERLAY
    }
}

object EditorToolbarPolicy {
    const val PREF_ORDER = "editor_toolbar_order"
    const val PREF_VISIBLE = "editor_toolbar_visible"
    const val PREF_SCHEMA_VERSION = "editor_toolbar_schema_version"
    const val CURRENT_SCHEMA_VERSION = 4

    // Keep the recommended actions compact and put optional actions after them.
    val defaultOrder: List<EditorToolbarAction> = listOf(
        EditorToolbarAction.IMAGE,
        EditorToolbarAction.TASK,
        EditorToolbarAction.HEADING,
        EditorToolbarAction.WIKILINK,
        EditorToolbarAction.CAMERA,
        EditorToolbarAction.RECORD,
        EditorToolbarAction.TIMESTAMP,
        EditorToolbarAction.UNDO,
        EditorToolbarAction.REDO,
        EditorToolbarAction.LIST,
        EditorToolbarAction.BOLD,
        EditorToolbarAction.ATTACHMENT,
        EditorToolbarAction.INDENT,
        EditorToolbarAction.OUTDENT,
        EditorToolbarAction.CUT_LINE,
        EditorToolbarAction.MOVE_LINE_UP,
        EditorToolbarAction.MOVE_LINE_DOWN,
        EditorToolbarAction.DATE_STAMP,
    )

    val defaultVisible: Set<String> = setOf(
        EditorToolbarAction.IMAGE.id,
        EditorToolbarAction.TASK.id,
        EditorToolbarAction.HEADING.id,
        EditorToolbarAction.WIKILINK.id,
        EditorToolbarAction.CAMERA.id,
        EditorToolbarAction.RECORD.id,
        EditorToolbarAction.TIMESTAMP.id,
        EditorToolbarAction.UNDO.id,
    )
    private val legacyDefaultOrder = listOf(
        EditorToolbarAction.IMAGE,
        EditorToolbarAction.TASK,
        EditorToolbarAction.HEADING,
        EditorToolbarAction.LIST,
        EditorToolbarAction.BOLD,
        EditorToolbarAction.ATTACHMENT,
        EditorToolbarAction.CAMERA,
        EditorToolbarAction.RECORD,
        EditorToolbarAction.INDENT,
        EditorToolbarAction.OUTDENT,
        EditorToolbarAction.CUT_LINE,
        EditorToolbarAction.MOVE_LINE_UP,
        EditorToolbarAction.MOVE_LINE_DOWN,
        EditorToolbarAction.TIMESTAMP,
        EditorToolbarAction.DATE_STAMP,
        EditorToolbarAction.WIKILINK,
        EditorToolbarAction.UNDO,
        EditorToolbarAction.REDO,
    ).map { it.id }
    private val legacyDefaultVisible = EditorToolbarAction.entries.map { it.id }.toSet()
    private val previousDefaultVisible = setOf(
        EditorToolbarAction.IMAGE.id,
        EditorToolbarAction.TASK.id,
        EditorToolbarAction.HEADING.id,
        EditorToolbarAction.CAMERA.id,
        EditorToolbarAction.RECORD.id,
        EditorToolbarAction.TIMESTAMP.id,
        EditorToolbarAction.UNDO.id,
        EditorToolbarAction.REDO.id,
    )
    private val previousDefaultOrder = listOf(
        EditorToolbarAction.IMAGE,
        EditorToolbarAction.TASK,
        EditorToolbarAction.HEADING,
        EditorToolbarAction.CAMERA,
        EditorToolbarAction.RECORD,
        EditorToolbarAction.TIMESTAMP,
        EditorToolbarAction.UNDO,
        EditorToolbarAction.REDO,
        EditorToolbarAction.LIST,
        EditorToolbarAction.BOLD,
        EditorToolbarAction.ATTACHMENT,
        EditorToolbarAction.INDENT,
        EditorToolbarAction.OUTDENT,
        EditorToolbarAction.CUT_LINE,
        EditorToolbarAction.MOVE_LINE_UP,
        EditorToolbarAction.MOVE_LINE_DOWN,
        EditorToolbarAction.DATE_STAMP,
        EditorToolbarAction.WIKILINK,
    ).map { it.id }

    fun normalizeOrder(raw: List<String>): List<String> {
        val known = raw.mapNotNull(EditorToolbarAction::fromId).distinct()
        return known.map { it.id } + defaultOrder.filterNot { it in known }.map { it.id }
    }

    fun migrateOrder(raw: List<String>, legacy: Boolean): List<String> {
        val normalized = normalizeOrder(raw)
        return if (legacy && (normalized == legacyDefaultOrder || normalized == previousDefaultOrder)) {
            defaultOrder.map { it.id }
        } else normalized
    }

    fun normalizeVisible(raw: Set<String>): Set<String> =
        raw.mapNotNull(EditorToolbarAction::fromId).map { it.id }.toSet()

    fun migrateVisible(raw: Set<String>, legacy: Boolean): Set<String> {
        val normalized = normalizeVisible(raw)
        return if (legacy && (normalized == legacyDefaultVisible || normalized == previousDefaultVisible)) {
            defaultVisible
        } else normalized
    }

    fun visiblePositions(
        order: List<EditorToolbarAction>,
        visible: Set<String>,
    ): Map<String, Int?> {
        var position = 0
        return order.associate { action ->
            action.id to if (action.id in visible) {
                ++position
            } else {
                null
            }
        }
    }

    fun move(order: List<EditorToolbarAction>, draggedId: String, targetId: String): List<EditorToolbarAction> {
        val fromIndex = order.indexOfFirst { it.id == draggedId }
        val targetIndex = order.indexOfFirst { it.id == targetId }
        if (fromIndex < 0 || targetIndex < 0 || fromIndex == targetIndex) return order
        return order.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(targetIndex, moved)
        }
    }

    fun parseOrder(raw: String?): List<String> =
        normalizeOrder(raw.orEmpty().split(',').map(String::trim).filter(String::isNotBlank))

    fun migrateOrder(raw: String?, legacy: Boolean): List<String> =
        migrateOrder(raw.orEmpty().split(',').map(String::trim).filter(String::isNotBlank), legacy)

    fun parseVisible(raw: String?): Set<String> =
        normalizeVisible(raw.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet())

    fun serializeOrder(order: List<String>): String = normalizeOrder(order).joinToString(",")

    fun serializeVisible(visible: Set<String>): String = normalizeVisible(visible).joinToString(",")
}
