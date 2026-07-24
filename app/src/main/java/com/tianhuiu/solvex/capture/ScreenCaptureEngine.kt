package com.tianhuiu.solvex.capture

import android.graphics.Bitmap

/**
 * 截屏引擎统一接口。
 *
 * 所有截屏方式（系统录屏、Shizuku ADB、无障碍服务）均实现此接口，
 * 调用方无需感知底层实现差异。
 */
interface ScreenCaptureEngine {

    /**
     * 初始化截屏所需资源，如建立投影会话、连接远程服务等。
     * prepare 保证幂等，多次调用不会重复创建资源。
     */
    suspend fun prepare() {}

    /**
     * 执行一次截屏操作。
     *
     * @return 截屏结果的 Bitmap，失败时返回 null
     */
    suspend fun capture(): Bitmap?

    /**
     * 释放所有持有的资源，包括投影会话、屏幕截图读取器、远程服务绑定等。
     * 调用后引擎不可再用，需重新 prepare。
     */
    fun release()
}
