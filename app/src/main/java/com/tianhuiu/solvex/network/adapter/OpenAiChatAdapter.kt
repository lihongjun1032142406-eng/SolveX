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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Chat Completions API 适配器。
 */
class OpenAiChatAdapter(
    private val client: OkHttpClient,
    internal val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        // 构建 OpenAI Chat Completions 请求体
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        if (msg.role == "tool") {
                            put("tool_call_id", msg.toolCallId ?: "")
                            put("content", msg.content)
                        } else if (msg.role == "assistant" && msg.toolCalls != null) {
                            put("content", msg.content)
                            putJsonArray("tool_calls") {
                                msg.toolCalls.forEach { tc ->
                                    addJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", tc.arguments)
                                        }
                                    }
                                }
                            }
                        } else if (msg.role == "user" && request.imagesBase64.isNotEmpty() && msg == request.messages.lastOrNull { it.role == "user" }) {
                            // 仅最后一条用户消息携带图片（简化处理）
                            put("content", buildJsonArray {
                                addJsonObject { put("type", "text"); put("text", msg.content) }
                                request.imagesBase64.forEach { img ->
                                    addJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$img") })
                                    }
                                }
                            })
                        } else {
                            put("content", msg.content)
                        }
                    }
                }
            }
            request.tools?.let { tools ->
                put("tools", ToolRegistry.formatForProvider(tools, ProviderKind.OPENAI_COMPATIBLE))
                put("tool_choice", "auto")
            }
        }

        val httpRequest = Request.Builder()
            .url("${request.provider.url}/chat/completions")
            .header("Authorization", "Bearer ${request.provider.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val toolCallsMap = mutableMapOf<Int, MutableToolCall>()

        val job = launch {
            try {
                sseClient.stream(
                    request = httpRequest,
                    firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
                    onEvent = { _, _, _, data ->
                        if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                        try {
                            val root = json.parseToJsonElement(data).jsonObject
                            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@stream null
                            val delta = choice["delta"]?.jsonObject ?: return@stream null
                            
                            var emittedDelta: String? = null

                            // 1. 文本增量解析
                            val contentElement = delta["content"]
                            if (contentElement is kotlinx.serialization.json.JsonPrimitive && !contentElement.isString && contentElement.content == "null") {
                                // 忽略 JSON null
                            } else if (contentElement is kotlinx.serialization.json.JsonPrimitive) {
                                val text = contentElement.content
                                if (text.isNotEmpty() && text != "null") {
                                    emittedDelta = text
                                }
                            }

                            // 2. 工具调用增量解析
                            delta["tool_calls"]?.jsonArray?.forEach { tcElement ->
                                val tc = tcElement.jsonObject
                                val index = (tc["index"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                                val entry = toolCallsMap.getOrPut(index) { MutableToolCall() }
                                
                                (tc["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { entry.id.append(it) }
                                val func = tc["function"]?.jsonObject
                                (func?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { entry.name.append(it) }
                                (func?.get("arguments") as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { entry.args.append(it) }
                            }

                            return@stream SseStreamClient.StreamEventResult(
                                delta = emittedDelta,
                                done = false
                            )
                        } catch (e: Exception) {
                            Log.e("OpenAiChatAdapter", "解析错误: $data", e)
                        }
                        null
                    },
                    onDelta = { trySend(LlmEvent.TextDelta(it)) },
                    onToolCall = { /* 通过 Done 触发 */ }
                )
                
                // 检查并发送所有解析出的工具调用
                toolCallsMap.values.forEach { tc ->
                    if (tc.name.isNotEmpty()) {
                        val argsMap = try {
                            val parsed = json.parseToJsonElement(tc.args.toString()).jsonObject
                            parsed.mapValues { it.value.jsonPrimitive.content }
                        } catch (_: Exception) {
                            mapOf("query" to tc.args.toString())
                        }
                        trySend(LlmEvent.ToolCall(
                            id = tc.id.toString(),
                            name = tc.name.toString(),
                            arguments = argsMap
                        ))
                    }
                }

                trySend(LlmEvent.Done); close()
            } catch (e: Exception) {
                trySend(LlmEvent.Error(e.message ?: "未知错误")); close(e)
            }
        }
        awaitClose { job.cancel() }
    }

    private class MutableToolCall {
        val id = StringBuilder()
        val name = StringBuilder()
        val args = StringBuilder()
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
