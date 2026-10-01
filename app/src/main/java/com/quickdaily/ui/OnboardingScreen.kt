package com.quickdaily.ui

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quickdaily.AppState
import com.quickdaily.BetaLogger
import com.quickdaily.DiaryConfig
import com.quickdaily.OnboardingPolicy
import com.quickdaily.OnboardingStore
import com.quickdaily.PermissionKind
import com.quickdaily.PermissionPolicy
import com.quickdaily.PermissionSpec
import com.quickdaily.PermissionStatus
import com.quickdaily.QuickDailyReadWidget
import com.quickdaily.QuickNoteWidget
import com.quickdaily.ShortcutPinResultReceiver
import com.quickdaily.TaskWidget
import com.quickdaily.WidgetIconCatalog
import com.quickdaily.util.VaultStoragePrefs
import com.quickdaily.ui.theme.rememberQuickDailyMotionPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun OnboardingScreen(
    appState: AppState,
    onFinished: () -> Unit,
    onExternalLaunch: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val config by appState.config.collectAsStateWithLifecycle()
    val initialPage = remember { OnboardingPolicy.clampPage(OnboardingStore.page(context)) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { OnboardingPolicy.PAGE_COUNT })
    val page = pagerState.currentPage
    var refreshKey by remember { mutableIntStateOf(0) }
    var detectionMessage by rememberSaveable { mutableStateOf("") }
    var skipDialogOpen by rememberSaveable { mutableStateOf(false) }
    val allFilesAccessGranted = remember(context, refreshKey) {
        onboardingStoragePermissionGranted(context)
    }
    val canAdvance = OnboardingPolicy.canAdvance(
        page = page,
        vaultConfigured = config.vaultPath.isNotBlank(),
        allFilesAccessGranted = allFilesAccessGranted,
    )
    val motionPolicy = rememberQuickDailyMotionPolicy()

    suspend fun detectObsidian(path: String) {
        if (path.isBlank()) return
        detectionMessage = context.getString(com.quickdaily.R.string.qd_onboarding_reading_config)
        val detection = try {
            val daily = appState.loadObsidianConfig(path)
            val obsidianApp = appState.loadObsidianAppConfig(path)
            appState.updateConfig { current ->
                if (daily != null) {
                    current.copy(
                        vaultPath = path,
                        diaryFolder = daily.diaryFolder,
                        dateFormat = daily.dateFormat,
                        templatePath = daily.templatePath,
                        imageStoragePath = obsidianApp?.attachmentFolderPath
                            ?.let { if (it == "/") "" else it.trimStart('/') }
                            ?: current.imageStoragePath,
                        imageLinkFormat = if (obsidianApp?.useMarkdownLinks == true) "described" else current.imageLinkFormat,
                    )
                } else {
                    current.copy(vaultPath = path)
                }
            }
            val manageFiles = PermissionPolicy.all().firstOrNull { it.id == PermissionPolicy.MANAGE_FILES_ID }
            val permissionMissing = manageFiles?.let {
                safePermissionStatus(context, it) == PermissionStatus.NOT_GRANTED
            } == true
            daily to permissionMissing
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            BetaLogger.logException("Onboarding", "detect_config_failed path=$path", error)
            detectionMessage = context.getString(com.quickdaily.R.string.qd_onboarding_config_read_failed)
            return
        }
        val (daily, permissionMissing) = detection
        detectionMessage = if (daily != null) {
            context.getString(com.quickdaily.R.string.qd_onboarding_config_read_success)
        } else if (permissionMissing) {
            context.getString(com.quickdaily.R.string.qd_onboarding_config_retry_after_permission)
        } else {
            context.getString(com.quickdaily.R.string.qd_onboarding_config_not_found)
        }
    }

    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = VaultStoragePrefs.saveSaf(context, uri)
        val validation = VaultStoragePrefs.validate(context)
        if (path == null || validation.status != com.quickdaily.util.VaultValidationStatus.VALID) {
            detectionMessage = validation.message.resolve(context).toString()
        } else {
            appState.updateConfig { current -> current.copy(vaultPath = path) }
            scope.launch { detectObsidian(path) }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshKey++
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshKey++
    }

    LaunchedEffect(pagerState.currentPage) {
        OnboardingStore.setPage(context, pagerState.currentPage)
    }

    DisposableEffect(lifecycleOwner, page) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                val vault = appState.config.value.vaultPath
                if (page == 2 && vault.isNotBlank()) scope.launch { detectObsidian(vault) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun goTo(next: Int) {
        val target = OnboardingPolicy.clampPage(next)
        if (target > page && !canAdvance) return
        scope.launch {
            if (motionPolicy.reducedMotion) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { skipDialogOpen = true },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(com.quickdaily.R.string.qd_onboarding_skip))
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (!canAdvance && page == 1) {
                        Text(
                            stringResource(com.quickdaily.R.string.qd_onboarding_select_vault_first),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (!canAdvance && page == 2) {
                        Text(
                            stringResource(com.quickdaily.R.string.qd_onboarding_grant_storage_first),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(OnboardingPolicy.PAGE_COUNT) { indicatorPage ->
                            val selectedPage = indicatorPage == page
                            val pageDescription = stringResource(
                                com.quickdaily.R.string.qd_onboarding_page_indicator,
                                indicatorPage + 1,
                                OnboardingPolicy.PAGE_COUNT,
                            )
                            Spacer(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (selectedPage) 10.dp else 8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (selectedPage) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    )
                                    .semantics {
                                        contentDescription = pageDescription
                                        selected = selectedPage
                                    },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (page > 0) {
                            OutlinedButton(
                                onClick = { goTo(page - 1) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(stringResource(com.quickdaily.R.string.qd_onboarding_previous)) }
                        }
                        Button(
                            onClick = {
                                if (page == OnboardingPolicy.PAGE_COUNT - 1) {
                                    OnboardingStore.complete(context)
                                    onFinished()
                                } else {
                                    goTo(page + 1)
                                }
                            },
                            enabled = canAdvance,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (page == OnboardingPolicy.PAGE_COUNT - 1) {
                                        com.quickdaily.R.string.qd_onboarding_start
                                    } else {
                                        com.quickdaily.R.string.qd_onboarding_next
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .pointerInput(page, canAdvance) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDrag) < 56f) return@detectHorizontalDragGestures
                                val target = if (totalDrag < 0f) page + 1 else page - 1
                                if (target !in 0 until OnboardingPolicy.PAGE_COUNT) return@detectHorizontalDragGestures
                                if (target > page && !canAdvance) return@detectHorizontalDragGestures
                                goTo(target)
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            ) { pagerPage ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (pagerPage) {
                        0 -> WelcomePage()
                        1 -> VaultPage(
                            config = config,
                            message = detectionMessage,
                            onPickVault = {
                                onExternalLaunch()
                                runCatching { vaultPicker.launch(null) }
                                    .onFailure { error ->
                                        BetaLogger.logException("Onboarding", "vault_picker_launch_failed", error)
                                        detectionMessage = context.getString(com.quickdaily.R.string.qd_onboarding_picker_failed)
                                    }
                            },
                        )
                        2 -> PermissionPage(
                            context = context,
                            refreshKey = refreshKey,
                            onExternalLaunch = onExternalLaunch,
                            onNotificationRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                        .onFailure { error ->
                                            BetaLogger.logException("Onboarding", "notification_permission_launch_failed", error)
                                        }
                                }
                            },
                            onLegacyStorageRequest = {
                                runCatching {
                                    legacyStorageLauncher.launch(
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                    )
                                }.onFailure { error ->
                                    BetaLogger.logException("Onboarding", "storage_permission_launch_failed", error)
                                }
                            },
                        )
                        else -> WidgetPage(context, refreshKey)
                    }
                }
            }
        }
    }
    if (skipDialogOpen) {
        OnboardingSkipConfirmationDialog(
            onDismiss = { skipDialogOpen = false },
            onConfirm = {
                skipDialogOpen = false
                OnboardingStore.skip(context)
                onFinished()
            },
        )
    }
}

private fun onboardingStoragePermissionGranted(context: Context): Boolean {
    val vault = VaultStoragePrefs.current(context)
    if (vault.backend == com.quickdaily.util.VaultBackend.SAF) {
        return VaultStoragePrefs.validate(context).status == com.quickdaily.util.VaultValidationStatus.VALID
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val spec = PermissionPolicy.all().firstOrNull { it.id == PermissionPolicy.MANAGE_FILES_ID }
            ?: return false
        return safePermissionStatus(context, spec) == PermissionStatus.GRANTED
    }
    return PermissionPolicy.all()
        .filter { it.id == "read_external_storage" || it.id == "write_external_storage" }
        .filter(PermissionPolicy::isApplicable)
        .all { safePermissionStatus(context, it) == PermissionStatus.GRANTED }
}

private fun safePermissionStatus(context: Context, spec: PermissionSpec): PermissionStatus =
    runCatching { PermissionPolicy.status(context, spec) }
        .getOrDefault(PermissionStatus.NOT_GRANTED)

private fun Context.startOnboardingSettings(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (error: Exception) {
        BetaLogger.logException("Onboarding", "settings_launch_failed action=${intent.action}", error)
        android.widget.Toast.makeText(
            this,
            getString(com.quickdaily.R.string.qd_onboarding_settings_failed),
            android.widget.Toast.LENGTH_LONG,
        ).show()
        false
    }

@Composable
private fun WelcomePage() {
    OnboardingHeader(
        Icons.Default.Speed,
        stringResource(com.quickdaily.R.string.qd_onboarding_welcome_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_welcome_subtitle),
    )
    FeatureCard(
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_local_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_local_body),
    )
    FeatureCard(
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_speed_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_speed_body),
    )
    FeatureCard(
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_capture_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_feature_capture_body),
    )
}

