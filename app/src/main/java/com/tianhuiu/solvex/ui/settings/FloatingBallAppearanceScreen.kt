package com.tianhuiu.solvex.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.FloatingBallAppearance
import com.tianhuiu.solvex.floating.FloatingBallMenu
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.tianhuiu.solvex.ui.components.SettingsItem as BaseSettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingBallAppearanceScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var draftAppearance by remember(viewModel.permissions.appearance) {
        mutableStateOf(viewModel.permissions.appearance)
    }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("悬浮球外观", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text("重置")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsGroup(title = "预览与调节") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BallStatePreview(draftAppearance)
                    }

                    SliderSettingRow(
                        label = "悬浮球直径",
                        value = draftAppearance.diameterDp,
                        range = 24f..64f,
                        onValueChange = { draftAppearance = draftAppearance.copy(diameterDp = it) },
                        onFinished = { viewModel.updateBallAppearance(draftAppearance) }
                    )
                    SliderSettingRow(
                        label = "文本字号",
                        value = draftAppearance.textSizeSp,
                        range = 8f..24f,
                        onValueChange = { draftAppearance = draftAppearance.copy(textSizeSp = it) },
                        onFinished = { viewModel.updateBallAppearance(draftAppearance) }
                    )
                    SliderSettingRow(
                        label = "整体透明度",
                        value = draftAppearance.overallOpacity * 100f,
                        range = 10f..100f,
                        onValueChange = {
                            draftAppearance = draftAppearance.copy(overallOpacity = it / 100f)
                        },
                        onFinished = { viewModel.updateBallAppearance(draftAppearance) }
                    )
                    SliderSettingRow(
                        label = "内容透明度",
                        value = draftAppearance.contentOpacity * 100f,
                        range = 10f..100f,
                        onValueChange = {
                            draftAppearance = draftAppearance.copy(contentOpacity = it / 100f)
                        },
                        onFinished = { viewModel.updateBallAppearance(draftAppearance) }
                    )
                }
            }

            // 模块二：扇形菜单
            item {
                SettingsGroup(title = "扇形菜单控制") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MenuAnimationPreview(draftAppearance)
                    }

                    BaseSettingsItem(
                        label = "启用扇形菜单",
                        subLabel = "闲置状态下双击悬浮球展开快捷菜单",
                        trailing = {
                            Switch(
                                checked = draftAppearance.enableMenu,
                                onCheckedChange = {
                                    val updated = draftAppearance.copy(enableMenu = it)
                                    draftAppearance = updated
                                    viewModel.updateBallAppearance(updated)
                                }
                            )
                        }
                    )

                    val menuEnabled = draftAppearance.enableMenu
                    val subItemAlpha = if (menuEnabled) 1f else 0.38f

                    Column(modifier = Modifier.alpha(subItemAlpha)) {
                        SliderSettingRow(
                            label = "加载动画直径",
                            value = draftAppearance.spinnerDiameterDp,
                            range = 30f..80f,
                            enabled = menuEnabled,
                            onValueChange = {
                                draftAppearance = draftAppearance.copy(spinnerDiameterDp = it)
                            },
                            onFinished = { viewModel.updateBallAppearance(draftAppearance) }
                        )

                        val menuItems = listOf(
                            Triple(
                                "engine",
                                "显示引擎切换",
                                "在视觉分析与纯文本模式间快速切换识别引擎"
                            ),
                            Triple(
                                "search",
                                "显示联网开关",
                                "解析过程中实时从互联网搜索相关参考资料"
                            ),
                            Triple(
                                "settings",
                                "显示打开设置",
                                "在悬浮菜单中直接跳转至应用主设置页"
                            ),
                            Triple("baidu", "显示打开百度", "快速启动网页手动进行搜索")
                        )
                        menuItems.forEach { (id, label, subtitle) ->
                            BaseSettingsItem(
                                label = label,
                                subLabel = subtitle,
                                enabled = menuEnabled,
                                trailing = {
                                    Switch(
                                        checked = id in draftAppearance.enabledMenuItems,
                                        onCheckedChange = { checked ->
                                            if (menuEnabled) {
                                                val newItems =
                                                    draftAppearance.enabledMenuItems.toMutableSet()
                                                if (checked) newItems.add(id) else newItems.remove(
                                                    id
                                                )
                                                val updated =
                                                    draftAppearance.copy(enabledMenuItems = newItems)
                                                draftAppearance = updated
                                                viewModel.updateBallAppearance(updated)
                                            }
                                        },
                                        enabled = menuEnabled
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showResetConfirm) {
        SolveXConfirmDialog(
            onDismissRequest = { showResetConfirm = false },
            onConfirm = {
                val reset = FloatingBallAppearance()
                draftAppearance = reset
                viewModel.updateBallAppearance(reset)
                showResetConfirm = false
            },
            title = "重置确认",
            message = "确定要将所有悬浮球外观设置恢复为默认值吗？此操作不可撤销。",
            confirmText = "确认重置",
            dismissText = "取消",
            isDestructive = true,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${value.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BallStatePreview(appearance: FloatingBallAppearance) {
    var previewState by remember { mutableStateOf(PreviewState.Idle) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 顶部球体容器
            Box(
                modifier = Modifier
                    .size(140.dp, 80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                PreviewBallContent(appearance, previewState)
            }

            // 底部横向图标切换器
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewState.entries.filter { it != PreviewState.Menu }.forEach { state ->
                    val isSelected = previewState == state
                    Surface(
                        onClick = { previewState = state },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = state.icon,
                                contentDescription = state.label,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAnimationPreview(appearance: FloatingBallAppearance) {
    val density = LocalDensity.current
    var menuVisible by remember { mutableStateOf(false) }
    var closeSignal by remember { mutableIntStateOf(0) }
    var cycleKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            closeSignal = 0
            cycleKey += 1
            menuVisible = false
            delay(1200)
            menuVisible = true
            delay(2800)
            closeSignal += 1
            delay(1000)
            menuVisible = false
            delay(1500)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.TopStart
        ) {
            val viewportWidth = maxWidth.toPx(density)
            val viewportHeight = maxHeight.toPx(density)
            val anchorX = (viewportWidth * 0.22f).roundToInt()
            val anchorY = (viewportHeight * 0.72f).roundToInt()

            if (menuVisible) {
                key(cycleKey) {
                    FloatingBallMenu(
                        anchorX = anchorX,
                        anchorY = anchorY,
                        enabledItems = appearance.enabledMenuItems,
                        activeItems = emptySet(),
                        onItemClick = {},
                        onDismiss = {},
                        modifier = Modifier.matchParentSize(),
                        viewportWidthPx = viewportWidth,
                        viewportHeightPx = viewportHeight,
                        closeSignal = closeSignal
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .offset(
                        x = with(density) { anchorX.toDp() } - (appearance.diameterDp / 2).dp,
                        y = with(density) { anchorY.toDp() } - (appearance.diameterDp / 2).dp
                    )
                    .size(appearance.diameterDp.dp)
                    .alpha(appearance.overallOpacity),
                shape = CircleShape,
                color = Color(0xFF2196F3)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = appearance.contentOpacity),
                        modifier = Modifier.size((appearance.diameterDp * 0.5f).dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.unit.Dp.toPx(density: Density): Float {
    return this.value * density.density
}

@Composable
private fun PreviewBallContent(appearance: FloatingBallAppearance, state: PreviewState) {
    val backgroundColor = when (state) {
        PreviewState.Idle -> Color(0xFF2196F3)
        PreviewState.Running -> Color(0xFF673AB7)
        PreviewState.Success -> Color(0xFF4CAF50)
        PreviewState.Hidden -> Color(0xFF2196F3)
        else -> Color.Gray
    }

    val rotation: Float
    if (state == PreviewState.Running) {
        val transition = rememberInfiniteTransition(label = "preview_rot")
        rotation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rot"
        ).value
    } else {
        rotation = 0f
    }

    val isHidden = state == PreviewState.Hidden
    val ballWidth = if (isHidden) appearance.diameterDp * 0.35f else appearance.diameterDp

    Box(
        modifier = Modifier
            .size(width = ballWidth.dp, height = appearance.diameterDp.dp)
            .clip(
                if (isHidden) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                else CircleShape
            )
            .background(backgroundColor.copy(alpha = appearance.overallOpacity)),
        contentAlignment = Alignment.Center
    ) {
        if (!isHidden) {
            Box(modifier = Modifier.alpha(appearance.contentOpacity)) {
                when (state) {
                    PreviewState.Idle -> {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }

                    PreviewState.Running -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(appearance.spinnerDiameterDp.dp),
                                color = Color.White,
                                strokeWidth = 3.dp,
                                trackColor = Color.Transparent
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size((appearance.diameterDp * 0.5f).dp)
                                    .rotate(rotation)
                            )
                        }
                    }

                    PreviewState.Success -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

private enum class PreviewState(val label: String, val icon: ImageVector) {
    Idle("闲置", Icons.Default.SmartToy),
    Running("运行", Icons.Default.Refresh),
    Success("完成", Icons.Default.Check),
    Hidden("隐藏", Icons.Default.Texture),
    Menu("菜单", Icons.Default.Refresh)
}
