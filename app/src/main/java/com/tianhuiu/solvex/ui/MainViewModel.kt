package com.tianhuiu.solvex.ui

import android.app.Application
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.SolveXApplication
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.FloatingBallAppearance
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.PermissionSettings
import com.tianhuiu.solvex.data.models.PermissionSetupStep
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.mode.ModeConfig
import com.tianhuiu.solvex.mode.UniversalMode
import com.tianhuiu.solvex.service.AdbCommandHelper
import com.tianhuiu.solvex.service.MainService
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import java.util.UUID

/**
 * 应用配置导出/导入的数据结构。
 */
@Serializable
data class ExportData(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
    val webSearch: com.tianhuiu.solvex.data.models.WebSearchSettings? = null,
    val permissions: com.tianhuiu.solvex.data.models.PermissionSettings? = null,
)

/**
 * 全局对话框的状态数据。
 */
data class GlobalDialogData(
    val title: String,
    val message: String,
    val confirmText: String = "确定",
    val dismissText: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: (() -> Unit)? = null,
    val isDestructive: Boolean = false,
    val icon: ImageVector? = null,
)

/**
 * SolveX 的核心 ViewModel。
 * 管理全局配置状态、权限生命周期、后台服务协调以及数据持久化逻辑。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val container = (application as SolveXApplication).container
    private val client get() = container.okHttpClient
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val llmClient get() = container.unifiedLLMClient
    private var pendingSaveJob: kotlinx.coroutines.Job? = null

    // region UI 响应式属性

    var providers by mutableStateOf(emptyList<ModelProvider>())
        private set
    var assistants by mutableStateOf(emptyList<AssistantConfig>())
        private set
    var permissions by mutableStateOf(PermissionSettings())
        private set
    var selectedAssistantId by mutableStateOf<String?>(null)
        private set
    var selectedEngine by mutableStateOf(EngineType.VISION_ENGINE)
        private set
    var autoScrollContent by mutableStateOf(true)
        private set
    var webSearchSettings by mutableStateOf(com.tianhuiu.solvex.data.models.WebSearchSettings())
        private set
    var currentModeConfig by mutableStateOf(ModeConfig())
        private set
    var defaultProviderId by mutableStateOf<String?>(null)
        private set
    var trustAllCertificates by mutableStateOf(false)
        private set
    var globalDialogState by mutableStateOf<GlobalDialogData?>(null)
        private set

    // endregion

    // region 运行状态与权限

    var isOverlayPermissionGranted by mutableStateOf(false)
        private set
    var isNotificationPermissionGranted by mutableStateOf(false)
        private set
    var isServiceRunning by mutableStateOf(false)
        private set
    var activeModeId by mutableStateOf<String?>(null)
        private set
    var showStopConfirmationDialog by mutableStateOf(false)
        private set
    var isShizukuRunning by mutableStateOf(false)
        private set
    var isShizukuPermissionGranted by mutableStateOf(false)
        private set
    var isShizukuInstalled by mutableStateOf(false)
        private set
    var isAccessibilityEnabled by mutableStateOf(false)
        private set
    var isServiceInRegularMode by mutableStateOf(true)
        private set
    var showPermissionSetupGuide by mutableStateOf(false)
        private set
    var currentSetupStep by mutableStateOf(PermissionSetupStep.OVERLAY)
        private set
    var deepLinkHistoryId by mutableStateOf<String?>(null)
    var launchCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private val _requestMediaProjection = MutableSharedFlow<Boolean>()
    val requestMediaProjection = _requestMediaProjection.asSharedFlow()
    val inAppNotifications = container.appNotificationManager.notifications

    val isAllPermissionsReady: Boolean
        get() {
            val mode = permissions.captureMode
            return isOverlayPermissionGranted && when (mode) {
                CaptureMode.TEXT_ONLY -> isAccessibilityEnabled
                CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
                else -> true
            }
        }

    // endregion

    // region 初始化与生命周期

    init {
        (application as SolveXApplication).viewModel = this
        
        // 监听后台服务状态
        viewModelScope.launch {
            MainService.isRunning.collect { running ->
                isServiceRunning = running
                activeModeId = if (running) UniversalMode.id else null
            }
        }

        viewModelScope.launch {
            MainService.isRegularMode.collect { regular ->
                isServiceInRegularMode = regular
            }
        }

        // 监听服务异常上报
        viewModelScope.launch {
            MainService.serviceError.collect { error ->
                showGlobalDialog(
                    GlobalDialogData(
                        title = "服务异常",
                        message = error,
                        confirmText = "知道了",
                        icon = Icons.Default.Warning
                    )
                )
            }
        }

        // 加载 DataStore 配置
        viewModelScope.launch {
            repository.appConfigFlow.collect { config ->
                if (config.providers.isEmpty() && config.assistants.isEmpty()) {
                    resetToDefault()
                } else {
                    providers = config.providers
                    assistants = config.assistants
                    permissions = config.permissions
                    selectedAssistantId = config.selectedAssistantId
                    selectedEngine = if (config.permissions.captureMode == CaptureMode.TEXT_ONLY) {
                        EngineType.TEXT_ENGINE
                    } else {
                        config.selectedEngine
                    }
                    currentModeConfig = config.modeConfig
                    defaultProviderId = config.defaultProviderId
                    autoScrollContent = config.autoScrollContent
                    webSearchSettings = config.webSearch
                    
                    if (trustAllCertificates != config.trustAllCertificates) {
                        trustAllCertificates = config.trustAllCertificates
                        container.refreshNetworkStack(trustAllCertificates)
                    }
                    checkPermissions()
                }
            }
        }

        // 更新启动计数
        viewModelScope.launch {
            launchCount = repository.launchCountFlow.first()
            repository.incrementLaunchCount()
        }
    }

    override fun onCleared() {
        super.onCleared()
        (getApplication<Application>() as SolveXApplication).viewModel = null
    }

    // endregion

    // region 公开工具方法

    fun showGlobalDialog(data: GlobalDialogData) {
        globalDialogState = data
    }

    fun showFeedbackDialog(title: String, message: String, icon: ImageVector? = null) {
        showGlobalDialog(GlobalDialogData(title, message, icon = icon))
    }

    fun dismissGlobalDialog() {
        globalDialogState = null
    }

    fun consumeDeepLink(): String? {
        val id = deepLinkHistoryId
        deepLinkHistoryId = null
        return id
    }

    fun dismissInAppNotification(id: String) {
        container.appNotificationManager.dismiss(id)
    }

    // endregion

    // region 数据持久化

    private fun save() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(300)
            repository.saveAppConfig(
                AppConfig(
                    providers = providers,
                    assistants = assistants,
                    permissions = permissions,
                    defaultProviderId = defaultProviderId,
                    selectedAssistantId = selectedAssistantId,
                    selectedEngine = selectedEngine,
                    modeConfig = currentModeConfig,
                    autoScrollContent = autoScrollContent,
                    webSearch = webSearchSettings,
                    trustAllCertificates = trustAllCertificates
                )
            )
        }
    }

    // endregion

    // region 配置项管理

    fun addProvider(p: ModelProvider) { providers += p; save() }
    fun updateProvider(p: ModelProvider) { providers = providers.map { if (it.id == p.id) p else it }; save() }
    fun updateProviders(l: List<ModelProvider>) { providers = l; save() }
    fun deleteProvider(id: String) { providers = providers.filter { it.id != id }; save() }

    fun addAssistant(a: AssistantConfig) { assistants += a; save() }
    fun updateAssistant(a: AssistantConfig) { assistants = assistants.map { if (it.id == a.id) a else it }; save() }
    fun updateAssistants(l: List<AssistantConfig>) { assistants = l; save() }
    fun setAssistant(id: String?) { selectedAssistantId = id; save() }
    fun deleteAssistant(id: String) { assistants = assistants.filter { it.id != id }; save() }

    fun updateModeConfig(id: String, c: ModeConfig) { currentModeConfig = c; save() }
    fun setMode(id: String) {}
    
    fun setEngine(engine: EngineType) {
        if (permissions.captureMode == CaptureMode.TEXT_ONLY && engine != EngineType.TEXT_ENGINE) return
        selectedEngine = engine
        save()
    }
    fun updatePermissions(p: PermissionSettings) { permissions = p; save(); checkPermissions() }
    fun updateAutoScrollContent(e: Boolean) { autoScrollContent = e; save() }
    fun updateWebSearchSettings(s: com.tianhuiu.solvex.data.models.WebSearchSettings) { webSearchSettings = s; save() }
    fun updateTrustAllCertificates(t: Boolean) { trustAllCertificates = t; container.refreshNetworkStack(t); save() }
    fun updateDefaultProviderId(id: String?) { defaultProviderId = id; save() }
    fun updateBallAppearance(a: FloatingBallAppearance) { permissions = permissions.copy(appearance = a); save() }
    fun updateShowStopConfirmationDialog(s: Boolean) { showStopConfirmationDialog = s }

    fun addSearchProvider(p: com.tianhuiu.solvex.data.models.SearchProviderConfig) { webSearchSettings = webSearchSettings.copy(providers = webSearchSettings.providers + p); save() }
    fun updateSearchProvider(p: com.tianhuiu.solvex.data.models.SearchProviderConfig) { webSearchSettings = webSearchSettings.copy(providers = webSearchSettings.providers.map { if (it.id == p.id) p else it }); save() }
    fun updateSearchProviders(l: List<com.tianhuiu.solvex.data.models.SearchProviderConfig>) { webSearchSettings = webSearchSettings.copy(providers = l); save() }
    fun deleteSearchProvider(id: String) { webSearchSettings = webSearchSettings.copy(providers = webSearchSettings.providers.filter { it.id != id }, selectedProviderId = if (webSearchSettings.selectedProviderId == id) null else webSearchSettings.selectedProviderId); save() }
    fun updateSelectedSearchProvider(id: String?) { webSearchSettings = webSearchSettings.copy(selectedProviderId = id); save() }

    // endregion

    // region 导入导出逻辑

    fun exportConfig(
        sp: List<ModelProvider> = providers,
        sa: List<AssistantConfig> = assistants,
        m: Map<String, Boolean> = emptyMap(),
        ss: List<com.tianhuiu.solvex.data.models.SearchProviderConfig> = webSearchSettings.providers,
        sm: Map<String, Boolean> = emptyMap(),
        includePermissions: Boolean = true
    ): String {
        val fp = sp.map { if (m[it.id] == true) it else it.copy(apiKey = "") }
        val fs = ss.map { if (sm[it.id] == true) it else it.copy(apiKey = "") }
        return json.encodeToString(
            ExportData.serializer(),
            ExportData(
                providers = fp,
                assistants = sa,
                webSearch = if (fs.isNotEmpty()) webSearchSettings.copy(providers = fs) else null,
                permissions = if (includePermissions) permissions else null
            )
        )
    }
    fun decodeImportConfig(s: String): ExportData? = try { json.decodeFromString(ExportData.serializer(), s) } catch (e: Exception) { null }
    fun importConfig(d: ExportData) {
        providers = mergeLists(providers, d.providers, { it.name }) { c, i -> i.copy(id = c.id) }
        assistants = mergeLists(assistants, d.assistants, { it.name }) { c, i -> i.copy(id = c.id) }
        d.webSearch?.let { webSearchSettings = it }
        d.permissions?.let { permissions = it }
        save()
    }
    private fun <T> mergeLists(cl: List<T>, il: List<T>, ns: (T) -> String, merger: (T, T) -> T): List<T> {
        val nl = cl.toMutableList()
        il.forEach { i -> val idx = nl.indexOfFirst { ns(it) == ns(i) }; if (idx != -1) nl[idx] = merger(nl[idx], i) else nl.add(i) }
        return nl
    }

    // endregion

    // region 连通性测试逻辑

    var connectivityTestStates by mutableStateOf<Map<String, ConnectivityTestState>>(emptyMap())
        private set

    suspend fun testSearchConnectivity(p: com.tianhuiu.solvex.data.models.SearchProviderConfig): ConnectivityTestState {
        connectivityTestStates = connectivityTestStates + (p.id to ConnectivityTestState.Testing)
        return try {
            val res = com.tianhuiu.solvex.network.search.SearchOrchestrator(client, json).validate(p)
            val st = if (res.errorCode == 0) {
                showFeedbackDialog("验证通过", "${p.name}: 接口连通正常", Icons.Default.CheckCircle)
                ConnectivityTestState.Success(0)
            } else {
                showFeedbackDialog("验证失败", "${p.name}: ${res.errorMessage ?: "接口返回异常"}", Icons.Default.Error)
                ConnectivityTestState.Failure(res.errorMessage ?: "验证失败")
            }
            connectivityTestStates = connectivityTestStates + (p.id to st); st
        } catch (e: Exception) {
            val st = ConnectivityTestState.Failure(e.message ?: "连接失败")
            showFeedbackDialog("连接失败", "${p.name}: ${e.message}", Icons.Default.Error)
            connectivityTestStates = connectivityTestStates + (p.id to st); st
        }
    }

    suspend fun testConnectivity(p: ModelProvider): ConnectivityTestState {
        connectivityTestStates = connectivityTestStates + (p.id to ConnectivityTestState.Testing)
        return try {
            val m = llmClient.fetchModels(p)
            val res = if (m.isNotEmpty()) {
                if (providers.any { it.id == p.id }) updateProvider(p.copy(availableModels = m))
                ConnectivityTestState.Success(m.size)
            } else {
                ConnectivityTestState.Failure("无可用模型")
            }
            connectivityTestStates = connectivityTestStates + (p.id to res); res
        } catch (e: Exception) {
            val res = ConnectivityTestState.Failure(e.message ?: "连接失败")
            connectivityTestStates = connectivityTestStates + (p.id to res); res
        }
    }

    suspend fun fetchModelsForProvider(p: ModelProvider): List<String> = try { val m = llmClient.fetchModels(p); if (m.isNotEmpty() && providers.any { it.id == p.id }) updateProvider(p.copy(availableModels = m)); m } catch (_: Exception) { emptyList() }
    suspend fun fetchModelsDirect(id: String): List<String> { val p = providers.find { it.id == id } ?: return emptyList(); return try { val m = llmClient.fetchModels(p); if (m.isNotEmpty()) updateProvider(p.copy(availableModels = m)); m } catch (_: Exception) { emptyList() } }

    // endregion

    // region 权限管理详细逻辑

    fun checkPermissions() {
        val ctx = getApplication<Application>()
        isOverlayPermissionGranted = Settings.canDrawOverlays(ctx)
        isNotificationPermissionGranted = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        isShizukuInstalled = try { ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: Exception) { try { ctx.packageManager.getPackageInfo("dev.rikka.shizuku", 0); true } catch (_: Exception) { false } }
        isShizukuRunning = if (isShizukuInstalled) Shizuku.pingBinder() else false
        isShizukuPermissionGranted = if (isShizukuRunning) { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED } else false
        isAccessibilityEnabled = SystemUtils.isAccessibilityServiceEnabled(ctx, SolveXAccessibilityService::class.java)
        val mode = permissions.captureMode
        val isReady = isOverlayPermissionGranted && when (mode) {
            CaptureMode.TEXT_ONLY -> isAccessibilityEnabled
            CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
            else -> true 
        }
        container.appNotificationManager.syncAll(isServiceRunning, isReady && !isServiceRunning, launchCount)
        checkAndStartPermissionSetup()
        if (isServiceRunning && !isReady) stopService()
    }

    private fun isShizukuPackageInstalled(): Boolean {
        return try { getApplication<Application>().packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: Exception) { try { getApplication<Application>().packageManager.getPackageInfo("dev.rikka.shizuku", 0); true } catch (_: Exception) { false } }
    }

    fun requestOverlayPermission() { getApplication<Application>().startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${getApplication<Application>().packageName}".toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    fun requestNotificationPermission() { getApplication<Application>().startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, getApplication<Application>().packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    fun requestAccessibilityPermission() { getApplication<Application>().startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); viewModelScope.launch { delay(500); checkPermissions() } }
    fun delayedPermissionCheck() { viewModelScope.launch { delay(800); checkPermissions() } }
    fun requestShizukuPermission() { if (isShizukuRunning && !isShizukuPermissionGranted) Shizuku.requestPermission(0) }

    private val brL = Shizuku.OnBinderReceivedListener { isShizukuRunning = true; checkPermissions() }
    private val bdL = Shizuku.OnBinderDeadListener { isShizukuRunning = false; isShizukuPermissionGranted = false }
    private val rpL = Shizuku.OnRequestPermissionResultListener { _, gr -> if (gr == android.content.pm.PackageManager.PERMISSION_GRANTED) { isShizukuPermissionGranted = true; viewModelScope.launch { val c = getApplication<Application>(); AdbCommandHelper.grantWriteSecureSettings(c); AdbCommandHelper.enableAccessibilityService(c); delay(1000); checkPermissions() } } }

    fun registerShizukuListeners() {
        Shizuku.removeBinderReceivedListener(brL); Shizuku.removeBinderDeadListener(bdL); Shizuku.removeRequestPermissionResultListener(rpL)
        Shizuku.addBinderReceivedListener(brL); Shizuku.addBinderDeadListener(bdL); Shizuku.addRequestPermissionResultListener(rpL)
        isShizukuRunning = Shizuku.pingBinder()
        if (isShizukuRunning) isShizukuPermissionGranted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun unregisterShizukuListeners() {
        Shizuku.removeBinderReceivedListener(brL); Shizuku.removeBinderDeadListener(bdL); Shizuku.removeRequestPermissionResultListener(rpL)
    }

    fun startService(isQuick: Boolean = false) {
        if (!isOverlayPermissionGranted) return
        
        // 更新当前配置中的裁剪模式以匹配启动意图
        val currentCrop = currentModeConfig.enableCrop ?: UniversalMode.shouldCrop
        if (isQuick && currentCrop) {
            updateModeConfig(UniversalMode.id, currentModeConfig.copy(enableCrop = false))
        } else if (!isQuick && !currentCrop) {
            updateModeConfig(UniversalMode.id, currentModeConfig.copy(enableCrop = true))
        }

        when (permissions.captureMode) {
            CaptureMode.SYSTEM -> viewModelScope.launch { _requestMediaProjection.emit(isQuick) }
            CaptureMode.SHIZUKU -> if (isShizukuPermissionGranted) startMainService(0, null, isQuick)
            CaptureMode.TEXT_ONLY -> if (isAccessibilityEnabled) startMainService(0, null, isQuick)
        }
    }
    fun startMainService(rc: Int, pd: Intent?, isQuick: Boolean = false) {
        activeModeId = UniversalMode.id
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MainService::class.java).apply { 
            action = MainService.ACTION_START
            putExtra(MainService.EXTRA_RESULT_CODE, rc)
            pd?.let { putExtra(MainService.EXTRA_PROJECTION_DATA, it) }
            putExtra(MainService.EXTRA_CAPTURE_MODE, permissions.captureMode)
            putExtra(MainService.EXTRA_IS_QUICK_START, isQuick)
        }
        ctx.startForegroundService(intent); checkPermissions()
    }
    fun stopService() {
        val intent = Intent(getApplication(), MainService::class.java).apply { action = MainService.ACTION_STOP }
        getApplication<Application>().stopService(intent); activeModeId = null; showStopConfirmationDialog = false; checkPermissions()
    }

    // endregion

    // region 业务逻辑与引导流程

    fun resetToDefault() {
        providers = listOf(ModelProvider(UUID.randomUUID().toString(), ProviderKind.OPENAI_COMPATIBLE, "OpenAI", "https://api.openai.com/v1", "", emptyList()))
        assistants = listOf(AssistantConfig(UUID.randomUUID().toString(), "题目解答助手", "提取题目和选项原文。", "请给出详细解题步骤，并在结尾输出最终答案。", "请结合图片给出详细解题步骤，并在结尾输出最终答案。"))
        selectedAssistantId = assistants.first().id
        webSearchSettings = com.tianhuiu.solvex.data.models.WebSearchSettings(
            enabled = false, 
            providers = listOf(com.tianhuiu.solvex.data.models.SearchProviderConfig(UUID.randomUUID().toString(), "Tavily (推荐)", com.tianhuiu.solvex.data.models.SearchProviderKind.TAVILY, "https://api.tavily.com/search"))
        )
        permissions = PermissionSettings()
        save()
    }

    fun checkAndStartPermissionSetup() {
        val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val mode = permissions.captureMode
        val req = isOverlayPermissionGranted && when (mode) {
            CaptureMode.TEXT_ONLY -> isAccessibilityEnabled
            CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
            else -> true
        }
        if (req && pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) && isNotificationPermissionGranted) {
            showPermissionSetupGuide = false; if (!permissions.isFirstLaunchSetupComplete) updatePermissions(permissions.copy(isFirstLaunchSetupComplete = true))
        } else { showPermissionSetupGuide = true; advanceSetupToMissingStep() }
    }
    fun advanceSetupToMissingStep() {
        val mode = permissions.captureMode
        currentSetupStep = when {
            !isOverlayPermissionGranted -> PermissionSetupStep.OVERLAY
            !isNotificationPermissionGranted -> PermissionSetupStep.NOTIFICATION
            mode == CaptureMode.TEXT_ONLY && !isAccessibilityEnabled -> PermissionSetupStep.ACCESSIBILITY
            !(getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(getApplication<Application>().packageName) -> PermissionSetupStep.BATTERY
            mode == CaptureMode.SHIZUKU && (!isShizukuPermissionGranted || !isShizukuRunning) -> PermissionSetupStep.SHIZUKU
            else -> PermissionSetupStep.DONE
        }
        if (currentSetupStep == PermissionSetupStep.DONE) { showPermissionSetupGuide = false; if (!permissions.isFirstLaunchSetupComplete) updatePermissions(permissions.copy(isFirstLaunchSetupComplete = true)) }
    }
    fun handleSetupStepAction(s: PermissionSetupStep) {
        when (s) {
            PermissionSetupStep.OVERLAY -> requestOverlayPermission()
            PermissionSetupStep.NOTIFICATION -> requestNotificationPermission()
            PermissionSetupStep.ACCESSIBILITY -> requestAccessibilityPermission()
            PermissionSetupStep.BATTERY -> getApplication<Application>().startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = "package:${getApplication<Application>().packageName}".toUri(); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            PermissionSetupStep.SHIZUKU -> {
                val pkg = getApplication<Application>().packageManager
                val isInst = try { pkg.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: Exception) { try { pkg.getPackageInfo("dev.rikka.shizuku", 0); true } catch (_: Exception) { false } }
                if (!isInst) getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku/releases".toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                else if (!isShizukuRunning) (pkg.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: pkg.getLaunchIntentForPackage("dev.rikka.shizuku"))?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getApplication<Application>().startActivity(this) }
                else requestShizukuPermission()
            }
            else -> {}
        }
    }
    fun skipPermissionSetup() { showPermissionSetupGuide = false }
    fun getRelevantSteps(): List<PermissionSetupStep> {
        val mode = permissions.captureMode
        return buildList {
            add(PermissionSetupStep.OVERLAY); add(PermissionSetupStep.NOTIFICATION)
            if (mode == CaptureMode.TEXT_ONLY) add(PermissionSetupStep.ACCESSIBILITY)
            add(PermissionSetupStep.BATTERY)
            if (mode == CaptureMode.SHIZUKU) add(PermissionSetupStep.SHIZUKU)
        }
    }

    // endregion
}

sealed class ConnectivityTestState {
    data object Testing : ConnectivityTestState()
    data class Success(val modelCount: Int) : ConnectivityTestState()
    data class Failure(val message: String) : ConnectivityTestState()
}
