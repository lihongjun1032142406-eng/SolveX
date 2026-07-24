package com.tianhuiu.solvex.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.ui.ConnectivityTestState
import kotlinx.coroutines.launch

/**
 * 连通性测试状态图标。
 * 统一管理加载、成功、失败、闲置以及“不支持”五种视觉反馈。
 *
 * @param state 当前的测试状态
 * @param onClick 点击图标触发的回调
 * @param enabled 组件是否启用
 * @param supported 该提供商是否支持连通性验证
 */
@Composable
fun ConnectivityStatusIcon(
    state: ConnectivityTestState?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supported: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.38f
    IconButton(
        onClick = onClick,
        enabled = enabled && supported,
        modifier = modifier
    ) {
        if (!supported) {
            Icon(
                Icons.Default.Block,
                contentDescription = "不支持验证",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f * alpha),
                modifier = Modifier.size(20.dp)
            )
        } else {
            when (state) {
                is ConnectivityTestState.Testing ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                is ConnectivityTestState.Success ->
                    Icon(
                        Icons.Default.CheckCircle,
                        "连接成功",
                        tint = Color(0xFF4CAF50).copy(alpha = alpha),
                        modifier = Modifier.size(20.dp)
                    )

                is ConnectivityTestState.Failure ->
                    Icon(
                        Icons.Default.Error,
                        "连接失败",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
                        modifier = Modifier.size(20.dp)
                    )

                else ->
                    Icon(
                        Icons.Default.Sync,
                        "测试连通",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        modifier = Modifier.size(20.dp)
                    )
            }
        }
    }
}

/**
 * 全局首选提供商展示卡片。
 * 提供统一的“Label + 卡片内容”布局。
 *
 * @param label 卡片上方的标签文字（如：全局首选提供方）
 * @param selectedName 当前选中的项名称，null 表示未设置
 * @param icon 卡片左侧展示的图标
 * @param onClick 点击卡片触发的回调
 * @param enabled 组件是否启用
 */
@Composable
fun PreferredProviderCard(
    label: String,
    selectedName: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingsSectionTitle(text = label, enabled = enabled)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = { if (enabled) onClick() },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TooltipText(
                        selectedName ?: "未设置 (将仅使用手动指定的配置)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        onClick = { if (enabled) onClick() },
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            }
        }
    }
}

/**
 * 通用的列表项选择底栏。
 *
 * @param title 标题
 * @param subTitle 副标题
 * @param items 数据列表 (ID 到 名称的映射)
 * @param selectedId 当前选中项的 ID
 * @param onItemSelected 选中回调
 * @param onDismissRequest 关闭回调
 * @param itemIcon 列表项左侧图标
 * @param noneLabel “不设置/自动”选项的标签
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericSelectionSheet(
    title: String,
    subTitle: String?,
    items: List<Pair<String, String>>,
    selectedId: String?,
    onItemSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    itemIcon: ImageVector = Icons.Default.Business,
    noneLabel: String = "不设置",
    noneSubLabel: String? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (subTitle != null) {
                Text(
                    subTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                item {
                    val isNoneSelected = selectedId == null
                    Surface(
                        onClick = {
                            onItemSelected(null)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNoneSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent,
                        border = if (isNoneSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Block,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isNoneSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    noneLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (noneSubLabel != null) {
                                    Text(
                                        noneSubLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            RadioButton(
                                selected = isNoneSelected,
                                onClick = null
                            )
                        }
                    }
                }

                items(items.size) { index ->
                    val (id, name) = items[index]
                    val isSelected = id == selectedId

                    Surface(
                        onClick = {
                            onItemSelected(id)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        itemIcon,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                TooltipText(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    onClick = {
                                        onItemSelected(id)
                                        onDismissRequest()
                                    }
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 状态角标组件。
 * 
 * @param text 显示的文字内容，若为 null 则显示为圆点。
 * @param color 背景颜色。
 * @param contentColor 文字颜色。
 */
@Composable
fun SettingBadge(
    text: String? = null,
    color: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError
) {
    if (text == null) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
    } else {
        Surface(
            color = color,
            contentColor = contentColor,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 设置项分组卡片容器。
 *
 * @param title 分组标题，由 [SettingsSectionTitle] 渲染
 * @param content 卡片内的内容
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (title != null) {
            SettingsSectionTitle(text = title)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * 标准化的设置段落标题。
 * 确保全应用标题在字体、颜色、间距上完全一致。
 * 
 * @param text 标题文本
 * @param enabled 是否处于激活状态
 */
@Composable
fun SettingsSectionTitle(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp)
    )
}

/**
 * 标准化的页面顶部信息横幅。
 * 
 * @param text 提示文本
 */
@Composable
fun SettingsInfoBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 封装后的标准下拉菜单组件。
 * 
 * @param label 标签
 * @param selectedOption 显示的当前选项
 * @param options 所有可选项列表
 * @param onOptionSelected 选项选中回调
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExposedDropdown(
    label: String,
    selectedOption: String,
    options: Array<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * 数值步进调节器。
 */
@Composable
fun NumberStepper(
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Long = 1,
    max: Long = 300,
    step: Long = 1,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.5f

    if (showEditDialog && enabled) {
        var textValue by remember { mutableStateOf(value.toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改数值") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            textValue = newValue
                        }
                    },
                    label = { Text("请输入 $min 到 $max 之间的数值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Timer, null) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val longValue = textValue.toLongOrNull() ?: value
                    onValueChange(longValue.coerceIn(min, max))
                    showEditDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val newValue = (value - step).coerceAtLeast(min)
                if (newValue != value) onValueChange(newValue)
            },
            enabled = enabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "减少",
                modifier = Modifier.size(20.dp),
                tint = if (value > min && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = alpha)
            )
        }

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(48.dp)
                .clickable(enabled = enabled) { showEditDialog = true },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )

        IconButton(
            onClick = {
                val newValue = (value + step).coerceAtMost(max)
                if (newValue != value) onValueChange(newValue)
            },
            enabled = enabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "增加",
                modifier = Modifier.size(20.dp),
                tint = if (value < max && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = alpha)
            )
        }
    }
}

