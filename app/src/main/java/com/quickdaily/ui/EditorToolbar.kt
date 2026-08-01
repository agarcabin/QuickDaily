package com.quickdaily.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdaily.EditorToolbarAction
import com.quickdaily.EditorToolbarPolicy

@Composable
fun EditorToolbarActions(
    order: List<String>,
    visible: Set<String>,
    onAction: (EditorToolbarAction) -> Unit,
    enabled: (EditorToolbarAction) -> Boolean = { true },
    recording: Boolean = false,
    recordingDurationMs: Long = 0L,
    tint: Color,
    buttonSize: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val actions = remember(order, visible) {
        EditorToolbarPolicy.normalizeOrder(order)
            .mapNotNull(EditorToolbarAction::fromId)
            .filter { it.id in visible }
    }
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            IconButton(
                onClick = { onAction(action) },
                enabled = enabled(action),
                modifier = Modifier.size(buttonSize),
            ) {
                EditorToolbarActionIcon(
                    action = action,
                    recording = recording,
                    recordingDurationMs = recordingDurationMs,
                    tint = tint,
                )
            }
        }
    }
}

@Composable
fun EditorToolbarActionIcon(
    action: EditorToolbarAction,
    recording: Boolean = false,
    recordingDurationMs: Long = 0L,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val actionDescription = action.label
    when (action) {
        EditorToolbarAction.IMAGE -> Icon(Icons.Default.Image, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.TASK -> Icon(Icons.Default.CheckBoxOutlineBlank, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.HEADING -> androidx.compose.material3.Text(
            "#",
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
        )
        EditorToolbarAction.LIST -> Icon(Icons.Default.FormatListBulleted, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.BOLD -> Icon(Icons.Default.FormatBold, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.ATTACHMENT -> Icon(Icons.Default.AttachFile, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.CAMERA -> Icon(Icons.Default.AddAPhoto, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.RECORD -> {
            val recordTint by animateColorAsState(
                targetValue = if (recording) MaterialTheme.colorScheme.error else tint,
                label = "recordingTint",
            )
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = recording,
                    label = "recordingIcon",
                ) { active ->
                    Icon(
                        if (active) Icons.Default.Stop else Icons.Default.Mic,
                        actionDescription,
                        tint = recordTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                androidx.compose.animation.AnimatedVisibility(visible = recording) {
                    androidx.compose.material3.Text(
                        formatDuration(recordingDurationMs),
                        color = recordTint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        EditorToolbarAction.INDENT -> Icon(Icons.Default.FormatIndentIncrease, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.OUTDENT -> Icon(Icons.Default.FormatIndentDecrease, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.CUT_LINE -> Icon(Icons.Default.ContentCut, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.MOVE_LINE_UP -> Icon(Icons.Default.KeyboardArrowUp, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.MOVE_LINE_DOWN -> Icon(Icons.Default.KeyboardArrowDown, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.TIMESTAMP -> Icon(Icons.Default.AccessTime, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.DATE_STAMP -> Icon(Icons.Default.Today, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.WIKILINK -> Icon(Icons.Default.Link, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.UNDO -> Icon(Icons.Default.Undo, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.REDO -> Icon(Icons.Default.Redo, actionDescription, tint = tint, modifier = modifier)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
