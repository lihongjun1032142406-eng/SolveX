package com.tianhuiu.solvex.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AnalysisStatus
import com.tianhuiu.solvex.data.models.ToolCallRecord
import com.tianhuiu.solvex.network.search.SearchHit
import kotlinx.serialization.json.Json

@Composable
fun StatusBadge(
    status: AnalysisStatus,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        AnalysisStatus.SUCCESS -> Color(0xFFE8F5E9)
        AnalysisStatus.FAILURE -> Color(0xFFFFEBEE)
        AnalysisStatus.CANCELLED -> Color(0xFFF5F5F5)
        AnalysisStatus.PROCESSING -> Color(0xFFE3F2FD)
    }
    val contentColor = when (status) {
        AnalysisStatus.SUCCESS -> Color(0xFF2E7D32)
        AnalysisStatus.FAILURE -> Color(0xFFC62828)
        AnalysisStatus.CANCELLED -> Color(0xFF616161)
        AnalysisStatus.PROCESSING -> Color(0xFF1565C0)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SolveXDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    trailingTitleAction: @Composable (() -> Unit)? = null,
    centerTitle: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            if (centerTitle) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (trailingTitleAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            trailingTitleAction()
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    trailingTitleAction?.invoke()
                }
            }
        },
        text = content,
        confirmButton = { confirmButton?.invoke() },
        dismissButton = { dismissButton?.invoke() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun SolveXConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "确认",
    dismissText: String? = "取消",
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
) {
    SolveXDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = icon,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors()
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismissRequest) {
                    Text(dismissText)
                }
            }
        } else null
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "正在加载中",
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TooltipText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    onClick: (() -> Unit)? = null
) {
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null || text.length > 8) {
                        Modifier.pointerInput(text) {
                            detectTapGestures(
                                onTap = { onClick?.invoke() },
                                onLongPress = { if (text.length > 8) showTooltip = true }
                            )
                        }
                    } else Modifier
                )
        )
    }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(onClick = { showTooltip = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

/**
 * 获取工具的中文显示名称。
 */
fun getToolDisplayName(name: String): String = when (name) {
    "web_search" -> "联网搜索"
    "set_clipboard" -> "写入剪贴板"
    "show_bubble_letters" -> "悬浮球展示"
    else -> name
}

/**
 * 工具调用记录展示组件。
 *
 * 以折叠卡片形式展示 AI 在推理过程中调用的工具列表，
 * 包括工具名称、参数和执行结果。
 *
 * @param toolCalls 工具调用记录列表
 * @param modifier Compose 修饰符
 */
@Composable
fun ToolCallSection(
    toolCalls: List<ToolCallRecord>,
    onDetailClick: (ToolCallRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    var expanded by remember { mutableStateOf(true) } // 默认展开

    Column(
        modifier = modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "工具调用 (${toolCalls.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    toolCalls.forEachIndexed { index, call ->
                        ToolCallItem(
                            index = index + 1,
                            call = call,
                            onClick = { onDetailClick(call) }
                        )
                        if (index < toolCalls.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallItem(
    index: Int,
    call: ToolCallRecord,
    onClick: () -> Unit
) {
    val statusIcon = when {
        call.result.isBlank() -> Icons.Default.HourglassEmpty
        call.result.startsWith("error:") -> Icons.Default.ErrorOutline
        else -> Icons.Default.CheckCircle
    }
    val statusColor = when {
        call.result.isBlank() -> MaterialTheme.colorScheme.tertiary
        call.result.startsWith("error:") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = call.result.isNotBlank(), onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = getToolDisplayName(call.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Column(modifier = Modifier.padding(start = 36.dp, top = 4.dp)) {
            if (call.arguments.isNotBlank()) {
                Text(
                    text = "参数: ${call.arguments}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (call.result.isNotBlank() && !call.result.startsWith("error:")) {
                Text(
                    text = "结果: ${call.result}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "点击查看完整记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 结构化展示工具执行详情的内容。
 */
@Composable
fun ToolDetailContent(
    call: ToolCallRecord,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "工具: ${getToolDisplayName(call.name)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "参数:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = call.arguments,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (call.name == "web_search" && !call.metadata.isNullOrBlank()) {
            val hits = remember(call.metadata) {
                try {
                    Json.decodeFromString<List<SearchHit>>(call.metadata)
                } catch (_: Exception) {
                    emptyList<SearchHit>()
                }
            }
            
            if (hits.isNotEmpty()) {
                Text(
                    text = "搜索到的网页 (${hits.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                
                hits.forEachIndexed { index, hit ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = hit.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = hit.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!hit.url.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hit.url))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("访问原网页", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else {
                DefaultResultDisplay(call.result)
            }
        } else {
            DefaultResultDisplay(call.result)
        }
    }
}

@Composable
private fun DefaultResultDisplay(result: String) {
    Text(
        text = "执行结果:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
    MathView(
        text = result,
        modifier = Modifier.fillMaxWidth()
    )
}
