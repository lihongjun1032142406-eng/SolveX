package com.tianhuiu.solvex.network.adapter

import android.util.Log
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.network.LlmEvent
import com.tianhuiu.solvex.network.ProviderAdapter
import com.tianhuiu.solvex.network.SseStreamClient
import com.tianhuiu.solvex.network.StreamRequest
import com.tianhuiu.solvex.network.ToolRegistry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic Messages API 适配器。
 */
class AnthropicAdapter(
    private val client: OkHttpClient,
    internal val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        // 构建 Anthropic Messages API 请求体
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", 4096)
            val systemMsg = request.messages.find { it.role == "system" }
            if (systemMsg != null) {
                put("system", systemMsg.content)
            }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != "system" }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == "assistant") "assistant" else "user")
                        put("content", buildJsonArray {
                            if (msg.role == "user" && request.imagesBase64.isNotEmpty() && msg == request.messages.lastOrNull { it.role == "user" }) {
                                request.imagesBase64.forEach { img ->
                                    add(buildJsonObject {
                                        put("type", "image")
                                        put("source", buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", "image/jpeg")
                                            put("data", img)
                                        })
                                    })
                                }
                            }
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", msg.content)
                            })
                        })
                    })
                }
            })
            request.tools?.let { tools ->
                put("tools", ToolRegistry.formatForProvider(tools, ProviderKind.ANTHROPIC))
            }
        }

        val httpRequest = Request.Builder()
            .url("${request.provider.url}/messages")
            .header("x-api-key", request.provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val job = launch {
            try {
                sseClient.stream(
                    request = httpRequest,
                    firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
                    onEvent = { _, _, _, data ->
                        try {
                            val obj = json.parseToJsonElement(data).jsonObject
                            val eventType = obj["type"]?.jsonPrimitive?.content
                            when (eventType) {
                                // 文本增量事件
                                "content_block_delta" -> {
                                    val delta = obj["delta"]?.jsonObject
                                    val text = delta?.get("text")?.jsonPrimitive?.content
                                    if (text != null) return@stream SseStreamClient.StreamEventResult(
                                        delta = text
                                    )
                                }
                                // 消息结束事件
                                "message_stop" -> return@stream SseStreamClient.StreamEventResult(
                                    done = true
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("AnthropicAdapter", "解析错误: $data", e)
                        }
                        null
                    },
                    onDelta = { trySend(LlmEvent.TextDelta(it)) },
                    onToolCall = { trySend(it) }
                )
                trySend(LlmEvent.Done); close()
            } catch (e: Exception) {
                trySend(LlmEvent.Error(e.message ?: "未知错误")); close(e)
            }
        }
        awaitClose { job.cancel() }
    }

    /** Anthropic 无公开模型列表接口，返回常用模型 */
    override suspend fun fetchModels(provider: ModelProvider): List<String> =
        listOf("claude-opus-4-7", "claude-sonnet-4-6", "claude-haiku-4-5")
}
