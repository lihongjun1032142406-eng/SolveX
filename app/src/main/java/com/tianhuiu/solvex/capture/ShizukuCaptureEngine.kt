package com.tianhuiu.solvex.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 Shizuku ADB 截屏引擎。
 *
 * 通过 Shizuku 授权执行 `screencap -p` 命令获取屏幕像素数据，
 * 无需 MediaProjection 弹窗授权，适配需要后台静默截屏的场景。
 *
 * @property context Android 上下文，用于 Shizuku 用户服务绑定
 */
class ShizukuCaptureEngine(private val context: Context) : ScreenCaptureEngine {

    /**
     * 执行 ADB screencap 命令并解析为 Bitmap。
     *
     * @return 屏幕截图 Bitmap，失败时返回 null
     */
    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = ShizukuShellScreencap.capturePng(context) ?: return@withContext null
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun release() {
        // 不主动释放 Shizuku 用户服务绑定，daemon 进程可跨服务启停复用。
    }
}
