package com.tianhuiu.solvex.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProcessingEvent
import com.tianhuiu.solvex.data.models.ProcessingResult
import com.tianhuiu.solvex.data.models.ProcessingRoute
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.ToolCallRecord
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.network.search.SearchOrchestrator
import com.tianhuiu.solvex.network.tools.AgentTool
import com.tianhuiu.solvex.network.tools.SetClipboardTool
import com.tianhuiu.solvex.network.tools.ShowBubbleLettersTool
import com.tianhuiu.solvex.network.tools.ToolContext
import com.tianhuiu.solvex.network.tools.WebSearchAgentTool
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.DateTimeUtils
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * SolveX 核心处理管道。
 * 负责协调截图、OCR/多模态提取、Agent 工具调用以及最终答案生成的完整生命周期。
 * 
 * 核心流程设计：
 * 1. [resolveModels]：根据全局配置静态解析当前任务所需的模型路径。
 * 2. [process]：执行主要解析流，包括摘要并行生成、结构化内容提取、Agent 工具决策循环。
 * 3. [runAnswerWithTools]：实现多轮对话循环，支持 LLM 根据需求自动调用联网搜索或自动化工具。
 */
class ProcessingPipeline(
    private val appContext: Context,
    private val unifiedClient: UnifiedLLMClient,
) {
    private val searchOrchestrator = SearchOrchestrator(unifiedClient.client, unifiedClient.json)

    /**
     * 任务解析所需的模型上下文快照。
     */
    data class ResolvedModels(
        val assistant: AssistantConfig,
        val textProvider: ModelProvider?,
        val textModel: String,
        val visionProvider: ModelProvider?,
        val visionModel: String,
        val ocrProvider: ModelProvider?,
        val ocrModel: String,
        val firstDeltaTimeoutMillis: Long,
        val engine: EngineType,
    )

    /**
     * 注入时效性信息及核心 Agent 决策逻辑的系统提示词构建。
     */
    private fun getSystemPrompt(base: String): String {
        val timeStr = DateTimeUtils.formatFull(System.currentTimeMillis())
        return "$base\n\n当前时间：$timeStr\n\n你可以调用工具联网搜索最新事实。当你不知道答案或答案随时长变化时，优先调用搜索工具。不要向用户解释你要搜索，直接调用工具。"
    }

    /**
     * 根据 [AppConfig] 动态计算各处理阶段的最优模型路径。
     * 支持“手动指定模型”优先于“默认提供商”的降级逻辑。
     */
    fun resolveModels(config: AppConfig): ResolvedModels {
        val assistant = config.assistants.find { it.id == config.selectedAssistantId }
            ?: config.assistants.firstOrNull()
            ?: error("请先配置助手")

        val c = config.currentModeConfig()
        val defaultPid = config.defaultProviderId

        val finalTextPid = c.textProviderId ?: defaultPid
        val finalVisionPid = c.visionProviderId ?: defaultPid
        val finalOcrPid = c.ocrProviderId ?: defaultPid

        val textProvider = config.providers.find { it.id == finalTextPid }
        val visionProvider = config.providers.find { it.id == finalVisionPid }
        val ocrProvider = config.providers.find { it.id == finalOcrPid }

        val resolvedTextModel = if (c.textModel.isNullOrBlank()) {
            textProvider?.defaultTextModel ?: ""
        } else c.textModel

        val resolvedVisionModel = if (c.visionModel.isNullOrBlank()) {
            visionProvider?.defaultVisionModel ?: ""
        } else c.visionModel

        val resolvedOcrModel = if (c.ocrModel.isNullOrBlank()) {
            ocrProvider?.defaultOcrModel ?: ""
        } else c.ocrModel

        return ResolvedModels(
            assistant = assistant,
            textProvider = textProvider,
            textModel = resolvedTextModel,
            visionProvider = visionProvider,
            visionModel = resolvedVisionModel,
            ocrProvider = ocrProvider,
            ocrModel = resolvedOcrModel,
            firstDeltaTimeoutMillis = c.firstDeltaTimeoutSeconds.coerceAtLeast(1) * 1000L,
            engine = config.selectedEngine
        )
    }

    /**
     * 构建包含提供商名称的展示型摘要。
     */
    private fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isNullOrBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    /**
     * 在处理开始前持久化原始截图并初始化结果存根。
     */
    fun createBaseResult(
        models: ResolvedModels,
        bitmap: Bitmap,
        detail: String
    ): ProcessingResult {
        val historyId = UUID.randomUUID().toString()
        val imagePath = FileUtils.saveBitmapToInternal(appContext, bitmap)

        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = if (models.engine == EngineType.TEXT_ENGINE) ProcessingRoute.OCR_THEN_LLM else ProcessingRoute.MULTIMODAL_DIRECT,
            status = ProcessingStatus.RUNNING,
            modelSummary = if (models.engine == EngineType.TEXT_ENGINE)
                buildModelSummary(models.textModel, models.textProvider?.name)
            else
                buildModelSummary(models.visionModel, models.visionProvider?.name),
            detail = detail,
            screenshotPath = imagePath,
            screenshotPaths = listOfNotNull(imagePath),
            events = listOf(ProcessingEvent(title = "请求开始", detail = "已创建处理记录"))
        )
    }

    /**
     * 初始化针对纯文本捕获路径的解析结果存根。
     */
    fun createBaseResultTextOnly(
        models: ResolvedModels,
        detail: String
    ): ProcessingResult {
        val historyId = UUID.randomUUID().toString()
        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = ProcessingRoute.OCR_THEN_LLM,
            status = ProcessingStatus.RUNNING,
            modelSummary = buildModelSummary(models.textModel, models.textProvider?.name),
            detail = detail,
            screenshotPath = null,
            screenshotPaths = emptyList(),
            events = listOf(ProcessingEvent(title = "文本捕获", detail = "已通过无障碍服务获取屏幕文本"))
        )
    }

    private data class CollectResult(
        val text: String,
        val toolCalls: List<ToolCallRecord>
    )

    /**
     * 启动基于截图的自动化处理流。
     * 
     * @param config 当前全局配置
     * @param bitmap 待解析的屏幕截图
     * @param onSummaryGenerated 当生成的标题/摘要准备好时的回调
     * @param onQueryExtracted 当结构化题目被初步提取时的流式回调
     * @param onDelta 主要分析结果的流式输出回调
     */
    suspend fun process(
        config: AppConfig,
        bitmap: Bitmap,
        onSummaryGenerated: (String, String) -> Unit = { _, _ -> },
        onQueryExtracted: (String) -> Unit = {},
        onDelta: (String) -> Unit,
        onSetClipboard: (String) -> Unit = {},
        onShowBubble: (String) -> Unit = {}
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val models = resolveModels(config)
        val base = createBaseResult(models, bitmap, "正在获取题目...")
        val imageBase64 = bitmap.toBase64Jpeg()

        coroutineScope {
            try {
                // [并行任务] 生成 UI 用的极简标题和摘要
                val summaryDeferred = async {
                    try {
                        val summaryText = collectTextStream(
                            provider = models.visionProvider ?: models.ocrProvider
                            ?: error("未配置视觉或文本模型"),
                            model = if (models.visionProvider != null) models.visionModel else models.ocrModel,
                            messages = listOf(
                                LlmMessage(role = "system", content = Prompts.SUMMARY_SYSTEM_PROMPT),
                                LlmMessage(role = "user", content = "请为此截图生成标题和摘要。")
                            ),
                            imagesBase64 = listOf(imageBase64),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
                        )
                        summaryText.text.let { notUsed ->
                            parseSummary(notUsed)?.let { (title, summary) ->
                                onSummaryGenerated(title, summary)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // [串行主任务 1/2] 结构化内容提取（OCR 或 Vision）
                val extractionSystemPrompt = if (models.assistant.useStructuredExtraction) {
                    "${Prompts.EXTRACTION_SYSTEM_BASE}\n用户配置：${models.assistant.ocrPrompt}"
                } else {
                    models.assistant.ocrPrompt
                }

                val extractionUserPrompt = if (models.assistant.useStructuredExtraction) {
                    if (models.engine == EngineType.TEXT_ENGINE) Prompts.OCR_EXTRACTION_USER_PROMPT else Prompts.VISION_EXTRACTION_USER_PROMPT
                } else {
                    "请提取屏幕中的所有文本内容。"
                }

                val extractedText = collectTextStream(
                    provider = (if (models.engine == EngineType.TEXT_ENGINE) models.ocrProvider else models.visionProvider) ?: error("模型未就绪"),
                    model = if (models.engine == EngineType.TEXT_ENGINE) models.ocrModel else models.visionModel,
                    messages = listOf(
                        LlmMessage(role = "system", content = getSystemPrompt(extractionSystemPrompt)),
                        LlmMessage(role = "user", content = extractionUserPrompt)
                    ),
                    imagesBase64 = listOf(imageBase64),
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    onDelta = onQueryExtracted
                ).text

                // 题目有效性验证逻辑
                val isEffectivelyEmpty = if (models.assistant.useStructuredExtraction) {
                    val structured = AutomationTools.parseStructuredQuestion(extractedText)
                    structured == null || (structured.question.isNullOrBlank() &&
                                          structured.options.isNullOrEmpty() &&
                                          structured.image_analysis.isNullOrBlank())
                } else {
                    extractedText.isBlank()
                }

                if (isEffectivelyEmpty) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未发现可处理的有效内容，请确保截图包含清晰的信息",
                        extractedText = extractedText.ifBlank { "（未识别到内容）" }
                    )
                }

                // [串行主任务 2/2] Agent 决策与深度解析
                val answerResult = runAnswerWithTools(
                    models = models,
                    config = config,
                    systemPrompt = if (models.engine == EngineType.TEXT_ENGINE)
                        getSystemPrompt("${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.textPrompt}")
                    else
                        getSystemPrompt("${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.visionPrompt}"),
                    userPrompt = if (models.engine == EngineType.TEXT_ENGINE)
                        "以下是 OCR 结果，请完成任务：\n$extractedText"
                    else
                        "请直接基于图片内容完成任务。文字提取参考：\n$extractedText",
                    imagesBase64 = if (models.engine == EngineType.TEXT_ENGINE) emptyList() else listOf(imageBase64),
                    textProvider = models.textProvider,
                    textModel = models.textModel,
                    visionProvider = models.visionProvider,
                    visionModel = models.visionModel,
                    engine = models.engine,
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    onDelta = onDelta,
                    onSetClipboard = onSetClipboard,
                    onShowBubble = onShowBubble
                )

                summaryDeferred.await()

                if (answerResult.text.isBlank() && extractedText.isBlank()) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "模型返回空内容，请检查模型配置是否正确"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = extractedText,
                    answer = answerResult.text,
                    detail = "分析完成",
                    toolCalls = answerResult.toolCalls
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                base.copy(status = ProcessingStatus.FAILURE, detail = SseStreamClient.translateNetworkException(e))
            }
        }
    }

    /**
     * 纯文本捕获路径。适用于“无障碍取字”模式。
     */
    suspend fun processTextOnly(
        config: AppConfig,
        capturedText: String,
        onSummaryGenerated: (String, String) -> Unit = { _, _ -> },
        onQueryExtracted: (String) -> Unit = {},
        onDelta: (String) -> Unit,
        onSetClipboard: (String) -> Unit = {},
        onShowBubble: (String) -> Unit = {}
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val models = resolveModels(config)
        val base = createBaseResultTextOnly(models, "正在分析文本内容...")

        coroutineScope {
            try {
                onQueryExtracted(capturedText)

                val summaryDeferred = async {
                    try {
                        val summaryText = collectTextStream(
                            provider = models.textProvider ?: models.ocrProvider
                                ?: error("未配置文本模型"),
                            model = models.textModel.ifBlank { models.ocrModel },
                            messages = listOf(
                                LlmMessage(role = "system", content = Prompts.SUMMARY_SYSTEM_PROMPT),
                                LlmMessage(role = "user", content = "请为以下文本生成标题和摘要。\n\n$capturedText")
                            ),
                            imagesBase64 = emptyList(),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
                        )
                        parseSummary(summaryText.text)?.let { (title, summary) ->
                            onSummaryGenerated(title, summary)
                        }
                    } catch (_: Exception) { }
                }

                if (capturedText.isBlank()) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未从屏幕中提取到文本内容",
                        extractedText = ""
                    )
                }

                val analysisPrompt = if (models.assistant.useStructuredExtraction) {
                    "${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.textPrompt}"
                } else {
                    models.assistant.textPrompt
                }

                val answerResult = runAnswerWithTools(
                    models = models,
                    config = config,
                    systemPrompt = getSystemPrompt(analysisPrompt),
                    userPrompt = "以下是从屏幕视图中提取的内容，请完成任务：\n$capturedText",
                    imagesBase64 = emptyList(),
                    textProvider = models.textProvider,
                    textModel = models.textModel,
                    visionProvider = models.visionProvider,
                    visionModel = models.visionModel,
                    engine = EngineType.TEXT_ENGINE,
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    onDelta = onDelta,
                    onSetClipboard = onSetClipboard,
                    onShowBubble = onShowBubble
                )

                summaryDeferred.await()

                if (answerResult.text.isBlank() && capturedText.isBlank()) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "模型返回空内容，请检查模型配置是否正确"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = capturedText,
                    answer = answerResult.text,
                    detail = "分析完成",
                    toolCalls = answerResult.toolCalls
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                base.copy(status = ProcessingStatus.FAILURE, detail = SseStreamClient.translateNetworkException(e))
            }
        }
    }

    /**
     * 辅助方法：解析 LLM 生成的标准格式摘要文本。
     */
    private fun parseSummary(text: String): Pair<String, String>? {
        return try {
            val title = text.lines()
                .find { it.startsWith("Title:", ignoreCase = true) }
                ?.removePrefix("Title:")?.trim()
            val summary = text.lines()
                .find { it.startsWith("Summary:", ignoreCase = true) }
                ?.removePrefix("Summary:")?.trim()

            if ((title != null) && (summary != null)) title to summary else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Agent 工具决策循环逻辑。
     * 支持最多 4 轮迭代，直到模型停止请求工具调用。
     */
    private suspend fun runAnswerWithTools(
        models: ResolvedModels,
        config: AppConfig,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        textProvider: ModelProvider?,
        textModel: String,
        visionProvider: ModelProvider?,
        visionModel: String,
        engine: EngineType,
        firstDeltaTimeoutMillis: Long,
        onDelta: (String) -> Unit,
        onSetClipboard: (String) -> Unit,
        onShowBubble: (String) -> Unit
    ): CollectResult {
        val tools = buildTools(config, onSetClipboard, onShowBubble)
        val provider = if (engine == EngineType.TEXT_ENGINE) textProvider ?: error("未配置文本模型")
        else visionProvider ?: error("未配置视觉模型")
        val model = if (engine == EngineType.TEXT_ENGINE) textModel else visionModel

        val messages = mutableListOf(
            LlmMessage(role = "system", content = systemPrompt),
            LlmMessage(role = "user", content = userPrompt)
        )

        val toolDefs = tools.map { it.definition }.takeIf { it.isNotEmpty() }
        val allToolCalls = mutableListOf<ToolCallRecord>()
        val fullContent = StringBuilder()

        repeat(4) { loopIndex ->
            val collectedDelta = StringBuilder()
            val turnToolCalls = mutableListOf<ToolCallRecord>()

            unifiedClient.stream(
                provider = provider,
                model = model,
                messages = messages,
                imagesBase64 = imagesBase64,
                tools = toolDefs,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis
            ).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LlmEvent.TextDelta -> {
                        val delta = event.text
                        if (delta.isNotBlank() && delta != "null") {
                            collectedDelta.append(delta)
                            onDelta(delta)
                        }
                    }
                    is LlmEvent.ToolCall -> {
                        turnToolCalls.add(ToolCallRecord(
                            id = event.id,
                            name = event.name,
                            arguments = unifiedClient.json.encodeToString(event.arguments)
                        ))
                    }
                    else -> {}
                }
            }

            val content = collectedDelta.toString()
            fullContent.append(content)
            
            if (turnToolCalls.isEmpty()) {
                return CollectResult(fullContent.toString(), allToolCalls)
            }

            // 维护对话上下文
            messages.add(LlmMessage(role = "assistant", content = content, toolCalls = turnToolCalls))

            // 执行本地或远程工具
            turnToolCalls.forEach { tc ->
                val tool = tools.find { it.name == tc.name }
                val toolResult = if (tool != null) {
                    try {
                        val argsJson = unifiedClient.json.parseToJsonElement(tc.arguments).jsonObject
                        tool.invoke(argsJson, ToolContext())
                    } catch (e: Exception) {
                        com.tianhuiu.solvex.network.tools.ToolResult("执行错误: ${e.message}")
                    }
                } else {
                    com.tianhuiu.solvex.network.tools.ToolResult("找不到工具: ${tc.name}")
                }

                val recordedCall = tc.copy(
                    result = toolResult.text,
                    metadata = toolResult.data
                )
                allToolCalls.add(recordedCall)

                messages.add(LlmMessage(
                    role = "tool",
                    content = toolResult.text,
                    toolCallId = tc.id,
                    toolName = tc.name
                ))
            }

            if (loopIndex == 3) {
                return CollectResult(fullContent.toString(), allToolCalls)
            }
        }

        return CollectResult(fullContent.toString(), allToolCalls)
    }

    /**
     * 动态构建工具定义列表。
     */
    private fun buildTools(
        config: AppConfig,
        onSetClipboard: (String) -> Unit,
        onShowBubble: (String) -> Unit
    ): List<AgentTool> {
        val list = mutableListOf<AgentTool>()
        if (config.webSearch.enabled) {
            list.add(WebSearchAgentTool(searchOrchestrator, config.webSearch))
        }
        list.add(SetClipboardTool(onSetClipboard))
        list.add(ShowBubbleLettersTool(onShowBubble))
        return list
    }

    /**
     * 低级流式文本收集器。
     */
    private suspend fun collectTextStream(
        provider: ModelProvider,
        model: String,
        messages: List<LlmMessage>,
        imagesBase64: List<String> = emptyList(),
        firstDeltaTimeoutMillis: Long,
        tools: List<ToolDef>? = null,
        onDelta: (String) -> Unit = {}
    ): CollectResult {
        val text = StringBuilder(4096)
        val toolCalls = mutableListOf<ToolCallRecord>()
        unifiedClient.stream(
            provider = provider,
            model = model,
            messages = messages,
            imagesBase64 = imagesBase64,
            tools = tools,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis
        ).collect { event ->
            currentCoroutineContext().ensureActive()
            when (event) {
                is LlmEvent.TextDelta -> {
                    val delta = event.text
                    if (delta != "null") {
                        text.append(delta)
                        onDelta(delta)
                    }
                }
                is LlmEvent.ToolCall -> {
                    toolCalls.add(ToolCallRecord(
                        id = event.id,
                        name = event.name,
                        arguments = unifiedClient.json.encodeToString(event.arguments),
                        result = ""
                    ))
                }
                is LlmEvent.Done -> {}
                is LlmEvent.Error -> throw Exception(event.message)
            }
        }
        return CollectResult(text = text.toString(), toolCalls = toolCalls)
    }
}

/**
 * Bitmap 转换为符合 AI 接口要求的 Base64 JPEG 字符串。
 */
private fun Bitmap.toBase64Jpeg(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
