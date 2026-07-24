package com.tianhuiu.solvex.network.tools

import com.tianhuiu.solvex.data.models.ProcessingEvent
import com.tianhuiu.solvex.network.ToolDef

/**
 * 工具执行上下文。
 */
data class ToolContext(
    val workflowId: String = "",
    val nodeId: String = ""
)

/**
 * 工具执行结果。
 */
data class ToolResult(
    val text: String,
    val events: List<ProcessingEvent> = emptyList(),
    val summary: String? = null,
    val data: String? = null // 结构化详情 (JSON)
)

/**
 * 工具接口。
 *
 * 每个工具实现此接口以封装具体的执行逻辑。
 */
interface AgentTool {
    val name: String
    val definition: ToolDef

    suspend fun invoke(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult
}
