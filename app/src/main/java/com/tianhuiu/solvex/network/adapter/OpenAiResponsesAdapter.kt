package com.tianhuiu.solvex.network.adapter

import android.util.Log
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.network.LlmEvent
import com.tianhuiu.solvex.network.ProviderAdapter
import com.tianhuiu.solvex.network.SseStreamClient
import com.tianhuiu.solvex.network.StreamRequest
import com.tianhuiu.solvex.network.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Responses API 适配器
 */
class OpenAiResponsesAdapter(
    private val client: OkHttpClient,
    internal val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        // 构建 OpenAI Responses API 请求体
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            val systemMsg = request.messages.find { it.role == "system" }
            if (systemMsg != null) {
                put("instructions", systemMsg.content)
            }
            putJsonArray("input") {
                request.messages.filter { it.role != "system" }.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == "assistant") "assistant" else "user")
                        put("content", buildJsonArray {
                            if (msg.role == "user" && request.imagesBase64.isNotEmpty() && msg == request.messages.lastOrNull { it.role == "user" }) {
                                request.imagesBase64.forEach { img ->
                                    addJsonObject {
                                        put("type", "input_image")
                                        put("image_url", "data:image/jpeg;base64,$img")
                                    }
                                }
                            }
                            add(buildJsonObject {
                                put("type", "input_text")
                                put("text", msg.content)
                            })
                        })
                    }
                }
            }
            request.tools?.let { tools ->
                put("tools", ToolRegistry.formatForProvider(tools, ProviderKind.OPENAI_RESPONSES))
            }
        }

        val httpRequest = Request.Builder()
            .url("${request.provider.url}/responses")
            .header("Authorization", "Bearer ${request.provider.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val job = launch {
            try {
                sseClient.stream(
                    request = httpRequest,
                    firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
                    onEvent = { _, eventType, _, data ->
                        if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                        try {
                            // 优先使用 SSE event 类型判断
                            when (eventType) {
                                "response.output_text.delta" -> {
                                    val delta =
                                        json.parseToJsonElement(data).jsonObject["delta"]?.jsonPrimitive?.content
                                    if (delta != null) return@stream SseStreamClient.StreamEventResult(
                                        delta = delta
                                    )
                                }

                                "response.completed" -> {
                                    return@stream SseStreamClient.StreamEventResult(done = true)
                                }
                            }
                            // 兜底：通过 JSON 中的 type 字段判断
                            val obj = json.parseToJsonElement(data).jsonObject
                            val type = obj["type"]?.jsonPrimitive?.content
                            when (type) {
                                "response.output_text.delta" -> {
                                    val delta = obj["delta"]?.jsonPrimitive?.content
                                    if (delta != null) return@stream SseStreamClient.StreamEventResult(
                                        delta = delta
                                    )
                                }

                                "response.completed" -> {
                                    return@stream SseStreamClient.StreamEventResult(done = true)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("OpenAiResponsesAdapter", "解析错误: $data", e)
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

    override suspend fun fetchModels(provider: ModelProvider): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${provider.url}/models")
                .header("Authorization", "Bearer ${provider.apiKey}")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val jsonObject = json.parseToJsonElement(body).jsonObject
                    jsonObject["data"]?.jsonArray?.asSequence()
                        ?.map { it.jsonObject["id"]?.jsonPrimitive?.content ?: "" }
                        ?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
}
