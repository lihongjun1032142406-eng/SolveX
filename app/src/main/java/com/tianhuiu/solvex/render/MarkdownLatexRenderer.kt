package com.tianhuiu.solvex.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

@Composable
fun MarkdownLatexText(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val parsed = remember(content) { parseMarkdown(content) }

    ProvideTextStyle(style) {
        Column(modifier = modifier) {
            splitDisplayMathBlocks(parsed.preprocessed).fastForEach { block ->
                when (block) {
                    is MarkdownRenderBlock.DisplayMath -> LatexBlock(
                        latex = block.latex,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    is MarkdownRenderBlock.Markdown -> {
                        val astTree = remember(block.content) {
                            markdownParser.buildMarkdownTreeFromString(block.content)
                        }
                        astTree.children.fastForEach { child ->
                            MarkdownNode(
                                node = child,
                                content = block.content
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> node.children.fastForEach { child -> MarkdownNode(child, content) }
        MarkdownElementTypes.PARAGRAPH -> Paragraph(node = node, content = content)
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> Heading(node = node, content = content)
        MarkdownElementTypes.UNORDERED_LIST -> ListNode(node = node, content = content, ordered = false)
        MarkdownElementTypes.ORDERED_LIST -> ListNode(node = node, content = content, ordered = true)
        MarkdownElementTypes.BLOCK_QUOTE -> QuoteBlock(node = node, content = content)
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            thickness = 1.dp
        )
        MarkdownElementTypes.INLINE_LINK -> LinkNode(node = node, content = content)
        MarkdownElementTypes.IMAGE -> ImageNode(node = node, content = content)
        GFMElementTypes.TABLE -> MarkdownTable(node = node, content = content)
        GFMElementTypes.INLINE_MATH -> LatexInline(latex = node.getText(content), modifier = Modifier.padding(horizontal = 1.dp))
        GFMElementTypes.BLOCK_MATH -> LatexBlock(latex = node.getText(content), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        MarkdownElementTypes.CODE_SPAN -> CodeSpan(node = node, content = content)
        MarkdownElementTypes.CODE_FENCE -> CodeFence(node = node, content = content)
        MarkdownTokenTypes.TEXT -> Text(text = node.getText(content))
        else -> node.children.fastForEach { child -> MarkdownNode(child, content) }
    }
}

@Composable
private fun Heading(
    node: ASTNode,
    content: String
) {
    val style = when (node.type) {
        MarkdownElementTypes.ATX_1 -> MaterialTheme.typography.headlineLarge
        MarkdownElementTypes.ATX_2 -> MaterialTheme.typography.headlineMedium
        MarkdownElementTypes.ATX_3 -> MaterialTheme.typography.headlineSmall
        MarkdownElementTypes.ATX_4 -> MaterialTheme.typography.titleLarge
        MarkdownElementTypes.ATX_5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    ProvideTextStyle(style) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                Paragraph(node = child, content = content, trim = true)
            }
        }
    }
}

@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    trim: Boolean = false
) {
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current
    val inlineContents = remember(node) { mutableMapOf<String, InlineTextContent>() }

    val annotated = remember(content, node, trim) {
        buildAnnotatedString {
            node.children.fastForEach { child ->
                appendMarkdownNodeContent(
                    node = child,
                    content = content,
                    trim = trim,
                    inlineContents = inlineContents,
                    density = density,
                    style = textStyle
                )
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContents,
        style = textStyle
    )
}

private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean,
    inlineContents: MutableMap<String, InlineTextContent>,
    density: Density,
    style: TextStyle
) {
    when {
        node is LeafASTNode -> {
            val text = node.getText(content).let { if (trim) it.trim() else it }
            if (node.type !in MarkdownInlineDelimiters) {
                append(text)
            }
        }
        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, density, style) }
            }
        }
        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, density, style) }
            }
        }
        node.type == GFMElementTypes.INLINE_MATH -> {
            val formula = node.getText(content)
            val placeholder = with(density) {
                assumeLatexSize(formula, style.fontSize.toPx()).let { rect ->
                    Placeholder(
                        width = rect.width().toFloat().toSp(),
                        height = rect.height().toFloat().toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                }
            }
            inlineContents[formula] = InlineTextContent(placeholder) {
                LatexInline(latex = formula)
            }
            appendInlineContent(formula, "[math]")
        }
        else -> node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, density, style) }
    }
}

private val MarkdownInlineDelimiters = setOf(
    MarkdownTokenTypes.EMPH,
    MarkdownTokenTypes.BACKTICK,
    MarkdownTokenTypes.LBRACKET,
    MarkdownTokenTypes.RBRACKET,
    MarkdownTokenTypes.LPAREN,
    MarkdownTokenTypes.RPAREN
)

@Composable
private fun QuoteBlock(node: ASTNode, content: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        node.children.fastForEach { child -> MarkdownNode(node = child, content = content) }
    }
}

@Composable
private fun ListNode(node: ASTNode, content: String, ordered: Boolean) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type != MarkdownElementTypes.LIST_ITEM) return@fastForEach
            val bullet = if (ordered) "${index++}. " else "• "
            Row {
                Text(text = bullet, color = MaterialTheme.colorScheme.primary)
                Column {
                    child.children.fastForEach { MarkdownNode(it, content) }
                }
            }
        }
    }
}

@Composable
private fun CodeSpan(node: ASTNode, content: String) {
    Text(
        text = node.getText(content).trim('`'),
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun CodeFence(node: ASTNode, content: String) {
    val code = extractCodeFenceContent(node, content)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = code, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun LinkNode(node: ASTNode, content: String) {
    val linkText = node.getText(content)
    Text(text = linkText, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
}

@Composable
private fun ImageNode(node: ASTNode, content: String) {
    // 占位，SolveX 目前主要处理文本
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(100.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text("图片内容", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MarkdownTable(node: ASTNode, content: String) {
    // 极简表格实现
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text("表格数据 (请查看详情)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
