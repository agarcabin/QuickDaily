package com.quickdaily.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quickdaily.ui.theme.LocalQuickDailyMotion

/**
 * Shared M3 completion surface for tags and wikilinks.
 * The outline makes the popup legible over editor content while tonal elevation
 * keeps its depth aligned with the rest of the Material 3 surface hierarchy.
 */
@Composable
internal fun QuickDailyAutocompleteSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motionPolicy = LocalQuickDailyMotion.current
    Surface(
        modifier = modifier.animateContentSize(animationSpec = motionPolicy.spatialSpec()),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}
