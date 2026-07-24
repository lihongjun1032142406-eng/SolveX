package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tianhuiu.solvex.mode.UniversalMode
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.UpdateViewModel
import com.tianhuiu.solvex.ui.components.SettingBadge
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

/**
 * 设置主页面：提供各类配置入口的列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel,
    updateViewModel: UpdateViewModel
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "设置",
                        fontWeight = FontWeight.Bold
                    )
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
                SettingsGroup(title = "核心配置") {
                    val config = viewModel.currentModeConfig
                    val isUnconfigured = config.ocrProviderId.isNullOrBlank() && 
                                       config.textProviderId.isNullOrBlank() && 
                                       config.visionProviderId.isNullOrBlank() &&
                                       viewModel.defaultProviderId.isNullOrBlank()

                    SettingsItem(
                        label = "模式设置",
                        subLabel = "配置 AI 解析引擎、提示词与核心行为",
                        icon = UniversalMode.icon,
                        onClick = { navController.navigate("settings/mode") },
                        badge = if (isUnconfigured) {
                            { SettingBadge(text = "去配置") }
                        } else null
                    )
                    SettingsItem(
                        label = "权限设置",
                        subLabel = "核心运行权限与后台稳定性配置",
                        icon = Icons.Default.Security,
                        onClick = { navController.navigate("settings/permissions") },
                        badge = if (!viewModel.isAllPermissionsReady) {
                            { SettingBadge(text = "去授权") }
                        } else null
                    )
                    SettingsItem(
                        label = "联网配置",
                        subLabel = "配置实时联网搜索与数据增强源",
                        icon = Icons.Default.Cloud,
                        onClick = { navController.navigate("settings/web_search") }
                    )
                }
            }
            item {
                SettingsGroup(title = "模型管理") {
                    SettingsItem(
                        label = "模型提供商",
                        subLabel = "管理 LLM 接口及 API 访问密钥",
                        icon = Icons.Default.AutoAwesome,
                        onClick = { navController.navigate("settings/models") },
                        badge = if (viewModel.providers.all { it.apiKey.isBlank() }) {
                            { SettingBadge(text = "去添加") }
                        } else null
                    )
                    SettingsItem(
                        label = "智能助手",
                        subLabel = "配置角色提示词与展示优先级",
                        icon = Icons.Default.SupportAgent,
                        onClick = { navController.navigate("settings/assistants") }
                    )
                }
            }
            item {
                SettingsGroup(title = "系统与环境") {
                    SettingsItem(
                        label = "通用设置",
                        subLabel = "调整 UI 偏好与系统交互方式",
                        icon = Icons.Default.AppSettingsAlt,
                        onClick = { navController.navigate("settings/general") }
                    )
                    SettingsItem(
                        label = "悬浮球外观",
                        subLabel = "自定义悬浮球大小、透明度及功能菜单",
                        icon = Icons.Default.Visibility,
                        onClick = { navController.navigate("settings/appearance") }
                    )
                    SettingsItem(
                        label = "数据管理",
                        subLabel = "配置文件与历史记录的导入导出",
                        icon = Icons.Default.Storage,
                        onClick = { navController.navigate("settings/io") }
                    )
                }
            }
            item {
                SettingsGroup(title = "关于") {
                    SettingsItem(
                        label = "功能指南",
                        subLabel = "快速了解 SolveX 的使用方法",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        onClick = { navController.navigate("settings/tutorial") }
                    )
                    SettingsItem(
                        label = "关于软件",
                        subLabel = "检查更新、版本信息与项目致谢",
                        icon = Icons.Default.Info,
                        onClick = { navController.navigate("settings/about") },
                        badge = if (updateViewModel.updateInfo != null) {
                            { SettingBadge() }
                        } else null
                    )
                }
            }
        }
    }
}
