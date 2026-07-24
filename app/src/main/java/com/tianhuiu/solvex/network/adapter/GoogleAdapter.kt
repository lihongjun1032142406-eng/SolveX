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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Gemini API 适配器。
 */
class GoogleAdapter(
    private val client: OkHttpClient,
    internal val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        // 构建 Gemini API 请求体
        val body = buildJsonObject {
            val systemMsg = request.messages.find { it.role == "system" }
            if (systemMsg != null) {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemMsg.content) })
                    })
                })
            }
            put("contents", buildJsonArray {
                request.messages.filter { it.role != "system" }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray {
                            if (msg.role == "user" && request.imagesBase64.isNotEmpty() && msg == request.messages.lastOrNull { it.role == "user" }) {
                                request.imagesBase64.forEach { img ->
                                    add(buildJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", "image/jpeg")
                                            put("data", img)
                                        })
                                    })
                                }
                            }
                            if (msg.role == "tool") {
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", msg.toolName ?: "unknown")
                                        put("response", buildJsonObject {
                                            put("content", msg.content)
                                        })
                                    })
                                })
                            } else if (msg.role == "assistant" && msg.toolCalls != null) {
                                msg.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("functionCall", buildJsonObject {
                                            put("name", tc.name)
                                            put("args", try {
                                                json.parseToJsonElement(tc.arguments).jsonObject
                                            } catch (_: Exception) {
                                                buildJsonObject { put("query", tc.arguments) }
                                            })
                                        })
                                    })
                                }
                            } else {
                                if (msg.content.isNotBlank()) {
                                    add(buildJsonObject { put("text", msg.content) })
                                }
                            }
                        })
                    })
                }
            })
            request.tools?.let { tools ->
                put("tools", buildJsonArray {
                    add(ToolRegistry.formatForProvider(tools, ProviderKind.GOOGLE))
                })
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "AUTO")
                    })
                })
            }
        }

        val httpRequest = Request.Builder()
            .url("${request.provider.url}/models/${request.model}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", request.provider.apiKey)
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
                            val candidates = obj["candidates"]?.jsonArray
                            val candidate = candidates?.firstOrNull()?.jsonObject
                            val content = candidate?.get("content")?.jsonObject
                            val parts = content?.get("parts")?.jsonArray ?: return@stream null
                            
                            // 1. 文本增量
                            val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                            if (text != null) return@stream SseStreamClient.StreamEventResult(delta = text)
                            
                            // 2. 工具调用
                            val funcCall = parts.firstOrNull()?.jsonObject?.get("functionCall")?.jsonObject
                            if (funcCall != null) {
                                val name = funcCall["name"]?.jsonPrimitive?.content ?: ""
                                val args = funcCall["args"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                                return@stream SseStreamClient.StreamEventResult(
                                    toolCall = LlmEvent.ToolCall(
                                        id = "gemini_${System.currentTimeMillis()}",
                                        name = name,
                                        arguments = args
                                    )
                                )
                            }

                            // 检查是否已完成
                            candidate["finishReason"]?.jsonPrimitive?.let { finish ->
                                if (finish.content == "STOP") {
                                    return@stream SseStreamClient.StreamEventResult(done = true)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GoogleAdapter", "解析错误: $data", e)
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
                .url("${provider.url}/models?key=${provider.apiKey}")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val jsonObject = json.parseToJsonElement(body).jsonObject
                    jsonObject["models"]?.jsonArray?.asSequence()
                        ?.map {
                            it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                                ?: ""
                        }
                        ?.filter { it.isNotEmpty() && !it.contains("embedding") }
                        ?.toList() ?: emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
}
