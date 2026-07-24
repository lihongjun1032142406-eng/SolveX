package com.tianhuiu.solvex.network.tools

import com.tianhuiu.solvex.data.models.WebSearchSettings
import com.tianhuiu.solvex.network.ToolDef
import com.tianhuiu.solvex.network.ToolParam
import com.tianhuiu.solvex.network.search.SearchOrchestrator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 联网搜索工具。
 */
class WebSearchAgentTool(
    private val orchestrator: SearchOrchestrator,
    private val settings: WebSearchSettings
) : AgentTool {
    override val name: String = "web_search"

    override val definition: ToolDef = ToolDef(
        name = "web_search",
        description = "搜索互联网获取最新事实、新闻、政策、赛事、产品、版本或其他时效性信息",
        params = listOf(
            ToolParam(
                name = "query",
                type = "string",
                description = "2到8个用于联网搜索的关键词或短语"
            )
        )
    )

    override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val outcome = orchestrator.execute(query, settings)
        
        val jsonData = if (outcome.results.isNotEmpty()) {
            kotlinx.serialization.json.Json.encodeToString(outcome.results)
        } else null

        return ToolResult(
            text = outcome.formattedText,
            summary = query.ifBlank { "搜索" },
            data = jsonData
        )
    }
}
