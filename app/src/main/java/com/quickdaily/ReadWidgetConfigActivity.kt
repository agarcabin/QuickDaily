package com.quickdaily

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quickdaily.ui.theme.QuickDailyTheme
import java.io.File

/** Configuration activity for one QuickDailyReadWidget instance. */
class ReadWidgetConfigActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var currentConfig = ReadWidgetConfig()

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            BetaLogger.log("ReadWidgetConfig", "file picker cancelled widgetId=$widgetId")
            return@registerForActivityResult
        }
        val path = ReadWidgetConfigStore.filePathFromUri(this, uri)
        if (path == null) {
            BetaLogger.log("ReadWidgetConfig", "file picker rejected widgetId=" + widgetId + " uri=" + uri)
            Toast.makeText(this, "请选择可访问的 Markdown 文件", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        BetaLogger.log("ReadWidgetConfig", "file picker selected widgetId=" + widgetId + " path=" + path)
        chooseConfig(ReadWidgetConfig(ReadWidgetTarget.CUSTOM, path))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BetaLogger.init(this, "ReadWidgetConfigActivity")
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        currentConfig = ReadWidgetConfigStore.load(this, widgetId)
        setContent {
            QuickDailyTheme {
                ReadWidgetConfigScreen(
                    currentConfig = currentConfig,
                    recentPages = ReadWidgetConfigStore.recentCustomPaths(this),
                    onBack = ::finish,
                    onTargetSelected = { target ->
                        if (target == ReadWidgetTarget.CUSTOM) {
                            filePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        } else {
                            chooseConfig(ReadWidgetConfig(target))
                        }
                    },
                    onRecentPageSelected = { path ->
                        chooseConfig(ReadWidgetConfig(ReadWidgetTarget.CUSTOM, path))
                    },
                    onRecentPageRemoved = { path ->
                        BetaLogger.log("ReadWidgetConfig", "recent page removed widgetId=" + widgetId + " path=" + path)
                        ReadWidgetConfigStore.removeCustomPage(this, path)
                        recreate()
                    },
                )
            }
        }
    }

    private fun chooseConfig(config: ReadWidgetConfig) {
        BetaLogger.log(
            "ReadWidgetConfig",
            "target selected widgetId=" + widgetId + " target=" + config.target.key +
                " pathConfigured=" + config.customRelativePath.isNotBlank(),
        )
        currentConfig = config
        ReadWidgetConfigStore.save(this, widgetId, config)
        QuickDailyReadWidget.refreshAllWidgets(this, immediate = true)
        finish()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReadWidgetConfigScreen(
    currentConfig: ReadWidgetConfig,
    recentPages: List<String>,
    onBack: () -> Unit,
    onTargetSelected: (ReadWidgetTarget) -> Unit,
    onRecentPageSelected: (String) -> Unit,
    onRecentPageRemoved: (String) -> Unit,
) {
    val targets = listOf(ReadWidgetTarget.TODAY, ReadWidgetTarget.CUSTOM)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("便签小部件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Text("选择显示页面", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "每个便签小部件可以独立显示今日日记或一个具体的 Markdown 页面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                items(targets, key = { it.key }) { target ->
                    ListItem(
                        headlineContent = { Text(target.label) },
                        leadingContent = {
                            RadioButton(
                                selected = currentConfig.target == target &&
                                    (target != ReadWidgetTarget.CUSTOM || currentConfig.customRelativePath.isBlank()),
                                onClick = { onTargetSelected(target) },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onTargetSelected(target) }
                            .semantics { role = Role.RadioButton },
                    )
                }
                if (recentPages.isNotEmpty()) {
                    item {
                        Text(
                            "最近的自定义页面",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 0.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                }
                items(recentPages, key = { it }) { path ->
                    val available = ReadWidgetConfigStore.customFilePath(
                        androidx.compose.ui.platform.LocalContext.current,
                        ReadWidgetConfig(ReadWidgetTarget.CUSTOM, path),
                    )?.let { File(it).isFile } == true
                    val checked = currentConfig.target == ReadWidgetTarget.CUSTOM &&
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
}