/**
 * 基础设置行项。
 */
@Composable
fun SettingsItem(
    label: String,
    subLabel: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    badge: @Composable (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if ((onClick != null) && enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f * alpha),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha)
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(
                        alpha = alpha
                    )
                ) {
                    trailing()
                }
            }
        }
    }
}

/**
 * 可拖动排序的列表项容器。
 */
@Composable
fun SortableListItem(
    index: Int,
    itemCount: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    val shadowElevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(shadowElevation, RoundedCornerShape(16.dp))
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f * alpha)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(alpha = alpha)
                ) {
                    content()
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { _ ->
                                        draggingOffset = 0f
                                        onDragStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingOffset += dragAmount.y
                                        val threshold = 50f 
                                        if (draggingOffset > threshold && index < itemCount - 1) {
                                            onSwap(index, index + 1)
                                            draggingOffset = 0f
                                        } else if (draggingOffset < -threshold && index > 0) {
                                            onSwap(index, index - 1)
                                            draggingOffset = 0f
                                        }
                                    },
                                    onDragEnd = {
                                        draggingOffset = 0f
                                        onDragEnd(index)
                                    },
                                    onDragCancel = {
                                        draggingOffset = 0f
                                        onDragEnd(index)
                                    }
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "拖动排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f * alpha),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 复合型模型选择器项。
 * 封装了从“选择提供商”到“选择模型”的二级对话框逻辑。
 */
@Composable
fun ModelSelectorItem(
    label: String,
    icon: ImageVector,
    providers: List<ModelProvider>,
    selectedProviderId: String?,
    selectedModel: String?,
    onModelSelected: (String?, String?) -> Unit,
    type: String = "text",
    onFetchModels: suspend (String) -> List<String> = { emptyList() },
    defaultProviderId: String? = null,
    badge: @Composable (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    val effectiveProviderId = selectedProviderId ?: defaultProviderId
    val selectedProvider = providers.find { it.id == effectiveProviderId }
    val isUsingDefault = selectedProviderId == null || selectedModel.isNullOrBlank()

    val subLabel = if (selectedProvider != null) {
        val modelName = if (isUsingDefault) {
            val baseModel = when (type) {
                "ocr" -> selectedProvider.defaultOcrModel.ifBlank { "未设置" }
                "vision" -> selectedProvider.defaultVisionModel.ifBlank { "未设置" }
                else -> selectedProvider.defaultTextModel.ifBlank { "未设置" }
            }
            "$baseModel (默认)"
        } else {
            selectedModel
        }
        "${selectedProvider.name}\n$modelName"
    } else {
        "未配置"
    }

    SettingsItem(
        label = label,
        subLabel = subLabel,
        icon = icon,
        onClick = { showDialog = true },
        badge = badge,
        trailing = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )

    if (showDialog) {
        var currentStep by remember { mutableIntStateOf(if (selectedProviderId == null) 0 else 1) }
        var tempProviderId by remember { mutableStateOf(selectedProviderId) }
        var searchQuery by remember { mutableStateOf("") }
        var showResetConfirm by remember { mutableStateOf(false) }

        if (showResetConfirm) {
            SolveXConfirmDialog(
                onDismissRequest = { showResetConfirm = false },
                onConfirm = {
                    showResetConfirm = false
                    showDialog = false
                    onModelSelected(null, null)
                },
                title = "重置为未配置",
                message = "确定要清除当前选择的提供方和模型吗？",
                confirmText = "重置",
                isDestructive = true
            )
        }

        SolveXDialog(
            onDismissRequest = { showDialog = false },
            title = if (currentStep == 0) "选择提供方" else "选择模型",
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text("重置为未配置", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showDialog = false }) { Text("取消") }
                }
            }
        ) {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (currentStep == 0) {
                    val filteredProviders = providers.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredProviders) { provider ->
                            Surface(
                                onClick = {
                                    tempProviderId = provider.id
                                    currentStep = 1
                                    searchQuery = ""
                                },
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TooltipText(
                                    provider.name,
                                    modifier = Modifier.padding(16.dp),
                                    onClick = {
                                        tempProviderId = provider.id
                                        currentStep = 1
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val provider = providers.find { it.id == tempProviderId }
                    if (provider != null) {
                        var isRefreshing by remember { mutableStateOf(false) }
                        val filteredModels = provider.availableModels.filter {
                            it.contains(searchQuery, ignoreCase = true)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                currentStep = 0
                                searchQuery = ""
                            }) {
                                Text("返回选择提供方")
                            }

                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        isRefreshing = true
                                        onFetchModels(provider.id)
                                        isRefreshing = false
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新模型")
                                }
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                val defaultModel = when (type) {
                                    "ocr" -> provider.defaultOcrModel
                                    "vision" -> provider.defaultVisionModel
                                    else -> provider.defaultTextModel
                                }.ifBlank { "未设置" }

                                Surface(
                                    onClick = {
                                        onModelSelected(tempProviderId, null)
                                        showDialog = false
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "使用默认模型",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            defaultModel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(filteredModels) { model ->
                                Surface(
                                    onClick = {
                                        onModelSelected(tempProviderId, model)
                                        showDialog = false
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(model, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
