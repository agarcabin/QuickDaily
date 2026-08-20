package com.quickdaily.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdaily.EditorToolbarAction
import com.quickdaily.EditorToolbarPolicy
import com.quickdaily.ui.theme.LocalQuickDailyMotion

internal object EditorToolbarLayoutPolicy {
    fun minimumButtonSize(buttonSize: Dp): Dp = buttonSize.coerceAtLeast(40.dp)

    fun fitCount(
        availableWidth: Dp,
        buttonSize: Dp,
        actionCount: Int,
        compact: Boolean = false,
    ): Int {
        if (actionCount <= 0) return 0
        val minimum = minimumButtonSize(buttonSize)
        val count = (availableWidth / minimum)
            .toInt()
            .coerceAtLeast(1)
            .coerceAtMost(actionCount)
        return if (compact) count.coerceAtMost(7) else count
    }

    fun slotWidth(availableWidth: Dp, buttonSize: Dp, actionCount: Int): Dp {
        val count = fitCount(availableWidth, buttonSize, actionCount)
        if (count == 0) return 0.dp
        return (availableWidth / count).coerceAtLeast(minimumButtonSize(buttonSize))
    }
}

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
    compact: Boolean = false,
    page: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onPageCountChanged: (Int) -> Unit = {},
    onUserPageInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val actions = remember(order, visible) {
        EditorToolbarPolicy.normalizeOrder(order)
            .mapNotNull(EditorToolbarAction::fromId)
            .filter { it.id in visible }
    }
    if (actions.isEmpty()) return

    val minimumButtonSize = EditorToolbarLayoutPolicy.minimumButtonSize(buttonSize)
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth.takeUnless { it == Dp.Infinity } ?: (minimumButtonSize * actions.size)
        val fitCount = EditorToolbarLayoutPolicy.fitCount(
            availableWidth = availableWidth,
            buttonSize = minimumButtonSize,
            actionCount = actions.size,
            compact = compact,
        )
        val pages = remember(actions, fitCount) { actions.chunked(fitCount) }
        val listState = rememberLazyListState()
        val motionPolicy = LocalQuickDailyMotion.current

        LaunchedEffect(pages.size) {
            onPageCountChanged(pages.size)
        }
        LaunchedEffect(listState, pages.size) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { onPageChanged(it.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
        }
        LaunchedEffect(page, pages.size) {
            val targetPage = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (motionPolicy.reducedMotion) listState.scrollToItem(targetPage)
            else listState.animateScrollToItem(targetPage)
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                onUserPageInteraction()
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.Start,
            flingBehavior = rememberSnapFlingBehavior(listState),
            userScrollEnabled = pages.size > 1,
        ) {
            itemsIndexed(
                items = pages,
                key = { pageIndex, page -> "toolbar-page-$pageIndex-${page.firstOrNull()?.id.orEmpty()}" },
            ) { _, pageActions ->
                val pageSlotWidth = EditorToolbarLayoutPolicy.slotWidth(
                    availableWidth = availableWidth,
                    buttonSize = minimumButtonSize,
                    actionCount = pageActions.size,
                )
                Row(
                    modifier = Modifier.width(availableWidth),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    pageActions.forEach { action ->
                        Box(
                            modifier = Modifier
                                .width(pageSlotWidth)
                                .sizeIn(minWidth = minimumButtonSize, minHeight = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (compact) {
                                Box(
                                    modifier = Modifier
                                        .size(minimumButtonSize)
                                        .clickable(
                                            enabled = enabled(action),
                                            onClick = { onAction(action) },
                                        )
                                        .semantics {
                                            contentDescription = action.label
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EditorToolbarActionIcon(
                                        action = action,
                                        recording = recording,
                                        recordingDurationMs = recordingDurationMs,
                                        tint = tint,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { onAction(action) },
                                    enabled = enabled(action),
                                    modifier = Modifier.size(minimumButtonSize),
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
                }
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
        EditorToolbarAction.ORDERED_LIST -> androidx.compose.material3.Text(
            "1.",
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
        )
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
        EditorToolbarAction.WIKILINK -> androidx.compose.material3.Text(
            text = "[[",
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier.semantics { contentDescription = actionDescription },
        )
        EditorToolbarAction.STRIKETHROUGH -> Icon(Icons.Default.FormatStrikethrough, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.INLINE_CODE -> Icon(Icons.Default.Code, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.QUOTE -> Icon(Icons.Default.FormatQuote, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.CODE_BLOCK -> Icon(Icons.Default.Code, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.HORIZONTAL_RULE -> Icon(Icons.Default.Remove, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.MARKDOWN_LINK -> Icon(Icons.Default.InsertLink, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.UNDO -> Icon(Icons.Default.Undo, actionDescription, tint = tint, modifier = modifier)
        EditorToolbarAction.REDO -> Icon(Icons.Default.Redo, actionDescription, tint = tint, modifier = modifier)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
