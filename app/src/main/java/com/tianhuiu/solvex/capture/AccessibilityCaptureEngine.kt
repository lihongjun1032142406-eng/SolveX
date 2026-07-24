package com.tianhuiu.solvex.capture

import android.graphics.Bitmap
import com.tianhuiu.solvex.service.SolveXAccessibilityService

/**
 * 基于无障碍服务的截屏引擎。
 *
 * 通过无障碍服务 API（[TakeScreenshotCallback]）截取屏幕内容，
 * 仅需无障碍权限即可工作，无需 MediaProjection 或 Shizuku。
 *
 * 注意：仅在无障碍服务已连接并处于活跃状态时可用，
 * 需通过 [SolveXAccessibilityService.instance] 访问服务实例。
 */
class AccessibilityCaptureEngine : ScreenCaptureEngine {

    /**
     * 通过无障碍服务截取当前屏幕。
     *
     * @return 屏幕截图 Bitmap，服务不可用或失败时返回 null
     */
    override suspend fun capture(): Bitmap? {
        return SolveXAccessibilityService.instance?.takeScreenshotCompat()
    }

    override fun release() {
        // 无需资源释放
    }
}
