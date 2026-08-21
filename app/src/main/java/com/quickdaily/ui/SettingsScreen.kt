package com.quickdaily.ui
import android.content.Intent
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.quickdaily.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quickdaily.AppState
import com.quickdaily.BetaLogger
import com.quickdaily.BuildConfig
import com.quickdaily.DiaryConfig
import com.quickdaily.EditorToolbarAction
import com.quickdaily.EditorToolbarPolicy
import com.quickdaily.WikilinkIndexRepository
import com.quickdaily.WikilinkIndexState
import com.quickdaily.HomeEntryMode
import com.quickdaily.ObsidianConfigReadResult
import com.quickdaily.ObsidianConfigReadStatus
import com.quickdaily.QuickNoteWidget
import com.quickdaily.WidgetImageFileResolver
import com.quickdaily.QuickDailyReadWidget
import com.quickdaily.TaskWidget
import com.quickdaily.WidgetIconCatalog
import com.quickdaily.TaskCompletionSoundMode
import com.quickdaily.TaskCompletionSoundPolicy
import com.quickdaily.TaskCompletionTimestampPolicy
import com.quickdaily.FloatingNoteAppearance
import com.quickdaily.SponsorEntry
import com.quickdaily.SponsorEntryRegistry
import com.quickdaily.SponsorReadState
import com.quickdaily.SettingsSliderDefaults
import com.quickdaily.WidgetAppearance
import com.quickdaily.ShortcutPinResultReceiver
import com.quickdaily.WidgetImageCropActivity
import com.quickdaily.WidgetRefreshCoordinator
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ShortcutHelper
import com.quickdaily.util.UriUtil
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.distinctUntilChanged
import com.quickdaily.ui.theme.LocalAppDimensions
import com.quickdaily.ui.theme.QuickDailyAccentPreset
import com.quickdaily.ui.theme.QuickDailyNightMode
import com.quickdaily.ui.theme.QuickDailyThemePreferences
import com.quickdaily.ui.theme.LocalQuickDailyMotion
import com.quickdaily.ui.theme.shouldShowDarkBackgroundBrightness


import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Checklist

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

internal enum class SettingsTab(val title: String) {
    QUICK_CAPTURE("速录设置"),
    WIDGETS("小部件"),
    APPEARANCE("外观设置"),
    OTHER("其他"),
}

private const val CHANGELOG_1_9_3_BETA = """QuickDaily 1.9.3-beta
• 新增 首次使用引导与悬浮窗控件教学
• 新增 PDF、Word、PowerPoint、Excel、OpenDocument 与 WPS 文档分享捕获
• 新增 任务完成提示音可选经典、木鱼、蜂鸣、系统或静音
• 新增 任务完成时间戳格式自定义
• 优化 设置页三级折叠结构、仓库配置与任务完成日期格式
• 优化 设置页下拉框、锚点文本、小部件外观与提示音试听"""

private const val DEFAULT_ANCHOR_TEXT = "## 今日速记"

private const val CHANGELOG_1_9_2_BETA = """QuickDaily 1.9.2-beta
• 新增 悬浮窗设置可选择保存后拉起 Obsidian；仅在 Obsidian 不在后台运行时拉起并自动回到桌面，默认关闭"""

private const val CHANGELOG_1_9_1_BETA = """QuickDaily 1.9.1-beta
• 新增 换行使用 4 空格继承缩进并续接无序列表、有序列表和任务，退格可整段回退
• 调整 便签小部件悬浮窗标题为“日期/页面名 + 速记”
• 新增 #标签仅在渲染模式和小部件中使用当前主题色文字"""

private const val CHANGELOG_1_9 = """1.9:
• 新增 悬浮窗透明度设置
• 新增 悬浮窗全屏/悬浮窗模式切换
• 新增 悬浮窗自定义页面录入
• 新增 悬浮窗沿用上次选择的目标位置
• 新增 悬浮窗标题拖动悬浮窗位置
• 新增 悬浮窗关闭后保留草稿内容开关
• 新增 首页自定义设置，可选择编辑页/悬浮窗录入/全屏录入
• 新增 工具栏顺序自定义
• 新增 工具栏按钮：删除线、分割线、Markdown 链接、行内代码、代码块、有序列表、双链、拍照、录音、剪切行、上移、下移、时间戳、日期戳、缩进、反缩进
• 新增 输入 "[[" 后根据页面内容自动补全
• 新增 双链补全悬浮窗页面别称支持
• 新增 时间戳格式："- YYYY-MM-DD hh:mm"
• 新增 时间格式对 "dd周" 的解析支持
• 新增 任务小部件自定义页面任务显示
• 新增 任务小部件子任务显示
• 新增 任务小部件显示任务完整内容开关
• 新增 任务小部件显示已完成任务开关
• 新增 便签小部件自定义页面
• 新增 夜间模式
• 新增 莫奈取色
• 新增 自定义深色模式背景亮度
• 新增 权限申请列表
• 调整 使用 MD3 风格，重绘 UI
• 调整 标签和双链补全悬浮窗样式
• 调整 关于页面信息排版
• 调整 悬浮窗首页 Logo，点击后进入编辑页
• 调整 悬浮窗下拉框
• 调整 悬浮窗文件名过长时省略部分文本
• 调整 编辑页面，支持子任务缩进渲染
• 调整 第二次点击日期戳或时间戳按钮时撤回插入内容
• 调整 输入法弹出速度优化
• 调整 双链补全内容，包含页面别称
• 调整 自定义图片，支持 PNG 格式
• 修复 悬浮窗拉起相机时概率覆盖相机的问题
• 修复 悬浮窗点击下方空白处无法拉起输入法的问题
• 修复 悬浮窗添加图片时概率失效的问题
• 修复 侧边栏启动器悬浮窗概率闪退的问题
• 修复 侧边栏启动器悬浮窗遮挡自定义文件选择器的问题
• 修复 子任务渲染失败的问题
• 修复 标题、列表、任务等格式混用时导致的文本错误
• 修复 澎湃系统无法选择自定义页面任务的问题
• 修复 文件名解析 "dd" 出错的问题
• 修复 便签小部件内容更新不及时的问题
• 修复 澎湃系统图速记添加附件时概率闪退的问题"""

private typealias ConfigChange = DiaryConfig.() -> DiaryConfig
private typealias OnConfigChange = (ConfigChange) -> Unit

internal data class SettingsConfigReadRequest(
    val generation: Long,
    val vaultPath: String,
    val customUri: String,
    val useCustomConfig: Boolean,
)

internal object SettingsConfigReadPolicy {
    fun canApply(
        request: SettingsConfigReadRequest,
        currentGeneration: Long,
        currentVaultPath: String,
        currentCustomUri: String,
        currentUseCustomConfig: Boolean,
    ): Boolean =
        request.generation == currentGeneration &&
            request.vaultPath == currentVaultPath.trim() &&
            request.customUri == currentCustomUri.trim().takeIf { currentUseCustomConfig }.orEmpty() &&
            request.useCustomConfig == currentUseCustomConfig
}

private data class SettingsConfigReadOutcome(
    val request: SettingsConfigReadRequest,
    val customResult: ObsidianConfigReadResult?,
    val obsidianConfig: DiaryConfig?,
    val appConfig: com.quickdaily.ObsidianAppConfig?,
)

private data class TimestampOption(val key: String, val label: String)

private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"

private fun vaultInitialDocumentUri(vaultPath: String): Uri? {
    val normalized = vaultPath.replace('\\', '/').trimEnd('/')
    val externalRoot = "/storage/emulated/0/"
    if (!normalized.startsWith(externalRoot, ignoreCase = true)) return null
    val relativePath = normalized.substring(externalRoot.length).trimStart('/')
    if (relativePath.isBlank()) return null
    return DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
        "primary:$relativePath"
    )
}

private fun templatePathRelativeToVault(vaultPath: String, selectedPath: String): String {
    val vault = vaultPath.replace('\\', '/').trimEnd('/')
    val selected = selectedPath.replace('\\', '/').trim()
    if (vault.isNotBlank()) {
        if (selected.equals(vault, ignoreCase = true)) return ""
        val vaultPrefix = "$vault/"
        if (selected.startsWith(vaultPrefix, ignoreCase = true)) {
            return selected.substring(vaultPrefix.length)
        }
    }
    return selected
}

private fun defaultObsidianConfigFilePath(vaultPath: String): String {
    val normalizedVaultPath = vaultPath.replace('\\', '/').trimEnd('/')
    return if (normalizedVaultPath.isBlank()) {
        "/.obsidian/daily-notes.json"
    } else {
        "$normalizedVaultPath/.obsidian/daily-notes.json"
    }
}

private fun documentDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}

private val timestampOptions = listOf(
    TimestampOption("none", "无时间戳"),
    TimestampOption("time_only", "仅时间"),
    TimestampOption("time_only_seconds", "时间（含秒）"),
    TimestampOption("list", "无序列表"),
    TimestampOption("ordered", "有序列表"),
    TimestampOption("list_time", "列表+时间"),
    TimestampOption("list_time_seconds", "列表+时间（秒）"),
    TimestampOption("date_time", "日期+时间"),
    TimestampOption("list_date_time", "列表+日期+时间"),
)

private data class NamingOption(val key: String, val label: String)
private val namingOptions = listOf(
    NamingOption("original", "图片原名"),
    NamingOption("timestamp_original", "时间戳+原名"),
    NamingOption("custom", "自定义名称"),
)

private val linkOptions = listOf(
    "described" to "Markdown：![image_name](路径)",
    "obsidian_wikilink" to "Obsidian：![[image_name]]",
)

private val timestampOrderOptions = listOf(
    "above" to "最上",
    "below" to "最下",
)

private val widgetStyleOptions = listOf(
    "light" to "白色",
    "dark" to "黑色",
    "custom" to "自定义",
    "system" to "跟随系统",
)

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val motionPolicy = LocalQuickDailyMotion.current
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { stateDescription = if (expanded) "已展开" else "已折叠" },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (motionPolicy.reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(animationSpec = motionPolicy.effectSpec()) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = motionPolicy.spatialSpec(),
                    )
            },
            exit = if (motionPolicy.reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = motionPolicy.effectSpec()) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = motionPolicy.spatialSpec(),
                    )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** Conditional settings content uses the same motion contract as collapsible sections. */
