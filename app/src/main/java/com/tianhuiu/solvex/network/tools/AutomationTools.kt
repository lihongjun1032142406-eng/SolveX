package com.tianhuiu.solvex.network.tools

import com.tianhuiu.solvex.network.ToolDef
import com.tianhuiu.solvex.network.ToolParam
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 剪贴板工具。
 */
class SetClipboardTool(
    private val onSet: (String) -> Unit
) : AgentTool {
    override val name: String = "set_clipboard"
    override val definition = ToolDef(
        name = "set_clipboard",
        description = "将最终答案写入系统剪贴板，方便用户粘贴",
        params = listOf(ToolParam("text", "string", "需要复制到剪贴板的纯文本内容"))
    )

    override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (text.isNotBlank()) {
            onSet(text)
        }
        return ToolResult(
            text = "已写入剪贴板",
            summary = "写入剪贴板: ${text.take(10)}"
        )
    }
}

/**
 * 悬浮球展示工具。
 */
class ShowBubbleLettersTool(
    private val onShow: (String) -> Unit
) : AgentTool {
    override val name: String = "show_bubble_letters"
    override val definition = ToolDef(
        name = "show_bubble_letters",
        description = "在悬浮球内展示 1-8 个关键字母或符号（如选项 A、B 或 对、错）",
        params = listOf(ToolParam("text", "string", "1-8个英文字母或对、错、√、×"))
    )

    override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (text.isNotBlank()) {
            onShow(text)
        }
        return ToolResult(
            text = "已在悬浮球展示",
            summary = "展示文字: $text"
        )
    }
}
