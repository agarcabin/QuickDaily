package com.quickdaily

import com.quickdaily.util.ContentUtil
import com.quickdaily.util.FileReadSnapshot
import com.quickdaily.util.ReadResult
import java.io.IOException

internal data class LoadedEditorDocument(val rawContent: String, val snapshot: FileReadSnapshot)

internal object EditorDocumentLoader {
    val readWorker = BoundedLoader(timeoutMs = 8_000, capacity = 2)
    fun load(isToday: Boolean, read: () -> FileReadSnapshot, template: () -> String): LoadedEditorDocument {
        val snapshot = read()
        val content = when (val result = snapshot.result) {
            is ReadResult.Success -> result.content
            ReadResult.NotFound -> {
                if (!isToday) throw IOException("editor_target_missing")
                ""
            }
            is ReadResult.Error -> throw IOException("editor_read_failed", result.exception)
        }
        val parsed = ContentUtil.parseFrontmatter(content)
        val raw = if (isToday && (content.isEmpty() || (parsed.hasFrontmatter && parsed.body.isBlank()))) {
            template().ifEmpty { content }
        } else content
        return LoadedEditorDocument(raw, snapshot)
    }
}