@Composable
private fun AnimatedSettingsVisibility(
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motionPolicy = LocalQuickDailyMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (motionPolicy.reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = motionPolicy.effectSpec()) +
                expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = motionPolicy.spatialSpec(),
                )
        },
        exit = if (motionPolicy.reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = motionPolicy.effectSpec()) +
                shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = motionPolicy.spatialSpec(),
                )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsDropdownMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ),
        text = {
            Text(
                label,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else null,
        // No leading slot: option text starts at the same inset as ordinary settings content.
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

@Composable
private fun ResettableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit,
    startLabel: String? = null,
    endLabel: String? = null,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stateDescription: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (stateDescription.isNullOrBlank()) {
                            Modifier
                        } else {
                            Modifier.semantics { this.stateDescription = stateDescription }
                        },
                    ),
            )
            if (startLabel != null || endLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        startLabel.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        endLabel.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "重置为默认值" },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Text(
                "重置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompactDropdownSetting(
    label: String,
    supportingText: String? = null,
    selectedKey: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selectedKey }?.second
        ?: options.firstOrNull()?.second.orEmpty()
    val resolvedSupportingText = supportingText?.takeIf { it.isNotBlank() }
    ListItem(
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label) },
        supportingContent = resolvedSupportingText?.let { text -> { Text(text) } },
        trailingContent = {
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(currentLabel)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (key, optionLabel) ->
                        SettingsDropdownMenuItem(
                            label = optionLabel,
                            selected = key == selectedKey,
                            onClick = {
                                onSelect(key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

/** Settings dividers always use the same content inset, including the first row in a card. */
private val SettingsContentInset = 16.dp

@Composable
private fun SettingsDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = SettingsContentInset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SliderSettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = SettingsContentInset),
    )
}

private fun settingSwitchDescription(
    enabled: Boolean,
    enabledText: String,
    disabledText: String,
): String {
    val description = if (enabled) enabledText else disabledText
    return if (description.endsWith("。")) description else "$description。"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    appState: AppState = viewModel(),
    onBack: () -> Unit,
    onExternalLaunch: () -> Unit = {},
    onRestartOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val navBarColorS = MaterialTheme.colorScheme.surface.toArgb()
    val windowSize = rememberQuickDailyWindowSize()
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    DisposableEffect(context, navBarColorS) {
        try {
            val window = (context as? Activity)?.window
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.navigationBarColor = navBarColorS
            }
        } catch (_: Exception) { }
        onDispose { }
    }

    // Keep the State objects stable at the pager boundary. Individual tabs read the
    // values they need, so changing a switch does not recompose the whole settings
    // shell, tab row, and every neighboring page.
    val configState = appState.config.collectAsStateWithLifecycle()
    val todayPathState = appState.todayPath.collectAsStateWithLifecycle()
    val initialConfig = remember { appState.config.value }

    // ── Local edit state ──
    var vaultPath by remember { mutableStateOf(initialConfig.vaultPath) }
    var obsidianConfigUri by remember { mutableStateOf(initialConfig.obsidianConfigUri) }
    var useCustomObsidianConfigPath by remember { mutableStateOf(initialConfig.useCustomObsidianConfigPath) }
    var diaryFolder by remember { mutableStateOf(initialConfig.diaryFolder) }
    var dateFormat by remember { mutableStateOf(initialConfig.dateFormat) }
    var templatePath by remember { mutableStateOf(initialConfig.templatePath) }
    var anchorText by remember { mutableStateOf(initialConfig.anchorText) }
    var imageStoragePath by remember { mutableStateOf(initialConfig.imageStoragePath) }

   var obsidianDetected by remember { mutableStateOf(false) }
   var obsidianMsg by remember { mutableStateOf("") }
   val scope = rememberCoroutineScope()
   var configReadGeneration by remember { mutableLongStateOf(0L) }
   var configReadJob by remember { mutableStateOf<Job?>(null) }
    var widgetImageUri by rememberSaveable { mutableStateOf(initialConfig.widgetImageUri) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun readObsidianConfig(request: SettingsConfigReadRequest): SettingsConfigReadOutcome {
        val selectedUri = request.customUri.takeIf { request.useCustomConfig && it.isNotBlank() }
        val customResult = selectedUri?.let { rawUri ->
            runCatching {
                appState.inspectObsidianConfig(Uri.parse(rawUri), request.vaultPath)
            }.getOrNull()
        }
        if (customResult?.status == ObsidianConfigReadStatus.INVALID_JSON) {
            return SettingsConfigReadOutcome(request, customResult, null, null)
        }
        val obsCfg = customResult?.config ?: appState.loadObsidianConfig(request.vaultPath)
        val appCfg = if (obsCfg != null) appState.loadObsidianAppConfig(request.vaultPath) else null
        return SettingsConfigReadOutcome(request, customResult, obsCfg, appCfg)
    }

    fun isCurrentConfigRead(request: SettingsConfigReadRequest): Boolean =
        SettingsConfigReadPolicy.canApply(
            request = request,
            currentGeneration = configReadGeneration,
            currentVaultPath = vaultPath,
            currentCustomUri = obsidianConfigUri,
            currentUseCustomConfig = useCustomObsidianConfigPath,
        )

    fun launchObsidianConfigRead(
        requestedVaultPath: String = vaultPath,
        requestedCustomUri: String = obsidianConfigUri,
        requestedUseCustomConfig: Boolean = useCustomObsidianConfigPath,
    ) {
        val request = SettingsConfigReadRequest(
            generation = configReadGeneration + 1L,
            vaultPath = requestedVaultPath.trim(),
            customUri = requestedCustomUri.trim().takeIf { requestedUseCustomConfig }.orEmpty(),
            useCustomConfig = requestedUseCustomConfig,
        )
        configReadGeneration = request.generation
        configReadJob?.cancel()
        configReadJob = scope.launch {
            val outcome = readObsidianConfig(request)
            if (!isCurrentConfigRead(request)) {
                BetaLogger.log(
                    "Settings/ObsidianConfig",
                    "discarded_stale_read generation=${request.generation} vault=${request.vaultPath}",
                )
                return@launch
            }
            val selectedUri = request.customUri.takeIf { request.useCustomConfig && it.isNotBlank() }
            val customResult = outcome.customResult
            if (customResult?.status == ObsidianConfigReadStatus.INVALID_JSON) {
                obsidianDetected = false
                obsidianMsg = "自定义配置文件 JSON 无效，已保留当前配置"
                return@launch
            }
            val obsCfg = outcome.obsidianConfig
            val appCfg = outcome.appConfig
            if (obsCfg != null) {
                diaryFolder = obsCfg.diaryFolder
                dateFormat = obsCfg.dateFormat
                templatePath = obsCfg.templatePath
                if (appCfg != null) {
                    imageStoragePath = appCfg.attachmentFolderPath.let {
                        if (it == "/") "" else it.trimStart('/')
                    }
                }
                obsidianDetected = true
                obsidianMsg = when {
                    customResult?.status == ObsidianConfigReadStatus.SUCCESS -> "已读取自定义 Obsidian 配置"
                    selectedUri != null -> "自定义配置文件不可用，已回退默认路径并读取"
                    else -> "已读取 Obsidian 配置"
                }
                if (!isCurrentConfigRead(request)) {
                    BetaLogger.log(
                        "Settings/ObsidianConfig",
                        "discarded_stale_read_before_save generation=${request.generation} vault=${request.vaultPath}",
                    )
                    return@launch
                }
                appState.saveConfig(appState.config.value.copy(
                    vaultPath = request.vaultPath,
                    obsidianConfigUri = request.customUri,
                    useCustomObsidianConfigPath = request.useCustomConfig,
                    diaryFolder = diaryFolder.trim().ifBlank { "Daily" },
                    dateFormat = dateFormat.trim().ifBlank { "YYYY-MM-DD" },
                    templatePath = templatePath.trim(),
                    imageStoragePath = imageStoragePath.trim(),
                    imageLinkFormat = if (appCfg?.useMarkdownLinks == true) "described" else appState.config.value.imageLinkFormat,
                ))
            } else {
                obsidianDetected = false
                obsidianMsg = if (selectedUri != null) {
                    "自定义配置文件不可用，默认路径也未找到"
                } else {
                    "未找到 .obsidian/daily-notes.json"
                }
            }
        }
    }

    // ── Update check state ──
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.quickdaily.util.ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var updateErrors by remember { mutableStateOf<List<com.quickdaily.util.SourceError>>(emptyList()) }
    var isLatest by remember { mutableStateOf(false) }

    // ── Tab state ──
    val tabs = remember { SettingsTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    var prefetchAdjacentPages by remember { mutableStateOf(false) }
    val settledPage by remember {
        derivedStateOf { pagerState.settledPage }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        yield()
        prefetchAdjacentPages = true
        BetaLogger.log("Settings/Pager", "adjacent_prefetch_enabled=true")
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val settledAt = SystemClock.elapsedRealtime()
                BetaLogger.log(
                    "Settings/Pager",
                    "settled_page=$page title=${tabs.getOrNull(page)?.title.orEmpty()} deferred_work=start",
                )
                withFrameNanos { }
                yield()
                BetaLogger.log(
                    "Settings/Pager",
                    "settled_page=$page deferred_work=frame_priority_done durationMs=${SystemClock.elapsedRealtime() - settledAt}",
                )
            }
    }

    // ── Picker launchers ──
    val vaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                vaultPath = path
                obsidianConfigUri = ""
                useCustomObsidianConfigPath = false
                launchObsidianConfigRead(
                    requestedVaultPath = path,
                    requestedCustomUri = "",
                    requestedUseCustomConfig = false,
                )
                WikilinkIndexRepository.refresh(context, path)
            }
        }
    }

    val imageStoragePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                imageStoragePath = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {
                    path.removePrefix(vaultPath).trimStart('/')
                } else {
                    path
                }
            }
        }
    }



    val diaryFolderPicker = rememberLauncherForActivityResult(

        ActivityResultContracts.OpenDocumentTree()

    ) { uri: Uri? ->

        uri?.let {

            val path = com.quickdaily.util.UriUtil.treeUriToPath(it)

            if (path != null) {

                diaryFolder = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {

                    path.removePrefix(vaultPath).trimStart('/')

                } else {

                    path

                }

            }

        }

    }

    val templatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val resultIntent = result.data ?: return@rememberLauncherForActivityResult
        val uri = resultIntent.data
            ?: resultIntent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }

        val path = UriUtil.documentUriToPath(uri)
        templatePath = if (path != null) {
            templatePathRelativeToVault(vaultPath, path)
        } else {
            documentDisplayName(context, uri) ?: uri.toString()
        }
    }

    val obsidianConfigPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }
        obsidianConfigUri = uri.toString()
        useCustomObsidianConfigPath = true
        appState.saveConfig(appState.config.value.copy(
            vaultPath = vaultPath.trim(),
            obsidianConfigUri = uri.toString(),
            useCustomObsidianConfigPath = true
        ))
        launchObsidianConfigRead(
            requestedVaultPath = vaultPath,
            requestedCustomUri = uri.toString(),
            requestedUseCustomConfig = true,
        )
    }

   val internalCropLauncher = rememberLauncherForActivityResult(
       ActivityResultContracts.StartActivityForResult()
   ) { result ->
       com.quickdaily.BetaLogger.log("WidgetCrop", "resultCode=${result.resultCode} resultPath=${result.data?.getStringExtra(WidgetImageCropActivity.EXTRA_RESULT_PATH)}")
       if (result.resultCode == Activity.RESULT_OK) {
           val savedPath = result.data?.getStringExtra(WidgetImageCropActivity.EXTRA_RESULT_PATH)
           if (savedPath != null && File(savedPath).isFile) {
           widgetImageUri = "file://$savedPath"
           appState.saveConfig(appState.config.value.copy(widgetImageUri = widgetImageUri))
           QuickNoteWidget.updateAllWidgets(context)
           ShortcutHelper.updateAllShortcuts(context)
           }
       }
   }

   val imagePicker = rememberLauncherForActivityResult(
       ActivityResultContracts.OpenDocument()
   ) { uri: Uri? ->
       uri?.let { srcUri ->
           val readable = runCatching {
               context.contentResolver.openInputStream(srcUri)?.use { stream ->
                   stream.read(ByteArray(1))
               } != null
           }.getOrElse { error ->
               com.quickdaily.BetaLogger.log(
                   "WidgetCrop",
                   "source uri unreadable uri=$srcUri exception=${error.javaClass.simpleName}",
               )
               false
           }
           if (!readable) {
               android.widget.Toast.makeText(
                   context,
                   "图片读取失败，请重新选择图片。",
                   android.widget.Toast.LENGTH_LONG,
               ).show()
               return@rememberLauncherForActivityResult
           }
           // OpenDocument grants a persistable read permission. Keep it before handing
           // the URI to the crop Activity because some document providers revoke the
           // transient picker grant as soon as the picker closes.
           try {
               context.contentResolver.takePersistableUriPermission(
                   srcUri,
                   Intent.FLAG_GRANT_READ_URI_PERMISSION,
               )
           } catch (error: Throwable) {
               BetaLogger.log(
                   "WidgetCrop",
                   "persist read permission failed uri=$srcUri exception=${error.javaClass.simpleName}",
               )
           }
           // The document picker has resumed MainActivity and cleared its external-launch
           // guard. Set it again before opening our crop Activity so onUserLeaveHint()
           // does not finish the entire task and send the user back to the launcher.
           onExternalLaunch()
           BetaLogger.log("WidgetCrop", "launch crop uri=$srcUri")
           try {
               internalCropLauncher.launch(
                   Intent(context, WidgetImageCropActivity::class.java).apply {
                       setDataAndType(
                           srcUri,
                           context.contentResolver.getType(srcUri) ?: "image/*",
                       )
                       addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                   }
               )
           } catch (error: Throwable) {
               BetaLogger.log(
                   "WidgetCrop",
                   "launch crop failed uri=$srcUri exception=${error.javaClass.simpleName}",
               )
               android.widget.Toast.makeText(
                   context,
                   "图片读取失败，请重新选择图片。",
                   android.widget.Toast.LENGTH_LONG,
               ).show()
           }
       }
   }
   
    fun buildConfig(): DiaryConfig {
        // A setting row persists through onConfigChange immediately. Read the
        // latest flow value here so the top-bar Save action cannot overwrite a
        // just-toggled value with a stale composition snapshot.
        val currentConfig = appState.config.value
        return DiaryConfig(
        vaultPath = vaultPath.trim(),
        obsidianConfigUri = obsidianConfigUri.trim(),
        useCustomObsidianConfigPath = useCustomObsidianConfigPath,
         diaryFolder = diaryFolder.trim().ifBlank { "Daily" },
        dateFormat = dateFormat.trim().ifBlank { "YYYY-MM-DD" },
        templatePath = templatePath.trim(),
        anchorText = anchorText,
        timestampFormat = currentConfig.timestampFormat,
        addAnchorIfMissing = currentConfig.addAnchorIfMissing,
        timestampOrder = currentConfig.timestampOrder,
        enterToSave = currentConfig.enterToSave,
        openObsidianAfterFloatingSave = currentConfig.openObsidianAfterFloatingSave,
        saveDraftOnFloatingClose = currentConfig.saveDraftOnFloatingClose,
        widgetImageUri = widgetImageUri,
        autoCheckUpdate = currentConfig.autoCheckUpdate,
        filterFrontmatter = currentConfig.filterFrontmatter,
        imageStoragePath = imageStoragePath.trim(),
        imageNamingFormat = currentConfig.imageNamingFormat,
          imageLinkFormat = currentConfig.imageLinkFormat,
          imageCustomNamingFormat = currentConfig.imageCustomNamingFormat,
          tagAutocomplete = true,
          wikilinkAutocomplete = true,
          systemSidebarSupport = currentConfig.systemSidebarSupport,
         homeEntryMode = currentConfig.homeEntryMode,
         toolbarOrder = currentConfig.toolbarOrder,
         toolbarVisible = currentConfig.toolbarVisible,
         loggingEnabled = currentConfig.loggingEnabled,
        taskPeriod = currentConfig.taskPeriod,
        taskCompletionSoundMode = currentConfig.taskCompletionSoundMode,
        taskCompletionTimestamp = currentConfig.taskCompletionTimestamp,
        taskCompletionTimestampFormat = currentConfig.taskCompletionTimestampFormat,
        taskShowCompleted = currentConfig.taskShowCompleted,
        taskShowFullContent = currentConfig.taskShowFullContent,
        taskGroupByDate = currentConfig.taskGroupByDate,
        widgetStyle = currentConfig.widgetStyle,
        widgetBackgroundColor = currentConfig.widgetBackgroundColor,
        widgetOpacity = currentConfig.widgetOpacity,
        floatingOpacity = currentConfig.floatingOpacity,
        )
    }

    fun saveFull() {
        appState.saveConfig(buildConfig())
    }

    fun savePathsAndRefreshIndex() {
        val previousVault = appState.config.value.vaultPath
        saveFull()
        val nextVault = vaultPath.trim()
        if (nextVault.isNotBlank() && nextVault != previousVault) {
            WikilinkIndexRepository.refresh(context, nextVault)
        }
    }

    fun saveAndBack() {
        saveFull()
        onBack()
    }

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::saveAndBack) {
                        Icon(Icons.Default.Check, "保存")
                    }
                },
                scrollBehavior = topBarScrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (windowSize.isLarge) Modifier.widthIn(max = 1200.dp) else Modifier),
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = if (prefetchAdjacentPages) 1 else 0,
            ) { page ->
                when (tabs[page]) {
                    SettingsTab.QUICK_CAPTURE -> StructuredEditorSettingsTab(
                        vaultPath = vaultPath,
                        configState = configState,
                        anchorText = anchorText,
                        onAnchorTextChange = { anchorText = it },
                        onConfigChange = { change -> appState.saveConfig(change(appState.config.value)) },
                        onRefreshWikilinkIndex = { WikilinkIndexRepository.refresh(context, vaultPath) },
                        onSave = ::saveAndBack,
                        isActive = settledPage == page,
                    )
                    SettingsTab.WIDGETS -> key(widgetImageUri) {
                        StructuredWidgetsTab(
                            configState = configState,
                            onConfigChange = { change ->
                                appState.saveConfig(change(appState.config.value))
                                QuickNoteWidget.updateAllWidgets(context)
                                WidgetRefreshCoordinator.refreshAll(context)
                            },
                            context = context,
                            onSave = { saveFull(); onBack() },
                        )
                    }
                    SettingsTab.APPEARANCE -> key(widgetImageUri) {
                        StructuredAppearanceTab(
                            context = context,
                            configState = configState,
                            widgetImageUri = widgetImageUri,
                            onConfigChange = { change ->
                                appState.saveConfig(change(appState.config.value))
                                QuickNoteWidget.updateAllWidgets(context)
                                WidgetRefreshCoordinator.refreshAll(context)
                                FloatingNoteAppearance.refresh(context)
                            },
                            onPickImage = {
                                onExternalLaunch()
                                imagePicker.launch(arrayOf("image/*"))
                            },
                            onResetImage = {
                                widgetImageUri = ""
                                appState.saveConfig(appState.config.value.copy(widgetImageUri = ""))
                                WidgetImageFileResolver.clearInternalCrops(context)
                                QuickNoteWidget.updateAllWidgets(context)
                                ShortcutHelper.updateAllShortcuts(context)
                            },
                            onSave = { saveFull(); onBack() },
                            isActive = settledPage == page,
                        )
                    }
                    SettingsTab.OTHER -> OtherTab(
                        vaultPath = vaultPath,
                        obsidianConfigUri = obsidianConfigUri,
                        useCustomObsidianConfigPath = useCustomObsidianConfigPath,
                        diaryFolder = diaryFolder,
                        dateFormat = dateFormat,
                        templatePath = templatePath,
                        imageStoragePath = imageStoragePath,
                        todayPathState = todayPathState,
                        obsidianDetected = obsidianDetected,
                        obsidianMsg = obsidianMsg,
                        onVaultPathChange = { vaultPath = it },
                        onDiaryFolderChange = { diaryFolder = it },
                        onDateFormatChange = { dateFormat = it },
                        onTemplatePathChange = { templatePath = it },
                        onImageStoragePathChange = { imageStoragePath = it },
                        onCustomObsidianConfigPathChange = { enabled ->
                            useCustomObsidianConfigPath = enabled
                            if (!enabled) obsidianConfigUri = ""
                        },
                        onPickVault = { onExternalLaunch(); vaultPicker.launch(null) },
                        onPickObsidianConfig = {
                            onExternalLaunch()
                            obsidianConfigPicker.launch(arrayOf("application/json", "text/plain"))
                        },
                        onClearObsidianConfig = {
                            obsidianConfigUri = ""
                            useCustomObsidianConfigPath = false
                        },
                        onPickTemplate = {
                            onExternalLaunch()
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                            vaultInitialDocumentUri(vaultPath)?.let {
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                            }
                            templatePicker.launch(intent)
                        },
                        onPickImageStorage = {
                            onExternalLaunch()
                            imageStoragePicker.launch(vaultInitialDocumentUri(vaultPath))
                        },
                        onPickDiaryFolder = {
                            onExternalLaunch()
                            diaryFolderPicker.launch(vaultInitialDocumentUri(vaultPath))
                        },
                        onReadObsidianConfig = { launchObsidianConfigRead() },
                        onSavePaths = ::savePathsAndRefreshIndex,
                        vaultEnabled = vaultPath.isNotBlank(),
                        onRestartOnboarding = onRestartOnboarding,
                        configState = configState,
                        isCheckingUpdate = isCheckingUpdate,
                        updateInfo = updateInfo,
                        updateStatus = updateStatus,
                        updateErrors = updateErrors,
                        isLatest = isLatest,
                        context = context,
                        onConfigChange = { change -> appState.saveConfig(change(appState.config.value)) },
                        onCheckUpdate = {
                            isCheckingUpdate = true
                            updateInfo = null
                            updateErrors = emptyList()
                            isLatest = false
                            updateStatus = "正在检查更新..."
                            scope.launch {
                                val result = com.quickdaily.util.UpdateChecker.checkUpdate(
                                    currentVersion = BuildConfig.VERSION_NAME, context = context
                                ) { progress -> updateStatus = progress }
                                when (result) {
                                    is com.quickdaily.util.UpdateResult.UpdateAvailable -> {
                                        updateInfo = result.info; updateStatus = ""
                                    }
                                    com.quickdaily.util.UpdateResult.UpToDate -> {
                                        isLatest = true; updateStatus = ""
                                    }
                                    is com.quickdaily.util.UpdateResult.Failed -> {
                                        updateErrors = result.errors; updateStatus = ""
                                    }
                                    else -> { updateStatus = "" }
                                }
                                isCheckingUpdate = false
                            }
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 1: Diary Storage
// ══════════════════════════════════════════════════════════

@Composable
private fun DiaryStorageTab(
    vaultPath: String,
    obsidianConfigUri: String,
    useCustomObsidianConfigPath: Boolean,
    diaryFolder: String,
    dateFormat: String,
    templatePath: String,
    imageStoragePath: String,
    todayPathState: State<String>,
    obsidianDetected: Boolean,
    obsidianMsg: String,
    onVaultPathChange: (String) -> Unit,
    onDiaryFolderChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onTemplatePathChange: (String) -> Unit,
    onImageStoragePathChange: (String) -> Unit,
    configState: State<DiaryConfig>,
    onConfigChange: OnConfigChange,
    onCustomObsidianConfigPathChange: (Boolean) -> Unit,
    onReadObsidianConfig: () -> Unit,
    onPickObsidianConfig: () -> Unit,
    onClearObsidianConfig: () -> Unit,
    onPickVault: () -> Unit,
    onPickTemplate: () -> Unit,
    onPickImageStorage: () -> Unit,

    onPickDiaryFolder: () -> Unit,
    onSave: () -> Unit,
    vaultEnabled: Boolean,
    embedded: Boolean = false,
) {
    val todayPath by todayPathState
    val config by configState
    val context = LocalContext.current
    Column(
        modifier = (if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            .padding(if (embedded) 0.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("仓库配置", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = vaultPath,
                    onValueChange = onVaultPathChange,
                    label = { Text("Obsidian 仓库路径") },
                    placeholder = { Text("/storage/emulated/0/Documents/Vault") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickVault) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )

                SettingsDivider()

                Button(onClick = onReadObsidianConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("从 Obsidian 读取配置")
                }

                SettingsDivider()

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCustomObsidianConfigPathChange(!useCustomObsidianConfigPath) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("是否启用自定义配置路径") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = useCustomObsidianConfigPath,
                                enabledText = "开启后使用自定义 Obsidian 配置文件路径。",
                                disabledText = "关闭后使用仓库默认路径 /.obsidian/daily-notes.json。",
                            ),
                        )
                    },
                    trailingContent = {
                        Checkbox(
                            checked = useCustomObsidianConfigPath,
                            onCheckedChange = onCustomObsidianConfigPathChange
                        )
                    }
                )

                AnimatedSettingsVisibility(visible = useCustomObsidianConfigPath) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onPickObsidianConfig, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (obsidianConfigUri.isBlank()) "选择配置文件" else "重新选择")
                        }
                        if (obsidianConfigUri.isNotBlank()) {
                            IconButton(onClick = onClearObsidianConfig) {
                                Icon(Icons.Default.Clear, "清除自定义配置文件")
                            }
                        }
                    }
                }

                SettingsDivider()
                Text(
                    text = "Obsidian 配置文件路径：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                )
                val defaultConfigPath = defaultObsidianConfigFilePath(vaultPath)
                val configPathText = if (!useCustomObsidianConfigPath) {
                    defaultConfigPath
                } else if (obsidianConfigUri.isBlank()) {
                    "未选择（使用仓库默认路径 $defaultConfigPath）"
                } else {
                    val uri = Uri.parse(obsidianConfigUri)
                    UriUtil.documentUriToPath(context, uri)
                        ?: documentDisplayName(context, uri)
                        ?: obsidianConfigUri
                }
                Text(
                    text = configPathText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                )
                if (obsidianMsg.isNotEmpty()) {
                    Text(obsidianMsg,
                        color = if (obsidianDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = SettingsContentInset),
                    )
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("日记文件配置", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = diaryFolder,
                    onValueChange = onDiaryFolderChange,
                    label = { Text("日记文件夹路径") },
                    placeholder = { Text("Daily") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onPickDiaryFolder) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )
                SettingsDivider()
                OutlinedTextField(
                    value = dateFormat,
                    onValueChange = onDateFormatChange,
                    label = { Text("日记文件名格式") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                SettingsDivider()
                OutlinedTextField(
                    value = templatePath,
                    onValueChange = onTemplatePathChange,
                    label = { Text("日记模板路径") },
                    placeholder = { Text("Templates/daily.md（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickTemplate) {
                            Icon(Icons.Default.FileOpen, "选择文件")
                        }
                    }
                )

                SettingsDivider()
                Text(
                    text = "\u4eca\u65e5\u65e5\u8bb0\u6587\u4ef6\u8def\u5f84\uff1a$todayPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                )
            }
        }


        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("附件配置", style = MaterialTheme.typography.titleSmall)
                DropdownSetting(
                    label = "图片命名格式",
                    selectedKey = config.imageNamingFormat,
                    options = namingOptions.map { it.key to it.label },
                    onSelect = { onConfigChange { copy(imageNamingFormat = it) } }
                )
                AnimatedSettingsVisibility(visible = config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange { copy(imageCustomNamingFormat = it) } },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onConfigChange { copy(imageCustomNamingFormat = "yyyy-MM-dd_HHmmss_{filename}{ext}") } }) {
                                Icon(Icons.Default.Refresh, "重置为默认")
                            }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "可用占位符（点击可复制）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val tokens = listOf(
                        "{filename}" to "原文件名（不含扩展名）",
                        "{ext}" to "扩展名（如 .jpg、.mp3）",
                        "yyyy" to "年份（4位）",
                        "MM" to "月份（2位）",
                        "dd" to "日（2位）",
                        "HH" to "小时（24小时制）",
                        "mm" to "分钟",
                        "ss" to "秒钟"
                    )
                    Column {
                        tokens.forEach { (token, desc) ->
                            Text(
                                text = "$token - $desc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(token, token))
                                            android.widget.Toast.makeText(context, "已复制 $token", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) { }
                                    }
                            )
                        }
                    }
                }
                SettingsDivider()
                DropdownSetting(
                    label = "图片链接格式",
                    selectedKey = config.imageLinkFormat,
                    options = linkOptions,
                    onSelect = { onConfigChange { copy(imageLinkFormat = it) } }
                )
                SettingsDivider()
                OutlinedTextField(
                    value = imageStoragePath,
                    onValueChange = onImageStoragePathChange,
                    label = { Text("附件储存目录") },
                    placeholder = { Text("assets/images（相对仓库路径）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickImageStorage) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )
                val exampleName = when (config.imageNamingFormat) {
                    "original" -> "image.jpg"
                    "timestamp_original" -> com.quickdaily.util.DateUtil.todayStr("YYYY-MM-DD") + "_" + (if (config.timestampFormat.contains("seconds")) com.quickdaily.util.DateUtil.nowTimeSecondsStr() else com.quickdaily.util.DateUtil.nowTimeStr()) + "_image.jpg"
                    "custom" -> { val f = config.imageCustomNamingFormat.ifEmpty { "image.jpg" }; f.replace("{filename}", "image").replace("{ext}", ".jpg") }
                    else -> "image.jpg"
                }
                SettingsDivider()
                Text(
                    text = "附件储存路径示例：$vaultPath/$imageStoragePath/$exampleName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                )
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 2: Editor Settings
// ══════════════════════════════════════════════════════════

@Composable
private fun AnchorTextDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = 520.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("编辑锚点文本", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("锚点文本") },
                    supportingText = { Text("锚点文本支持换行，可留空。") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onSave) { Text("保存") }
                    TextButton(onClick = onReset) { Text("重置") }
                }
            }
        }
    }
}

