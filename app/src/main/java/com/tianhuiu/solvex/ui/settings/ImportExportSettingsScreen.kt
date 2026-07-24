package com.tianhuiu.solvex.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.SearchProviderConfig
import com.tianhuiu.solvex.ui.ExportData
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.ui.components.TooltipText
import com.tianhuiu.solvex.utils.SystemUtils
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<ExportData?>(null) }

    // 选择状态 (使用 SnapshotStateMap 确保响应式更新)
    val selectedProviders = remember { mutableStateMapOf<String, Boolean>() }
    val includeApiKeyMap = remember { mutableStateMapOf<String, Boolean>() }
    val selectedAssistants = remember { mutableStateMapOf<String, Boolean>() }
    val selectedSearchProviders = remember { mutableStateMapOf<String, Boolean>() }
    val includeSearchApiKeyMap = remember { mutableStateMapOf<String, Boolean>() }
    var includeSystemConfig by remember { mutableStateOf(true) }

    var showProviderSheet by remember { mutableStateOf(false) }
    var showAssistantSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // 初始化选择状态
    LaunchedEffect(viewModel.providers, viewModel.assistants, viewModel.webSearchSettings.providers) {
        viewModel.providers.forEach {
            if (it.id !in selectedProviders) selectedProviders[it.id] = true
            if (it.id !in includeApiKeyMap) includeApiKeyMap[it.id] = true
        }
        viewModel.assistants.forEach {
            if (it.id !in selectedAssistants) selectedAssistants[it.id] = true
        }
        viewModel.webSearchSettings.providers.forEach {
            if (it.id !in selectedSearchProviders) selectedSearchProviders[it.id] = true
            if (it.id !in includeSearchApiKeyMap) includeSearchApiKeyMap[it.id] = true
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(exportContent.toByteArray())
                }
                viewModel.showFeedbackDialog(
                    title = "导出成功",
                    message = "配置已成功保存到文件",
                    icon = Icons.Default.CheckCircle
                )
            } catch (e: Exception) {
                viewModel.showFeedbackDialog(
                    title = "导出失败",
                    message = e.message ?: "未知错误",
                    icon = Icons.Default.Warning
                )
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    val data = viewModel.decodeImportConfig(content)
                    if (data != null) {
                        pendingImportData = data
                        showImportDialog = true
                    } else {
                        SystemUtils.showFeedback(
                            context,
                            userMessage = "文件格式错误",
                            detailedLog = "Selected file is not a valid configuration format"
                        )
                    }
                }
            } catch (e: Exception) {
                SystemUtils.showFeedback(
                    context,
                    userMessage = "读取失败",
                    detailedLog = "Read failed: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("数据管理", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部操作区（备份/导入）
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            icon = Icons.Default.Backup,
                            label = "全量备份",
                            description = "备份所有配置到文件",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportContent = viewModel.exportConfig()
                                createDocumentLauncher.launch("SolveX_Backup_${System.currentTimeMillis()}.json")
                            }
                        )
                        ActionCard(
                            icon = Icons.Default.UploadFile,
                            label = "导入配置",
                            description = "从 JSON 文件恢复",
                            modifier = Modifier.weight(1f),
                            onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }
            }

            // 2. 分项导出入口
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "分项导出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    // 选择提供方入口
                    val providerCount = viewModel.providers.filter { selectedProviders[it.id] == true }.size
                    SelectionEntryCard(
                        title = "选择模型提供方",
                        subtitle = "已选择 $providerCount 个项目",
                        icon = Icons.Default.Cloud,
                        onClick = { showProviderSheet = true }
                    )

                    Spacer(Modifier.height(12.dp))

                    // 选择助手入口
                    val assistantCount = viewModel.assistants.filter { selectedAssistants[it.id] == true }.size
                    SelectionEntryCard(
                        title = "选择助手配置",
                        subtitle = "已选择 $assistantCount 个项目",
                        icon = Icons.Default.Psychology,
                        onClick = { showAssistantSheet = true }
                    )

                    Spacer(Modifier.height(12.dp))

                    // 选择搜索引擎入口
                    val searchCount = viewModel.webSearchSettings.providers.filter { selectedSearchProviders[it.id] == true }.size
                    SelectionEntryCard(
                        title = "选择搜索引擎",
                        subtitle = "已选择 $searchCount 个项目",
                        icon = Icons.Default.Language,
                        onClick = { showSearchSheet = true }
                    )

                    Spacer(Modifier.height(12.dp))

                    // 系统与外观开关
                    SelectionToggleCard(
                        title = "系统与外观配置",
                        subtitle = "包含悬浮球样式、权限偏好等",
                        icon = Icons.Default.Settings,
                        checked = includeSystemConfig,
                        onCheckedChange = { includeSystemConfig = it }
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val providers = viewModel.providers.filter { selectedProviders[it.id] == true }
                            val assistants = viewModel.assistants.filter { selectedAssistants[it.id] == true }
                            val searchProviders = viewModel.webSearchSettings.providers.filter { selectedSearchProviders[it.id] == true }
                            exportContent = viewModel.exportConfig(
                                sp = providers, 
                                sa = assistants, 
                                m = includeApiKeyMap,
                                ss = searchProviders,
                                sm = includeSearchApiKeyMap,
                                includePermissions = includeSystemConfig
                            )
                            createDocumentLauncher.launch("SolveX_Export_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = selectedProviders.values.any { it } || 
                                selectedAssistants.values.any { it } || 
                                selectedSearchProviders.values.any { it } || 
                                includeSystemConfig
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导出选中项", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. 危险操作
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    Text(
                        "危险区域",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("重置所有配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Text("清空全部数据并恢复到出厂默认状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProviderSheet) {
        MultiSelectionSheet(
            title = "选择模型提供方",
            items = viewModel.providers,
            selectedMap = selectedProviders,
            sheetState = sheetState,
            onDismissRequest = { showProviderSheet = false },
            itemContent = { provider ->
                Column(modifier = Modifier.weight(1f)) {
                    TooltipText(
                        provider.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        onClick = { selectedProviders[provider.id] = !(selectedProviders[provider.id] ?: false) }
                    )
                    Text(
                        provider.type.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("密钥", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = includeApiKeyMap[provider.id] ?: false,
                        onCheckedChange = { includeApiKeyMap[provider.id] = it },
                        enabled = selectedProviders[provider.id] ?: false
                    )
                }
            }
        )
    }

    if (showAssistantSheet) {
        MultiSelectionSheet(
            title = "选择助手配置",
            items = viewModel.assistants,
            selectedMap = selectedAssistants,
            sheetState = sheetState,
            onDismissRequest = { showAssistantSheet = false },
            itemContent = { assistant ->
                TooltipText(
                    assistant.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    onClick = { selectedAssistants[assistant.id] = !(selectedAssistants[assistant.id] ?: false) }
                )
            }
        )
    }

    if (showSearchSheet) {
        MultiSelectionSheet(
            title = "选择搜索引擎",
            items = viewModel.webSearchSettings.providers,
            selectedMap = selectedSearchProviders,
            sheetState = sheetState,
            onDismissRequest = { showSearchSheet = false },
            itemContent = { provider ->
                Column(modifier = Modifier.weight(1f)) {
                    TooltipText(
                        provider.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        onClick = { selectedSearchProviders[provider.id] = !(selectedSearchProviders[provider.id] ?: false) }
                    )
                    Text(
                        provider.kind.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("密钥", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = includeSearchApiKeyMap[provider.id] ?: false,
                        onCheckedChange = { includeSearchApiKeyMap[provider.id] = it },
                        enabled = selectedSearchProviders[provider.id] ?: false
                    )
                }
            }
        )
    }

    if (showResetDialog) {
        SolveXConfirmDialog(
            onDismissRequest = { showResetDialog = false },
            onConfirm = {
                viewModel.resetToDefault()
                showResetDialog = false
            },
            title = "确认重置",
            message = "确定要重置所有配置吗？此操作无法撤销。",
            confirmText = "重置",
            isDestructive = true,
            icon = Icons.Default.Restore
        )
    }

    if (showImportDialog && pendingImportData != null) {
        val data = pendingImportData!!

        SolveXDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportData = null
            },
            icon = Icons.Default.UploadFile,
            title = "导入配置",
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportData?.let {
                            viewModel.importConfig(it)
                            viewModel.showFeedbackDialog(
                                title = "导入成功",
                                message = "配置已成功从文件导入",
                                icon = Icons.Default.CheckCircle
                            )
                        }
                        showImportDialog = false
                        pendingImportData = null
                    }
                ) { Text("导入", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingImportData = null
                    }
                ) { Text("取消") }
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "即将导入以下配置：",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                ImportDetailRow(Icons.Default.Cloud, "模型提供方", "${data.providers.size} 个")
                ImportDetailRow(Icons.Default.Psychology, "助手配置", "${data.assistants.size} 个")
                if (data.webSearch != null) {
                    ImportDetailRow(Icons.Default.Language, "联网配置", "包含")
                }
                if (data.permissions != null) {
                    ImportDetailRow(Icons.Default.Settings, "系统与外观", "包含")
                }
                HorizontalDivider()
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "导入配置后，同名配置将被覆盖，且无法撤销。请核对后再进行导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectionSheet(
    title: String,
    items: List<T>,
    selectedMap: SnapshotStateMap<String, Boolean>,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    itemContent: @Composable RowScope.(T) -> Unit,
    idSelector: (T) -> String = { (it as? ModelProvider)?.id ?: (it as? AssistantConfig)?.id ?: (it as? SearchProviderConfig)?.id ?: "" }
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
                    modifier = Modifier.width(32.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                val allSelected = items.isNotEmpty() && items.all { selectedMap[idSelector(it)] == true }
                TextButton(onClick = {
                    val target = !allSelected
                    items.forEach { selectedMap[idSelector(it)] = target }
                }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    val id = idSelector(item)
                    val isSelected = selectedMap[id] ?: false
                    Surface(
                        onClick = { selectedMap[id] = !isSelected },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = { selectedMap[id] = it })
                            Spacer(Modifier.width(8.dp))
                            itemContent(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun SelectionEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ImportDetailRow(icon: ImageVector, label: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, modifier = Modifier.weight(1f))
        Text(text = count, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
