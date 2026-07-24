package com.tianhuiu.solvex.ui.settings

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.ui.ConnectivityTestState
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ConnectivityStatusIcon
import com.tianhuiu.solvex.ui.components.GenericSelectionSheet
import com.tianhuiu.solvex.ui.components.PreferredProviderCard
import com.tianhuiu.solvex.ui.components.SettingsSectionTitle
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.ui.components.SortableListItem
import com.tianhuiu.solvex.ui.components.TooltipText
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * 模型提供商管理屏幕。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelSettingsScreen(
    viewModel: MainViewModel,
    onEditProvider: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var providerToDelete by remember { mutableStateOf<ModelProvider?>(null) }
    val providersList = remember { mutableStateListOf<ModelProvider>() }

    LaunchedEffect(viewModel.providers) {
        providersList.clear()
        providersList.addAll(viewModel.providers)
    }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState()
    var showProviderSheet by remember { mutableStateOf(false) }

    if (showProviderSheet) {
        GenericSelectionSheet(
            title = "选择默认提供方",
            subTitle = "决定了在未手动指定模型场景下的优先尝试顺序",
            items = viewModel.providers.map { it.id to it.name },
            selectedId = viewModel.defaultProviderId,
            onItemSelected = { viewModel.updateDefaultProviderId(it) },
            onDismissRequest = { showProviderSheet = false },
            sheetState = sheetState,
            noneLabel = "不设置默认提供方",
            noneSubLabel = "将仅使用手动指定的模型"
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("模型提供商", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditProvider(null) }) {
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 1. 全局首选提供方
                item {
                    val selectedProvider = viewModel.providers.find { it.id == viewModel.defaultProviderId }
                    PreferredProviderCard(
                        label = "全局首选提供方",
                        selectedName = selectedProvider?.name,
                        icon = Icons.Default.Business,
                        onClick = { showProviderSheet = true }
                    )
                }

                // 2. 列表标题
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSectionTitle(text = "已配置提供商列表")
                    }
                }

                // 3. 提供商列表
                itemsIndexed(providersList, key = { _, item -> item.id }) { index, provider ->
                    val testState = viewModel.connectivityTestStates[provider.id]

                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        SortableListItem(
                            index = index,
                            itemCount = providersList.size,
                            isDragging = draggedItemIndex == index,
                            onDragStart = { draggedItemIndex = index },
                            onDragEnd = {
                                draggedItemIndex = null
                                viewModel.updateProviders(providersList.toList())
                            },
                            onSwap = { from, to ->
                                Collections.swap(providersList, from, to)
                                draggedItemIndex = to
                            }
                        ) {
                            ConnectivityStatusIcon(
                                state = testState,
                                onClick = {
                                    scope.launch {
                                        when (val result = viewModel.testConnectivity(provider)) {
                                            is ConnectivityTestState.Success ->
                                                viewModel.showFeedbackDialog(
                                                    title = "连接成功",
                                                    message = "${provider.name}: 连通成功 (${result.modelCount} 个模型)",
                                                    icon = Icons.Default.CheckCircle
                                                )

                                            is ConnectivityTestState.Failure ->
                                                viewModel.showFeedbackDialog(
                                                    title = "连接失败",
                                                    message = "${provider.name}: ${result.message}",
                                                    icon = Icons.Default.Error
                                                )

                                            else -> {}
                                        }
                                    }
                                }
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                TooltipText(
                                    provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    onClick = { onEditProvider(provider.id) }
                                )
                                Text(
                                    provider.type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row {
                                IconButton(onClick = { onEditProvider(provider.id) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = { providerToDelete = provider }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
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
                providerToDelete?.let { viewModel.deleteProvider(it.id) }
                providerToDelete = null
            },
            title = "确认删除",
            message = "确定要删除提供方 \"${providerToDelete?.name}\" 吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }
}

/**
 * 预览已同步模型的对话框。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ModelPreviewDialog(
    provider: ModelProvider,
    isFetching: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filteredModels = remember(searchQuery, provider.availableModels) {
        if (searchQuery.isBlank()) {
            provider.availableModels
        } else {
            provider.availableModels.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    SolveXDialog(
        onDismissRequest = onDismiss,
        title = "可用模型 - ${provider.name}",
        centerTitle = false,
        trailingTitleAction = {
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontWeight = FontWeight.Bold) }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索模型") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (isFetching && provider.availableModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isFetching) "正在加载..." else "无可用模型，请点击刷新图标",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredModels) { model ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "model",
                                                            model
                                                        )
                                                    )
                                                )
                                                com.tianhuiu.solvex.utils.SystemUtils.showToast(
                                                    context,
                                                    "已复制: $model"
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "model",
                                                            model
                                                        )
                                                    )
                                                )
                                                com.tianhuiu.solvex.utils.SystemUtils.showToast(
                                                    context,
                                                    "已复制: $model"
                                                )
                                            }
                                        }
                                    )
                            ) {
                                Text(
                                    text = model,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
