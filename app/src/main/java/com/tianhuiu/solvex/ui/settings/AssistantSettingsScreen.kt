package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.GenericSelectionSheet
import com.tianhuiu.solvex.ui.components.PreferredProviderCard
import com.tianhuiu.solvex.ui.components.SettingsInfoBanner
import com.tianhuiu.solvex.ui.components.SettingsSectionTitle
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SortableListItem
import com.tianhuiu.solvex.ui.components.TooltipText
import java.util.Collections

/**
 * 智能助手设置屏幕。
 * 管理助手的排序、删除、编辑以及全局首选助手的选择。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssistantSettingsScreen(
    viewModel: MainViewModel,
    onEditAssistant: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var assistantIdToDelete by remember { mutableStateOf<String?>(null) }
    val assistantsList = remember { mutableStateListOf<AssistantConfig>() }

    LaunchedEffect(viewModel.assistants) {
        assistantsList.clear()
        assistantsList.addAll(viewModel.assistants)
    }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showAssistantSheet by remember { mutableStateOf(false) }

    if (showAssistantSheet) {
        GenericSelectionSheet(
            title = "选择默认助手",
            subTitle = "决定了在未手动指定场景下的默认处理身份",
            items = viewModel.assistants.map { it.id to it.name },
            selectedId = viewModel.selectedAssistantId,
            onItemSelected = { viewModel.setAssistant(it) },
            onDismissRequest = { showAssistantSheet = false },
            sheetState = sheetState,
            itemIcon = Icons.Default.SupportAgent,
            noneLabel = "自动选择",
            noneSubLabel = "使用列表首项作为默认助手"
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("智能助手", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditAssistant(null) }) {
                Icon(Icons.Default.Add, contentDescription = "添加助手")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. 提示信息
            SettingsInfoBanner(text = "长按右侧图标拖动排序，排序决定首页抽屉展示顺序")

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 2. 全局首选助手
                item {
                    val selectedAssistant = viewModel.assistants.find { it.id == viewModel.selectedAssistantId }
                    PreferredProviderCard(
                        label = "当前首选助手",
                        selectedName = selectedAssistant?.name,
                        icon = Icons.Default.SupportAgent,
                        onClick = { showAssistantSheet = true }
                    )
                }

                // 3. 列表标题
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSectionTitle(text = "已配置助手列表")
                    }
                }

                // 4. 助手列表
                itemsIndexed(assistantsList, key = { _, item -> item.id }) { index, assistant ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        SortableListItem(
                            index = index,
                            itemCount = assistantsList.size,
                            isDragging = draggedItemIndex == index,
                            onDragStart = { draggedItemIndex = index },
                            onDragEnd = {
                                draggedItemIndex = null
                                viewModel.updateAssistants(assistantsList.toList())
                            },
                            onSwap = { from, to ->
                                Collections.swap(assistantsList, from, to)
                                draggedItemIndex = to
                            }
                        ) {
                            TooltipText(
                                assistant.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                onClick = { onEditAssistant(assistant.id) }
                            )

                            Row {
                                IconButton(onClick = { onEditAssistant(assistant.id) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = { assistantIdToDelete = assistant.id }) {
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

    if (assistantIdToDelete != null) {
        val assistant = viewModel.assistants.find { it.id == assistantIdToDelete }
        SolveXConfirmDialog(
            onDismissRequest = { assistantIdToDelete = null },
            onConfirm = {
                assistantIdToDelete?.let { viewModel.deleteAssistant(it) }
                assistantIdToDelete = null
            },
            title = "确认删除",
            message = "确定要删除助手 \"${assistant?.name}\" 吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }
}
