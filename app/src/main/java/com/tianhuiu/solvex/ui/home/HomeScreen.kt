package com.tianhuiu.solvex.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.PermissionSetupStep
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.TooltipText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToTutorial: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val lifecycleOwner = LocalLifecycleOwner.current
    val inAppNotifications by (viewModel.getApplication<com.tianhuiu.solvex.SolveXApplication>().container.appNotificationManager.notifications).collectAsState(initial = emptyList<com.tianhuiu.solvex.data.models.InAppNotification>())

    val sheetState = rememberModalBottomSheetState()
    var showAssistantSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAssistantSheet) {
        AssistantSelectionSheet(
            viewModel = viewModel,
            onDismissRequest = { showAssistantSheet = false },
            onNavigateToSettings = onNavigateToSettings,
            sheetState = sheetState
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SolveX",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    )
                },
                windowInsets = WindowInsets(top = 0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
                )
            )
        }
    ) { padding ->
        // 停止服务确认弹窗
        if (viewModel.showStopConfirmationDialog) {
            SolveXConfirmDialog(
                onDismissRequest = { viewModel.updateShowStopConfirmationDialog(false) },
                onConfirm = { viewModel.stopService() },
                title = "停止服务",
                message = "确认要停止 SolveX 服务吗？停止后将无法使用实时捕获功能。",
                confirmText = "确认停止",
                isDestructive = true,
                icon = Icons.Default.StopCircle
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            surfaceColor,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 1. 首页通知
            if (inAppNotifications.isNotEmpty() == true) {
                item {
                    StatusBar(
                        notifications = inAppNotifications,
                        onDismiss = { viewModel.dismissInAppNotification(it) },
                        onNavigateToTutorial = onNavigateToTutorial
                    )
                }
            }

            // 2. 核心配置看板
            item {
                ConfigurationBoard(
                    viewModel = viewModel,
                    onOpenAssistantSelection = { showAssistantSheet = true }
                )
            }

            // 3. 操作区
            item {
                Spacer(Modifier.height(8.dp))
                ActionSection(viewModel)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ConfigurationBoard(
    viewModel: MainViewModel,
    onOpenAssistantSelection: () -> Unit
) {
    val selectedAssistant = viewModel.assistants.find { it.id == viewModel.selectedAssistantId }
        ?: viewModel.assistants.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("核心配置", "调整分析方式和模型")

        // 权限引导移动到此处
        if (viewModel.showPermissionSetupGuide) {
            PermissionSetupGuideCard(
                viewModel = viewModel,
                onAction = { viewModel.handleSetupStepAction(viewModel.currentSetupStep) },
                onSkip = { viewModel.skipPermissionSetup() }
            )
        }

        Surface(modifier = Modifier.fillMaxWidth()) {
            Column {
                // 助手选择卡片
                Surface(
                    onClick = onOpenAssistantSelection,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "当前助手",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TooltipText(
                                selectedAssistant?.name ?: "未选择助手",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                onClick = onOpenAssistantSelection
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 引擎选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngineCardCompact(
                        modifier = Modifier.weight(1f),
                        type = EngineType.TEXT_ENGINE,
                        isSelected = viewModel.selectedEngine == EngineType.TEXT_ENGINE,
                        onClick = { viewModel.setEngine(EngineType.TEXT_ENGINE) }
                    )
                    val textCaptureOn = viewModel.permissions.captureMode == CaptureMode.TEXT_ONLY
                    EngineCardCompact(
                        modifier = Modifier.weight(1f),
                        type = EngineType.VISION_ENGINE,
                        isSelected = viewModel.selectedEngine == EngineType.VISION_ENGINE,
                        onClick = { if (!textCaptureOn) viewModel.setEngine(EngineType.VISION_ENGINE) },
                        enabled = !textCaptureOn
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSelectionSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    onNavigateToSettings: () -> Unit,
    sheetState: androidx.compose.material3.SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "选择助手",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    onDismissRequest()
                    onNavigateToSettings()
                }) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("管理")
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(viewModel.assistants.size) { index ->
                    val assistant = viewModel.assistants[index]
                    val isSelected = assistant.id == (viewModel.selectedAssistantId ?: viewModel.assistants.firstOrNull()?.id)

                    Surface(
                        onClick = {
                            viewModel.setAssistant(assistant.id)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.3f
                        )
                        else Color.Transparent,
                        border = if (isSelected) BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TooltipText(
                                assistant.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                onClick = {
                                    viewModel.setAssistant(assistant.id)
                                    onDismissRequest()
                                }
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }

                if (viewModel.assistants.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "暂无助手，请前往设置创建",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EngineCardCompact(
    modifier: Modifier = Modifier,
    type: EngineType,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val bgColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .height(90.dp)
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            borderColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (type == EngineType.TEXT_ENGINE) Icons.Default.TextFields else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    if (type == EngineType.TEXT_ENGINE) "无障碍取字" else "多模态视觉",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }

            if (!enabled) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "无障碍取字模式下不可用",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionSection(viewModel: MainViewModel) {
    val isOverlayEnabled = viewModel.isOverlayPermissionGranted
    val isAccessibilityEnabled = viewModel.isAccessibilityEnabled
    val isShizukuGranted = viewModel.isShizukuPermissionGranted
    val captureMode = viewModel.permissions.captureMode

    val isRunning = viewModel.isServiceRunning

    // 根据截屏模式判断启动按钮是否可用
    val isStartEnabled = when (captureMode) {
        CaptureMode.TEXT_ONLY -> isOverlayEnabled && isAccessibilityEnabled
        CaptureMode.SYSTEM -> isOverlayEnabled
        CaptureMode.SHIZUKU -> isOverlayEnabled && isShizukuGranted
        else -> isOverlayEnabled
    }

    // 服务正在运行时，即使权限被撤销也应允许停止
    val isClickable = isRunning || isStartEnabled

    // 生成按钮禁用原因提示（服务正在运行时不显示禁用提示）
    val disabledHint: String? = when {
        isRunning || isStartEnabled -> null
        !isOverlayEnabled -> "请先授予「显示在其他应用上层」权限"
        captureMode == CaptureMode.TEXT_ONLY -> "无障碍取字模式需要先开启「SolveX 无障碍服务」"
        else -> "请先启动 Shizuku 并授权 SolveX"
    }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceInterval = 500L

    fun safeAction(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastClickTime) > debounceInterval) {
            lastClickTime = currentTime
            action()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(20.dp),
            color = when {
                !isRunning && !isStartEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isRunning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            border = BorderStroke(
                1.dp,
                when {
                    !isRunning && !isStartEnabled -> MaterialTheme.colorScheme.outlineVariant
                    isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isClickable) {
                            Modifier.combinedClickable(
                                onClick = {
                                    safeAction {
                                        if (isRunning) {
                                            viewModel.updateShowStopConfirmationDialog(true)
                                        } else {
                                            viewModel.startService(isQuick = false)
                                        }
                                    }
                                },
                                onLongClick = {
                                    safeAction {
                                        if (!isRunning) {
                                            viewModel.startService(isQuick = true)
                                        }
                                    }
                                }
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRunning) Icons.Default.StopCircle else Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = when {
                            isRunning -> MaterialTheme.colorScheme.error
                            !isStartEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isRunning) {
                            if (viewModel.isServiceInRegularMode) "停止常规解析" else "停止自动解析"
                        } else {
                            "启用解析"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.error
                            !isStartEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp)
        ) {
            val statusText = when {
                isRunning -> {
                    if (viewModel.isServiceInRegularMode) "当前正处于：常规模式 (支持手动裁剪)" else "当前正处于：自动模式 (全屏快速解析)"
                }

                disabledHint != null -> disabledHint
                else -> "提示：点击常规解析，长按自动解析快捷方式"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (disabledHint != null)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionSetupGuideCard(
    viewModel: MainViewModel,
    onAction: () -> Unit,
    onSkip: () -> Unit
) {
    val currentStep = viewModel.currentSetupStep
    val relevantSteps = viewModel.getRelevantSteps()
    val totalSteps = relevantSteps.size
    val stepIndex = if (currentStep == PermissionSetupStep.DONE) totalSteps else {
        val index = relevantSteps.indexOf(currentStep)
        if (index == -1) 1 else index + 1
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (currentStep != PermissionSetupStep.DONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stepIndex.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentStep.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "待配置 · 共 $totalSteps 步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (currentStep.isOptional) {
                        TextButton(onClick = onSkip) {
                            Text("跳过", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                Text(
                    currentStep.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        when (currentStep) {
                            PermissionSetupStep.SHIZUKU -> "立即授权 Shizuku"
                            else -> "前往系统设置"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "准备就绪",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "SolveX 已准备就绪，可以开始使用了",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