@Composable
private fun VaultPage(config: DiaryConfig, message: String, onPickVault: () -> Unit) {
    OnboardingHeader(
        Icons.Default.FolderOpen,
        stringResource(com.quickdaily.R.string.qd_onboarding_vault_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_vault_subtitle),
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(com.quickdaily.R.string.qd_onboarding_current_vault), style = MaterialTheme.typography.titleMedium)
            Text(
                config.vaultPath.ifBlank { stringResource(com.quickdaily.R.string.qd_onboarding_vault_unselected) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onPickVault, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (config.vaultPath.isBlank()) {
                            com.quickdaily.R.string.qd_onboarding_select_vault
                        } else {
                            com.quickdaily.R.string.qd_onboarding_reselect_vault
                        },
                    ),
                )
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Text(
        stringResource(com.quickdaily.R.string.qd_onboarding_vault_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionPage(
    context: Context,
    refreshKey: Int,
    onExternalLaunch: () -> Unit,
    onNotificationRequest: () -> Unit,
    onLegacyStorageRequest: () -> Unit,
) {
    OnboardingHeader(
        Icons.Default.Security,
        stringResource(com.quickdaily.R.string.qd_onboarding_permissions_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_permissions_subtitle),
    )
    val ids = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(PermissionPolicy.MANAGE_FILES_ID)
        } else {
            add("read_external_storage")
            add("write_external_storage")
        }
        add(PermissionPolicy.OVERLAY_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("post_notifications")
        add(PermissionPolicy.ACCESSIBILITY_ID)
    }
    ids.mapNotNull { id -> PermissionPolicy.all().firstOrNull { it.id == id } }
        .filter(PermissionPolicy::isApplicable)
        .forEach { spec ->
            val status = remember(spec.id, refreshKey) { safePermissionStatus(context, spec) }
            val importance = when (spec.id) {
                PermissionPolicy.MANAGE_FILES_ID, "read_external_storage", "write_external_storage" ->
                    stringResource(com.quickdaily.R.string.qd_onboarding_importance_core)
                PermissionPolicy.ACCESSIBILITY_ID -> stringResource(com.quickdaily.R.string.qd_onboarding_importance_optional)
                else -> stringResource(com.quickdaily.R.string.qd_onboarding_importance_recommended)
            }
            PermissionCard(spec, status, importance) {
                when (spec.kind) {
                    PermissionKind.RUNTIME -> when (spec.id) {
                        "post_notifications" -> onNotificationRequest()
                        "read_external_storage", "write_external_storage" -> onLegacyStorageRequest()
                        else -> Unit
                    }
                    PermissionKind.MANAGE_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val launched = context.startOnboardingSettings(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            },
                        )
                        if (launched) onExternalLaunch()
                    } else onLegacyStorageRequest()
                    PermissionKind.OVERLAY -> {
                        val launched = context.startOnboardingSettings(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            },
                        )
                        if (launched) onExternalLaunch()
                    }
                    PermissionKind.ACCESSIBILITY -> {
                        if (context.startOnboardingSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
                            onExternalLaunch()
                        }
                    }
                    PermissionKind.SYSTEM -> Unit
                }
            }
        }
}

