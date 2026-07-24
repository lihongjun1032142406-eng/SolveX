package com.tianhuiu.solvex.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.BuildConfig
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.DownloadStatus
import com.tianhuiu.solvex.data.models.UpdateLevel
import com.tianhuiu.solvex.data.models.VersionInfo
import com.tianhuiu.solvex.network.UpdateManager
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 版本更新 ViewModel：检查更新、下载和安装。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val updateManager = UpdateManager(
        application,
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
        Json { ignoreUnknownKeys = true }
    )

    var updateInfo by mutableStateOf<VersionInfo?>(null)
        private set

    var downloadStatus by mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
        private set

    var isCheckingUpdate by mutableStateOf(false)
        private set

    var showDialogManually by mutableStateOf(false)
        private set

    // 当前会话中是否已关闭弹窗
    var isDismissedInSession by mutableStateOf(false)
        private set

    // 是否为刚刚检查出的更新（用于控制推荐更新的自动弹窗）
    var isFreshUpdate by mutableStateOf(false)
        private set

    /**
     * 启动时恢复缓存更新信息，按策略检测更新。
     */
    fun initialize() {
        viewModelScope.launch {
            isFreshUpdate = false
            repository.cachedVersionFlow.first()?.let { cachedJson ->
                updateManager.parseCachedVersion(cachedJson)?.let { cachedInfo ->
                    if (cachedInfo.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = cachedInfo
                    }
                }
            }
            
            // 每次启动都执行静默检查
            checkForUpdates(manual = false)
        }
    }

    /**
     * 检查更新：ETag 条件请求 + 缓存兜底。
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) {
                isCheckingUpdate = true
                showDialogManually = true
                isDismissedInSession = false
            }

            val savedEtag = repository.updateEtagFlow.first()

            updateManager.checkUpdate(etag = savedEtag)
                .onSuccess { (info, newEtag) ->
                    if (manual) isCheckingUpdate = false
                    repository.saveLastUpdateCheck(System.currentTimeMillis())
                    if (newEtag != null) repository.saveUpdateEtag(newEtag)
                    repository.saveCachedVersion(updateManager.encodeVersion(info))

                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = info
                        isFreshUpdate = true
                        
                        // 判断是否需要自动弹出
                        if (!manual) {
                            val lastDismissed = repository.lastUpdateDialogDismissedFlow.first()
                            val sevenDaysMillis = TimeUnit.DAYS.toMillis(7)
                            val isExpired = (System.currentTimeMillis() - lastDismissed) > sevenDaysMillis
                            
                            // 强制更新始终显示，推荐更新仅在超过 7 天或从未关闭过时弹出
                            if (info.updateLevel == UpdateLevel.CRITICAL || isExpired || lastDismissed == 0L) {
                                isDismissedInSession = false
                                showDialogManually = true
                            } else {
                                // 冷却期内，不弹窗，仅保持 updateInfo 用于红点显示
                                isDismissedInSession = true
                                showDialogManually = false
                            }
                        } else {
                            isDismissedInSession = false
                            showDialogManually = true
                        }

                        repository.saveConsecutiveNoUpdate(0)
                    } else {
                        updateInfo = null
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (manual) SystemUtils.showToast(getApplication(), "当前已是最新版本")
                    }
                }
                .onFailure { error ->
                    if (manual) isCheckingUpdate = false

                    if (error is UpdateManager.NotModifiedException) {
                        repository.saveLastUpdateCheck(System.currentTimeMillis())
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (updateInfo != null && updateInfo!!.versionCode > BuildConfig.VERSION_CODE) {
                            isFreshUpdate = true
                        }

                        if (manual) SystemUtils.showToast(getApplication(), "当前已是最新版本")
                        return@launch
                    }

                    if (manual) {
                        val cachedJson = repository.cachedVersionFlow.first()
                        if (cachedJson != null) {
                            val cachedInfo = updateManager.parseCachedVersion(cachedJson)
                            if (cachedInfo != null && cachedInfo.versionCode > BuildConfig.VERSION_CODE) {
                                updateInfo = cachedInfo
                                isFreshUpdate = true
                                isDismissedInSession = false
                                SystemUtils.showToast(getApplication(), "网络不可用，请稍后再试")
                                return@launch
                            }
                        }
                        val message = error.message ?: "未知错误"
                        SystemUtils.showFeedback(
                            getApplication(),
                            userMessage = "检查更新失败",
                            detailedLog = "Update check failed: $message"
                        )
                    }
                }
        }
    }

    /**
     * 下载并安装 APK。
     */
    fun startUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            updateManager.downloadApk(info.githubUrl, info.giteeUrl).collect { status ->
                downloadStatus = status
                if (status is DownloadStatus.Success) {
                    updateManager.installApk(status.apkPath)
                }
            }
        }
    }

    /**
     * 关闭更新弹窗。
     */
    fun dismissUpdateDialog() {
        if (updateInfo?.isDismissible != true) return
        showDialogManually = false
        isDismissedInSession = true
        downloadStatus = DownloadStatus.Idle
        
        // 记录关闭时间
        viewModelScope.launch {
            repository.saveLastUpdateDialogDismissed(System.currentTimeMillis())
        }
    }
}
