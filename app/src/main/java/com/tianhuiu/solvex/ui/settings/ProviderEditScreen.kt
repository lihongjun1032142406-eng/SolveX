package com.tianhuiu.solvex.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.ui.ConnectivityTestState
import com.tianhuiu.solvex.ui.GlobalDialogData
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ExposedDropdown
import com.tianhuiu.solvex.ui.components.SettingsSectionTitle
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 模型提供方编辑页面。
 * 用于配置服务商类型、API 地址、密钥及默认模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    providerId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val existingProvider = remember(providerId, viewModel.providers) {
        viewModel.providers.find { it.id == providerId }
    }

    val types = ProviderKind.entries.toTypedArray()
    var type by remember { mutableStateOf(existingProvider?.type ?: types[0]) }
    var name by remember { mutableStateOf(existingProvider?.name ?: "") }
    var url by remember { mutableStateOf(existingProvider?.url ?: "") }
    var apiKey by remember { mutableStateOf(existingProvider?.apiKey ?: "") }
    var defaultOcrModel by remember { mutableStateOf(existingProvider?.defaultOcrModel ?: "") }
    var defaultTextModel by remember { mutableStateOf(existingProvider?.defaultTextModel ?: "") }
    var defaultVisionModel by remember { mutableStateOf(existingProvider?.defaultVisionModel ?: "") }
    var availableModels by remember(existingProvider) { 
        mutableStateOf(existingProvider?.availableModels ?: emptyList()) 
    }

    var apiKeyVisible by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val currentProviderState = ModelProvider(
        id = providerId ?: "",
        type = type,
        name = name,
        url = url,
        apiKey = apiKey,
        availableModels = availableModels,
        defaultOcrModel = defaultOcrModel,
        defaultTextModel = defaultTextModel,
        defaultVisionModel = defaultVisionModel
    )

    val attemptBack = {
        val hasChanged = if (existingProvider == null) {
            name.isNotBlank() || url.isNotBlank() || apiKey.isNotBlank()
        } else {
            type != existingProvider.type ||
                    name != existingProvider.name ||
                    url != existingProvider.url ||
                    apiKey != existingProvider.apiKey ||
                    defaultOcrModel != existingProvider.defaultOcrModel ||
                    defaultTextModel != existingProvider.defaultTextModel ||
                    defaultVisionModel != existingProvider.defaultVisionModel
        }

        if (hasChanged) {
            viewModel.showGlobalDialog(
                GlobalDialogData(
                    title = "放弃修改",
                    message = "当前有未保存的内容，确定要放弃并返回吗？",
                    confirmText = "放弃并返回",
                    onConfirm = { onBack() },
                    isDestructive = true
                )
            )
        } else {
            onBack()
        }
    }

    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (providerId == null) "添加提供方" else "编辑提供方", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (providerId == null) {
                                viewModel.addProvider(currentProviderState.copy(id = UUID.randomUUID().toString()))
                            } else {
                                viewModel.updateProvider(currentProviderState.copy(id = providerId))
                            }
                            onBack()
                        },
                        enabled = name.isNotBlank() && url.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 连接配置
            ProviderSection(title = "连接配置", icon = Icons.Default.Link) {
                ExposedDropdown(
                    label = "服务商类型",
                    selectedOption = type.displayName,
                    options = types,
                    optionLabel = { it.displayName },
                    onOptionSelected = { type = it }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("提供方名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例如：DeepSeek、MyOpenAI") },
                    leadingIcon = { Icon(Icons.Default.Business, null) },
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("API 地址 (Endpoint)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://api.openai.com") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API 密钥 (Key)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (apiKeyVisible) "隐藏" else "显示"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 模型设置
            ProviderSection(
                title = "模型设置", 
                icon = Icons.Default.SmartToy,
                trailing = {
                    val testState = viewModel.connectivityTestStates[providerId ?: ""]
                    val isFetching = testState is ConnectivityTestState.Testing

                    TextButton(
                        onClick = {
                            scope.launch {
                                when (val result = viewModel.testConnectivity(currentProviderState)) {
                                    is ConnectivityTestState.Success -> {
                                        val models = viewModel.fetchModelsForProvider(currentProviderState)
                                        if (models.isNotEmpty()) availableModels = models
                                        viewModel.showFeedbackDialog(
                                            title = "同步成功",
                                            message = "成功同步 ${result.modelCount} 个模型",
                                            icon = Icons.Default.SmartToy
                                        )
                                    }
                                    is ConnectivityTestState.Failure -> {
                                        viewModel.showFeedbackDialog(
                                            title = "获取失败",
                                            message = result.message,
                                            icon = Icons.Default.Save
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        },
                        enabled = !isFetching && url.isNotBlank()
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFetching) "正在获取..." else "获取列表")
                    }
                }
            ) {
                Text(
                    "留空则使用服务商默认逻辑。点击上方“获取列表”可同步可用模型。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = defaultTextModel,
                    onValueChange = { defaultTextModel = it },
                    label = { Text("默认文本模型 (Text)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("gpt-4o-mini") },
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = defaultVisionModel,
                    onValueChange = { defaultVisionModel = it },
                    label = { Text("默认视觉模型 (Vision)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("gpt-4o") },
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = defaultOcrModel,
                    onValueChange = { defaultOcrModel = it },
                    label = { Text("默认 OCR 模型 (OCR)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("gpt-4o-mini") },
                    shape = RoundedCornerShape(12.dp)
                )

                Surface(
                    onClick = { showPreviewDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "查看已同步模型",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "当前已缓存 ${availableModels.size} 个可用模型",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPreviewDialog) {
        val testState = viewModel.connectivityTestStates[providerId ?: ""]
        ModelPreviewDialog(
            provider = currentProviderState,
            isFetching = testState is ConnectivityTestState.Testing,
            onRefresh = {
                scope.launch {
                    when (val result = viewModel.testConnectivity(currentProviderState)) {
                        is ConnectivityTestState.Success -> {
                            val models = viewModel.fetchModelsForProvider(currentProviderState)
                            if (models.isNotEmpty()) availableModels = models
                        }
                        else -> {}
                    }
                }
            },
            onDismiss = { showPreviewDialog = false }
        )
    }
}

/**
 * 编辑页面的段落容器。
 */
@Composable
fun ProviderSection(
    title: String,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                SettingsSectionTitle(text = title)
            }
            trailing?.invoke()
        }
        content()
    }
}
