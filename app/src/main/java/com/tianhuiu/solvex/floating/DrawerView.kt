package com.tianhuiu.solvex.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AnalysisStatus
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.data.models.ToolCallRecord
import com.tianhuiu.solvex.render.MarkdownParser
import com.tianhuiu.solvex.ui.components.LoadingOverlay
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.ui.components.ToolCallSection
import com.tianhuiu.solvex.ui.components.ToolDetailContent
import com.tianhuiu.solvex.ui.history.DetailSection
import com.tianhuiu.solvex.ui.history.MetadataRow
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.DateTimeUtils
import com.tianhuiu.solvex.utils.NotificationUtils

/**
 * 侧边抽屉视图
 */
@Composable
fun DrawerView(
    item: HistoryItem?,
    showMetadata: Boolean,
    autoScroll: Boolean = true,
    onClose: () -> Unit
) {
    var selectedToolCall by remember { mutableStateOf<ToolCallRecord?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showMetadata) "解析详情" else "实时解析",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (item != null) {
                        val scrollState = rememberScrollState()

                        LaunchedEffect(item.query, item.result, autoScroll) {
                            if (autoScroll) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (showMetadata) {
                                val timeStr = DateTimeUtils.formatFull(item.timestamp)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        )
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            MetadataRow(
                                                icon = Icons.Default.Schedule,
                                                label = "时间",
                                                value = timeStr
                                            )
                                            StatusBadge(item.status)
                                        }
                                        MetadataRow(
                                            icon = Icons.Default.Face,
                                            label = "助手",
                                            value = item.assistantName ?: "默认"
                                        )
                                    }
                                }
                            }

                            val sections = remember(item.result) {
                                MarkdownParser.parse(item.result)
                            }

                            val isQueryPlaceholder =
                                item.query == "正在思考中..." || item.query.isBlank()
                            val isResultPlaceholder = item.result.isBlank() || 
                                (item.result == "正在思考中..." && sections.all { it.title.isEmpty() && it.content.isBlank() })

                            if (item.status == AnalysisStatus.PROCESSING && isQueryPlaceholder) {
                                LoadingState("正在提取内容...")
                            } else {
                                if (!isQueryPlaceholder) {
                                    val isStructured = remember(item.query) {
                                        item.query.contains("{") && item.query.contains("}")
                                    }

                                    DetailSection(
                                        title = if (isStructured) "解析问题" else "提取内容",
                                        content = NotificationUtils.renderStructuredQuestion(item.query),
                                        badgeText = AutomationTools.extractQuestionType(item.query)
                                    )
                                }

                                if (item.status == AnalysisStatus.PROCESSING && isResultPlaceholder) {
                                    LoadingState("AI 正在分析中...")
                                } else if (item.status != AnalysisStatus.CANCELLED) {
                                    sections.forEachIndexed { index, section ->
                                        DetailSection(
                                            title = section.title,
                                            content = section.content,
                                            isPrimary = index == sections.lastIndex
                                        )
                                    }
                                }
                            }

                            // 工具调用记录
                            if (item.toolCalls.isNotEmpty()) {
                                ToolCallSection(
                                    toolCalls = item.toolCalls,
                                    onDetailClick = { selectedToolCall = it }
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    LoadingOverlay(
                        isLoading = item == null,
                        message = "加载解析数据中..."
                    )
                }
            }

            // 替代 Dialog 的 Overlay
            selectedToolCall?.let { call ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedToolCall = null },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp)
                            .clickable(enabled = false) {}, // 防止点击内部导致关闭
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .heightIn(max = 500.dp)
                        ) {
                            Text(
                                text = "工具调用详情",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))

                            ToolDetailContent(
                                call = call,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(Modifier.height(16.dp))
                            TextButton(
                                onClick = { selectedToolCall = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("关闭", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
