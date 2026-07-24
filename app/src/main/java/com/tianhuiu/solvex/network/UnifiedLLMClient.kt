package com.tianhuiu.solvex.network

import android.util.Log
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.network.adapter.AnthropicAdapter
import com.tianhuiu.solvex.network.adapter.GoogleAdapter
import com.tianhuiu.solvex.network.adapter.OpenAiChatAdapter
import com.tianhuiu.solvex.network.adapter.OpenAiResponsesAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * 模型适配器接口：所有 LLM 提供商适配器需实现此接口。
 */
interface ProviderAdapter {
    /** 发起流式推理请求，返回 LLM 事件流 */
    suspend fun stream(request: StreamRequest): Flow<LlmEvent>

    /** 获取可用模型列表 */
    suspend fun fetchModels(provider: ModelProvider): List<String>
}

/**
 * 统一 LLM 客户端：根据提供商类型分发推理请求至对应适配器。
 */
class UnifiedLLMClient(
    val client: OkHttpClient,
    val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val sseClient = SseStreamClient(client)

    private val adapters = mutableMapOf<ProviderKind, ProviderAdapter>()

    private fun getAdapter(kind: ProviderKind): ProviderAdapter = adapters.getOrPut(kind) {
        when (kind) {
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiChatAdapter(client, sseClient, json)
            ProviderKind.OPENAI_RESPONSES -> OpenAiResponsesAdapter(client, sseClient, json)
            ProviderKind.ANTHROPIC -> AnthropicAdapter(client, sseClient, json)
            ProviderKind.GOOGLE -> GoogleAdapter(client, sseClient, json)
        }
    }

    /**
     * 发起流式推理请求，根据 provider.type 路由到对应适配器。
     */
    suspend fun stream(
        provider: ModelProvider,
        model: String,
        messages: List<LlmMessage>,
        imagesBase64: List<String> = emptyList(),
        tools: List<ToolDef>? = null,
        firstDeltaTimeoutMillis: Long = 30000,
    ): Flow<LlmEvent> {
        val adapter = getAdapter(provider.type)
        return adapter.stream(
            StreamRequest(
                provider = provider,
                model = model,
                messages = messages,
                imagesBase64 = imagesBase64,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis
            )
        )
    }

    /**
     * 获取指定提供商的可用模型列表。
     */
    suspend fun fetchModels(provider: ModelProvider): List<String> {
        val adapter = getAdapter(provider.type)
        return try {
            adapter.fetchModels(provider)
        } catch (e: Exception) {
            Log.e("UnifiedLLMClient", "获取模型列表失败", e)
            emptyList()
        }
    }
}