@Composable
private fun CompletionTimestampDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("编辑完成时间戳格式", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("完成时间戳格式") },
                    supportingText = {
                        Text("可使用 YYYY、YY、MM、M、DD、D、HH、H、mm、m、ss、s 等 Obsdian 通用日期格式。")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onSave) { Text("保存") }
                    TextButton(onClick = onReset) { Text("重置") }
                }
            }
        }
    }
}

@Composable
private fun CompletionTimestampSetting(
    enabled: Boolean,
    format: String,
    onEnabledChange: (Boolean) -> Unit,
    onFormatChange: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(format) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .clickable {
                    draft = format
                    dialogOpen = true
                }
                .semantics {
                    contentDescription = "编辑完成时间戳格式"
                },
        ) {
            Text("完成时间戳", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (enabled) {
                    "开启后完成任务时将追加写入「$format」，点击此处文本可编辑自定义时间戳的格式。"
                } else {
                    "关闭后完成任务时不会再追加写入时间戳。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }

    if (dialogOpen) {
        CompletionTimestampDialog(
            value = draft,
            onValueChange = { draft = it },
            onDismiss = { dialogOpen = false },
            onSave = {
                onFormatChange(TaskCompletionTimestampPolicy.normalizeFormat(draft))
                dialogOpen = false
            },
            onReset = { draft = TaskCompletionTimestampPolicy.DEFAULT_FORMAT },
        )
    }
}

@Composable
private fun TimestampPreviewSection(
    timestampFormat: String,
    addAnchorIfMissing: Boolean,
    anchorText: String,
    focusNonce: Int,
) {
    val requester = remember { BringIntoViewRequester() }
    val focusProgress = remember { Animatable(0f) }
    val motionPolicy = LocalQuickDailyMotion.current
    // The nonce survives tab switches through rememberSaveable. Initialize the
    // local guard from the restored value so re-entering this tab does not
    // replay an old focus animation; only a genuinely incremented nonce is a
    // new request.
    var lastHandledFocusNonce by remember { mutableIntStateOf(focusNonce) }
    LaunchedEffect(focusNonce) {
        if (focusNonce == lastHandledFocusNonce) return@LaunchedEffect
        lastHandledFocusNonce = focusNonce
        withFrameNanos { }
        requester.bringIntoView()
        if (motionPolicy.reducedMotion) {
            focusProgress.snapTo(0f)
        } else {
            focusProgress.snapTo(0f)
            focusProgress.animateTo(1f, animationSpec = tween(durationMillis = 360))
            focusProgress.animateTo(0f, animationSpec = tween(durationMillis = 1140))
        }
    }

    val previewText = remember(timestampFormat, addAnchorIfMissing, anchorText) {
        if (timestampFormat == "none") {
            ""
        } else {
            val now = DateUtil.nowTimeStr()
            val nowSec = DateUtil.nowTimeSecondsStr()
            buildString {
                if (addAnchorIfMissing && anchorText.isNotBlank()) {
                    appendLine(anchorText)
                }
                append(when (timestampFormat) {
                    "time_only" -> "$now 这是一段文本"
                    "time_only_seconds" -> "$nowSec 这是一段文本"
                    "list" -> "- 这是一段文本"
                    "ordered" -> "1. 这是一段文本"
                    "list_time" -> "- $now 这是一段文本"
                    "list_time_seconds" -> "- $nowSec 这是一段文本"
                    "date_time" -> "${DateUtil.nowDateTimeChineseStr()} 这是一段文本"
                    "list_date_time" -> "- ${DateUtil.nowDateTimeChineseStr()} 这是一段文本"
                    else -> "- 这是一段文本"
                })
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f * focusProgress.value),
                ),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = SettingsContentInset, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text("时间戳示例：", style = MaterialTheme.typography.labelSmall)
        if (timestampFormat == "none") {
            Text(
                "当前未启用时间戳。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StructuredEditorSettingsTab(
    vaultPath: String,
    configState: State<DiaryConfig>,
    anchorText: String,
    onAnchorTextChange: (String) -> Unit,
    onConfigChange: OnConfigChange,
    onRefreshWikilinkIndex: () -> Unit,
    onSave: () -> Unit,
    isActive: Boolean,
) {
    val config by configState
    val wikilinkIndex by if (isActive) {
        WikilinkIndexRepository.indexState.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(WikilinkIndexState()) }
    }
    var anchorDialogOpen by rememberSaveable { mutableStateOf(false) }
    var anchorDraft by rememberSaveable { mutableStateOf(anchorText) }
    var timestampPreviewFocusNonce by rememberSaveable { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollapsibleSettingsSection("编辑器设置") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CompactDropdownSetting(
                            label = "启动首页",
                            supportingText = "该选项只影响从桌面图标启动的行为。",
                            selectedKey = config.homeEntryMode,
                            options = HomeEntryMode.entries.map { it.key to it.label },
                            onSelect = { key -> onConfigChange { copy(homeEntryMode = key) } },
                        )
                        SettingsDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("过滤 Front matter") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.filterFrontmatter,
                                        enabledText = "开启后将隐藏页面中的 yaml 元数据。",
                                        disabledText = "关闭后将正常显示页面中的 yaml 元数据。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = config.filterFrontmatter,
                                    onCheckedChange = { enabled -> onConfigChange { copy(filterFrontmatter = enabled) } },
                                )
                            },
                        )
                        SettingsDivider()
                        EditorToolbarSettingsEntry(config = config, onConfigChange = onConfigChange)
                        SettingsDivider()
                        ListItem(
                            modifier = Modifier.clickable(
                                enabled = vaultPath.isNotBlank() && !wikilinkIndex.loading,
                                role = Role.Button,
                                onClickLabel = "刷新双链和标签补全索引",
                                onClick = onRefreshWikilinkIndex,
                            ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("双链标签的补全索引") },
                            supportingContent = {
                                Text(
                                    when {
                                        vaultPath.isBlank() -> "请先设置仓库路径"
                                        wikilinkIndex.rootPath == vaultPath && wikilinkIndex.loading -> "正在扫描 Markdown 页面和标签"
                                        wikilinkIndex.rootPath == vaultPath && wikilinkIndex.error != null -> wikilinkIndex.error!!
                                        wikilinkIndex.rootPath == vaultPath && wikilinkIndex.indexed && wikilinkIndex.tagsIndexed ->
                                            "已索引 ${wikilinkIndex.entries.size} 个页面，${wikilinkIndex.aliasCount} 个别称，${wikilinkIndex.tags.size} 个标签"
                                        wikilinkIndex.rootPath == vaultPath && wikilinkIndex.indexed ->
                                            "已索引 ${wikilinkIndex.entries.size} 个页面，${wikilinkIndex.aliasCount} 个别称；标签索引请手动刷新"
                                        else -> "尚未刷新当前仓库的双链和标签索引"
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = onRefreshWikilinkIndex,
                                    enabled = vaultPath.isNotBlank() && !wikilinkIndex.loading,
                                ) { Icon(Icons.Default.Refresh, contentDescription = "刷新双链和标签补全索引") }
                            },
                        )
                    }
                }
            }

            CollapsibleSettingsSection("悬浮窗设置") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("回车触发保存") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.enterToSave,
                                        enabledText = "开启后可在悬浮窗中按回车键直接触发保存，动作更快捷，但回车换行功能失效。",
                                        disabledText = "关闭后在悬浮窗中可以使用回车键正常换行。",
                                    ),
                                )
                            },
                            trailingContent = { Switch(config.enterToSave, { onConfigChange { copy(enterToSave = it) } }) },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("保存时拉起Obsidian") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.openObsidianAfterFloatingSave,
                                        enabledText = "开启后保存成功时将自动后台拉起 Obsidian，适合使用 ob 本体进行同步的用户。",
                                        disabledText = "关闭后保存成功时不再自动拉起 Obsidian，后台更纯净。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(config.openObsidianAfterFloatingSave, { onConfigChange { copy(openObsidianAfterFloatingSave = it) } })
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("退出时保存草稿") },
                            supportingContent = {
                                Text(
                                    if (config.saveDraftOnFloatingClose) {
                                        "开启后当悬浮窗退出时将默认保存到 Obsidian。"
                                    } else {
                                        "关闭后草稿内容将保存在悬浮窗当中，直至手动保存。"
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(config.saveDraftOnFloatingClose, { onConfigChange { copy(saveDraftOnFloatingClose = it) } })
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("系统侧边启动器支持") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.systemSidebarSupport,
                                        enabledText = "开启后可使用系统侧边栏启动器拉起速录悬浮窗，但会增加些许启动和关闭时间。",
                                        disabledText = "关闭后系统侧边栏启动器同步首页启动动作，启动速度更快。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(config.systemSidebarSupport, { onConfigChange { copy(systemSidebarSupport = it) } })
                            },
                        )
                    }
                }
            }

            CollapsibleSettingsSection("时间戳设置") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompactDropdownSetting(
                            label = "时间戳格式",
                            supportingText = "选择时间戳的显示方式。",
                            selectedKey = config.timestampFormat,
                            options = timestampOptions.map { it.key to it.label },
                            onSelect = { key ->
                                onConfigChange { copy(timestampFormat = key) }
                                timestampPreviewFocusNonce++
                            },
                        )
                        SettingsDivider()
                        CompactDropdownSetting(
                            label = "时间戳插入顺序",
                            supportingText = "可以选择最新加入的时间戳，插入到文本的最上方或者最下方。",
                            selectedKey = config.timestampOrder,
                            options = timestampOrderOptions,
                            onSelect = { key ->
                                onConfigChange { copy(timestampOrder = key) }
                                timestampPreviewFocusNonce++
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            modifier = Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = "编辑锚点文本",
                                onClick = {
                                    anchorDraft = anchorText
                                    anchorDialogOpen = true
                                },
                            ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("锚点文本") },
                            supportingContent = { Text("当前锚点文本为：$anchorText") },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        anchorDraft = anchorText
                                        anchorDialogOpen = true
                                    },
                                ) {
                                    Icon(Icons.Default.EditNote, contentDescription = "编辑锚点文本")
                                }
                            },
                        )
                        if (anchorDialogOpen) {
                            AnchorTextDialog(
                                value = anchorDraft,
                                onValueChange = { anchorDraft = it },
                                onDismiss = { anchorDialogOpen = false },
                                onSave = {
                                    onAnchorTextChange(anchorDraft)
                                    anchorDialogOpen = false
                                    timestampPreviewFocusNonce++
                                },
                                onReset = { anchorDraft = DEFAULT_ANCHOR_TEXT },
                            )
                        }
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("无锚点时自动添加") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.addAnchorIfMissing,
                                        enabledText = "开启后保存时会在未找到锚点文本时自动添加锚点文本。",
                                        disabledText = "关闭后保存时不会自动添加缺失的锚点文本。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = config.addAnchorIfMissing,
                                    onCheckedChange = {
                                        onConfigChange { copy(addAnchorIfMissing = it) }
                                        timestampPreviewFocusNonce++
                                    },
                                )
                            },
                        )
                        SettingsDivider()
                        TimestampPreviewSection(
                            timestampFormat = config.timestampFormat,
                            addAnchorIfMissing = config.addAnchorIfMissing,
                            anchorText = anchorText,
                            focusNonce = timestampPreviewFocusNonce,
                        )
                    }
                }
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSettingsTab(
    vaultPath: String,
    configState: State<DiaryConfig>,
    anchorText: String,
    onAnchorTextChange: (String) -> Unit,
    onConfigChange: OnConfigChange,
    onRefreshWikilinkIndex: () -> Unit,
    onSave: () -> Unit,
    isActive: Boolean,
) {
    val config by configState
    val context = LocalContext.current
    var anchorDialogOpen by rememberSaveable { mutableStateOf(false) }
    var anchorDraft by rememberSaveable { mutableStateOf(anchorText) }
    var timestampPreviewFocusNonce by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(isActive, vaultPath) {
        if (isActive) {
            val startedAt = SystemClock.elapsedRealtime()
            BetaLogger.log("Settings/PageWork", "page=editor work=index_subscribe start")
            withFrameNanos { }
            yield()
            BetaLogger.log(
                "Settings/PageWork",
                "page=editor work=index_subscribe end durationMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
    }
    val wikilinkIndex by if (isActive) {
        WikilinkIndexRepository.indexState.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(WikilinkIndexState()) }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("时间戳设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSetting(
                    label = "时间戳格式",
                    supportingText = "选择时间戳的显示方式。",
                    selectedKey = config.timestampFormat,
                    options = timestampOptions.map { it.key to it.label },
                    onSelect = {
                        onConfigChange { copy(timestampFormat = it) }
                        timestampPreviewFocusNonce++
                    }
                )
                SettingsDivider()

                Text("时间戳插入顺序", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "可以选择最新加入的时间戳，插入到文本的最上方或者最下方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = config.timestampOrder == "above",
                        onClick = {
                            onConfigChange { copy(timestampOrder = "above") }
                            timestampPreviewFocusNonce++
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("最上") }
                    SegmentedButton(
                        selected = config.timestampOrder == "below",
                        onClick = {
                            onConfigChange { copy(timestampOrder = "below") }
                            timestampPreviewFocusNonce++
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("最下") }
                }
                SettingsDivider()

                ListItem(
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "编辑锚点文本",
                        onClick = {
                            anchorDraft = anchorText
                            anchorDialogOpen = true
                        },
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("锚点文本") },
                    supportingContent = { Text("当前锚点文本为：$anchorText") },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                anchorDraft = anchorText
                                anchorDialogOpen = true
                            },
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = "编辑锚点文本")
                        }
                    },
                )
                if (anchorDialogOpen) {
                    AnchorTextDialog(
                        value = anchorDraft,
                        onValueChange = { anchorDraft = it },
                        onDismiss = { anchorDialogOpen = false },
                        onSave = {
                            onAnchorTextChange(anchorDraft)
                            anchorDialogOpen = false
                            timestampPreviewFocusNonce++
                        },
                        onReset = { anchorDraft = DEFAULT_ANCHOR_TEXT },
                    )
                }
                SettingsDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("无锚点时自动添加", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (config.addAnchorIfMissing) {
                                "开启后保存时会在未找到锚点文本时自动添加锚点文本。"
                            } else {
                                "关闭后保存时不会自动添加缺失的锚点文本。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = config.addAnchorIfMissing, onCheckedChange = {
                        onConfigChange { copy(addAnchorIfMissing = it) }
                        timestampPreviewFocusNonce++
                    })
                }

                SettingsDivider()
                TimestampPreviewSection(
                    timestampFormat = config.timestampFormat,
                    addAnchorIfMissing = config.addAnchorIfMissing,
                    anchorText = anchorText,
                    focusNonce = timestampPreviewFocusNonce,
                )
            }
        }

                Text("编辑器设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("启动首页", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "该选项只影响从桌面图标启动的行为。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    HomeEntryMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = HomeEntryMode.fromKey(config.homeEntryMode) == mode,
                            onClick = { onConfigChange { copy(homeEntryMode = mode.key) } },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = HomeEntryMode.entries.size,
                            ),
                        ) { Text(mode.label) }
                    }
                }
                SettingsDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("过滤 Front matter") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.filterFrontmatter,
                                enabledText = "开启后将隐藏页面中的 yaml 元数据。",
                                disabledText = "关闭后将正常显示页面中的 yaml 元数据。",
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.filterFrontmatter, onCheckedChange = {
                            onConfigChange { copy(filterFrontmatter = it) }
                        })
                    }
                )
                SettingsDivider()
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("标签自动补全") },
                    supportingContent = {
                        Text(
                            if (config.tagAutocomplete) {
                                "开启后输入#时补全索引中的标签，索引需要在下方手动刷新。"
                            } else {
                                "关闭后输入#时不会补全索引中的标签。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.tagAutocomplete, onCheckedChange = {
                            onConfigChange { copy(tagAutocomplete = it) }
                        })
                    }
                )
                SettingsDivider()
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("双链自动补全") },
                    supportingContent = {
                        Text(
                            if (config.wikilinkAutocomplete) {
                                "开启后输入[[时补全索引中的 Markdown 页面。"
                            } else {
                                "关闭后输入[[时不会补全索引中的 Markdown 页面。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.wikilinkAutocomplete, onCheckedChange = {
                            onConfigChange { copy(wikilinkAutocomplete = it) }
                        })
                    }
                )

                SettingsDivider()
                EditorToolbarSettingsEntry(
                    config = config,
                    onConfigChange = onConfigChange,
                )
                SettingsDivider()
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = vaultPath.isNotBlank() && !wikilinkIndex.loading,
                        role = Role.Button,
                        onClickLabel = "刷新双链和标签补全索引",
                        onClick = onRefreshWikilinkIndex,
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                     headlineContent = { Text("双链标签的补全索引") },
                     supportingContent = {
                         Text(
                             when {
                                 vaultPath.isBlank() -> "请先设置仓库路径"
                                 wikilinkIndex.rootPath == vaultPath && wikilinkIndex.loading -> "正在扫描 Markdown 页面和标签"
                                 wikilinkIndex.rootPath == vaultPath && wikilinkIndex.error != null -> wikilinkIndex.error!!
                                 wikilinkIndex.rootPath == vaultPath && wikilinkIndex.indexed && wikilinkIndex.tagsIndexed -> "已索引 ${wikilinkIndex.entries.size} 个页面，${wikilinkIndex.aliasCount} 个别称，${wikilinkIndex.tags.size} 个标签"
                                 wikilinkIndex.rootPath == vaultPath && wikilinkIndex.indexed -> "已索引 ${wikilinkIndex.entries.size} 个页面，${wikilinkIndex.aliasCount} 个别称；标签索引请手动刷新"
                                 else -> "尚未刷新当前仓库的双链和标签索引"
                             }
                         )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = onRefreshWikilinkIndex,
                            enabled = vaultPath.isNotBlank() && !wikilinkIndex.loading,
                        ) {
                             Icon(Icons.Default.Refresh, contentDescription = "刷新双链和标签补全索引")
                        }
                    },
                )
            }
        }

        Text("悬浮窗设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("回车触发保存") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.enterToSave,
                                enabledText = "开启后可在悬浮窗中按回车键直接触发保存，动作更快捷，但回车换行功能失效。",
                                disabledText = "关闭后在悬浮窗中可以使用回车键正常换行。",
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.enterToSave, onCheckedChange = {
                            onConfigChange { copy(enterToSave = it) }
                        })
                    }
                )
                SettingsDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("保存时拉起Obsidian") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.openObsidianAfterFloatingSave,
                                enabledText = "开启后保存成功时将自动后台拉起 Obsidian，适合使用 ob 本体进行同步的用户。",
                                disabledText = "关闭后保存成功时不再自动拉起 Obsidian，后台更纯净。",
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.openObsidianAfterFloatingSave,
                            onCheckedChange = {
                                onConfigChange { copy(openObsidianAfterFloatingSave = it) }
                            },
                            modifier = Modifier.semantics {
                                stateDescription = if (config.openObsidianAfterFloatingSave) "已开启" else "已关闭"
                            },
                        )
                    },
                )
                SettingsDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("退出时保存草稿") },
                    supportingContent = {
                        Text(
                            if (config.saveDraftOnFloatingClose) {
                                "开启后当悬浮窗退出时将默认保存到 Obsidian。"
                            } else {
                                "关闭后草稿内容将保存在悬浮窗当中，直至手动保存。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.saveDraftOnFloatingClose,
                            onCheckedChange = { onConfigChange { copy(saveDraftOnFloatingClose = it) } },
                        )
                    },
                )
                SettingsDivider()
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("系统侧边启动器支持") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.systemSidebarSupport,
                                enabledText = "开启后可使用系统侧边栏启动器拉起速录悬浮窗，但会增加些许启动和关闭时间。",
                                disabledText = "关闭后系统侧边栏启动器同步首页启动动作，启动速度更快。",
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.systemSidebarSupport,
                            onCheckedChange = {
                                onConfigChange { copy(systemSidebarSupport = it) }
                            }
                        )
                    }
                )
                SettingsDivider()
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
    }
}

