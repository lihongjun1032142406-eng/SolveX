package com.tianhuiu.solvex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tianhuiu.solvex.render.MarkdownLatexText

/**
 * 核心数学渲染组件。
 * 已升级为原生渲染引擎（对齐 HanakoAI 标准），移除 WebView 以获得极致流畅度。
 */
@Composable
fun MathView(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: Float = 1.4f, // 保持兼容性，但 native 引擎由 style 决定
    forceMarkdown: Boolean = false, // 保持兼容性
    onRendered: (() -> Unit)? = null
) {
    MarkdownLatexText(
        content = text,
        modifier = modifier
    )
    
    // 原生渲染几乎是瞬时的，直接回调完成
    onRendered?.invoke()
}
