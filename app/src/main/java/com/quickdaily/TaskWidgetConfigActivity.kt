package com.quickdaily

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quickdaily.ui.theme.QuickDailyTheme

/** Compose M3 chooser launched from one task widget instance. */
class TaskWidgetConfigActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetId.INVALID
    private var currentConfig: TaskWidgetConfig = TaskWidgetConfig()

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            BetaLogger.log("TaskWidgetConfig", "file picker cancelled widgetId=$widgetId")
            return@registerForActivityResult
        }
        val filePath = TaskWidgetConfigStore.filePathFromUri(this, uri)
        if (filePath == null) {
            BetaLogger.log("TaskWidgetConfig", "file picker rejected widgetId=$widgetId uri=$uri")
            Toast.makeText(this, "请选择可访问的 Markdown 文件", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        BetaLogger.log("TaskWidgetConfig", "file picker selected widgetId=$widgetId path=$filePath")
        chooseConfig(TaskWidgetConfig(TaskWidgetScope.CUSTOM, filePath))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BetaLogger.init(this, "TaskWidgetConfigActivity")
        widgetId = intent.getIntExtra(AppWidgetId.EXTRA, AppWidgetId.INVALID)
        if (widgetId == AppWidgetId.INVALID) {
            BetaLogger.log("TaskWidgetConfig", "finish invalid widgetId")
            finish()
            return
        }
        currentConfig = TaskWidgetConfigStore.load(this, widgetId)
        BetaLogger.log(
            "TaskWidgetConfig",
            "open widgetId=$widgetId scope=${currentConfig.scope.key} path=${currentConfig.customRelativePath}",
        )
        setContent {
            QuickDailyTheme {
                TaskWidgetConfigScreen(
                    currentConfig = currentConfig,
                    recentPages = TaskWidgetConfigStore.recentCustomPaths(this),
                    onBack = { finish() },
                    onScopeSelected = { scope ->
                        if (scope == TaskWidgetScope.CUSTOM) {
                            filePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        } else {
                            chooseConfig(TaskWidgetConfig(scope))
                        }
                    },
                    onRecentPageSelected = { path ->
                        chooseConfig(TaskWidgetConfig(TaskWidgetScope.CUSTOM, path))
                    },
                    onRecentPageRemoved = { path ->
                        BetaLogger.log("TaskWidgetConfig", "recent page removed widgetId=$widgetId path=$path")
                        TaskWidgetConfigStore.removeCustomPage(this, path)
                        recreate()
                    },
                )
            }
        }
    }

    private fun chooseConfig(config: TaskWidgetConfig) {
        BetaLogger.log("TaskWidgetConfig", "scope selected widgetId=$widgetId scope=${config.scope.key}")
        currentConfig = config
        TaskWidgetConfigStore.save(this, widgetId, config)
        TaskWidget.refreshAllWidgets(this, immediate = true)
        finish()
    }

    private object AppWidgetId {
        const val EXTRA = "appWidgetId"
        const val INVALID = -1
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskWidgetConfigScreen(
    currentConfig: TaskWidgetConfig,
    recentPages: List<String>,
    onBack: () -> Unit,
    onScopeSelected: (TaskWidgetScope) -> Unit,
    onRecentPageSelected: (String) -> Unit,
    onRecentPageRemoved: (String) -> Unit,
) {
    val scopes = listOf(
        TaskWidgetScope.TODAY,
        TaskWidgetScope.WEEK,
        TaskWidgetScope.MONTH,
        TaskWidgetScope.CUSTOM,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务小部件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("选择任务范围", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "每个桌面小部件可以独立显示不同范围或 Markdown 页面。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(scopes, key = { it.key }) { scope ->
                val checked = currentConfig.scope == scope &&
                    (scope != TaskWidgetScope.CUSTOM || currentConfig.customRelativePath.isBlank())
                ListItem(
                    headlineContent = { Text(scope.label) },
                    leadingContent = {
                        RadioButton(
                            selected = checked,
                            onClick = { onScopeSelected(scope) },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onScopeSelected(scope) }
                        .semantics { role = Role.RadioButton },
                )
            }

            item {
                AnimatedVisibility(visible = recentPages.isNotEmpty()) {
                    Text(
                        "最近的自定义页面",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
            }

            items(recentPages, key = { it }) { path ->
                val available = TaskWidgetConfigStore.customFilePath(
                    androidx.compose.ui.platform.LocalContext.current,
                    TaskWidgetConfig(TaskWidgetScope.CUSTOM, path),
                )?.let { java.io.File(it).isFile } == true
                val checked = currentConfig.scope == TaskWidgetScope.CUSTOM &&
                    currentConfig.customRelativePath == path
                ListItem(
                    headlineContent = {
                        Text(
                            TaskWidgetConfigStore.displayName(path).ifBlank { path },
                            color = if (available) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = if (!available) {
                        { Text("文件不可用", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    leadingContent = {
                        RadioButton(
                            selected = checked,
                            onClick = { onRecentPageSelected(path) },
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onRecentPageRemoved(path) }) {
                            Icon(Icons.Default.Close, contentDescription = "移除自定义页面")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onRecentPageSelected(path) }
                        .semantics { role = Role.RadioButton },
                )
            }
        }
    }
}
