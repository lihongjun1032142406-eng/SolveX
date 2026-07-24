package com.tianhuiu.solvex.data.models

import com.tianhuiu.solvex.mode.ModeConfig
import com.tianhuiu.solvex.mode.ModeRegistry
import kotlinx.serialization.Serializable

/**
 * 模型提供方类型：定义不同的 API 适配器。
 */
@Serializable
enum class ProviderKind(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI Compatible"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Gemini"),
}

/**
 * 识别引擎类型：决定处理流程。
 */
@Serializable
enum class EngineType(val displayName: String) {
    TEXT_ENGINE("文本引擎"),
    VISION_ENGINE("视觉引擎"),
}

/**
 * 模型提供方配置。
 */
@Serializable
data class ModelProvider(
    val id: String,
    val type: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val name: String,
    val url: String,
    val apiKey: String,
    val availableModels: List<String> = emptyList(),
    val defaultOcrModel: String = "",
    val defaultTextModel: String = "",
    val defaultVisionModel: String = "",
)

/**
 * 智能助手身份配置。
 */
@Serializable
data class AssistantConfig(
    val id: String,
    val name: String,
    val ocrPrompt: String,
    val textPrompt: String,
    val visionPrompt: String,
    val useStructuredExtraction: Boolean = true,
)

/**
 * 抽屉弹出侧边。
 */
@Serializable
enum class DrawerSide(val displayName: String) {
    LEFT("左侧"),
    RIGHT("右侧")
}

/**
 * 抽屉设置。
 */
@Serializable
data class DrawerSettings(
    val side: DrawerSide = DrawerSide.LEFT,
    val widthPercent: Float = 0.9f // 0.3 to 0.9
)

/**
 * 悬浮球外观设置。
 */
@Serializable
data class FloatingBallAppearance(
    val diameterDp: Float = 42f,
    val spinnerDiameterDp: Float = 52f,
    val textSizeSp: Float = 14f,
    val overallOpacity: Float = 1.0f,
    val contentOpacity: Float = 1.0f,
    val enableMenu: Boolean = true,
    val enabledMenuItems: Set<String> = setOf("engine", "search", "settings", "baidu"),
)

/**
 * 权限及基础设置。
 */
@Serializable
data class PermissionSettings(
    val enableAutoHideBall: Boolean = true,
    val captureMode: String = CaptureMode.SYSTEM,
    val drawerSettings: DrawerSettings = DrawerSettings(),
    val appearance: FloatingBallAppearance = FloatingBallAppearance(),
    val enableScreenProtection: Boolean = false,
    val enableStealthMode: Boolean = false,
    val hasShownStealthWarning: Boolean = false,
    val isFirstLaunchSetupComplete: Boolean = false,
)

/**
 * 截屏模式常量。
 */
object CaptureMode {
    const val SYSTEM = "system"
    const val SHIZUKU = "shizuku"
    const val TEXT_ONLY = "text_only"

    fun toDisplayName(mode: String?): String = when (mode) {
        SHIZUKU -> "Shizuku ADB"
        SYSTEM -> "系统录屏"
        TEXT_ONLY -> "无障碍取字"
        else -> mode ?: "未知"
    }
}

/**
 * 权限引导步骤，按推荐顺序排列。
 */
enum class PermissionSetupStep(
    val displayName: String,
    val description: String,
    val isOptional: Boolean = false,
) {
    OVERLAY("显示悬浮球", "允许在屏幕边缘显示一个小球，方便您随时呼唤 AI"),
    NOTIFICATION("接收通知", "当 AI 解析完成后，通过通知提醒您查看结果", isOptional = true),
    ACCESSIBILITY("开启无障碍服务", "这是“无障碍取字”模式的核心，能让 AI 直接阅读屏幕文字"),
    BATTERY("允许后台运行", "防止系统为了省电偷偷关闭 SolveX，保证服务稳定不掉线"),
    SHIZUKU("Shizuku 授权", "最稳定、最高效的授权方式，推荐高阶用户使用"),
    DONE("配置完成", "太棒了！SolveX 已准备就绪，点击下方按钮开始使用吧"),
}

/**
 * 全局应用配置根对象。
 */
@Serializable
data class AppConfig(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
    val permissions: PermissionSettings = PermissionSettings(),
    val defaultProviderId: String? = null,
    val selectedAssistantId: String? = null,
    val selectedEngine: EngineType = EngineType.VISION_ENGINE,
    val modeConfig: ModeConfig = ModeRegistry.getUniversal().defaultConfig(),
    val autoScrollContent: Boolean = true,
    val webSearch: WebSearchSettings = WebSearchSettings(),
    val trustAllCertificates: Boolean = false,
)

/**
 * 搜索引擎类型。
 */
@Serializable
enum class SearchProviderKind(val displayName: String) {
    TAVILY("Tavily"),
    SERPER("Serper.dev"),
    BRAVE("Brave Search"),
    CUSTOM("自定义 (Tavily 兼容)")
}

/**
 * 搜索引擎配置。
 */
@Serializable
data class SearchProviderConfig(
    val id: String,
    val name: String,
    val kind: SearchProviderKind = SearchProviderKind.TAVILY,
    val baseUrl: String = "",
    val apiKey: String = ""
)

/**
 * 联网搜索设置。
 */
@Serializable
data class WebSearchSettings(
    val enabled: Boolean = false,
    val providers: List<SearchProviderConfig> = emptyList(),
    val selectedProviderId: String? = null,
    val maxResults: Int = 3,
)

// 获取当前模式配置
fun AppConfig.currentModeConfig(): ModeConfig {
    return modeConfig
}
