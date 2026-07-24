package com.tianhuiu.solvex.service

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.coroutineScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.tianhuiu.solvex.R
import com.tianhuiu.solvex.SolveXApplication
import com.tianhuiu.solvex.capture.ScreenCaptureEngine
import com.tianhuiu.solvex.capture.ShizukuCaptureEngine
import com.tianhuiu.solvex.capture.SystemCaptureEngine
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.AnalysisStatus
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.data.models.ProcessingResult
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.floating.BallStatus
import com.tianhuiu.solvex.floating.DrawerManager
import com.tianhuiu.solvex.floating.FloatingBallManager
import com.tianhuiu.solvex.floating.FloatingBallMenuController
import com.tianhuiu.solvex.floating.RegionMode
import com.tianhuiu.solvex.floating.TextRegionManager
import com.tianhuiu.solvex.mode.UniversalMode
import com.tianhuiu.solvex.network.SseStreamClient
import com.tianhuiu.solvex.utils.NotificationUtils
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 核心业务流
 */
class MainService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _isRegularMode = MutableStateFlow(true)
        val isRegularMode = _isRegularMode.asStateFlow()

        private val _serviceError = MutableSharedFlow<String>(replay = 0)
        val serviceError = _serviceError.asSharedFlow()

        const val CHANNEL_ID = "main_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.tianhuiu.solvex.ACTION_START"
        const val ACTION_STOP = "com.tianhuiu.solvex.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_PROJECTION_DATA = "EXTRA_PROJECTION_DATA"
        const val EXTRA_CAPTURE_MODE = "EXTRA_CAPTURE_MODE"
        const val EXTRA_IS_QUICK_START = "EXTRA_IS_QUICK_START"
    }

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var floatingBallManager: FloatingBallManager? = null
    private var menuController: FloatingBallMenuController? = null
    private var drawerManager: DrawerManager? = null
    private var currentHistoryId: String? = null
    private var processingJob: Job? = null
    private var captureEngine: ScreenCaptureEngine? = null
    private var textRegionManager: TextRegionManager? = null
    private var pipeline: com.tianhuiu.solvex.network.ProcessingPipeline? = null
    private var stealthJob: Job? = null
    private var isStealthActive = false
    private lateinit var repository: SettingsRepository
    private lateinit var historyRepository: HistoryRepository

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var currentConfig: AppConfig? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        (application as SolveXApplication).viewModel?.checkPermissions()
        savedStateRegistryController.performRestore(null)
        repository = SettingsRepository(this)

        val container = (application as SolveXApplication).container
        historyRepository = container.historyRepository

        // 清理意外中断的任务
        lifecycle.coroutineScope.launch {
            historyRepository.cleanupProcessingItems(
                query = "用户已关闭软件",
                result = "程序意外终止或手动清理后台导致任务取消"
            )
        }

        pipeline = container.processingPipeline
        drawerManager = DrawerManager(this, historyRepository)
        textRegionManager = TextRegionManager(this)
        menuController = FloatingBallMenuController(
            this,
            getSystemService(WINDOW_SERVICE) as android.view.WindowManager,
            getEnabledItems = { 
                currentConfig?.permissions?.appearance?.enabledMenuItems ?: setOf("engine", "search", "baidu", "settings")
            },
            getActiveItems = {
                val cfg = currentConfig
                buildSet {
                    if (cfg?.selectedEngine == EngineType.VISION_ENGINE) add("engine")
                    if (cfg?.webSearch?.enabled == true) add("search")
                }
            }
        ) { actionId ->
            handleMenuAction(actionId)
        }

        floatingBallManager = FloatingBallManager(this).apply {
            onBallAutoHidden = {
                menuController?.dismiss()
            }
            onSingleClick = {
                if (processingJob?.isActive == true) {
                    currentHistoryId?.let { id ->
                        lifecycle.coroutineScope.launch {
                            val config = repository.appConfigFlow.first()
                            drawerManager?.show(
                                historyId = id,
                                side = config.currentModeConfig().drawerSide,
                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                showMetadata = false,
                                autoScrollEnabled = config.autoScrollContent
                            )
                        }
                    }
                } else {
                    processingJob = lifecycle.coroutineScope.launch {
                        try {
                            val config = repository.appConfigFlow.first()
                            val mode = UniversalMode
                            val needCrop = config.currentModeConfig().enableCrop ?: mode.shouldCrop
                            val isTextOnly = config.permissions.captureMode == CaptureMode.TEXT_ONLY

                            if (isTextOnly || needCrop) {
                                if (isTextOnly) {
                                    // 实时无障碍取字模式：背景透明，扫描节点
                                    if (SolveXAccessibilityService.instance == null) {
                                        lifecycle.coroutineScope.launch {
                                            _serviceError.emit("无障碍取字需要无障碍服务，请先开启 SolveX 无障碍服务")
                                        }
                                        floatingBallManager?.updateStatus(BallStatus.ERROR)
                                        SystemUtils.vibrateError(this@MainService)
                                        return@launch
                                    }

                                    floatingBallManager?.updateStatus(BallStatus.RUNNING)
                                    val selection = textRegionManager?.selectRegion(
                                        mode = RegionMode.LIVE_SCAN
                                    )
                                    if (selection == null) {
                                        floatingBallManager?.updateStatus(defaultIdleStatus)
                                        return@launch
                                    }
                                    
                                    val text = selection.scannedText
                                    if (text.isBlank()) {
                                        lifecycle.coroutineScope.launch { _serviceError.emit("当前选区未发现可提取的文本内容") }
                                        floatingBallManager?.updateStatus(BallStatus.ERROR)
                                        SystemUtils.vibrateError(this@MainService)
                                        return@launch
                                    }
                                    SystemUtils.vibrateSuccess(this@MainService)
                                    processTextContent(text, config)
                                } else {
                                    // 静态图片裁剪模式：背景黑色，不扫描节点
                                    floatingBallManager?.updateStatus(BallStatus.RUNNING)
                                    floatingBallManager?.tempHide()
                                    delay(100)
                                    val fullBitmap = try {
                                        captureEngine?.capture()
                                    } finally {
                                        floatingBallManager?.restore()
                                    }

                                    if (fullBitmap != null) {
                                        val selection = textRegionManager?.selectRegion(
                                            mode = RegionMode.IMAGE_CROP,
                                            bitmap = fullBitmap
                                        )
                                        if (selection == null) {
                                            fullBitmap.recycle()
                                            floatingBallManager?.updateStatus(defaultIdleStatus)
                                            return@launch
                                        }

                                        SystemUtils.vibrateSuccess(this@MainService)
                                        val cropped = SystemUtils.cropBitmap(fullBitmap, selection.region)
                                        if (cropped != null) {
                                            processImageContent(cropped, config)
                                        } else {
                                            fullBitmap.recycle()
                                            floatingBallManager?.updateStatus(BallStatus.ERROR)
                                        }
                                    } else {
                                        handleCaptureFailure(config)
                                    }
                                }
                                return@launch
                            }

                            // 无需裁剪的普通模式
                            updateStatus(BallStatus.RUNNING)
                            floatingBallManager?.tempHide()
                            delay(100)
                            val bitmap = try {
                                captureEngine?.capture()
                            } finally {
                                floatingBallManager?.restore()
                            }

                            if (bitmap != null) {
                                SystemUtils.vibrateSuccess(this@MainService)
                                processImageContent(bitmap, config)
                            } else {
                                handleCaptureFailure(config)
                            }

                        } catch (_: CancellationException) {
                            drawerManager?.hide()
                        } catch (e: Exception) {
                            handleGeneralError(e)
                        } finally {
                            currentHistoryId = null
                            processingJob = null
                        }
                    }
                }
            }
            onDoubleClick = { x, y ->
                if (processingJob?.isActive == true) {
                    processingJob?.cancel()
                    processingJob = null

                    // 清理记录
                    currentHistoryId?.let { id ->
                        cleanupScope.launch {
                            historyRepository.updateHistoryItem(id) { current ->
                                current.copy(
                                    query = "用户已取消",
                                    result = "用户已取消",
                                    status = AnalysisStatus.CANCELLED
                                )
                            }
                        }
                    }

                    drawerManager?.hide()
                    // 强制恢复状态
                    updateStatus(defaultIdleStatus)
                } else {
                    lifecycle.coroutineScope.launch {
                        val config = repository.appConfigFlow.first()
                        if (config.permissions.appearance.enableMenu) {
                            menuController?.show(x, y)
                        }
                    }
                }
            }
            onLongClick = {
                SystemUtils.vibrate(this@MainService, 50)
                switchEngine()
            }
        }

        lifecycle.coroutineScope.launch {
            repository.appConfigFlow.collect { config ->
                currentConfig = config
                floatingBallManager?.enableAutoHide = config.permissions.enableAutoHideBall
                floatingBallManager?.appearance = config.permissions.appearance
                applyPrivacyPolicy(config)
            }
        }
    }

    private fun handleMenuAction(actionId: String) {
        when (actionId) {
            "engine" -> switchEngine()
            "search" -> switchSearch()
            "baidu" -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "https://www.baidu.com".toUri())
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    lifecycle.coroutineScope.launch {
                        _serviceError.emit("无法打开浏览器: ${e.message}")
                    }
                }
            }
            "settings" -> {
                val intent = Intent(this, com.tianhuiu.solvex.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("navigate_to", "settings")
                }
                startActivity(intent)
            }
            else -> SystemUtils.vibrate(this@MainService, 50)
        }
    }

    private fun applyPrivacyPolicy(config: AppConfig) {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val permissions = config.permissions
            val isShizukuReady = try {
                permissions.enableStealthMode &&
                        Shizuku.pingBinder() &&
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }

            if (isShizukuReady) {
                startStealthMonitor()
            } else {
                stopStealthMonitor()
                withContext(Dispatchers.Main) {
                    updateWindowsSecure(permissions.enableScreenProtection)
                    if (floatingBallManager?.status == BallStatus.LOW_PROFILE ||
                        floatingBallManager?.status == BallStatus.PROTECTED) {
                        floatingBallManager?.updateStatus(BallStatus.IDLE)
                    }
                }
            }
        }
    }

    private fun startStealthMonitor() {
        if (stealthJob?.isActive == true) return

        stealthJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
            var svc: IShizukuShellService? = null
            var lastCount = -1

            withContext(Dispatchers.Main) {
                isStealthActive = false
                floatingBallManager?.updateStatus(BallStatus.LOW_PROFILE)
                floatingBallManager?.defaultIdleStatus = BallStatus.LOW_PROFILE
            }

            while (true) {
                try {
                    if (svc == null || !svc.asBinder().isBinderAlive) {
                        svc = ShizukuUserServiceClient.acquire(this@MainService)
                        if (svc == null) {
                            delay(3000)
                            continue
                        }
                    }

                    val count = svc.getSecureWindowCount()
                    val shouldBeSecure = count > 0

                    if (isStealthActive != shouldBeSecure) {
                        isStealthActive = shouldBeSecure
                        val config = repository.appConfigFlow.first()

                        withContext(Dispatchers.Main) {
                            val finalSecure = shouldBeSecure || config.permissions.enableScreenProtection
                            updateWindowsSecure(finalSecure)
                            floatingBallManager?.updateStatus(
                                if (shouldBeSecure) BallStatus.PROTECTED else BallStatus.LOW_PROFILE
                            )
                        }
                    }

                    // FLAG_SECURE 窗口数量减少时，短暂隐藏悬浮球避免被屏幕抓拍捕获
                    if (lastCount > 0 && count < lastCount) {
                        withContext(Dispatchers.Main) {
                            floatingBallManager?.tempHide()
                        }
                        delay(800)
                        withContext(Dispatchers.Main) {
                            floatingBallManager?.restore()
                        }
                    }
                    lastCount = count

                    delay(1500)
                } catch (e: Exception) {
                    svc = null
                    delay(3000)
                }
            }
        }
    }

    private fun stopStealthMonitor() {
        stealthJob?.cancel()
        stealthJob = null
        isStealthActive = false
        // 退出隐匿模式时，自动恢复状态改回 IDLE
        floatingBallManager?.defaultIdleStatus = BallStatus.IDLE
    }

    private fun updateWindowsSecure(enabled: Boolean) {
        floatingBallManager?.updateSecureFlag(enabled)
        drawerManager?.updateSecureFlag(enabled)
        textRegionManager?.updateScreenProtection(enabled)
    }

    private suspend fun processTextContent(text: String, config: AppConfig) {
        historyRepository.deleteProcessingItems()

        val models = pipeline?.resolveModels(config) ?: return
        val initialResult = pipeline?.createBaseResultTextOnly(models, "正在获取文本内容...") ?: return
        val historyId = initialResult.id
        currentHistoryId = historyId
        val historyItem = HistoryItem(
            id = historyId,
            query = "正在思考中...",
            result = "正在思考中...",
            imagePath = null,
            mode = UniversalMode.id,
            assistantName = initialResult.assistantName,
            providerName = initialResult.modelSummary,
            modelName = initialResult.modelSummary,
            engineName = "无障碍取字",
            status = AnalysisStatus.PROCESSING
        )
        historyRepository.addHistoryItem(historyItem)

        if (config.currentModeConfig().autoOpenDrawer) {
            drawerManager?.show(
                historyId = historyId,
                side = config.currentModeConfig().drawerSide,
                widthPercent = config.permissions.drawerSettings.widthPercent,
                showMetadata = false
            )
        }

        var currentQueryText = ""
        var currentResultText = ""
        var pendingUpdateJob: Job? = null

        fun scheduleUpdate() {
            if (pendingUpdateJob?.isActive == true) return
            pendingUpdateJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
                delay(500)
                historyRepository.updateHistoryItem(historyId) { current ->
                    current.copy(
                        query = currentQueryText.ifEmpty { current.query },
                        result = currentResultText.ifEmpty { current.result }
                    )
                }
            }
        }

        try {
            val result = pipeline?.processTextOnly(
                config = config,
                capturedText = text,
                onSummaryGenerated = { title, summary ->
                    lifecycle.coroutineScope.launch {
                        historyRepository.updateHistoryItem(historyId) { current ->
                            current.copy(title = title, summary = summary)
                        }
                    }
                },
                onQueryExtracted = { delta ->
                    lifecycle.coroutineScope.launch(Dispatchers.Main) {
                        currentQueryText = delta
                        drawerManager?.setLiveQuery(delta)
                        scheduleUpdate()
                    }
                },
                onDelta = { delta ->
                    lifecycle.coroutineScope.launch(Dispatchers.Main) {
                        currentResultText += delta
                        drawerManager?.appendLiveResult(delta)
                        scheduleUpdate()
                    }
                },
                onSetClipboard = { text ->
                    lifecycle.coroutineScope.launch(Dispatchers.Main) {
                        SystemUtils.copyToClipboard(this@MainService, text)
                    }
                },
                onShowBubble = { text ->
                    lifecycle.coroutineScope.launch(Dispatchers.Main) {
                        floatingBallManager?.showText(text)
                    }
                }
            )
            result?.let { handleProcessingResult(it, historyId, config, pendingUpdateJob) }
        } catch (e: CancellationException) {
            drawerManager?.hide()
            cleanupScope.launch {
                historyRepository.updateHistoryItem(historyId) { current ->
                    current.copy(
                        query = "用户已取消", result = "用户已取消", status = AnalysisStatus.CANCELLED
                    )
                }
            }
            throw e
        }
    }

    private suspend fun processImageContent(image: android.graphics.Bitmap, config: AppConfig) {
        historyRepository.deleteProcessingItems()

        val models = pipeline?.resolveModels(config) ?: return
        val initialResult = pipeline?.createBaseResult(models, image, "正在获取题目...") ?: return
        val historyId = initialResult.id
        currentHistoryId = historyId
        val historyItem = HistoryItem(
            id = historyId,
            query = "正在思考中...",
            result = "正在思考中...",
            imagePath = initialResult.screenshotPath,
            mode = UniversalMode.id,
            assistantName = initialResult.assistantName,
            providerName = initialResult.modelSummary,
            modelName = initialResult.modelSummary,
            engineName = config.selectedEngine.displayName,
            status = AnalysisStatus.PROCESSING
        )
        historyRepository.addHistoryItem(historyItem)

        try {
            if (config.currentModeConfig().autoOpenDrawer) {
                drawerManager?.show(
                    historyId = historyId,
                    side = config.currentModeConfig().drawerSide,
                    widthPercent = config.permissions.drawerSettings.widthPercent,
                    showMetadata = false
                )
            }

            var currentQueryText = ""
            var currentResultText = ""
            var pendingUpdateJob: Job? = null

            fun scheduleUpdate() {
                if (pendingUpdateJob?.isActive == true) return
                pendingUpdateJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
                    delay(500)
                    historyRepository.updateHistoryItem(historyId) { current ->
                        current.copy(
                            query = currentQueryText.ifEmpty { current.query },
                            result = currentResultText.ifEmpty { current.result }
                        )
                    }
                }
            }

            try {
                val result = pipeline?.process(
                    config = config,
                    bitmap = image,
                    onSummaryGenerated = { title, summary ->
                        lifecycle.coroutineScope.launch {
                            historyRepository.updateHistoryItem(historyId) { current ->
                                current.copy(title = title, summary = summary)
                            }
                        }
                    },
                    onQueryExtracted = { delta ->
                        lifecycle.coroutineScope.launch(Dispatchers.Main) {
                            currentQueryText = delta
                            drawerManager?.setLiveQuery(delta)
                            scheduleUpdate()
                        }
                    },
                    onDelta = { delta ->
                        lifecycle.coroutineScope.launch(Dispatchers.Main) {
                            currentResultText += delta
                            drawerManager?.appendLiveResult(delta)
                            scheduleUpdate()
                        }
                    },
                    onSetClipboard = { text ->
                        lifecycle.coroutineScope.launch(Dispatchers.Main) {
                            SystemUtils.copyToClipboard(this@MainService, text)
                        }
                    },
                    onShowBubble = { text ->
                        lifecycle.coroutineScope.launch(Dispatchers.Main) {
                            floatingBallManager?.showText(text)
                        }
                    }
                )
                result?.let { handleProcessingResult(it, historyId, config, pendingUpdateJob) }
            } finally {
                image.recycle()
            }
        } catch (e: CancellationException) {
            drawerManager?.hide()
            cleanupScope.launch {
                historyRepository.updateHistoryItem(historyId) { current ->
                    current.copy(
                        query = "用户已取消",
                        result = "用户已取消",
                        status = AnalysisStatus.CANCELLED
                    )
                }
            }
            throw e
        }
    }

    private fun handleCaptureFailure(config: AppConfig) {
        val captureHint = when (config.permissions.captureMode) {
            CaptureMode.SYSTEM -> "请确认已授予屏幕录制权限"
            CaptureMode.SHIZUKU -> "请确认 Shizuku 已连接并授权"
            else -> "截图失败，请检查截屏权限设置"
        }
        android.util.Log.e("SolveX", "截图失败: $captureHint")
        lifecycle.coroutineScope.launch {
            _serviceError.emit(captureHint)
        }
        floatingBallManager?.updateStatus(BallStatus.ERROR)
        SystemUtils.vibrateError(this@MainService)
        drawerManager?.hide()

        if (config.currentModeConfig().allowNotification) {
            NotificationUtils.sendResultNotification(this@MainService, "截图失败", captureHint)
        }
    }

    private fun handleGeneralError(e: Exception) {
        android.util.Log.e("SolveX", "流程异常", e)
        floatingBallManager?.updateStatus(BallStatus.ERROR)
        SystemUtils.vibrateError(this@MainService)
        drawerManager?.hide()

        currentHistoryId?.let { historyId ->
            lifecycle.coroutineScope.launch {
                historyRepository.updateHistoryItem(historyId) { current ->
                    current.copy(
                        title = current.title ?: "解析失败",
                        result = SseStreamClient.translateNetworkException(e),
                        status = AnalysisStatus.FAILURE
                    )
                }
            }
        }

        lifecycle.coroutineScope.launch {
            try {
                val config = repository.appConfigFlow.first()
                if (config.currentModeConfig().allowNotification) {
                    NotificationUtils.sendResultNotification(
                        this@MainService,
                        "解析异常",
                        SseStreamClient.translateNetworkException(e)
                    )
                }
            } catch (_: Exception) { }
        }
    }

    private fun switchEngine() {
        lifecycle.coroutineScope.launch {
            val config = repository.appConfigFlow.first()
            // 无障碍取字模式下禁止切换引擎
            if (config.permissions.captureMode == CaptureMode.TEXT_ONLY) return@launch
            val newEngine = if (config.selectedEngine == EngineType.VISION_ENGINE) {
                EngineType.TEXT_ENGINE
            } else {
                EngineType.VISION_ENGINE
            }
            repository.saveAppConfig(config.copy(selectedEngine = newEngine))
            SystemUtils.vibrate(this@MainService, 100)
        }
    }

    private fun switchSearch() {
        lifecycle.coroutineScope.launch {
            val config = repository.appConfigFlow.first()
            val newEnabled = !config.webSearch.enabled
            repository.saveAppConfig(config.copy(
                webSearch = config.webSearch.copy(enabled = newEnabled)
            ))
            SystemUtils.vibrate(this@MainService, 100)
        }
    }

    /**
     * 统一处理解析结果：状态更新、通知、自动化动作、数据库持久化。
     */
    private suspend fun handleProcessingResult(
        result: ProcessingResult,
        historyId: String,
        config: AppConfig,
        pendingUpdateJob: Job?
    ) {
        pendingUpdateJob?.cancelAndJoin()
        drawerManager?.clearLiveBuffer()

        if (result.status == ProcessingStatus.SUCCESS) {
            SystemUtils.vibrateSuccess(this@MainService)

            // 统一结果投递：提取最终答案 → 复制到剪贴板
            val finalAnswer = NotificationUtils.extractFinalAnswer(result.answer ?: "")
            if (finalAnswer.isNotBlank()) {
                SystemUtils.deliverResult(this@MainService, finalAnswer)
            }
            floatingBallManager?.updateStatus(BallStatus.SUCCESS)

            val currentHistory = historyRepository.historyItemsFlow.first().find { it.id == historyId }
            if (config.currentModeConfig().allowNotification) {
                val notifyTitle = currentHistory?.title ?: "解析完成"
                val rawAnswer = result.answer ?: "已获取最终答案"
                NotificationUtils.sendResultNotification(
                    this@MainService, notifyTitle,
                    NotificationUtils.extractFinalAnswer(rawAnswer), historyId
                )
            }
            historyRepository.updateHistoryItem(historyId) { current ->
                current.copy(
                    query = result.extractedText ?: current.query,
                    result = result.answer ?: "已获取最终答案",
                    status = AnalysisStatus.SUCCESS,
                    toolCalls = result.toolCalls
                )
            }
        } else {
            floatingBallManager?.updateStatus(BallStatus.ERROR)
            SystemUtils.vibrateError(this@MainService)
            drawerManager?.hide()

            if (config.currentModeConfig().allowNotification) {
                NotificationUtils.sendResultNotification(
                    this@MainService, "解析失败", result.detail, historyId
                )
            }
            historyRepository.updateHistoryItem(historyId) { current ->
                current.copy(
                    title = current.title ?: "解析失败",
                    result = result.detail,
                    status = AnalysisStatus.FAILURE
                )
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        (application as SolveXApplication).viewModel?.checkPermissions()
        stopStealthMonitor()
        captureEngine?.release()
        captureEngine = null
        menuController?.dismiss()
        floatingBallManager?.hide()
        currentHistoryId?.let { id ->
            cleanupScope.launch {
                historyRepository.updateHistoryItem(id) { current ->
                    if (current.status == AnalysisStatus.PROCESSING) {
                        current.copy(
                            query = "用户已取消",
                            result = "用户已取消",
                            status = AnalysisStatus.CANCELLED
                        )
                    } else current
                }
            }
        }
        processingJob?.cancel()
        viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val isQuick = intent.getBooleanExtra(EXTRA_IS_QUICK_START, false)
                _isRegularMode.value = !isQuick
                startAsForeground(intent)
            }
            ACTION_STOP -> stopSelf()
            NotificationUtils.ACTION_VIEW_HISTORY -> {
                val historyId = intent.getStringExtra(NotificationUtils.EXTRA_HISTORY_ID)
                if (historyId != null) {
                    lifecycle.coroutineScope.launch {
                        val config = repository.appConfigFlow.first()
                        drawerManager?.show(
                            historyId = historyId,
                            side = config.currentModeConfig().drawerSide,
                            widthPercent = config.permissions.drawerSettings.widthPercent,
                            showMetadata = false
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 以前台服务形式启动，根据截屏模式创建对应引擎。
     */
    private fun startAsForeground(intent: Intent?) {
        createNotificationChannel()

        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: CaptureMode.SYSTEM

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SolveX 运行中")
            .setContentText("AI 也会犯错，不要过度相信！")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        var fgsType = 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (captureMode == CaptureMode.SYSTEM) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        }

        startForeground(NOTIFICATION_ID, notification, fgsType)
        // 根据截屏模式创建引擎
        captureEngine?.release()
        captureEngine = when (captureMode) {
            CaptureMode.SHIZUKU -> ShizukuCaptureEngine(this)
            CaptureMode.TEXT_ONLY -> null
            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                @Suppress("DEPRECATION")
                val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

                if (resultCode != 0 && data != null) {
                    SystemCaptureEngine(this, resultCode, data).also { engine ->
                        lifecycle.coroutineScope.launch {
                            delay(100)
                            engine.prepare()
                        }
                    }
                } else null
            }
        }

        floatingBallManager?.show()
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "后台核心服务",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 SolveX 在后台运行以进行屏幕解析"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)
    }
}
