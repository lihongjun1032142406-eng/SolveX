package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.ui.components.TutorialContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var pendingMode by remember { mutableStateOf<String?>(null) }
    var showAccessibilityConfirm by remember { mutableStateOf(false) }
    var showStealthModeWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("通用设置", fontWeight = FontWeight.Bold) },
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
                SettingsGroup(title = "录制与取字方式") {
                    SettingsItem(
                        label = "系统屏幕录制",
                        subLabel = "通过系统录屏权限建立截图会话，兼容性最强",
                        icon = Icons.Default.PhoneAndroid,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.SYSTEM,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.SYSTEM
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.SYSTEM
                                            )
                                        )
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.SYSTEM
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.SYSTEM))
                            }
                        }
                    )
                    SettingsItem(
                        label = "无障碍取字",
                        subLabel = "框选屏幕区域提取文本内容，不产生截图。利用无障碍服务能力实现。",
                        icon = Icons.Default.Description,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.TEXT_ONLY,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.TEXT_ONLY
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.TEXT_ONLY
                                            )
                                        )
                                        if (!viewModel.isAccessibilityEnabled) {
                                            showAccessibilityConfirm = true
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.TEXT_ONLY
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.TEXT_ONLY))
                                if (!viewModel.isAccessibilityEnabled) {
                                    showAccessibilityConfirm = true
                                }
                            }
                        }
                    )
                    SettingsItem(
                        label = "Shizuku ADB",
                        subLabel = "通过 Shizuku 授权后调用高级系统接口截图，无需每次点击授权。",
                        icon = Icons.Default.Terminal,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.SHIZUKU,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.SHIZUKU
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.SHIZUKU
                                            )
                                        )
                                        if (!viewModel.isShizukuPermissionGranted) {
                                            viewModel.requestShizukuPermission()
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.SHIZUKU
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.SHIZUKU))
                                if (!viewModel.isShizukuPermissionGranted) {
                                    viewModel.requestShizukuPermission()
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "隐私保护") {
                    SettingsItem(
                        label = "防截屏录屏",
                        subLabel = "开启后应用的内容将无法被系统或其他应用截取。这是系统级底层保护。",
                        icon = Icons.Default.Visibility,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableScreenProtection,
                                onCheckedChange = {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableScreenProtection = it
                                        )
                                    )
                                }
                            )
                        },
                        onClick = {
                            viewModel.updatePermissions(
                                viewModel.permissions.copy(
                                    enableScreenProtection = !viewModel.permissions.enableScreenProtection
                                )
                            )
                        }
                    )
                    SettingsItem(
                        label = "隐匿模式",
                        subLabel = if (viewModel.isShizukuPermissionGranted) {
                            "监测到受保护应用时自动同步开启自身隐私保护，并从多任务列表中隐藏。"
                        } else {
                            "隐匿模式使用需要激活 Shizuku 后使用。"
                        },
                        icon = Icons.Default.Warning,
                        enabled = viewModel.isShizukuPermissionGranted,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableStealthMode && viewModel.isShizukuPermissionGranted,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showStealthModeWarning = true
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                enableStealthMode = false
                                            )
                                        )
                                    }
                                },
                                enabled = viewModel.isShizukuPermissionGranted
                            )
                        },
                        onClick = {
                            if (viewModel.isShizukuPermissionGranted) {
                                if (!viewModel.permissions.enableStealthMode) {
                                    showStealthModeWarning = true
                                } else {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableStealthMode = false
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "显示设置") {
                    SettingsItem(
                        label = "跟随内容输出滚动",
                        subLabel = "开启后将自动滚动跟随最新内容",
                        icon = Icons.Default.Swipe,
                        trailing = {
                            Switch(
                                checked = viewModel.autoScrollContent,
                                onCheckedChange = {
                                    viewModel.updateAutoScrollContent(it)
                                }
                            )
                        },
                        onClick = {
                            viewModel.updateAutoScrollContent(!viewModel.autoScrollContent)
                        }
                    )
                    SettingsItem(
                        label = "隐藏悬浮球",
                        subLabel = "开启后悬浮球无操作时将自动隐藏到屏幕边缘",
                        icon = Icons.Default.Visibility,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableAutoHideBall,
                                onCheckedChange = {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableAutoHideBall = it
                                        )
                                    )
                                }
                            )
                        },
                        onClick = {
                            viewModel.updatePermissions(
                                viewModel.permissions.copy(
                                    enableAutoHideBall = !viewModel.permissions.enableAutoHideBall
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    if (showAccessibilityConfirm) {
        SolveXConfirmDialog(
            onDismissRequest = { showAccessibilityConfirm = false },
            onConfirm = {
                showAccessibilityConfirm = false
                viewModel.requestAccessibilityPermission()
            },
            title = "授权无障碍服务",
            message = "无障碍取字模式需要启用“SolveX 截屏助手”服务。点击确认将前往系统设置页，请在“已安装的服务”中找到并开启。",
            confirmText = "前往设置",
            dismissText = "取消",
            icon = Icons.Default.AccessibilityNew
        )
    }

    if (showStealthModeWarning) {
        SolveXDialog(
            onDismissRequest = { showStealthModeWarning = false },
            confirmButton = {
                TextButton(onClick = {
                    showStealthModeWarning = false
                    viewModel.updatePermissions(
                        viewModel.permissions.copy(enableStealthMode = true)
                    )
                }) {
                    Text("确认开启", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStealthModeWarning = false }) {
                    Text("取消")
                }
            },
            title = "开启隐匿模式",
            icon = Icons.Default.Warning
        ) {
            TutorialContent(
                markdown = """
                    开启后 SolveX 会实时监测当前屏幕。当探测到受保护的隐私应用时，将执行**防截屏保护**功能。
                    > **提示**：隐匿模式会增加设备耗电。若遇系统卡顿请及时关闭。
                """.trimIndent(),
                modifier = Modifier.heightIn(max = 160.dp)
            )
        }
    }

    if (pendingMode != null) {
        SolveXConfirmDialog(
            onDismissRequest = { pendingMode = null },
            onConfirm = { pendingMode = null },
            title = "禁止切换方式",
            message = "当前服务正在运行中，为保障系统稳定性，无法直接更改录制方式。请先返回首页手动点击“停止服务”，然后再进行方式切换。",
            confirmText = "知道了",
            dismissText = null,
            icon = Icons.Default.Warning
        )
    }
}