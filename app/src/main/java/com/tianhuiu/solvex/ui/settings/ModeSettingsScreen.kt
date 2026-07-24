package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.DrawerSide
import com.tianhuiu.solvex.mode.UniversalMode
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ModelSelectorItem
import com.tianhuiu.solvex.ui.components.NumberStepper
import com.tianhuiu.solvex.ui.components.SettingBadge
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    // 模式已固定为通用模式
    val mode = UniversalMode
    val config = viewModel.currentModeConfig

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("${mode.displayName}配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                SettingsGroup(title = "模型配置") {
                    val defaultUnset = viewModel.defaultProviderId.isNullOrBlank()
                    
                    ModelSelectorItem(
                        label = "OCR 模型",
                        icon = Icons.Default.TextFields,
                        providers = viewModel.providers,
                        selectedProviderId = config.ocrProviderId,
                        selectedModel = config.ocrModel,
                        type = "ocr",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                UniversalMode.id,
                                config.copy(ocrProviderId = pid, ocrModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId,
                        badge = if (defaultUnset && config.ocrProviderId.isNullOrBlank()) {
                            { SettingBadge(text = "去配置") }
                        } else null
                    )
                    ModelSelectorItem(
                        label = "文本分析模型",
                        icon = Icons.Default.Description,
                        providers = viewModel.providers,
                        selectedProviderId = config.textProviderId,
                        selectedModel = config.textModel,
                        type = "text",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                UniversalMode.id,
                                config.copy(textProviderId = pid, textModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId,
                        badge = if (defaultUnset && config.textProviderId.isNullOrBlank()) {
                            { SettingBadge(text = "去配置") }
                        } else null
                    )
                    ModelSelectorItem(
                        label = "多模态模型",
                        icon = Icons.Default.AutoAwesome,
                        providers = viewModel.providers,
                        selectedProviderId = config.visionProviderId,
                        selectedModel = config.visionModel,
                        type = "vision",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                UniversalMode.id,
                                config.copy(visionProviderId = pid, visionModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId,
                        badge = if (defaultUnset && config.visionProviderId.isNullOrBlank()) {
                            { SettingBadge(text = "去配置") }
                        } else null
                    )
                }
            }

            item {
                SettingsGroup(title = "性能设置") {
                    SettingsItem(
                        label = "响应超时 (秒)",
                        subLabel = "等待首个字符返回的最长时间",
                        icon = Icons.Default.Timer,
                        trailing = {
                            NumberStepper(
                                value = config.firstDeltaTimeoutSeconds,
                                step = 5,
                                onValueChange = { seconds ->
                                    viewModel.updateModeConfig(
                                        UniversalMode.id,
                                        config.copy(firstDeltaTimeoutSeconds = seconds)
                                    )
                                }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "通知与显示") {
                    val isNotificationEnabled = viewModel.isNotificationPermissionGranted

                    SettingsItem(
                        label = "启用通知",
                        subLabel = if (isNotificationEnabled) "识别完成后发送系统通知" else "系统通知权限已禁用，请在设置中开启",
                        icon = Icons.Default.Notifications,
                        enabled = isNotificationEnabled,
                        onClick = {
                            viewModel.updateModeConfig(UniversalMode.id, config.copy(allowNotification = !config.allowNotification))
                        },
                        trailing = {
                            Switch(
                                checked = config.allowNotification && isNotificationEnabled,
                                onCheckedChange = {
                                    viewModel.updateModeConfig(UniversalMode.id, config.copy(allowNotification = it))
                                },
                                enabled = isNotificationEnabled
                            )
                        }
                    )
                    SettingsItem(
                        label = "自动弹出抽屉",
                        subLabel = if (config.autoOpenDrawer) "常规交互：自动弹出侧边抽屉实时展示 AI 思考过程" else "自动交互：保持界面简洁，仅在需要时手动打开结果",
                        icon = Icons.AutoMirrored.Filled.ViewSidebar,
                        onClick = {
                            viewModel.updateModeConfig(UniversalMode.id, config.copy(autoOpenDrawer = !config.autoOpenDrawer))
                        },
                        trailing = {
                            Switch(
                                checked = config.autoOpenDrawer,
                                onCheckedChange = {
                                    viewModel.updateModeConfig(UniversalMode.id, config.copy(autoOpenDrawer = it))
                                }
                            )
                        }
                    )
                    SettingsItem(
                        label = "抽屉弹出位置",
                        subLabel = "选择侧边抽屉的弹出方向",
                        icon = Icons.Default.Dock,
                        enabled = config.autoOpenDrawer,
                        trailing = {
                            Row {
                                DrawerSide.entries.forEach { side ->
                                    FilterChip(
                                        selected = config.drawerSide == side,
                                        onClick = {
                                            viewModel.updateModeConfig(UniversalMode.id, config.copy(drawerSide = side))
                                        },
                                        label = { Text(side.displayName) },
                                        modifier = Modifier.padding(start = 8.dp),
                                        enabled = config.autoOpenDrawer
                                    )
                                }
                            }
                        }
                    )
                    SettingsItem(
                        label = "启用截图裁剪",
                        subLabel = if (config.enableCrop == true) "常规模式：截图后手动框选区域，适合精确解析" else "自动模式：截图后直接全屏解析，适合快速扫一眼",
                        icon = Icons.Default.Crop,
                        onClick = {
                            val current = config.enableCrop ?: mode.shouldCrop
                            viewModel.updateModeConfig(UniversalMode.id, config.copy(enableCrop = !current))
                        },
                        trailing = {
                            Switch(
                                checked = config.enableCrop ?: mode.shouldCrop,
                                onCheckedChange = { enabled ->
                                    viewModel.updateModeConfig(UniversalMode.id, config.copy(enableCrop = enabled))
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