@Composable
private fun EditorToolbarSettingsEntry(
    config: DiaryConfig,
    onConfigChange: OnConfigChange,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(
            role = Role.Button,
            onClickLabel = "打开工具栏编辑",
            onClick = { dialogOpen = true },
        ),
        headlineContent = { Text("工具栏编辑") },
        supportingContent = {
            Text("当前已显示 ${config.toolbarVisible.size} 项工具，点击调整工具项目的启用和顺序。")
        },
        trailingContent = {
            Icon(Icons.Default.Tune, contentDescription = "打开工具栏编辑")
        },
    )

    if (dialogOpen) {
        EditorToolbarSettingsDialog(
            config = config,
            onConfigChange = onConfigChange,
            onDismiss = { dialogOpen = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorToolbarSettingsDialog(
    config: DiaryConfig,
    onConfigChange: OnConfigChange,
    onDismiss: () -> Unit,
) {
    var order by remember(config.toolbarOrder) {
        mutableStateOf(EditorToolbarPolicy.normalizeOrder(config.toolbarOrder).mapNotNull(EditorToolbarAction::fromId))
    }
    var visible by remember(config.toolbarVisible) {
        mutableStateOf(EditorToolbarPolicy.normalizeVisible(config.toolbarVisible))
    }
    val scrollState = rememberScrollState()
    val itemTops = remember { mutableStateMapOf<String, Int>() }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    var draggedStartTop by remember { mutableIntStateOf(0) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    val latestOrder by rememberUpdatedState(order)

    val latestVisible by rememberUpdatedState(visible)
    val visiblePositions = remember(order, visible) {
        EditorToolbarPolicy.visiblePositions(order, visible)
    }

    fun saveToolbarConfig(
        nextOrder: List<EditorToolbarAction> = order,
        nextVisible: Set<String> = visible,
    ) {
        onConfigChange {
            copy(
                toolbarOrder = nextOrder.map { it.id },
                toolbarVisible = nextVisible,
            )
        }
    }

    fun commitVisibility(nextVisible: Set<String>) {
        visible = nextVisible
        saveToolbarConfig(nextOrder = order, nextVisible = nextVisible)
    }

    fun dropOrder(): List<EditorToolbarAction> {
        val currentDraggedId = draggedId ?: return latestOrder
        val draggedCenter = draggedStartTop + draggedOffset + draggedHeight / 2f
        val candidates = latestOrder
            .filter { it.id != currentDraggedId }
            .mapNotNull { action ->
                val top = itemTops[action.id] ?: return@mapNotNull null
                val height = itemHeights[action.id] ?: return@mapNotNull null
                action to (top + height / 2f)
            }
        val targetInfo = if (draggedOffset >= 0f) {
            candidates.lastOrNull { draggedCenter > it.second }
        } else {
            candidates.firstOrNull { draggedCenter < it.second }
        } ?: return latestOrder
        return EditorToolbarPolicy.move(
            latestOrder,
            draggedId = currentDraggedId,
            targetId = targetInfo.first.id,
        )
    }

    fun finishDrag(commitOrder: Boolean) {
        val finalOrder = if (commitOrder) dropOrder() else latestOrder
        if (commitOrder) {
            order = finalOrder
        }
        val finalVisible = latestVisible
        draggedId = null
        draggedOffset = 0f
        saveToolbarConfig(nextOrder = finalOrder, nextVisible = finalVisible)
    }

    fun resetToolbarConfig() {
        val defaultOrder = EditorToolbarPolicy.defaultOrder
        val defaultVisible = EditorToolbarPolicy.defaultVisible
        draggedId = null
        draggedOffset = 0f
        order = defaultOrder
        visible = defaultVisible
        saveToolbarConfig(nextOrder = defaultOrder, nextVisible = defaultVisible)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("工具栏编辑", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    "长按左侧手柄拖动调整顺序，开关控制显示隐藏。多出的无法显示的工具，可以通过左滑工具栏来到第二页。修改会立即保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    order.forEach { action ->
                        key(action.id) {
                            val isDragging = draggedId == action.id
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        itemTops[action.id] = coordinates.positionInParent().y.roundToInt()
                                        itemHeights[action.id] = coordinates.size.height
                                    }
                                    // Avoid placement animation during reordering; combining it with
                                    // the dragged offset can leave a stale item layer behind.
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .offset { IntOffset(0, if (isDragging) draggedOffset.roundToInt() else 0) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .pointerInput(action.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedId = action.id
                                                        draggedOffset = 0f
                                                        draggedStartTop = itemTops[action.id] ?: 0
                                                        draggedHeight = itemHeights[action.id] ?: 0
                                                    },
                                                    onDragCancel = {
                                                        finishDrag(commitOrder = false)
                                                    },
                                                    onDragEnd = {
                                                        finishDrag(commitOrder = true)
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        if (draggedId != action.id) return@detectDragGesturesAfterLongPress
                                                        draggedOffset += amount.y
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "长按拖动排序",
                                        )
                                    }
                                },
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        EditorToolbarActionIcon(
                                            action = action,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(action.label)
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        when (val position = visiblePositions[action.id]) {
                                            null -> "已被隐藏"
                                            1 -> "当前顺序第 1 项"
                                            else -> "第 $position 项"
                                        }
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = action.id in visible,
                                        onCheckedChange = { checked ->
                                            val nextVisible = if (checked) visible + action.id else visible - action.id
                                            commitVisibility(nextVisible)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { resetToolbarConfig() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置")
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 3: Widgets & Shortcuts
// ══════════════════════════════════════════════════════════

private fun requestPinWidget(
    context: android.content.Context,
    provider: Class<*>,
    requestCode: Int,
) {
    try {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, provider)
        if (manager.isRequestPinAppWidgetSupported) {
            val callback = android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, ShortcutPinResultReceiver::class.java)
                    .setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            manager.requestPinAppWidget(component, null, callback)
        } else {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }
}

@Composable
private fun WidgetPinEntry(
    title: String,
    icon: Painter,
    provider: Class<*>,
    requestCode: Int,
    context: android.content.Context,
    refreshKey: Int,
) {
    val manager = remember(context) { android.appwidget.AppWidgetManager.getInstance(context) }
    val component = remember(provider) { android.content.ComponentName(context, provider) }
    val added = remember(component, refreshKey) { manager.getAppWidgetIds(component).isNotEmpty() }
    val status = when {
        added -> "已添加"
        !manager.isRequestPinAppWidgetSupported -> "当前桌面不支持一键添加，请长按桌面手动添加"
        else -> "未添加"
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painter = icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        trailingContent = {
            OutlinedButton(
                onClick = { requestPinWidget(context, provider, requestCode) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("添加到桌面") }
        },
    )
}

@Composable
private fun StructuredWidgetsTab(
    configState: State<DiaryConfig>,
    onConfigChange: OnConfigChange,
    context: android.content.Context,
    onSave: () -> Unit,
) {
    val config by configState
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var widgetStatusRefreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) widgetStatusRefreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollapsibleSettingsSection("任务小部件") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CompactDropdownSetting(
                            label = "完成提示音",
                            supportingText = "完成任务时播放提示音。",
                            selectedKey = config.taskCompletionSoundMode,
                            options = TaskCompletionSoundMode.entries.map { it.key to it.label },
                            onSelect = { key ->
                                val mode = TaskCompletionSoundMode.fromKey(key)
                                onConfigChange { copy(taskCompletionSoundMode = mode.key) }
                                TaskCompletionSoundPolicy.preview(context, mode)
                            },
                        )
                        SettingsDivider(modifier = Modifier.padding(vertical = 8.dp))
                        CompletionTimestampSetting(
                            enabled = config.taskCompletionTimestamp,
                            format = config.taskCompletionTimestampFormat,
                            onEnabledChange = { enabled ->
                                onConfigChange { copy(taskCompletionTimestamp = enabled) }
                            },
                            onFormatChange = { format ->
                                onConfigChange { copy(taskCompletionTimestampFormat = format) }
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("按日期分类") },
                            supportingContent = {
                                Text(
                                    if (config.taskGroupByDate) {
                                        "开启后本周和本月任务会按日期分组；只有今日任务时不显示日期。"
                                    } else {
                                        "关闭后本周和本月任务将按当前顺序连续显示。"
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = config.taskGroupByDate,
                                    onCheckedChange = { onConfigChange { copy(taskGroupByDate = it) } },
                                )
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("显示已完成任务") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.taskShowCompleted,
                                        enabledText = "开启后已完成的任务将继续保留在任务小部件中。",
                                        disabledText = "关闭后已完成的任务将在任务小部件中暂时隐藏，不影响便签小部件。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(config.taskShowCompleted, { onConfigChange { copy(taskShowCompleted = it) } })
                            },
                        )
                        SettingsDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("显示任务所有内容") },
                            supportingContent = {
                                Text(
                                    settingSwitchDescription(
                                        enabled = config.taskShowFullContent,
                                        enabledText = "开启后将显示任务的完整文本，即使字数超出了数量限制。",
                                        disabledText = "关闭后任务内容至多显示两行，保障列表清晰整洁。",
                                    ),
                                )
                            },
                            trailingContent = {
                                Switch(config.taskShowFullContent, { onConfigChange { copy(taskShowFullContent = it) } })
                            },
                        )
                    }
                }
            }

            CollapsibleSettingsSection("小部件快速添加") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("小部件", style = MaterialTheme.typography.titleSmall)
                        WidgetPinEntry("便签小部件", painterResource(WidgetIconCatalog.note), QuickDailyReadWidget::class.java, 1, context, widgetStatusRefreshKey)
                        SettingsDivider()
                        WidgetPinEntry("任务小部件", painterResource(WidgetIconCatalog.task), TaskWidget::class.java, 3, context, widgetStatusRefreshKey)
                        SettingsDivider()
                        WidgetPinEntry("快速录入小部件", painterResource(WidgetIconCatalog.quickEntry), QuickNoteWidget::class.java, 2, context, widgetStatusRefreshKey)
                    }
                }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("快捷方式", style = MaterialTheme.typography.titleSmall)
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(WidgetIconCatalog.quickEntry),
                                    contentDescription = null,
                                )
                            },
                            headlineContent = { Text("快速录入图标") },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = { ShortcutHelper.pinShortcutToDesktop(context) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text("添加到桌面") }
                            },
                        )
                    }
                }
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WidgetsTab(
    widgetImageUri: String,
    configState: State<DiaryConfig>,
    onConfigChange: OnConfigChange,
    context: android.content.Context,
    onPickImage: () -> Unit,
    onResetImage: () -> Unit,
    onSave: () -> Unit,
    isActive: Boolean,
    appearanceOnly: Boolean = false,
    embedded: Boolean = false,
    showHeading: Boolean = true,
) {
    val config by configState
    var previewBitmap by remember(widgetImageUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(widgetImageUri, isActive) {
        previewBitmap = null
        if (isActive && widgetImageUri.isNotEmpty()) {
            val startedAt = SystemClock.elapsedRealtime()
            BetaLogger.log("Settings/PageWork", "page=widgets work=image_decode start")
            previewBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val path = widgetImageUri.removePrefix("file://")
                    BitmapFactory.decodeFile(path)
                }.getOrNull()
            }
            BetaLogger.log(
                "Settings/PageWork",
                "page=widgets work=image_decode end durationMs=${SystemClock.elapsedRealtime() - startedAt} loaded=${previewBitmap != null}",
            )
        }
    }
    var appearanceStyle by remember(config.widgetStyle) { mutableStateOf(config.widgetStyle) }
    var appearanceColor by remember(config.widgetBackgroundColor) { mutableLongStateOf(config.widgetBackgroundColor) }
    var appearanceOpacity by remember(config.widgetOpacity) { mutableIntStateOf(config.widgetOpacity) }
    fun commitAppearance() {
        onConfigChange {
            copy(
                widgetStyle = appearanceStyle,
                widgetBackgroundColor = appearanceColor,
                widgetOpacity = appearanceOpacity,
            )
        }
    }
    Column(
        modifier = (if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            .padding(if (embedded) 0.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeading) {
            Text(
                if (appearanceOnly) "桌面小部件外观" else "小部件快速添加",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (appearanceOnly) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("小部件背景色", style = MaterialTheme.typography.titleSmall)
                CompactDropdownSetting(
                    label = "小部件背景色",
                    supportingText = "应用于所有桌面小部件。",
                    selectedKey = appearanceStyle,
                    options = widgetStyleOptions,
                    onSelect = { style ->
                        appearanceStyle = style
                        when (style) {
                            "light" -> appearanceColor = 0xFFFFFFFFL
                            "dark" -> appearanceColor = 0xFF202124L
                        }
                        commitAppearance()
                    },
                )
                AnimatedSettingsVisibility(visible = appearanceStyle == "custom") {
                    SettingsDivider()
                    val red = ((appearanceColor shr 16) and 0xFF).toInt()
                    SliderSettingLabel("红色 $red")
                    ResettableSlider(
                        modifier = Modifier.padding(horizontal = SettingsContentInset),
                        value = red / 255f,
                        onValueChange = { value ->
                            appearanceColor = SettingsSliderDefaults.withRed(
                                appearanceColor,
                                (value * 255).roundToInt(),
                            )
                        },
                        onValueChangeFinished = ::commitAppearance,
                        onReset = {
                            appearanceColor = SettingsSliderDefaults.resetRed(appearanceColor)
                            commitAppearance()
                        },
                        stateDescription = "红色 $red",
                    )
                    val green = ((appearanceColor shr 8) and 0xFF).toInt()
                    SliderSettingLabel("绿色 $green")
                    ResettableSlider(
                        modifier = Modifier.padding(horizontal = SettingsContentInset),
                        value = green / 255f,
                        onValueChange = { value ->
                            appearanceColor = SettingsSliderDefaults.withGreen(
                                appearanceColor,
                                (value * 255).roundToInt(),
                            )
                        },
                        onValueChangeFinished = ::commitAppearance,
                        onReset = {
                            appearanceColor = SettingsSliderDefaults.resetGreen(appearanceColor)
                            commitAppearance()
                        },
                        stateDescription = "绿色 $green",
                    )
                    val blue = (appearanceColor and 0xFF).toInt()
                    SliderSettingLabel("蓝色 $blue")
                    ResettableSlider(
                        modifier = Modifier.padding(horizontal = SettingsContentInset),
                        value = blue / 255f,
                        onValueChange = { value ->
                            appearanceColor = SettingsSliderDefaults.withBlue(
                                appearanceColor,
                                (value * 255).roundToInt(),
                            )
                        },
                        onValueChangeFinished = ::commitAppearance,
                        onReset = {
                            appearanceColor = SettingsSliderDefaults.resetBlue(appearanceColor)
                            commitAppearance()
                        },
                        stateDescription = "蓝色 $blue",
                    )
                }
                SettingsDivider()
                SliderSettingLabel("小部件背景透明度 ${appearanceOpacity}%")
                ResettableSlider(
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                    value = appearanceOpacity / 100f,
                    onValueChange = { appearanceOpacity = (it * 100).roundToInt().coerceIn(0, 100) },
                    onValueChangeFinished = ::commitAppearance,
                    onReset = {
                        appearanceOpacity = WidgetAppearance.DEFAULT_OPACITY_PERCENT
                        commitAppearance()
                    },
                    startLabel = "更透明",
                    endLabel = "更深色",
                    stateDescription = "小部件背景透明度 $appearanceOpacity%",
                )
                SettingsDivider()
                QuickEntryIconSetting(
                    widgetImageUri = widgetImageUri,
                    previewBitmap = previewBitmap,
                    onPickImage = onPickImage,
                    onResetImage = onResetImage,
                )
            }
        }
        }

        if (!appearanceOnly) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
               Text("小部件", style = MaterialTheme.typography.titleSmall)

                Button(
                    onClick = {
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val comp = android.content.ComponentName(context, QuickDailyReadWidget::class.java)
                            if (mgr.isRequestPinAppWidgetSupported) {
                                val cb = android.app.PendingIntent.getBroadcast(context, 1,
                                    Intent(context, ShortcutPinResultReceiver::class.java).setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                                mgr.requestPinAppWidget(comp, null, cb)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(WidgetIconCatalog.note), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("便签小部件")
                }

                Button(
                    onClick = { ShortcutHelper.pinShortcutToDesktop(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(WidgetIconCatalog.quickEntry),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("快速录入图标")
                }

                Button(
                    onClick = {
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val comp = android.content.ComponentName(context, QuickNoteWidget::class.java)
                            if (mgr.isRequestPinAppWidgetSupported) {
                                val cb = android.app.PendingIntent.getBroadcast(context, 2,
                                    Intent(context, ShortcutPinResultReceiver::class.java).setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                                mgr.requestPinAppWidget(comp, null, cb)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(WidgetIconCatalog.quickEntry), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("快速录入小部件")
                }

                Button(
                    onClick = {
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val comp = android.content.ComponentName(context, TaskWidget::class.java)
                            if (mgr.isRequestPinAppWidgetSupported) {
                                val cb = android.app.PendingIntent.getBroadcast(context, 3,
                                    Intent(context, ShortcutPinResultReceiver::class.java).setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                                mgr.requestPinAppWidget(comp, null, cb)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(WidgetIconCatalog.task), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("任务小部件")
                }
            }
        }

        Text("任务小部件", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val taskPeriodOptions = listOf("today" to "今日任务", "week" to "本周任务", "month" to "本月任务")
                CompactDropdownSetting(
                    label = "任务显示时间段",
                    supportingText = "选择桌面任务小部件显示的任务范围。",
                    selectedKey = config.taskPeriod,
                    options = taskPeriodOptions,
                    onSelect = { key -> onConfigChange { copy(taskPeriod = key) } },
                )

                SettingsDivider()
                CompactDropdownSetting(
                    label = "完成提示音",
                    supportingText = "在任务小部件中完成任务时播放提示音。",
                    selectedKey = config.taskCompletionSoundMode,
                    options = TaskCompletionSoundMode.entries.map { it.key to it.label },
                    onSelect = { key ->
                        val mode = TaskCompletionSoundMode.fromKey(key)
                        onConfigChange { copy(taskCompletionSoundMode = mode.key) }
                        TaskCompletionSoundPolicy.preview(context, mode)
                    },
                )
                SettingsDivider()
                CompletionTimestampSetting(
                    enabled = config.taskCompletionTimestamp,
                    format = config.taskCompletionTimestampFormat,
                    onEnabledChange = { enabled ->
                        onConfigChange { copy(taskCompletionTimestamp = enabled) }
                    },
                    onFormatChange = { format ->
                        onConfigChange { copy(taskCompletionTimestampFormat = format) }
                    },
                )
                SettingsDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("按日期分类") },
                    supportingContent = {
                        Text(
                            if (config.taskGroupByDate) {
                                "开启后本周和本月任务会按日期分组；只有今日任务时不显示日期。"
                            } else {
                                "关闭后本周和本月任务将按当前顺序连续显示。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.taskGroupByDate,
                            onCheckedChange = { onConfigChange { copy(taskGroupByDate = it) } },
                        )
                    },
                )
                SettingsDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("显示已完成任务") },
                    supportingContent = {
                        Text(
                            if (config.taskShowCompleted) {
                                "开启后已完成的任务将继续保留在任务小部件中。"
                            } else {
                                "关闭后已完成的任务将在任务小部件中暂时隐藏，不影响便签小部件。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.taskShowCompleted,
                            onCheckedChange = {
                                onConfigChange { copy(taskShowCompleted = it) }
                            }
                        )
                    }
                )
                SettingsDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("显示任务所有内容") },
                    supportingContent = {
                        Text(
                            if (config.taskShowFullContent) {
                                "开启后将显示任务的完整文本，即使字数超出了数量限制。"
                            } else {
                                "关闭后任务内容至多显示两行，保障列表清晰整洁。"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = config.taskShowFullContent,
                            onCheckedChange = {
                                onConfigChange { copy(taskShowFullContent = it) }
                            }
                        )
                    }
                )
            }
        }
        }

       if (!embedded) {
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
       }
    }
}

@Composable
private fun QuickEntryIconSetting(
    widgetImageUri: String,
    previewBitmap: Bitmap?,
    onPickImage: () -> Unit,
    onResetImage: () -> Unit,
) {
    Text("快速录入小部件 · 自定义图标内容", style = MaterialTheme.typography.titleSmall)
    Text(
        "选择一张图片作为快速录入小部件和快速录入图标的内容",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 2.dp,
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "当前图标",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_add_white),
                    contentDescription = "快速录入小部件默认加号图标",
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (widgetImageUri.isNotEmpty()) "更换图片" else "选择图片")
            }
            OutlinedButton(
                onClick = onResetImage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("重置默认")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 4: Other (Updates, Accessibility, About)
// ══════════════════════════════════════════════════════════

@Composable
private fun StructuredAppearanceTab(
    context: android.content.Context,
    configState: State<DiaryConfig>,
    widgetImageUri: String,
    onConfigChange: OnConfigChange,
    onPickImage: () -> Unit,
    onResetImage: () -> Unit,
    onSave: () -> Unit,
    isActive: Boolean,
) {
    val config by configState
    var floatingOpacity by remember(config.floatingOpacity) { mutableIntStateOf(config.floatingOpacity) }
    var floatingNightModeKey by rememberSaveable {
        mutableStateOf(FloatingNoteAppearance.nightMode(context).key)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollapsibleSettingsSection("编辑器外观") {
                AppearanceSettingsSection(context = context, showHeading = false)
            }

            CollapsibleSettingsSection("悬浮窗外观") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        CompactDropdownSetting(
                            label = "\u591c\u95f4\u6a21\u5f0f",
                             supportingText = "\u4ec5\u7528\u4e8e\u63a7\u5236\u60ac\u6d6e\u7a97\u7684\u6df1\u8272\u4e3b\u9898\u3002",
                            selectedKey = floatingNightModeKey,
                            options = listOf(
                                QuickDailyNightMode.DARK,
                                QuickDailyNightMode.LIGHT,
                                QuickDailyNightMode.SYSTEM,
                            ).map { it.key to it.label },
                            onSelect = { key ->
                                floatingNightModeKey = key
                                FloatingNoteAppearance.setNightMode(
                                    context,
                                    QuickDailyNightMode.fromKey(key),
                                )
                            },
                        )
                        SettingsDivider()
                        Spacer(Modifier.height(12.dp))
                        SliderSettingLabel("悬浮窗透明度 ${floatingOpacity}%")
                        ResettableSlider(
                            modifier = Modifier.padding(horizontal = SettingsContentInset),
                            value = floatingOpacity / 100f,
                            onValueChange = { floatingOpacity = (it * 100).roundToInt().coerceIn(0, 100) },
                            onValueChangeFinished = {
                                onConfigChange { copy(floatingOpacity = floatingOpacity) }
                                FloatingNoteAppearance.refresh(context)
                            },
                            onReset = {
                                floatingOpacity = FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT
                                onConfigChange { copy(floatingOpacity = floatingOpacity) }
                                FloatingNoteAppearance.refresh(context)
                            },
                            startLabel = "更透明",
                            endLabel = "更深色",
                            stateDescription = "透明度 $floatingOpacity%",
                        )
                    }
                }
            }

            CollapsibleSettingsSection("桌面小部件外观") {
                WidgetsTab(
                    widgetImageUri = widgetImageUri,
                    configState = configState,
                    onConfigChange = onConfigChange,
                    context = context,
                    onPickImage = onPickImage,
                    onResetImage = onResetImage,
                    onSave = {},
                    isActive = isActive,
                    appearanceOnly = true,
                    embedded = true,
                    showHeading = false,
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearanceTab(
    context: android.content.Context,
    configState: State<DiaryConfig>,
    widgetImageUri: String,
    onConfigChange: OnConfigChange,
    onPickImage: () -> Unit,
    onResetImage: () -> Unit,
    onSave: () -> Unit,
    isActive: Boolean,
) {
    val config by configState
    var floatingOpacity by remember(config.floatingOpacity) { mutableIntStateOf(config.floatingOpacity) }
    var floatingNightModeKey by rememberSaveable {
        mutableStateOf(FloatingNoteAppearance.nightMode(context).key)
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppearanceSettingsSection(context)
        Text("悬浮窗外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                CompactDropdownSetting(
                    label = "\u591c\u95f4\u6a21\u5f0f",
                     supportingText = "\u4ec5\u7528\u4e8e\u63a7\u5236\u60ac\u6d6e\u7a97\u7684\u6df1\u8272\u4e3b\u9898\u3002",
                    selectedKey = floatingNightModeKey,
                    options = listOf(
                        QuickDailyNightMode.DARK,
                        QuickDailyNightMode.LIGHT,
                        QuickDailyNightMode.SYSTEM,
                    ).map { it.key to it.label },
                    onSelect = { key ->
                        floatingNightModeKey = key
                        FloatingNoteAppearance.setNightMode(
                            context,
                            QuickDailyNightMode.fromKey(key),
                        )
                    },
                )
                SettingsDivider()
                Spacer(Modifier.height(12.dp))
                SliderSettingLabel("悬浮窗透明度 ${floatingOpacity}%")
                ResettableSlider(
                    modifier = Modifier.padding(horizontal = SettingsContentInset),
                    value = floatingOpacity / 100f,
                    onValueChange = { floatingOpacity = (it * 100).roundToInt().coerceIn(0, 100) },
                    onValueChangeFinished = {
                        onConfigChange { copy(floatingOpacity = floatingOpacity) }
                        FloatingNoteAppearance.refresh(context)
                    },
                    onReset = {
                        floatingOpacity = FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT
                        onConfigChange { copy(floatingOpacity = floatingOpacity) }
                        FloatingNoteAppearance.refresh(context)
                    },
                    startLabel = "更透明",
                    endLabel = "更深色",
                    stateDescription = "透明度 $floatingOpacity%",
                )
            }
        }
        WidgetsTab(
            widgetImageUri = widgetImageUri,
            configState = configState,
            onConfigChange = onConfigChange,
            context = context,
            onPickImage = onPickImage,
            onResetImage = onResetImage,
            onSave = {},
            isActive = isActive,
            appearanceOnly = true,
            embedded = true,
        )
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtherTab(
    vaultPath: String,
    obsidianConfigUri: String,
    useCustomObsidianConfigPath: Boolean,
    diaryFolder: String,
    dateFormat: String,
    templatePath: String,
    imageStoragePath: String,
    todayPathState: State<String>,
    obsidianDetected: Boolean,
    obsidianMsg: String,
    onVaultPathChange: (String) -> Unit,
    onDiaryFolderChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onTemplatePathChange: (String) -> Unit,
    onImageStoragePathChange: (String) -> Unit,
    onCustomObsidianConfigPathChange: (Boolean) -> Unit,
    onPickVault: () -> Unit,
    onPickObsidianConfig: () -> Unit,
    onClearObsidianConfig: () -> Unit,
    onPickTemplate: () -> Unit,
    onPickImageStorage: () -> Unit,
    onPickDiaryFolder: () -> Unit,
    onReadObsidianConfig: () -> Unit,
    onSavePaths: () -> Unit,
    vaultEnabled: Boolean,
    onRestartOnboarding: () -> Unit,
    configState: State<DiaryConfig>,
    isCheckingUpdate: Boolean,
    updateInfo: com.quickdaily.util.ReleaseInfo?,
    updateStatus: String,
    updateErrors: List<com.quickdaily.util.SourceError>,
    isLatest: Boolean,
    context: android.content.Context,
    onConfigChange: OnConfigChange,
    onCheckUpdate: () -> Unit,
) {
    val config by configState
    val otherScrollState = rememberScrollState()
    val donationBringIntoViewRequester = remember { BringIntoViewRequester() }
    val otherScope = rememberCoroutineScope()
    val motionPolicy = LocalQuickDailyMotion.current
    var donationFocusNonce by remember { mutableIntStateOf(0) }
    val donationFocusProgress = remember { Animatable(0f) }
    LaunchedEffect(donationFocusNonce) {
        if (donationFocusNonce == 0) return@LaunchedEffect
        if (motionPolicy.reducedMotion) {
            donationFocusProgress.snapTo(0f)
        } else {
            donationFocusProgress.snapTo(0f)
            donationFocusProgress.animateTo(1f, animationSpec = tween(durationMillis = 420))
            donationFocusProgress.animateTo(0f, animationSpec = tween(durationMillis = 1380))
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
            .verticalScroll(otherScrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CollapsibleSettingsSection(
            title = "路径配置",
            initiallyExpanded = false,
            summary = vaultPath.ifBlank { "当前未设置 Vault" },
        ) {
            DiaryStorageTab(
                vaultPath = vaultPath,
                obsidianConfigUri = obsidianConfigUri,
                useCustomObsidianConfigPath = useCustomObsidianConfigPath,
                diaryFolder = diaryFolder,
                dateFormat = dateFormat,
                templatePath = templatePath,
                imageStoragePath = imageStoragePath,
                todayPathState = todayPathState,
                obsidianDetected = obsidianDetected,
                obsidianMsg = obsidianMsg,
                onVaultPathChange = onVaultPathChange,
                onDiaryFolderChange = onDiaryFolderChange,
                onDateFormatChange = onDateFormatChange,
                onTemplatePathChange = onTemplatePathChange,
                onImageStoragePathChange = onImageStoragePathChange,
                configState = configState,
                onConfigChange = onConfigChange,
                onCustomObsidianConfigPathChange = onCustomObsidianConfigPathChange,
                onReadObsidianConfig = onReadObsidianConfig,
                onPickObsidianConfig = onPickObsidianConfig,
                onClearObsidianConfig = onClearObsidianConfig,
                onPickVault = onPickVault,
                onPickTemplate = onPickTemplate,
                onPickImageStorage = onPickImageStorage,
                onPickDiaryFolder = onPickDiaryFolder,
                onSave = onSavePaths,
                vaultEnabled = vaultEnabled,
                embedded = true,
            )
        }

        CollapsibleSettingsSection(
            title = "权限申请",
            initiallyExpanded = false,
        ) {
            PermissionRequestSection(context, showHeading = false)
        }

        CollapsibleSettingsSection(
            title = "更新设置",
            initiallyExpanded = false,
        ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("启动时自动检查更新") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.autoCheckUpdate,
                                enabledText = "开启后每次启动应用时自动检测 GitHub 最新版本",
                                disabledText = "关闭后启动应用时不会自动检查更新",
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.autoCheckUpdate, onCheckedChange = {
                            onConfigChange { copy(autoCheckUpdate = it) }
                        })
                    }
                )
                SettingsDivider()
                Button(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth(), enabled = !isCheckingUpdate) {
                    Icon(Icons.Default.Update, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCheckingUpdate) updateStatus.ifEmpty { "检查中..." } else "检查更新")
                }
                if (isCheckingUpdate && updateStatus.isNotEmpty()) {
                    Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (updateInfo != null) {
                    Text("发现新版本：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(updateInfo.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Button(onClick = { com.quickdaily.util.UpdateChecker.openReleasePage(context, updateInfo.releaseUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Text("前往下载")
                    }
                }
                if (isLatest) {
                    Text("当前已是最新版本（${BuildConfig.VERSION_NAME}）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (updateErrors.isNotEmpty()) {
                    Text("检查更新失败，已尝试  个镜像源：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                   updateErrors.forEach { err ->
                        Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        }

        CollapsibleSettingsSection(
            title = "反馈",
            initiallyExpanded = false,
        ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("记录日志") },
                    supportingContent = {
                        Text(
                            settingSwitchDescription(
                                enabled = config.loggingEnabled,
                                enabledText = "开启后记录操作日志，可能带来一定程度的性能损耗",
                                disabledText = "关闭后不记录操作日志",
                            ) + "提交完 BUG 后请手动关闭。",
                        )
                    },
                    trailingContent = {
                        Switch(checked = config.loggingEnabled, onCheckedChange = {
                            onConfigChange { copy(loggingEnabled = it) }
                            com.quickdaily.BetaLogger.configure(context, it, true)
                        })
                    }
                )
                SettingsDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestartOnboarding,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("重置新手引导")
                }
            }
        }

        AnimatedSettingsVisibility(visible = config.loggingEnabled) {
            Text(
                "完整调试日志可能包含部分日记正文和本地路径，请仅在定位 BUG 时开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = { com.quickdaily.BetaLogger.shareLog(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BugReport, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("分享 Beta 日志")
            }
        }
        }

        CollapsibleSettingsSection(
            title = "关于",
            initiallyExpanded = true,
        ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QuickDaily ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)

                var showAllChangelog by rememberSaveable { mutableStateOf(false) }
                val coolapkA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("酷安社区 @附近的人") }
                }
                ClickableText(text = coolapkA, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/400522"))) })

                val githubA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("GitHub @IvanTszan") }
                }
                ClickableText(text = githubA, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agarcabin/QuickDaily"))) })

                val qqA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("QQ群：1050092886") }
                }
                ClickableText(text = qqA, onClick = {
                    val qi = Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1050092886&card_type=group&source=qrcode"))
                    try { context.startActivity(qi) }
                    catch (_: Exception) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?from=app&p=android&jump_from=webapi&k=20251120"))) }
                        catch (_: Exception) { android.widget.Toast.makeText(context, "请安装 QQ 或手动搜索群号 1050092886", android.widget.Toast.LENGTH_LONG).show() }
                    }
                })

                Text("更新内容：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (showAllChangelog) {
                Text(CHANGELOG_1_9_3_BETA + "\n\n" + CHANGELOG_1_9_2_BETA + "\n\n" + CHANGELOG_1_9_1_BETA + "\n\n" + CHANGELOG_1_9 + "\n\n" +
                    "1.8:\n" +
                    "• 新增 小部件大小调整支持自适应\n" +
                    "• 新增 任务小部件滴声开关\n" +
                    "• 新增 任务小部件完成时间戳\n" +
                    "• 新增 自定义ob配置文件路径\n" +
                    "• 新增 系统自带侧边栏启动器触发悬浮窗\n" +
                    "• 调整 悬浮窗光标颜色自适应\n" +
                    "• 调整 更完整的调试日志收集\n" +
                    "• 调整 降低小部件刷新频率，防止小部件卡死\n" +
                    "• 调整 配置文件统一存放目录 0/Document/QuickDaily\n" +
                    "• 调整 首页调整为悬浮窗，原首页调整为次级编辑器页面，入口在悬浮窗左上角\n" +
                    "• 修复 快速添加（桌面图标）添加失败\n" +
                    "• 修复 图片堆积BUG\n" +
                    "• 修复 小部件回车换行失效\n\n" +
                    "1.7:\n" +
                    "• 修复 日记模板路径选择器选择异常\n" +
                    "• 修复 撤销按钮对md符号失效\n" +
                    "• 修复 设置界面多余的横线\n" +
                    "• 修复 日志保存位置异常\n\n" +
                    "1.6:\n" +
                    "• 新增 首页/小部件 标签自动补全（拉取Obsidian已有标签）\n" +
                    "• 新增 首页/小部件 标签渲染为蓝色\n" +
                    "• 新增 首页/小部件 标题#按钮切换逻辑：# ，## ，### ，无格式\n" +
                    "• 新增 首页/小部件 工具栏添加撤销、重做、收起键盘按钮\n" +
                    "• 新增 首页/小部件 全类型附件插入\n" +
                    "• 新增 首页 标题栏添加打开 Obsidian 日记按钮\n" +
                    "• 新增 便签小部件 支持上下滚动、支持实时渲染、支持任务交互\n" +
                    "• 新增 任务小部件 时间段选择（日/周/月）\n" +
                    "• 新增 设置 自定义图片添加裁剪步骤\n" +
                    "• 新增 设置 小部件背景色以及透明度开发自定义\n" +
                    "• 新增 设置 图片Markdow链接 新增格式 ![[filename]]\n" +
                    "• 新增 设置 锚点文本支持换行\n" +
                    "• 新增 每日凌晨自动刷新桌面小部件内容\n" +
                    "• 调整 设置 界面 UI 分类\n" +
                    "• 调整 悬浮窗 图片选择器样式同部位工具栏样式\n" +
                    "• 修复 工具栏小白条颜色适配\n" +
                    "• 修复 本周/本月任务小部件无法勾选任务\n" +
                    "• 修复 图片文件夹选择保存路径导致ob库路径异常\n" +
                    "• 修复 图片保存时图片Markdow格式链接跟ob不兼容\n" +
                    "• 修复 悬浮窗 语音输入时只能输入单字\n" +
                    "• 修复 悬浮窗 无文字时图片保存失败\n" +
                    "• 修复 桌面添加多个速录图标时，部分图标变成灰色\n" +
                    "• 修复 图片储存目录文件夹选择器无法正常选择附件目录\n" +
                    "• 修复 工具栏标题#按钮后无空格\n" +
                    "• 修复 模板中 ymal 被重复载入\n" +
                    "• 修复 锚点位置设置为下方插入时误插入到文本最后\n\n" +
                    "1.5:\n" +
                    "• 新增 首页/悬浮窗 底部工具栏 \n" +
                    "• 新增 首页 阅读视图图片显示 \n" +
                    "• 新增 对 Templater 插件日期格式支持 \n" +
                    "• 新增 安卓小部件添加页面 预览图 \n" +
                    "• 调整 快速添加（桌面图标）的默认图标样式 \n" +
                    "• 修复 悬浮窗 任务切换格式错误 \n" +
                    "• 修复 悬浮窗 空任务异常触发保存 \n" +
                    "• 修复 小部件 今日任务刷新异常 \n\n" +
                    "1.4:\n" +
                    "• 新增 Frontmatter 过滤 \n" +
                    "• 新增 WW 等日期格式支持 \n" +
                    "• 新增 悬浮窗增加图片录入功能（可批量导入）\n" +
                    "• 新增 悬浮窗增加任务录入功能（双击切换任务状态） \n" +
                    "• 新增 《今日任务》桌面小部件 \n\n" +
                    "1.3:\n" +
                    "• 新增 7种时间戳格式设置，可适配Thino/Knomo \n" +
                    "• 新增 时间戳文本插入顺序\n" +
                    "• 新增 无锚点时自动添加锚点文本\n" +
                    "• 修复 清空日记内容后无法重新加载模板\n" +
                    "• 修复 每日首次录入内容时略过日记模板\n\n" +
                    "1.2:\n" +
                    "• 新增 快速添加（桌面图标）\n" +
                    "• 修复 磁贴点击后收回状态栏\n\n" +
                    "1.1:\n" +
                    "• 新增 小部件时间戳，回车保存\n" +
                    "• 新增 小部件自定义图片\n" +
                    "• 新增 状态栏快捷磁贴\n" +
                    "• 新增 文本分享至本应用\n" +
                    "• 新增 检测更新\n\n" +
                    "1.0:\n" +
                    "• 正式发布！APP 更名为 QuickDaily\n" +
                    "• 开源发布到 GitHub",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                Text(
                        CHANGELOG_1_9_3_BETA,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                TextButton(
                    onClick = { showAllChangelog = !showAllChangelog },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(if (showAllChangelog) "收起内容" else "更多内容")
                }
            }
        }
        }

        CollapsibleSettingsSection(
            title = "支持",
            initiallyExpanded = true,
        ) {
        SponsorListCard(
            context = context,
            onRequestDonation = {
                otherScope.launch {
                    donationBringIntoViewRequester.bringIntoView()
                    donationFocusNonce++
                }
            },
        )
        val donationCardShape = MaterialTheme.shapes.medium
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(donationBringIntoViewRequester)
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.48f * donationFocusProgress.value,
                        ),
                    ),
                    shape = donationCardShape,
                ),
            shape = donationCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "\u6253\u8d4f\u7801",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u4e2a\u4eba\u5f00\u53d1\u8005\u7ef4\u62a4\u4e0d\u6613\uff0c\u82e5\u60a8\u559c\u6b22\u6216\u8ba4\u53ef QuickDaily\uff0c\u6b22\u8fce\u5c0f\u989d\u9f13\u52b1\uff1a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(8.dp))
                val donationPainter = painterResource(id = R.drawable.qr_donate)
                val donationAspectRatio = donationPainter.intrinsicSize.let { intrinsicSize ->
                    if (intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite() &&
                        intrinsicSize.width > 0f && intrinsicSize.height > 0f
                    ) {
                        intrinsicSize.width / intrinsicSize.height
                    } else {
                        4f / 3f
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(donationAspectRatio)
                        .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            try {
                                val bm = BitmapFactory.decodeResource(context.resources, R.drawable.qr_donate)
                                val fn = "QuickDaily_donate_.jpg"
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val vals = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, fn)
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                        put(MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vals)
                                    uri?.let {
                                        context.contentResolver.openOutputStream(it)?.use { out -> bm.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                                        vals.clear(); vals.put(MediaStore.Images.Media.IS_PENDING, 0)
                                        context.contentResolver.update(it, vals, null, null)
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES); dir.mkdirs()
                                    val file = File(dir, fn); FileOutputStream(file).use { out -> bm.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                                }
                                android.widget.Toast.makeText(context, "已保存至相册，谢谢！", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { android.widget.Toast.makeText(context, "保存失败: ", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = donationPainter,
                        contentDescription = "赞赏码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    "（长按图片保存到相册）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun SponsorListCard(
    context: android.content.Context,
    onRequestDonation: () -> Unit,
) {
    val entries = remember(context) { SponsorEntryRegistry.entries(context) }
    val readState = remember(context, entries) {
        mutableStateMapOf<String, Boolean>().apply {
            entries.forEach { entry -> put(entry.id, SponsorReadState.isRead(context, entry.id)) }
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    var bubbleWindowBounds by remember { mutableStateOf<IntRect?>(null) }
    val avatarWindowBounds = remember { mutableStateMapOf<String, IntRect>() }
    val selectedEntry = entries.firstOrNull { it.id == selectedId }
    val density = LocalDensity.current
    val bubbleGapPx = with(density) { 8.dp.roundToPx() }
    val windowView = LocalView.current.rootView
    val windowWidth = windowView.width
    val windowHeight = windowView.height
    val windowMarginPx = with(density) { 8.dp.roundToPx() }
    val bubbleTailWidthPx = with(density) { 20.dp.toPx() }
    val bubbleTailHeightPx = with(density) { 10.dp.toPx() }
    val bubbleTailContentGap = 8.dp

    LaunchedEffect(selectedId) {
        bubbleSize = IntSize.Zero
        bubbleWindowBounds = null
    }

    fun recordBounds(id: String, coordinates: LayoutCoordinates) {
        val position = coordinates.positionInWindow()
        avatarWindowBounds[id] = IntRect(
            left = position.x.roundToInt(),
            top = position.y.roundToInt(),
            right = position.x.roundToInt() + coordinates.size.width,
            bottom = position.y.roundToInt() + coordinates.size.height,
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "\u8d5e\u52a9\u5217\u8868",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    entries.forEach { entry ->
                        SponsorAvatar(
                            entry = entry,
                            selected = selectedId == entry.id,
                            unread = readState[entry.id] != true,
                            onClick = {
                                SponsorReadState.markRead(context, entry.id)
                                readState[entry.id] = true
                                selectedId = if (selectedId == entry.id) null else entry.id
                            },
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                recordBounds(entry.id, coordinates)
                            },
                        )
                    }
                    SponsorPlaceholder(
                        onClick = {
                            selectedId = null
                            onRequestDonation()
                        },
                    )
                }
                selectedEntry?.let { entry ->
                    val anchor = avatarWindowBounds[entry.id]
                    if (anchor != null) {
                        val effectiveWindowWidth = windowWidth.takeIf { it > 0 } ?: windowView.width
                        val bubbleAbove = sponsorBubbleAbove(
                            avatarWindowBounds = anchor,
                            bubbleSize = bubbleSize,
                            gapPx = bubbleGapPx,
                            marginPx = windowMarginPx,
                        )
                        val bubbleLeft = sponsorBubbleLeft(
                            avatarWindowBounds = anchor,
                            bubbleWidthPx = bubbleSize.width,
                            marginPx = windowMarginPx,
                            windowWidth = effectiveWindowWidth,
                        )
                        val bubbleTailOnTop = bubbleWindowBounds?.let { bounds ->
                            SponsorBubbleTailPolicy.tailOnTop(
                                avatarTop = anchor.top,
                                avatarBottom = anchor.bottom,
                                bubbleTop = bounds.top,
                                bubbleBottom = bounds.bottom,
                            )
                        } ?: !bubbleAbove
                        Popup(
                            popupPositionProvider = remember(
                                anchor,
                                bubbleSize,
                                windowWidth,
                                windowHeight,
                            ) {
                                SponsorBubblePositionProvider(
                                    avatarWindowBounds = anchor,
                                    gapPx = bubbleGapPx,
                                    marginPx = windowMarginPx,
                                    fallbackWindowSize = IntSize(windowWidth, windowHeight),
                                )
                            },
                            onDismissRequest = { selectedId = null },
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                            ),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .onSizeChanged { bubbleSize = it }
                                    .onGloballyPositioned { coordinates ->
                                        val position = coordinates.positionInWindow()
                                        val bounds = IntRect(
                                            left = position.x.roundToInt(),
                                            top = position.y.roundToInt(),
                                            right = position.x.roundToInt() + coordinates.size.width,
                                            bottom = position.y.roundToInt() + coordinates.size.height,
                                        )
                                        if (bubbleWindowBounds != bounds) bubbleWindowBounds = bounds
                                    }
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                shape = SponsorSpeechBubbleShape(
                                    tailOnTop = bubbleTailOnTop,
                                    tailOffsetPx = (
                                        ((anchor.left + anchor.right) / 2) -
                                            (bubbleWindowBounds?.left ?: bubbleLeft)
                                        ).toFloat(),
                                    tailWidthPx = bubbleTailWidthPx,
                                    tailHeightPx = bubbleTailHeightPx,
                                    cornerRadiusPx = with(density) { 12.dp.toPx() },
                                ),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 2.dp,
                            ) {
                                val tailContentPadding = with(density) {
                                    bubbleTailHeightPx.toDp() + bubbleTailContentGap
                                }
                                Box(
                                    modifier = Modifier.padding(
                                        // Keep text in the rounded body. The shape's actual tail
                                        // direction can differ from the initial placement while
                                        // Popup measures or when the bubble is clamped by the window.
                                        top = if (bubbleTailOnTop) tailContentPadding else 0.dp,
                                        bottom = if (bubbleTailOnTop) 0.dp else tailContentPadding,
                                    ),
                                ) {
                                    Text(
                                        text = "${entry.nickname}\uff1a${entry.message}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Start,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "感谢以上用户的鼎力支持。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Positions the sponsor message in window coordinates so it can escape its card without escaping the screen. */
private class SponsorBubblePositionProvider(
    private val avatarWindowBounds: IntRect,
    private val gapPx: Int,
    private val marginPx: Int,
    private val fallbackWindowSize: IntSize,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val effectiveWindowWidth = windowSize.width.takeIf { it > 0 } ?: fallbackWindowSize.width
        val effectiveWindowHeight = windowSize.height.takeIf { it > 0 } ?: fallbackWindowSize.height
        val maxX = (effectiveWindowWidth - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (effectiveWindowHeight - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val preferredAbove = avatarWindowBounds.top - popupContentSize.height - gapPx
        val preferredBelow = avatarWindowBounds.bottom + gapPx
        val preferredY = if (sponsorBubbleAbove(avatarWindowBounds, popupContentSize, gapPx, marginPx)) {
            preferredAbove
        } else {
            preferredBelow
        }
        return IntOffset(
            x = sponsorBubbleLeft(
                avatarWindowBounds = avatarWindowBounds,
                bubbleWidthPx = popupContentSize.width,
                marginPx = marginPx,
                windowWidth = effectiveWindowWidth,
            ).coerceIn(marginPx, maxX),
            y = preferredY.coerceIn(marginPx, maxY),
        )
    }
}

private fun sponsorBubbleAbove(
    avatarWindowBounds: IntRect,
    bubbleSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): Boolean = bubbleSize.height > 0 &&
    avatarWindowBounds.top - bubbleSize.height - gapPx >= marginPx

private fun sponsorBubbleLeft(
    avatarWindowBounds: IntRect,
    bubbleWidthPx: Int,
    marginPx: Int,
    windowWidth: Int,
): Int {
    if (bubbleWidthPx <= 0) return avatarWindowBounds.left
    val maxX = (windowWidth - bubbleWidthPx - marginPx).coerceAtLeast(marginPx)
    return ((avatarWindowBounds.left + avatarWindowBounds.right) / 2 - bubbleWidthPx / 2)
        .coerceIn(marginPx, maxX)
}

/** Speech-bubble surface with a small tail pointing at the selected avatar. */
private class SponsorSpeechBubbleShape(
    private val tailOnTop: Boolean,
    private val tailOffsetPx: Float,
    private val tailWidthPx: Float,
    private val tailHeightPx: Float,
    private val cornerRadiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val bodyTop = if (tailOnTop) tailHeightPx else 0f
        val bodyBottom = if (tailOnTop) size.height else (size.height - tailHeightPx)
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = bodyTop,
                    right = size.width,
                    bottom = bodyBottom.coerceAtLeast(bodyTop),
                    radiusX = cornerRadiusPx,
                    radiusY = cornerRadiusPx,
                ),
            )
            val tailX = tailOffsetPx.coerceIn(tailWidthPx / 2f, size.width - tailWidthPx / 2f)
            if (tailOnTop) {
                moveTo(tailX - tailWidthPx / 2f, bodyTop)
                lineTo(tailX, 0f)
                lineTo(tailX + tailWidthPx / 2f, bodyTop)
            } else {
                moveTo(tailX - tailWidthPx / 2f, bodyBottom)
                lineTo(tailX, size.height)
                lineTo(tailX + tailWidthPx / 2f, bodyBottom)
            }
            close()
        }
        return Outline.Generic(body)
    }
}

private val SponsorAvatarSize = 56.dp
private val SponsorUnreadDotSize = 14.dp
private val SponsorUnreadDotColor = Color(0xFFFF3B30)

@Composable
private fun SponsorAvatar(
    entry: SponsorEntry,
    selected: Boolean,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(SponsorAvatarSize),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(entry.avatarRes),
            contentDescription = "${entry.nickname} 的头像，点击查看付款留言",
            modifier = Modifier
                .size(SponsorAvatarSize)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "查看 ${entry.nickname} 的付款留言",
                    onClick = onClick,
                )
                .semantics {
                    stateDescription = when {
                        selected -> "留言已展开"
                        unread -> "有未读留言"
                        else -> "留言已读"
                    }
                },
            contentScale = ContentScale.Crop,
        )
        if (unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(SponsorUnreadDotSize)
                    .background(SponsorUnreadDotColor, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun SponsorPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(SponsorAvatarSize), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(SponsorAvatarSize)
                .semantics {
                    contentDescription = "期待你的支持，点击查看打赏码"
                },
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsSection(
    context: android.content.Context,
    showHeading: Boolean = true,
) {
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var useMonet by rememberSaveable {
        mutableStateOf(QuickDailyThemePreferences.isMonetEnabled(context))
    }
    var selectedPresetKey by rememberSaveable {
        mutableStateOf(QuickDailyThemePreferences.selectedPreset(context).key)
    }
    var nightModeKey by rememberSaveable {
        mutableStateOf(QuickDailyThemePreferences.nightMode(context).key)
    }
    var darkBackgroundBrightness by rememberSaveable {
        mutableIntStateOf(QuickDailyThemePreferences.darkBackgroundBrightness(context))
    }
    val selectedPreset = QuickDailyAccentPreset.fromKey(selectedPresetKey)
    val selectedNightMode = QuickDailyNightMode.fromKey(nightModeKey)

    if (showHeading) {
        Text(
            "编辑器外观",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentInset, vertical = 4.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("莫奈取色") },
                supportingContent = {
                    Text(
                        when {
                            !monetSupported -> "当前 Android 版本不支持莫奈，使用下方预设强调色"
                            useMonet -> "开启后跟随系统壁纸动态生成 Material 3 色板。"
                            else -> "关闭后使用预设强调色。"
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = useMonet && monetSupported,
                        enabled = monetSupported,
                        onCheckedChange = {
                            useMonet = it
                            QuickDailyThemePreferences.setMonetEnabled(context, it)
                        },
                    )
                },
            )
            AnimatedSettingsVisibility(visible = !(useMonet && monetSupported)) {
                SettingsDivider()
                Column(modifier = Modifier.padding(horizontal = SettingsContentInset, vertical = 12.dp)) {
                    Text("预设强调色", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "选择一个预设作为应用强调色，将同步于编辑页、悬浮窗和桌面小部件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(QuickDailyAccentPreset.entries, key = { it.key }) { preset ->
                            FilterChip(
                                selected = !useMonet && selectedPreset == preset,
                                onClick = {
                                    selectedPresetKey = preset.key
                                    useMonet = false
                                    QuickDailyThemePreferences.selectAccentPreset(context, preset)
                                },
                                label = { Text(preset.label) },
                                leadingIcon = {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(preset.previewColor),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            SettingsDivider()
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                CompactDropdownSetting(
                    label = "夜间模式",
                    supportingText = "仅用于控制编辑器页面的深色主题。",
                    selectedKey = nightModeKey,
                    options = listOf(
                        QuickDailyNightMode.DARK,
                        QuickDailyNightMode.LIGHT,
                        QuickDailyNightMode.SYSTEM,
                    ).map { it.key to it.label },
                    onSelect = { key ->
                        nightModeKey = key
                        QuickDailyThemePreferences.setNightMode(context, QuickDailyNightMode.fromKey(key))
                    },
                )
                AnimatedSettingsVisibility(visible = shouldShowDarkBackgroundBrightness(selectedNightMode)) {
                    SettingsDivider()
                    Column {
                        Spacer(Modifier.height(12.dp))
                        SliderSettingLabel("\u591c\u95f4\u6a21\u5f0f\u80cc\u666f\u4eae\u5ea6 ${darkBackgroundBrightness}%")
                        ResettableSlider(
                            modifier = Modifier.padding(horizontal = SettingsContentInset),
                            value = darkBackgroundBrightness / 100f,
                            onValueChange = {
                                darkBackgroundBrightness = (it * 100f).roundToInt().coerceIn(0, 100)
                            },
                            onValueChangeFinished = {
                                QuickDailyThemePreferences.setDarkBackgroundBrightness(
                                    context,
                                    darkBackgroundBrightness,
                                )
                            },
                            onReset = {
                                darkBackgroundBrightness = QuickDailyThemePreferences.DEFAULT_DARK_BACKGROUND_BRIGHTNESS
                                QuickDailyThemePreferences.setDarkBackgroundBrightness(
                                    context,
                                    darkBackgroundBrightness,
                                )
                            },
                            valueRange = 0f..1f,
                            startLabel = "更暗",
                            endLabel = "更亮",
                            stateDescription = "\u591c\u95f4\u6a21\u5f0f\u80cc\u666f\u4eae\u5ea6 $darkBackgroundBrightness%",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestSection(
    context: android.content.Context,
    showHeading: Boolean = true,
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshKey++
    }
    val specs = remember {
        com.quickdaily.PermissionPolicy.visibleInSettings()
            .filter(com.quickdaily.PermissionPolicy::isApplicable)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun request(spec: com.quickdaily.PermissionSpec) {
        when (spec.kind) {
            com.quickdaily.PermissionKind.RUNTIME -> {
                spec.androidPermission?.let(permissionLauncher::launch)
            }
            com.quickdaily.PermissionKind.OVERLAY -> {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (_: Exception) { }
            }
            com.quickdaily.PermissionKind.MANAGE_FILES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } catch (_: Exception) { }
                }
            }
            com.quickdaily.PermissionKind.ACCESSIBILITY -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) { }
            }
            com.quickdaily.PermissionKind.SYSTEM -> Unit
        }
    }

    if (showHeading) {
        Text("权限申请", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentInset, vertical = 8.dp),
        ) {
            Text(
                "以下是本APP运行所需要的所有权限，请按需授权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            specs.forEachIndexed { index, spec ->
                if (index > 0) {
                    SettingsDivider()
                }
                val status = remember(spec.id, refreshKey) {
                    com.quickdaily.PermissionPolicy.status(context, spec)
                }
                val (statusText, statusColor) = when (status) {
                    com.quickdaily.PermissionStatus.GRANTED -> "已获得" to MaterialTheme.colorScheme.primary
                    com.quickdaily.PermissionStatus.NOT_GRANTED -> "未获得" to MaterialTheme.colorScheme.error
                    com.quickdaily.PermissionStatus.NOT_REQUIRED -> "当前系统无需" to MaterialTheme.colorScheme.onSurfaceVariant
                    com.quickdaily.PermissionStatus.SYSTEM_MANAGED -> "系统管理" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(spec.title) },
                    supportingContent = {
                        Column {
                            Text(spec.description)
                            Text(
                                statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    },
                    trailingContent = {
                        TextButton(
                            enabled = status == com.quickdaily.PermissionStatus.NOT_GRANTED,
                            onClick = { request(spec) },
                        ) {
                            Text(if (status == com.quickdaily.PermissionStatus.NOT_GRANTED) "申请" else statusText)
                        }
                    },
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Shared: ExposedDropdownMenu setting
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    supportingText: String? = null,
    selectedKey: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey
    val resolvedSupportingText = supportingText?.takeIf { it.isNotBlank() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = resolvedSupportingText?.let { text -> { Text(text) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, display) ->
                SettingsDropdownMenuItem(
                    label = display,
                    selected = key == selectedKey,
                    onClick = { onSelect(key); expanded = false },
                )
            }
        }
    }
}

