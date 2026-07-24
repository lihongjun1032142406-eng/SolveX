package com.tianhuiu.solvex.network

import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ToolCallRecord
import kotlinx.serialization.Serializable

/**
 * 工具参数定义。
 */
@Serializable
data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val pattern: String? = null
)

/**
 * 外部工具/函数定义。
 */
@Serializable
data class ToolDef(
    val name: String,
    val description: String,
    val params: List<ToolParam>
)

/**
 * 大模型对话消息。
 */
@Serializable
data class LlmMessage(
    val role: String, // system, user, assistant, tool
    val content: String,
    val toolCallId: String? = null, // 仅用于 tool 角色
    val toolName: String? = null, // 仅用于 tool 角色 (兼容部分提供商)
    val toolCalls: List<ToolCallRecord>? = null // 仅用于 assistant 角色
)

/**
 * 大模型响应事件流。
 */
sealed class LlmEvent {
    data class TextDelta(val text: String) : LlmEvent()
    data class ToolCall(val id: String, val name: String, val arguments: Map<String, String>) : LlmEvent()
    data object Done : LlmEvent()
    data class Error(val message: String) : LlmEvent()
}

/**
 * 推理流请求参数。
 */
data class StreamRequest(
    val provider: ModelProvider,
    val model: String,
    val messages: List<LlmMessage>,
    val imagesBase64: List<String> = emptyList(),
    val tools: List<ToolDef>? = null,
    val firstDeltaTimeoutMillis: Long
)
