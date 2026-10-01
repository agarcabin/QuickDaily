package com.quickdaily

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.quickdaily.util.FileUtil
import com.quickdaily.ui.theme.QuickDailyTheme

private data class PendingFolderSelection(
    val uri: String,
    val displayName: String,
    val grantFlags: Int,
)

/** Compose M3 chooser launched from one task widget instance. */
class TaskWidgetConfigActivity : LocalizedComponentActivity() {
    private var widgetId: Int = AppWidgetId.INVALID
    private var currentConfig: TaskWidgetConfig = TaskWidgetConfig()
    private var pendingFolderSelection by mutableStateOf<PendingFolderSelection?>(null)

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
            Toast.makeText(this, getString(R.string.qd_widget_markdown_file_required), Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        BetaLogger.log("TaskWidgetConfig", "file picker selected widgetId=$widgetId path=$filePath")
        chooseConfig(TaskWidgetConfig(TaskWidgetScope.CUSTOM, filePath))
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            BetaLogger.log("TaskWidgetConfig", "folder picker cancelled widgetId=$widgetId")
            return@registerForActivityResult
        }
        val data = result.data
        val uri = data?.data
        if (uri == null) {
            BetaLogger.log("TaskWidgetConfig", "folder picker returned without uri widgetId=$widgetId")
            return@registerForActivityResult
        }
        val requestedFlags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        if (requestedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            Toast.makeText(this, getString(R.string.qd_task_folder_read_permission), Toast.LENGTH_LONG).show()
            BetaLogger.log("TaskWidgetConfig", "folder picker rejected without read grant widgetId=$widgetId uri=$uri")
            return@registerForActivityResult
        }
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                requestedFlags,
            )
            contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
        }.getOrDefault(false)
        if (!persisted) {
            Toast.makeText(this, getString(R.string.qd_task_folder_permission_failed), Toast.LENGTH_LONG).show()
            BetaLogger.log("TaskWidgetConfig", "folder picker permission not persisted widgetId=$widgetId uri=$uri flags=$requestedFlags")
            return@registerForActivityResult
        }
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder == null || !folder.exists() || !folder.canRead()) {
            Toast.makeText(this, getString(R.string.qd_task_folder_unavailable), Toast.LENGTH_LONG).show()
            BetaLogger.log("TaskWidgetConfig", "folder picker rejected widgetId=$widgetId uri=$uri")
            return@registerForActivityResult
        }
        pendingFolderSelection = PendingFolderSelection(
            uri = uri.toString(),
            displayName = folder.name.orEmpty().ifBlank { getString(R.string.qd_task_custom_folder_title) },
            grantFlags = requestedFlags,
        )
        BetaLogger.log(
            "TaskWidgetConfig",
            "folder picker selected widgetId=$widgetId uri=$uri name=${folder.name.orEmpty()} flags=$requestedFlags",
        )
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
        if (savedInstanceState != null) {
            val pendingUri = savedInstanceState.getString(STATE_PENDING_FOLDER_URI).orEmpty()
            if (pendingUri.isNotBlank()) {
                pendingFolderSelection = PendingFolderSelection(
                    uri = pendingUri,
                    displayName = savedInstanceState.getString(STATE_PENDING_FOLDER_NAME)
                        .orEmpty()
                        .ifBlank { getString(R.string.qd_task_custom_folder_title) },
                    grantFlags = savedInstanceState.getInt(STATE_PENDING_FOLDER_FLAGS),
                )
            }
        }
        BetaLogger.log(
            "TaskWidgetConfig",
            "open widgetId=$widgetId scope=${currentConfig.scope.key} path=${currentConfig.customRelativePath}",
        )
        setContent {
            QuickDailyTheme {
                TaskWidgetConfigScreen(
                    currentConfig = currentConfig,
                    recentPages = TaskWidgetConfigStore.recentCustomPaths(this),
                    pendingFolderSelection = pendingFolderSelection,
                    onBack = { finish() },
                    onScopeSelected = { scope ->
                        if (scope == TaskWidgetScope.CUSTOM) {
                            filePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        } else if (scope == TaskWidgetScope.CUSTOM_FOLDER) {
                            folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                                )
                            })
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
                    onFolderSelectionDismiss = { pendingFolderSelection = null },
                    onFolderSelectionConfirm = { window, includeSubfolders ->
                        pendingFolderSelection?.let { pending ->
                            val saved = chooseConfig(
                                TaskWidgetConfig(
                                    scope = TaskWidgetScope.CUSTOM_FOLDER,
                                    customFolderUri = pending.uri,
                                    customFolderName = pending.displayName,
                                    customFolderGrantFlags = pending.grantFlags,
                                    folderTimeWindow = window,
                                    folderIncludeSubfolders = includeSubfolders,
                                ),
                            )
                            if (saved) pendingFolderSelection = null
                        }
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingFolderSelection?.let { pending ->
            outState.putString(STATE_PENDING_FOLDER_URI, pending.uri)
            outState.putString(STATE_PENDING_FOLDER_NAME, pending.displayName)
            outState.putInt(STATE_PENDING_FOLDER_FLAGS, pending.grantFlags)
        }
        super.onSaveInstanceState(outState)
    }

    private fun chooseConfig(config: TaskWidgetConfig): Boolean {
        BetaLogger.log("TaskWidgetConfig", "scope selected widgetId=$widgetId scope=${config.scope.key}")
        val previousConfig = currentConfig
        if (!TaskWidgetConfigStore.save(this, widgetId, config)) {
            Toast.makeText(this, getString(R.string.qd_task_save_failed), Toast.LENGTH_LONG).show()
            return false
        }
        val updated = runCatching {
            TaskWidget.updateWidget(
                context = this,
                appWidgetManager = AppWidgetManager.getInstance(this),
                widgetId = widgetId,
                result = null,
                configOverride = config,
            )
        }.getOrDefault(false)
        if (!updated) {
            TaskWidgetConfigStore.save(this, widgetId, previousConfig)
            runCatching {
                TaskWidget.updateWidget(
                    context = this,
                    appWidgetManager = AppWidgetManager.getInstance(this),
                    widgetId = widgetId,
                    result = null,
                    configOverride = previousConfig,
                )
            }
            Toast.makeText(this, getString(R.string.qd_task_update_failed), Toast.LENGTH_LONG).show()
            return false
        }
        currentConfig = config
        TaskWidget.refreshAllWidgets(this, immediate = true)
        if (config.scope == TaskWidgetScope.CUSTOM_FOLDER) {
            Toast.makeText(this, getString(R.string.qd_task_folder_selected, config.customFolderName), Toast.LENGTH_SHORT).show()
        }
        finish()
        return true
    }

    private object AppWidgetId {
        const val EXTRA = "appWidgetId"
        const val INVALID = -1
    }

    companion object {
        private const val STATE_PENDING_FOLDER_URI = "pending_folder_uri"
        private const val STATE_PENDING_FOLDER_NAME = "pending_folder_name"
        private const val STATE_PENDING_FOLDER_FLAGS = "pending_folder_flags"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskWidgetConfigScreen(
    currentConfig: TaskWidgetConfig,
    recentPages: List<String>,
    pendingFolderSelection: PendingFolderSelection?,
    onBack: () -> Unit,
    onScopeSelected: (TaskWidgetScope) -> Unit,
    onRecentPageSelected: (String) -> Unit,
    onRecentPageRemoved: (String) -> Unit,
    onFolderSelectionDismiss: () -> Unit,
    onFolderSelectionConfirm: (TaskWidgetFolderTimeWindow, Boolean) -> Unit,
) {
    val scopes = listOf(
        TaskWidgetScope.TODAY,
        TaskWidgetScope.WEEK,
        TaskWidgetScope.MONTH,
        TaskWidgetScope.CUSTOM,
        TaskWidgetScope.CUSTOM_FOLDER,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qd_task_widget_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.qd_common_close))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
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
                        Text(stringResource(R.string.qd_task_select_range), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.qd_task_widget_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                items(scopes, key = { it.key }) { scope ->
                    val checked = when (scope) {
                        TaskWidgetScope.CUSTOM -> currentConfig.scope == scope && currentConfig.customRelativePath.isBlank()
                        TaskWidgetScope.CUSTOM_FOLDER -> currentConfig.scope == scope && currentConfig.customFolderUri.isNotBlank()
                        else -> currentConfig.scope == scope
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(scope.labelRes)) },
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
                            stringResource(R.string.qd_widget_recent_custom_page),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                }

                items(recentPages, key = { it }) { path ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val available = TaskWidgetConfigStore.customFilePath(
                        context,
                        TaskWidgetConfig(TaskWidgetScope.CUSTOM, path),
                    )?.let { FileUtil.exists(it) && !FileUtil.isDirectory(it) } == true
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
                            { Text(stringResource(R.string.qd_widget_file_unavailable), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        leadingContent = {
                            RadioButton(
                                selected = checked,
                                onClick = { onRecentPageSelected(path) },
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onRecentPageRemoved(path) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.qd_widget_remove_custom_page))
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
    if (pendingFolderSelection != null) {
        FolderTaskOptionsDialog(
            selection = pendingFolderSelection,
            onDismiss = onFolderSelectionDismiss,
            onConfirm = onFolderSelectionConfirm,
        )
    }
}

@Composable
private fun FolderTaskOptionsDialog(
    selection: PendingFolderSelection,
    onDismiss: () -> Unit,
    onConfirm: (TaskWidgetFolderTimeWindow, Boolean) -> Unit,
) {
    var windowKey by androidx.compose.runtime.saveable.rememberSaveable(selection.uri) {
        mutableStateOf(TaskWidgetFolderTimeWindow.MONTH.key)
    }
    var includeSubfolders by androidx.compose.runtime.saveable.rememberSaveable(selection.uri) {
        mutableStateOf(false)
    }
    val window = TaskWidgetFolderTimeWindow.fromKey(windowKey)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp),
        title = { Text(stringResource(R.string.qd_task_custom_folder_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.qd_task_folder_name, selection.displayName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.qd_task_folder_range_question), style = MaterialTheme.typography.titleSmall)
                TaskWidgetFolderTimeWindow.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { windowKey = option.key }
                            .semantics { role = Role.RadioButton },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = window == option, onClick = { windowKey = option.key })
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val includeSubfoldersDescription = stringResource(
                    if (includeSubfolders) R.string.qd_common_enabled else R.string.qd_common_disabled,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { includeSubfolders = !includeSubfolders }
                        .semantics {
                            role = Role.Switch
                            stateDescription = includeSubfoldersDescription
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.qd_task_read_subfolders), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = includeSubfolders,
                        onCheckedChange = { includeSubfolders = it },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.qd_task_folder_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(window, includeSubfolders) }) { Text(stringResource(R.string.qd_common_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.qd_common_cancel)) } },
    )
}