@Composable
private fun PermissionCard(
    spec: PermissionSpec,
    status: PermissionStatus,
    importance: String,
    onRequest: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text("${stringResource(spec.titleRes)} · $importance")
            },
            supportingContent = {
                Column {
                    Text(stringResource(spec.descriptionRes))
                    Text(
                        stringResource(
                            if (status == PermissionStatus.GRANTED) {
                                com.quickdaily.R.string.qd_onboarding_permission_granted
                            } else {
                                com.quickdaily.R.string.qd_onboarding_permission_not_granted
                            },
                        ),
                        color = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            trailingContent = {
                TextButton(
                    onClick = onRequest,
                    enabled = status == PermissionStatus.NOT_GRANTED,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(
                        stringResource(
                            if (status == PermissionStatus.GRANTED) {
                                com.quickdaily.R.string.qd_onboarding_permission_done
                            } else {
                                com.quickdaily.R.string.qd_onboarding_permission_request
                            },
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun WidgetPage(context: Context, refreshKey: Int) {
    OnboardingHeader(
        Icons.Default.Widgets,
        stringResource(com.quickdaily.R.string.qd_onboarding_widgets_title),
        stringResource(com.quickdaily.R.string.qd_onboarding_widgets_subtitle),
    )
    WidgetPinCard(
        context = context,
        provider = QuickNoteWidget::class.java,
        title = stringResource(com.quickdaily.R.string.qd_onboarding_quick_widget_title),
        description = stringResource(com.quickdaily.R.string.qd_onboarding_quick_widget_description),
        requestCode = 105,
        icon = painterResource(WidgetIconCatalog.quickEntry),
        refreshKey = refreshKey,
    )
    WidgetPinCard(
        context = context,
        provider = TaskWidget::class.java,
        title = stringResource(com.quickdaily.R.string.qd_onboarding_task_widget_title),
        description = stringResource(com.quickdaily.R.string.qd_onboarding_task_widget_description),
        requestCode = 103,
        icon = painterResource(WidgetIconCatalog.task),
        refreshKey = refreshKey,
    )
    WidgetPinCard(
        context = context,
        provider = QuickDailyReadWidget::class.java,
        title = stringResource(com.quickdaily.R.string.qd_onboarding_diary_widget_title),
        description = stringResource(com.quickdaily.R.string.qd_onboarding_diary_widget_description),
        requestCode = 101,
        icon = painterResource(WidgetIconCatalog.note),
        refreshKey = refreshKey,
    )
}

@Composable
private fun WidgetPinCard(
    context: Context,
    provider: Class<*>,
    title: String,
    description: String,
    requestCode: Int,
    icon: Painter,
    refreshKey: Int,
) {
    val manager = remember { AppWidgetManager.getInstance(context) }
    val component = remember(provider) { ComponentName(context, provider) }
    val added = remember(refreshKey, component) { manager.getAppWidgetIds(component).isNotEmpty() }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painter = icon, contentDescription = null) },
            headlineContent = { Text(title) },
            supportingContent = {
                Text(
                    if (added) {
                        stringResource(com.quickdaily.R.string.qd_onboarding_widget_added, description)
                    } else {
                        description
                    },
                )
            },
            trailingContent = {
                TextButton(
                    enabled = !added,
                    onClick = {
                        if (manager.isRequestPinAppWidgetSupported) {
                            val callback = PendingIntent.getBroadcast(
                                context,
                                requestCode,
                                Intent(context, ShortcutPinResultReceiver::class.java)
                                    .setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                            runCatching {
                                manager.requestPinAppWidget(component, null, callback)
                            }.onFailure { error ->
                                BetaLogger.logException(
                                    "Onboarding",
                                    "widget_pin_failed provider=${provider.name}",
                                    error,
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(com.quickdaily.R.string.qd_onboarding_widget_pin_failed),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(com.quickdaily.R.string.qd_onboarding_widget_manual_add),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }.onFailure { error ->
                                BetaLogger.logException("Onboarding", "home_launch_failed", error)
                            }
                        }
                    },
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(if (added) Icons.Default.CheckCircle else Icons.Default.Shortcut, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(
                            if (added) com.quickdaily.R.string.qd_onboarding_added else com.quickdaily.R.string.qd_onboarding_add,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun OnboardingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
