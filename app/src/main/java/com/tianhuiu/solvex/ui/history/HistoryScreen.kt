package com.tianhuiu.solvex.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.mode.UniversalMode
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.utils.DateTimeUtils
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 历史记录列表屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onItemClick: (String) -> Unit,
    autoScroll: Boolean = true,
) {
    val historyItems by viewModel.historyItems.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val storageSize by viewModel.storageSize.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) historyItems
            else historyItems.filter {
                it.title?.contains(searchQuery, ignoreCase = true) == true ||
                        it.query.contains(searchQuery, ignoreCase = true) ||
                        it.result.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    var isInitialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(historyItems.isNotEmpty(), searchQuery) {
        if (historyItems.isNotEmpty()) {
            if (!isInitialScrollDone || searchQuery.isNotEmpty()) {
                listState.scrollToItem(0)
                isInitialScrollDone = true
            }
        }
    }

    val latestItem = historyItems.firstOrNull()
    var prevLatestId by remember { mutableStateOf(latestItem?.id) }
    var prevLatestStatus by remember { mutableStateOf(latestItem?.status) }

    LaunchedEffect(latestItem?.id, latestItem?.status) {
        if (latestItem != null) {
            val isNewItem = latestItem.id != prevLatestId
            val statusChanged = latestItem.status != prevLatestStatus
            val isProcessing =
                latestItem.status == com.tianhuiu.solvex.data.models.AnalysisStatus.PROCESSING
            val isSuccess =
                latestItem.status == com.tianhuiu.solvex.data.models.AnalysisStatus.SUCCESS

            if (isNewItem || (statusChanged && (isProcessing || isSuccess))) {
                if (autoScroll) {
                    listState.animateScrollToItem(0)
                }
            }

            prevLatestId = latestItem.id
            prevLatestStatus = latestItem.status
        }
    }

    // 分页加载逻辑
    LaunchedEffect(listState, searchQuery) {
        if (searchQuery.isBlank()) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collect { lastIndex ->
                    if (lastIndex != null && lastIndex >= historyItems.size - 2) {
                        viewModel.loadMore()
                    }
                }
        }
    }

    var initialLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        initialLoadDone = true
    }
    !initialLoadDone && historyItems.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            val title = if (totalCount > 0) {
                if (totalCount > 99) "历史记录 (99+)" else "历史记录 ($totalCount)"
            } else {
                "历史记录"
            }
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(top = 0.dp),
                actions = {
                    if (totalCount > 0) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空记录")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索栏
            if (totalCount > 0 || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索历史记录") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            if (filteredItems.isEmpty()) {
                if (totalCount == 0 && searchQuery.isEmpty()) {
                    EmptyHistoryState()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到相关记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(filteredItems, key = { _, item -> item.id }) { _, item ->
                        HistoryCard(
                            item = item,
                            onLongClick = { itemToDelete = item },
                            onClick = { onItemClick(item.id) }
                        )
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // 清空确认弹窗
    if (showClearDialog) {
        val sizeStr = FileUtils.formatFileSize(storageSize)
        val totalCount by viewModel.totalCount.collectAsState()
        SolveXConfirmDialog(
            onDismissRequest = { showClearDialog = false },
            onConfirm = {
                viewModel.clearHistory()
                showClearDialog = false
            },
            title = "确认清空",
            message = "确定要删除所有历史记录吗？此操作不可撤销。\n您总共有 $totalCount 条历史记录，将释放约 $sizeStr 空间。",
            confirmText = "确认",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }

    // 单条删除确认弹窗
    itemToDelete?.let { item ->
        SolveXConfirmDialog(
            onDismissRequest = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteItem(item.id)
                itemToDelete = null
            },
            title = "删除记录",
            message = "确定要删除这条记录吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }
}

private fun resolveModeDisplayName(modeId: String): String {
    return if (modeId == UniversalMode.id) UniversalMode.displayName else modeId
}

/**
 * 历史记录卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(item: HistoryItem, onLongClick: () -> Unit, onClick: () -> Unit) {
    val timeStr = DateTimeUtils.formatShort(item.timestamp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧信息区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(item.status)
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = item.title ?: item.query,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.summary != null) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.assistantName ?: "助手",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (item.mode != null) {
                        Text(
                            text = "· ${resolveModeDisplayName(item.mode)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 右侧缩略图
            item.imagePath?.let { path ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    HistoryThumbnail(path)
                }
            }
        }
    }
}

/**
 * 缩略图组件。
 */
@Composable
fun HistoryThumbnail(path: String) {
    val bitmapState = produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateInSampleSize(options, 200, 200)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)
            } catch (_: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 遮罩渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.05f)
                        )
                    )
                )
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.History,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * 无历史记录时的占位状态。
 */
@Composable
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.History,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "暂无历史记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewEmptyHistory() {
    MaterialTheme { EmptyHistoryState() }
}
