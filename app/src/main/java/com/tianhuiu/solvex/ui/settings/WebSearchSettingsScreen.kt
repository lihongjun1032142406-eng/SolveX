package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.SearchProviderConfig
import com.tianhuiu.solvex.data.models.SearchProviderKind
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ConnectivityStatusIcon
import com.tianhuiu.solvex.ui.components.ExposedDropdown
import com.tianhuiu.solvex.ui.components.GenericSelectionSheet
import com.tianhuiu.solvex.ui.components.NumberStepper
import com.tianhuiu.solvex.ui.components.PreferredProviderCard
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem
import com.tianhuiu.solvex.ui.components.SettingsSectionTitle
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.ui.components.SortableListItem
import com.tianhuiu.solvex.ui.components.TooltipText
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

/**
 * 联网搜索配置屏幕。
 * 允许用户管理搜索提供商、设置全局首选引擎以及配置 HTTPS 证书信任等高级选项。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WebSearchSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settings = viewModel.webSearchSettings
    val providersList = remember { mutableStateListOf<SearchProviderConfig>() }
    var providerToDelete by remember { mutableStateOf<SearchProviderConfig?>(null) }
    var providerToEdit by remember { mutableStateOf<SearchProviderConfig?>(null) }

    LaunchedEffect(settings.providers) {
        providersList.clear()
        providersList.addAll(settings.providers)
    }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showProviderSheet by remember { mutableStateOf(false) }

    if (showProviderSheet) {
        GenericSelectionSheet(
            title = "选择默认搜索引擎",
            subTitle = "决定了在未手动指定搜索引擎场景下的默认处理引擎",
            items = settings.providers.map { it.id to it.name },
            selectedId = settings.selectedProviderId,
            onItemSelected = { viewModel.updateSelectedSearchProvider(it) },
            onDismissRequest = { showProviderSheet = false },
            sheetState = sheetState,
            itemIcon = Icons.Default.Language,
            noneLabel = "不设置首选引擎",
            noneSubLabel = "将仅使用手动指定的配置"
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("联网配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                providerToEdit = SearchProviderConfig(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    kind = SearchProviderKind.TAVILY,
                    baseUrl = "https://api.tavily.com/search"
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加提供方")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 1. 通用设置组
                item {
                    SettingsGroup(title = "通用设置") {
                        SettingsItem(
                            label = "启用联网搜索",
                            subLabel = "允许模型调用 web_search 工具进行联网查询获取实时信息",
                            icon = Icons.Default.Cloud,
                            trailing = {
                                Switch(
                                    checked = settings.enabled,
                                    onCheckedChange = { viewModel.updateWebSearchSettings(settings.copy(enabled = it)) }
                                )
                            }
                        )

                        SettingsItem(
                            label = "信任所有 HTTPS 证书",
                            subLabel = "绕过系统 SSL 验证，过期或域名不匹配的 HTTPS 服务（存在安全风险）",
                            icon = Icons.Default.Security,
                            trailing = {
                                Switch(
                                    checked = viewModel.trustAllCertificates,
                                    onCheckedChange = { viewModel.updateTrustAllCertificates(it) }
                                )
                            }
                        )

                        val isEnabled = settings.enabled
                        SettingsItem(
                            label = "最大结果数量",
                            subLabel = "单次搜索参考的网页数量",
                            icon = Icons.AutoMirrored.Filled.List,
                            enabled = isEnabled,
                            trailing = {
                                NumberStepper(
                                    value = settings.maxResults.toLong(),
                                    onValueChange = { viewModel.updateWebSearchSettings(settings.copy(maxResults = it.toInt())) },
                                    min = 1,
                                    max = 10,
                                    enabled = isEnabled
                                )
                            }
                        )
                    }
                }

                // 2. 全局首选搜索引擎
                item {
                    val selectedProvider = settings.providers.find { it.id == settings.selectedProviderId }
                    PreferredProviderCard(
                        label = "全局首选搜索引擎",
                        selectedName = selectedProvider?.name,
                        icon = Icons.Default.Language,
                        onClick = { showProviderSheet = true },
                        enabled = settings.enabled
                    )
                }

                // 3. 提供商列表标题
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSectionTitle(
                            text = "搜索提供商列表",
                            enabled = settings.enabled
                        )
                    }
                }

                // 5. 提供商列表项
                itemsIndexed(providersList, key = { _, item -> item.id }) { index, provider ->
                    val testState = viewModel.connectivityTestStates[provider.id]
                    val isEnabled = settings.enabled
                    val isSupported = provider.kind == SearchProviderKind.TAVILY || provider.kind == SearchProviderKind.CUSTOM

                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        SortableListItem(
                            index = index,
                            itemCount = providersList.size,
                            isDragging = draggedItemIndex == index,
                            onDragStart = { draggedItemIndex = index },
                            onDragEnd = {
                                draggedItemIndex = null
                                viewModel.updateSearchProviders(providersList.toList())
                            },
                            onSwap = { from, to ->
                                Collections.swap(providersList, from, to)
                                draggedItemIndex = to
                            },
                            enabled = isEnabled
                        ) {
                            ConnectivityStatusIcon(
                                state = testState,
                                onClick = {
                                    scope.launch { viewModel.testSearchConnectivity(provider) }
                                },
                                enabled = isEnabled,
                                supported = isSupported
                            )

                            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                TooltipText(
                                    provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    onClick = { if (isEnabled) providerToEdit = provider },
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    provider.kind.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }

                            Row {
                                IconButton(onClick = { providerToEdit = provider }, enabled = isEnabled) {
                                    Icon(Icons.Default.Edit, null, tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { providerToDelete = provider }, enabled = isEnabled) {
                                    Icon(Icons.Default.Delete, null, tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (providerToDelete != null) {
        SolveXConfirmDialog(
            onDismissRequest = { providerToDelete = null },
            onConfirm = {
                providerToDelete?.let { viewModel.deleteSearchProvider(it.id) }
                providerToDelete = null
            },
            title = "确认删除",
            message = "确定要删除搜索引擎 \"${providerToDelete?.name}\" 吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }

    if (providerToEdit != null) {
        SearchProviderEditDialog(
            provider = providerToEdit!!,
            onDismiss = { providerToEdit = null },
            onSave = { updated ->
                if (settings.providers.any { it.id == updated.id }) {
                    viewModel.updateSearchProvider(updated)
                } else {
                    viewModel.addSearchProvider(updated)
                }
                providerToEdit = null
            }
        )
    }
}

@Composable
fun SearchProviderEditDialog(
    provider: SearchProviderConfig,
    onDismiss: () -> Unit,
    onSave: (SearchProviderConfig) -> Unit,
) {
    var name by remember { mutableStateOf(provider.name) }
    var kind by remember { mutableStateOf(provider.kind) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    fun updateDefaultUrl(newKind: SearchProviderKind) {
        baseUrl = when (newKind) {
            SearchProviderKind.TAVILY -> "https://api.tavily.com/search"
            SearchProviderKind.SERPER -> "https://google.serper.dev/search"
            SearchProviderKind.BRAVE -> "https://api.search.brave.com/res/v1/web/search"
            SearchProviderKind.CUSTOM -> baseUrl
        }
    }

    SolveXDialog(
        onDismissRequest = onDismiss,
        title = if (provider.name.isBlank()) "添加提供商" else "编辑提供商",
        confirmButton = {
            TextButton(
                onClick = { onSave(SearchProviderConfig(provider.id, name, kind, baseUrl, apiKey)) },
                enabled = name.isNotBlank() && apiKey.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdown(
                label = "服务商类型",
                selectedOption = kind.displayName,
                options = SearchProviderKind.entries.toTypedArray(),
                optionLabel = { it.displayName },
                onOptionSelected = {
                    kind = it
                    updateDefaultUrl(it)
                    if (name.isBlank()) name = it.displayName
                }
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("显示名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Business, null) },
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址 (Endpoint)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
    }
}
