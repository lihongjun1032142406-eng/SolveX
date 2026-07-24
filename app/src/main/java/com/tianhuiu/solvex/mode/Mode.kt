package com.tianhuiu.solvex.mode

import androidx.compose.ui.graphics.vector.ImageVector
import com.tianhuiu.solvex.data.models.DrawerSide
import kotlinx.serialization.Serializable

/**
 * 模式统一契约。
 *
 * 所有解析模式（通用模式、速查模式等）均实现此接口。
 * 每个模式可定义默认配置、是否需要裁剪、以及关联的 UI 图标。
 */
interface Mode {
    /** 唯一标识符，用于持久化和导航 */
    val id: String
    /** 展示给用户的名称 */
    val displayName: String
    /** 模式简介说明 */
    val description: String
    /** 设置页面显示的图标 */
    val icon: ImageVector
    /** 是否默认启用截图裁剪 */
    val shouldCrop: Boolean
    /** 默认模式配置 */
    fun defaultConfig(): ModeConfig
}

/**
 * 模式运行时配置。
 *
 * 控制通知、悬浮窗 Toast、抽屉行为、OCR/文本/视觉模型
 * 以及首次超时时间等参数。
 *
 * @property allowNotification 是否允许发送系统通知
 * @property showFloatingToast 是否在悬浮窗显示 Toast 提示
 * @property autoOpenDrawer 是否在解析开始后自动打开侧边抽屉
 * @property drawerSide 抽屉弹出侧边（左侧/右侧）
 * @property enableCrop 是否启用截图裁剪（null 表示使用 [Mode.shouldCrop] 默认值）
 * @property firstDeltaTimeoutSeconds 首条流式响应超时时间（秒）
 */
@Serializable
data class ModeConfig(
    val allowNotification: Boolean = true,
    val showFloatingToast: Boolean = true,
    val autoOpenDrawer: Boolean = true,
    val drawerSide: DrawerSide = DrawerSide.LEFT,
    val enableCrop: Boolean? = null,
    val ocrProviderId: String? = null,
    val ocrModel: String? = null,
    val textProviderId: String? = null,
    val textModel: String? = null,
    val visionProviderId: String? = null,
    val visionModel: String? = null,
    val firstDeltaTimeoutSeconds: Long = 10,
)
