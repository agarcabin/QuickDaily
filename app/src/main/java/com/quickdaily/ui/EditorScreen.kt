package com.quickdaily.ui

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AttachFile
import com.quickdaily.BetaLogger
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalClipboardManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.quickdaily.AppState
import com.quickdaily.EditorImageInsertPolicy
import com.quickdaily.markdown.MdRenderer
import com.quickdaily.markdown.toggleTaskCheck
import com.quickdaily.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.quickdaily.CaptureFileUtil
import com.quickdaily.EditorMediaUtil
import com.quickdaily.EditorToolbarAction
import com.quickdaily.EditorStampAction
import com.quickdaily.EditorStampToggleState
import com.quickdaily.TextIndentPolicy
import com.quickdaily.EditorTextActionPolicy
import com.quickdaily.EditorLinePrefixPolicy
import com.quickdaily.EditorAutoIndentPolicy
import com.quickdaily.EditorAutoIndentState
import com.quickdaily.FloatingCursorPolicy
import com.quickdaily.EditorStampPolicy
import com.quickdaily.ui.theme.LocalQuickDailyMotion
import com.quickdaily.WikilinkIndexRepository
import com.quickdaily.WikilinkCandidate
import com.quickdaily.WikilinkCandidatePolicy
import com.quickdaily.WikilinkRecentStore
import com.quickdaily.WikilinkPolicy
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal object EditorCursorPolicy {
    fun isSelectionValid(selectionStart: Int, selectionEnd: Int, layoutTextLength: Int): Boolean =
        layoutTextLength >= 0 &&
            selectionStart in 0..layoutTextLength &&
            selectionEnd in selectionStart..layoutTextLength
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    appState: AppState = viewModel(),
    onExternalLaunch: () -> Unit = {},
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowSize = rememberQuickDailyWindowSize()
    // Bug2: 设置导航栏颜色与工具栏一致（提前 capture，避免 @Composable 访问问题）
    val navBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val diaryContent by appState.diaryContent.collectAsStateWithLifecycle()
    val isLoaded by appState.isLoaded.collectAsStateWithLifecycle()
    val editorConflict by appState.editorConflict.collectAsStateWithLifecycle()
    val todayPath by appState.todayPath.collectAsStateWithLifecycle()
    val editorTargetRelativePath by appState.editorTargetRelativePath.collectAsStateWithLifecycle()
    val config by appState.config.collectAsStateWithLifecycle()
    val canUndo by appState.canUndo.collectAsStateWithLifecycle()
    val canRedo by appState.canRedo.collectAsStateWithLifecycle()
    val wikilinkIndex by WikilinkIndexRepository.indexState.collectAsStateWithLifecycle()
    val completionIndex = wikilinkIndex.takeIf {
        it.rootPath == config.vaultPath && it.indexed && it.tagsIndexed && it.error == null
    }
    val allTags = completionIndex?.tags.orEmpty()
    val view = LocalView.current
    val title = todayPath.substringAfterLast("/").removeSuffix(".md")
    val clipboardManager = LocalClipboardManager.current
    DisposableEffect(context, navBarColor) {
        try {
            val window = (context as? Activity)?.window
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.navigationBarColor = navBarColor
            }
        } catch (_: Exception) { }
        onDispose { }
    }

    var showPreview by remember { mutableStateOf(false) }
    var toolbarPage by remember { mutableIntStateOf(0) }
    var toolbarPageCount by remember { mutableIntStateOf(1) }
    val keyboardVisible = WindowInsets.isImeVisible
    val motionPolicy = LocalQuickDailyMotion.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var autoIndentState by remember { mutableStateOf<EditorAutoIndentState?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<java.io.File?>(null) }
    var recordingStartedAt by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var wikilinkPopupDismissKey by remember { mutableStateOf<String?>(null) }
    var recentWikilinks by remember { mutableStateOf(WikilinkRecentStore.load(context)) }
    var stampToggleState by remember { mutableStateOf(EditorStampToggleState()) }

    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) toolbarPage = 0
    }

    fun applyInsertedLink(link: String) {
        val next = EditorMediaUtil.insertLink(textFieldValue.text, textFieldValue.selection, link)
        stampToggleState = stampToggleState.clear()
        textFieldValue = next
        appState.onContentChanged(next.text, forceUndoPoint = true)
    }

    fun insertImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val link = runCatching { EditorMediaUtil.imageLink(context, uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (link != null) applyInsertedLink(link)
            }
        }
    }

    fun insertImagesInSelectionOrder(uris: List<Uri>) {
        scope.launch(Dispatchers.IO) {
            val links = EditorImageInsertPolicy.processInSelectionOrder(uris) { uri ->
                runCatching { EditorMediaUtil.imageLink(context, uri) }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                links.filterNotNull().forEach(::applyInsertedLink)
            }
        }
    }

    fun finishRecording() {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        recordingStartedAt = null
        recordingElapsedMs = 0L
        val stopped = runCatching { activeRecorder.stop() }.isSuccess
        runCatching { activeRecorder.reset() }
        runCatching { activeRecorder.release() }
        if (!stopped || file == null) {
            file?.delete()
            return
        }
        val host = context as? androidx.activity.ComponentActivity
        (host?.lifecycleScope ?: scope).launch(Dispatchers.IO) {
            val link = runCatching { EditorMediaUtil.audioLink(context, file) }.getOrNull()
            if (link != null) file.delete()
            withContext(Dispatchers.Main) {
                if (link != null) applyInsertedLink(link)
                else Toast.makeText(context, "录音保存失败，临时文件已保留", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startRecordingNow() {
        if (recorder != null) return
        val file = runCatching { CaptureFileUtil.newAudioFile(context) }.getOrNull() ?: return
        val nextRecorder = runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        if (nextRecorder == null) {
            file.delete()
            Toast.makeText(context, "无法开始录音", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = nextRecorder
        recordingFile = file
        recordingStartedAt = SystemClock.elapsedRealtime()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (success && uri != null) insertImage(uri)
        else file?.delete()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = runCatching { CaptureFileUtil.newImageFile(context) }.getOrNull()
            if (file == null) {
                Toast.makeText(context, "无法创建照片文件", Toast.LENGTH_SHORT).show()
            } else {
                val uri = CaptureFileUtil.fileUri(context, file)
                pendingCameraFile = file
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "请允许相机权限后再拍照", Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingNow()
        else Toast.makeText(context, "请允许录音权限后再录音", Toast.LENGTH_SHORT).show()
    }

    fun toggleRecording() {
        if (recorder != null) {
            finishRecording()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingNow()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recordingStartedAt) {
        while (recordingStartedAt != null) {
            recordingElapsedMs = SystemClock.elapsedRealtime() - (recordingStartedAt ?: break)
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose { finishRecording() }
    }

    // 图片选择器（跟悬浮窗一样）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        insertImagesInSelectionOrder(uris)
    }
    // 附件选择器
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val link = if (mimeType.startsWith("image/")) {
                val links = ImageUtil.processImages(
                    context, listOf(uri), config.vaultPath,
                    config.imageStoragePath, config.imageNamingFormat,
                    config.imageLinkFormat, config.imageCustomNamingFormat
                )
                links.firstOrNull() ?: return@launch
            } else {
                val vaultPath = config.vaultPath
                val storagePath = config.imageStoragePath
                val dir = if (storagePath.isBlank()) "" else storagePath.trim('/')
                val dirPath = if (dir.isNotEmpty()) vaultPath.trimEnd('/') + "/" + dir else vaultPath.trimEnd('/')
                val destDirFile = java.io.File(dirPath)
                destDirFile.mkdirs()
                val displayName = com.quickdaily.util.ImageUtil.getDisplayName(context, uri)
                val ext = com.quickdaily.util.ImageUtil.getExtension(context, uri)
                val fileName = com.quickdaily.util.ImageUtil.generateFileName(config.imageNamingFormat, displayName, ext, config.imageCustomNamingFormat)
                val destFile = java.io.File(destDirFile, fileName)
                context.contentResolver.openInputStream(uri)?.use { input: java.io.InputStream? ->
                    if (input != null) {
                        java.io.FileOutputStream(destFile).use { output: java.io.FileOutputStream ->
                            input.copyTo(output)
                        }
                    }
                }
                val relativePath = if (dir.isNotEmpty()) dir + "/" + fileName else fileName
                "![[" + relativePath + "]]"
            }
            withContext(Dispatchers.Main) {
                val next = EditorMediaUtil.insertLink(
                    textFieldValue.text,
                    textFieldValue.selection,
                    link,
                )
                stampToggleState = stampToggleState.clear()
                textFieldValue = next
                appState.onContentChanged(next.text, forceUndoPoint = true)
            }
        }
    }


    // -- Tag autocomplete --
    val tagCompletion = remember(textFieldValue, config.tagAutocomplete, allTags) {
        if (!config.tagAutocomplete || completionIndex == null) return@remember Triple(false, "", 0)
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.start
        if (cursor > 0 && cursor <= text.length) {
            val before = text.substring(0, cursor)
            val hi = before.lastIndexOf('#')
            if (hi >= 0) {
                val after = before.substring(hi + 1)
                // A lone # is a valid completion prefix: show recent tags immediately.
                if (after.isEmpty() || (after[0] != ' ' && !after.all { it == '#' })) {
                    val p = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }
                    val wordBefore = hi > 0 && (text[hi - 1].isLetterOrDigit() || text[hi - 1] == '_')
                    if (!wordBefore) {
                        val tagFinished = p in allTags && (after.length == p.length || after.length > p.length && (!after[p.length].isLetterOrDigit() && after[p.length] != '#'))
                        if (!tagFinished) {
                            return@remember Triple(true, p, hi)
                        }
                    }
                }
            }
        }
        Triple(false, "", 0)
    }

    val (tagActive, tagPrefix, tagHashPos) = tagCompletion

    val matchingTags = remember(tagActive, tagPrefix, allTags) {
        if (!tagActive) emptyList()
        else {
            val p = tagPrefix
            if (p.isEmpty()) {
                val recent = com.quickdaily.util.RecentTags.get(context)
                (recent + allTags.filterNot { it in recent }).take(3)
            }
            else {
                allTags.filter { it.contains(p as CharSequence, ignoreCase = true) }.take(8)
            }
        }
    }

    val latestTextFieldValue by rememberUpdatedState(textFieldValue)
    val selectTag: (String) -> Unit = remember(tagHashPos) {
        { tag ->
            val currentValue = latestTextFieldValue
            val text = currentValue.text
            val cursor = currentValue.selection.start
            val hp = tagHashPos
            val needSpaceBefore = hp > 0 && text[hp - 1] != ' ' && text[hp - 1] != '\n'
            val prefix = if (needSpaceBefore) " #" else "#"
            val newText = text.substring(0, hp) + prefix + tag + " " + text.substring(cursor)
            val newCursor = hp + prefix.length + tag.length + 1
            stampToggleState = stampToggleState.clear()
            textFieldValue = TextFieldValue(newText, TextRange(newCursor))
            appState.onContentChanged(newText, forceUndoPoint = true)
            com.quickdaily.util.RecentTags.record(context, tag)
        }
    }

    LaunchedEffect(config.vaultPath) {
        WikilinkIndexRepository.ensureIndexed(context, config.vaultPath)
    }
    LaunchedEffect(wikilinkIndex.error) {
        wikilinkIndex.error?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val wikilinkTrigger = remember(
        textFieldValue.text,
        textFieldValue.selection,
        config.wikilinkAutocomplete,
        config.vaultPath,
        wikilinkIndex.rootPath,
        wikilinkIndex.error,
    ) {
        if (!config.wikilinkAutocomplete || config.vaultPath.isBlank() || wikilinkIndex.rootPath != config.vaultPath || wikilinkIndex.error != null) {
            null
        } else {
            WikilinkPolicy.trigger(textFieldValue.text, textFieldValue.selection.start)
        }
    }
    val matchingWikilinks = remember(wikilinkIndex.candidates, wikilinkTrigger, recentWikilinks) {
        val trigger = wikilinkTrigger ?: return@remember emptyList<WikilinkCandidate>()
        if (trigger.query.isBlank()) {
            recentWikilinks.take(WikilinkCandidatePolicy.DEFAULT_LIMIT)
        } else {
            WikilinkPolicy.filterWikilinkCandidates(wikilinkIndex.candidates, trigger.query)
        }
    }
    val aliasCounts = remember(wikilinkIndex.aliases) {
        wikilinkIndex.aliases.groupingBy { it.alias }.eachCount()
    }

    fun selectWikilink(candidate: WikilinkCandidate) {
        val currentTrigger = wikilinkTrigger ?: return
        val replacement = candidate.insertionText
        val text = textFieldValue.text
        val newText = text.substring(0, currentTrigger.start) + replacement + text.substring(currentTrigger.replaceEnd)
        val cursor = currentTrigger.start + replacement.length
        stampToggleState = stampToggleState.clear()
        textFieldValue = TextFieldValue(newText, TextRange(cursor))
        appState.onContentChanged(newText, forceUndoPoint = true)
        WikilinkRecentStore.record(context, candidate)
        recentWikilinks = listOf(candidate) + recentWikilinks.filterNot { it.stableKey == candidate.stableKey }
            .take(9)
    }

    fun applyTextAction(
        result: com.quickdaily.EditorTextActionResult,
        invalidateStamp: Boolean = true,
    ) {
        result.clipboardText?.let { clipboardManager.setText(AnnotatedString(it)) }
        if (invalidateStamp) stampToggleState = stampToggleState.clear()
        if (result.text != textFieldValue.text) {
            textFieldValue = TextFieldValue(result.text, result.selection)
            appState.onContentChanged(result.text, forceUndoPoint = true)
        } else {
            textFieldValue = TextFieldValue(result.text, result.selection)
        }
    }

    val wikilinkTriggerKey = wikilinkTrigger?.let { "${it.start}:${it.replaceEnd}:${it.query}" }

    LaunchedEffect(editorTargetRelativePath) { appState.loadEditorTarget(editorTargetRelativePath) }
    LaunchedEffect(diaryContent) {
        if (diaryContent != textFieldValue.text) {
            stampToggleState = stampToggleState.clear()
            autoIndentState = null
            textFieldValue = TextFieldValue(diaryContent)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
            TopAppBar(
                title = { Text(title.ifEmpty { "QuickDaily" }, style = MaterialTheme.typography.titleMedium) },
                actions = {

                    TextButton(onClick = {
                        val vaultName = config.vaultPath.trimEnd('/').substringAfterLast('/')
                        if (vaultName.isNotBlank()) {
                            try {
                                val relativePath = Uri.encode(appState.currentEditorRelativePath())
                                val uri = Uri.parse("obsidian://open?vault=${Uri.encode(vaultName)}&file=$relativePath")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("打开Obsidian",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = { showPreview = !showPreview }) {
                    Icon(
                        if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,
                        if (showPreview) "返回编辑" else "预览",
                    )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
        },
        bottomBar = {
            if (!showPreview) {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    // 小白条上方 + 输入法上方
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = if (windowSize.isLarge) 12.dp else 4.dp,
                                vertical = 4.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            EditorToolbarActions(
                                order = config.toolbarOrder,
                                visible = config.toolbarVisible,
                                tint = MaterialTheme.colorScheme.primary,
                                enabled = { action ->
                                    when (action) {
                                        EditorToolbarAction.UNDO -> canUndo
                                        EditorToolbarAction.REDO -> canRedo
                                        else -> true
                                    }
                                },
                                recording = recorder != null,
                                recordingDurationMs = recordingElapsedMs,
                                buttonSize = 48.dp,
                                page = toolbarPage,
                                onPageChanged = { toolbarPage = it },
                                onPageCountChanged = { toolbarPageCount = it },
                                onAction = { action ->
                                    when (action) {
                                        EditorToolbarAction.IMAGE -> {
                                            onExternalLaunch()
                                            imagePicker.launch("image/*")
                                        }
                                        EditorToolbarAction.CAMERA -> {
                                            onExternalLaunch()
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                val file = runCatching { CaptureFileUtil.newImageFile(context) }.getOrNull()
                                                if (file == null) {
                                                    Toast.makeText(context, "无法创建照片文件", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val uri = CaptureFileUtil.fileUri(context, file)
                                                    pendingCameraFile = file
                                                    pendingCameraUri = uri
                                                    cameraLauncher.launch(uri)
                                                }
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                        EditorToolbarAction.RECORD -> toggleRecording()
                                        EditorToolbarAction.ATTACHMENT -> {
                                            onExternalLaunch()
                                            attachmentPicker.launch("*/*")
                                        }
                                        EditorToolbarAction.INDENT -> {
                                            val result = TextIndentPolicy.indent(textFieldValue.text, textFieldValue.selection)
                                            stampToggleState = stampToggleState.clear()
                                            textFieldValue = TextFieldValue(result.text, result.selection)
                                            appState.onContentChanged(result.text, forceUndoPoint = true)
                                        }
                                        EditorToolbarAction.OUTDENT -> {
                                            val result = TextIndentPolicy.outdent(textFieldValue.text, textFieldValue.selection)
                                            stampToggleState = stampToggleState.clear()
                                            textFieldValue = TextFieldValue(result.text, result.selection)
                                            appState.onContentChanged(result.text, forceUndoPoint = true)
                                        }
                                        EditorToolbarAction.CUT_LINE -> applyTextAction(
                                            EditorTextActionPolicy.cutLine(textFieldValue.text, textFieldValue.selection)
                                        )
                                        EditorToolbarAction.MOVE_LINE_UP -> applyTextAction(
                                            EditorTextActionPolicy.moveLineUp(textFieldValue.text, textFieldValue.selection)
                                        )
                                        EditorToolbarAction.MOVE_LINE_DOWN -> applyTextAction(
                                            EditorTextActionPolicy.moveLineDown(textFieldValue.text, textFieldValue.selection)
                                        )
                                        EditorToolbarAction.TIMESTAMP -> {
                                            val (result, nextState) = stampToggleState.toggle(
                                                text = textFieldValue.text,
                                                selection = textFieldValue.selection,
                                                action = EditorStampAction.TIMESTAMP,
                                                insertion = EditorStampPolicy.toolbarTimestampInsertion(),
                                            )
                                            stampToggleState = nextState
                                            applyTextAction(result, invalidateStamp = false)
                                        }
                                        EditorToolbarAction.DATE_STAMP -> {
                                            val (result, nextState) = stampToggleState.toggle(
                                                text = textFieldValue.text,
                                                selection = textFieldValue.selection,
                                                action = EditorStampAction.DATE_STAMP,
                                                insertion = EditorStampPolicy.dateInsertion(),
                                            )
                                            stampToggleState = nextState
                                            applyTextAction(result, invalidateStamp = false)
                                        }
                                        EditorToolbarAction.WIKILINK -> applyTextAction(
                                            EditorTextActionPolicy.insert(textFieldValue.text, textFieldValue.selection, "[[")
                                        )
                                        EditorToolbarAction.STRIKETHROUGH -> applyTextAction(
                                            EditorTextActionPolicy.toggleDelimiter(textFieldValue.text, textFieldValue.selection, "~~")
                                        )
                                        EditorToolbarAction.INLINE_CODE -> applyTextAction(
                                            EditorTextActionPolicy.toggleDelimiter(textFieldValue.text, textFieldValue.selection, "`")
                                        )
                                        EditorToolbarAction.QUOTE -> applyTextAction(
                                            EditorLinePrefixPolicy.apply(textFieldValue.text, textFieldValue.selection, EditorToolbarAction.QUOTE)
                                        )
                                        EditorToolbarAction.CODE_BLOCK -> applyTextAction(
                                            EditorLinePrefixPolicy.apply(textFieldValue.text, textFieldValue.selection, EditorToolbarAction.CODE_BLOCK)
                                        )
                                        EditorToolbarAction.HORIZONTAL_RULE -> applyTextAction(
                                            EditorTextActionPolicy.horizontalRule(textFieldValue.text, textFieldValue.selection)
                                        )
                                        EditorToolbarAction.MARKDOWN_LINK -> applyTextAction(
                                            EditorTextActionPolicy.markdownLink(textFieldValue.text, textFieldValue.selection)
                                        )
                                        EditorToolbarAction.TASK,
                                        EditorToolbarAction.HEADING,
                                        EditorToolbarAction.LIST,
                                        EditorToolbarAction.ORDERED_LIST -> applyTextAction(
                                            EditorLinePrefixPolicy.apply(
                                                textFieldValue.text,
                                                textFieldValue.selection,
                                                action,
                                            )
                                        )
                                        EditorToolbarAction.BOLD -> {
                                            val t = textFieldValue.text
                                            val c = textFieldValue.selection.start
                                            val nt = if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                                                t.substring(0, c - 2) + t.substring(c + 2)
                                            } else {
                                                t.substring(0, c) + "****" + t.substring(c)
                                            }
                                            val nc = if (nt.length < t.length) c - 2 else c + 2
                                            stampToggleState = stampToggleState.clear()
                                            textFieldValue = TextFieldValue(nt, TextRange(nc))
                                            appState.onContentChanged(nt, forceUndoPoint = true)
                                        }
                                        EditorToolbarAction.UNDO -> {
                                            appState.undo()
                                            BetaLogger.log("Toolbar", "undo")
                                        }
                                        EditorToolbarAction.REDO -> {
                                            appState.redo()
                                            BetaLogger.log("Toolbar", "redo")
                                        }
                                    }
                                },
                            )
                        }
                        val toolbarArrowRotation by animateFloatAsState(
                            targetValue = when {
                                keyboardVisible -> 0f
                                toolbarPage > 0 -> 90f
                                else -> 270f
                            },
                            animationSpec = if (motionPolicy.reducedMotion) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "toolbarKeyboardPageArrow",
                        )
                        ToolbarIconButton(
                            icon = {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    if (keyboardVisible) "关闭键盘" else if (toolbarPage > 0) "返回第一页工具" else "打开第二页工具",
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = toolbarArrowRotation },
                                )
                            },
                            onClick = {
                                if (keyboardVisible) {
                                    val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                                    BetaLogger.log("Toolbar", "close_keyboard")
                                } else if (toolbarPageCount > 1) {
                                    toolbarPage = if (toolbarPage == 0) 1 else 0
                                    BetaLogger.log("Toolbar", "switch_page page=$toolbarPage")
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            editorConflict?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "检测到磁盘文件有更新",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "本地未保存内容未被覆盖，请选择保留哪一份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = appState::useDiskConflict) {
                                Text("采用磁盘版本")
                            }
                            OutlinedButton(onClick = appState::keepLocalConflict) {
                                Text("保留本地并覆盖磁盘")
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!isLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (showPreview) {
                    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        MdRenderer(text = diaryContent, vaultBasePath = config.vaultPath, imageStoragePath = config.imageStoragePath.takeIf { it.isNotBlank() }, onToggleCheckbox = { index ->
                            appState.onContentChanged(toggleTaskCheck(diaryContent, index), forceUndoPoint = true)
                        })
                    }
                } else {
            val scrollState = rememberScrollState()
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            var viewportH by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val padPx = with(density) { 16.dp.toPx() }
            val sel = textFieldValue.selection.start
            val layoutText = textLayoutResult?.layoutInput?.text
            LaunchedEffect(sel, layoutText) {
                textLayoutResult?.let { layout ->
                    if (viewportH <= 0) return@let
                    if (!EditorCursorPolicy.isSelectionValid(
                            selectionStart = sel,
                            selectionEnd = textFieldValue.selection.end,
                            layoutTextLength = layout.layoutInput.text.length,
                        )
                    ) return@let
                    val r = layout.getCursorRect(sel)
                    val cursorY = r.bottom + padPx
                    val st = scrollState.value
                    val midY = st + viewportH / 2
                    if (cursorY > midY) {
                        val target = (cursorY - viewportH / 2).toInt().coerceAtLeast(0)
                        scrollState.animateScrollTo(target)
                    } else if (r.top + padPx < st) {
                        scrollState.animateScrollTo((r.top + padPx).toInt().coerceAtLeast(0))
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                        .onSizeChanged { viewportH = it.height }
                ) {
                        BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val result = EditorAutoIndentPolicy.apply(
                                previous = textFieldValue,
                                proposed = newValue,
                                state = autoIndentState,
                            )
                            autoIndentState = result.state
                            if (result.value.text != textFieldValue.text) {
                                stampToggleState = stampToggleState.clear()
                            }
                            textFieldValue = result.value
                            appState.onContentChanged(result.value.text)
                        },
                        onTextLayout = { textLayoutResult = it },
                        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                        cursorBrush = SolidColor(FloatingCursorPolicy.colorFor(MaterialTheme.colorScheme.background)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 400.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && wikilinkTriggerKey != null) {
                                    wikilinkPopupDismissKey = wikilinkTriggerKey
                                    true
                                } else if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Enter &&
                                    matchingTags.isNotEmpty()
                                ) {
                                    selectTag(matchingTags.first())
                                    true
                                } else if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Enter &&
                                    matchingWikilinks.isNotEmpty()
                                ) {
                                    selectWikilink(matchingWikilinks.first())
                                    true
                                } else {
                                    false
                                }
                            },
                        decorationBox = { innerField ->
                            if (textFieldValue.text.isEmpty()) Text("开始写今天的日记...", color = Color.Gray, fontSize = 16.sp)
                            innerField()
                        }
                    )
                }
                // Tag autocomplete Popup near cursor
                if (tagActive && matchingTags.isNotEmpty()) {
                    val cursorPos = textFieldValue.selection.start
                    val layoutResult = textLayoutResult
                    val cr = layoutResult?.takeIf { cursorPos <= it.layoutInput.text.length }?.getCursorRect(cursorPos)
                    val density = LocalDensity.current
                    val padPx = with(density) { 16.dp.toPx() }
                    val sy = scrollState.value
                    val popupX = ((cr?.left?.toInt() ?: 0) + padPx.toInt()).coerceAtLeast(8)
                    val popupY = ((cr?.bottom?.toInt() ?: 0) + padPx.toInt() - sy + 8).coerceAtLeast(0)
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(popupX, popupY),
                        properties = PopupProperties(focusable = false, dismissOnBackPress = true, dismissOnClickOutside = true)
                    ) {
                        QuickDailyAutocompleteSurface(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .heightIn(max = 200.dp),
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 2.dp)) {
                                matchingTags.forEach { tag ->
                                    TextButton(
                                        onClick = { selectTag(tag) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            "#$tag",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
            }
                        }
                    }
                }
                if (wikilinkTrigger != null &&
                    (wikilinkIndex.loading || matchingWikilinks.isNotEmpty()) &&
                    wikilinkTriggerKey != wikilinkPopupDismissKey
                ) {
                    val cursorPos = textFieldValue.selection.start
                    val layoutResult = textLayoutResult
                    val cr = layoutResult?.takeIf { cursorPos <= it.layoutInput.text.length }?.getCursorRect(cursorPos)
                    val density = LocalDensity.current
                    val padPx = with(density) { 16.dp.toPx() }
                    val sy = scrollState.value
                    val popupX = ((cr?.left?.toInt() ?: 0) + padPx.toInt()).coerceAtLeast(8)
                    val popupY = ((cr?.bottom?.toInt() ?: 0) + padPx.toInt() - sy + 8).coerceAtLeast(0)
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(popupX, popupY),
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        )
                    ) {
                        QuickDailyAutocompleteSurface(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .heightIn(max = 240.dp),
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 2.dp)) {
                                if (wikilinkIndex.loading && matchingWikilinks.isEmpty()) {
                                    Text(
                                        "正在建立双链索引…",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    matchingWikilinks.forEach { candidate ->
                                        TextButton(
                                            onClick = { selectWikilink(candidate) },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.Start,
                                            ) {
                                                Text(
                                                    candidate.displayText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (candidate.alias != null && aliasCounts[candidate.alias] ?: 0 > 1) {
                                                    Text(
                                                        candidate.targetPath,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
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
            }
                }
            }
        }
    }
}

@Composable
private fun ObsidianIcon(modifier: Modifier = Modifier) {
    val tintColor = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = modifier) {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.45f)
            lineTo(w / 2f, h)
            lineTo(0f, h * 0.45f)
            close()
        }
        drawPath(path, tintColor)
        
        val inner = Path().apply {
            moveTo(w / 2f, h * 0.25f)
            lineTo(w * 0.6f, h * 0.45f)
            lineTo(w / 2f, h * 0.65f)
            lineTo(w * 0.4f, h * 0.45f)
            close()
        }
        drawPath(inner, tintColor.copy(alpha = 0.35f))
    }
}

@Composable
private fun ToolbarIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val dim = com.quickdaily.ui.theme.LocalAppDimensions.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(dim.spacing3xl)
    ) {
        icon()
    }
}


