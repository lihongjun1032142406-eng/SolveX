package com.tianhuiu.solvex.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tianhuiu.solvex.R

/**
 * 通知工具类。
 *
 * 负责发送解析结果通知及结果文本提取。
 * 提供结构化题目渲染（JSON → Markdown）和最终答案正则提取功能。
 */
object NotificationUtils {
    private const val CHANNEL_ID = "solvex_result_channel"
    private const val CHANNEL_NAME = "解析结果通知"

    const val ACTION_VIEW_HISTORY = "com.tianhuiu.solvex.VIEW_HISTORY"
    const val EXTRA_HISTORY_ID = "history_id"

    private val finalAnswerPatterns = listOf(
        // 提取以 ### 最终 开头的 Markdown 三级标题段落
        Regex("""###\s*最终.*?\s*\n+(.*)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        // 降级匹配 最终答案：xxxx
        Regex("""最终答案[：:]\s*(.*)""", setOf(RegexOption.DOT_MATCHES_ALL))
    )

    private val latexDetectPattern = Regex(
        """\$|\\\[|\\\(|\\begin\{|\\frac|\\sqrt|\\sum|\\int|\\alpha|\\beta|\\gamma|\\theta|\\pi|\\infty"""
    )

    /**
     * 检测内容是否包含 LaTeX 或数学公式标记。
     *
     * @param text 待检测文本
     * @return 是否包含 LaTeX 标记
     */
    fun hasLatex(text: String): Boolean = latexDetectPattern.containsMatchIn(text)

    /**
     * 从完整解析结果中提取最终答案文本。
     *
     * 按优先级依次匹配预定义的正则模式，匹配失败则降级返回前 100 字符。
     *
     * @param fullAnswer AI 返回的完整解析内容
     * @return 提取到的最终答案文本
     */
    fun extractFinalAnswer(fullAnswer: String): String {
        for (pattern in finalAnswerPatterns) {
            val match = pattern.find(fullAnswer)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotBlank()) {
                    return extracted
                }
            }
        }
        return fullAnswer.take(100) // 降级返回前 100 字
    }

    /**
     * 将 JSON 格式的题目渲染为 Markdown。
     * 如果不是 JSON 格式，则原样返回。
     */
    fun renderStructuredQuestion(rawText: String): String {
        val structured = AutomationTools.parseStructuredQuestion(rawText) ?: return rawText

        val content = buildString {
            if (!structured.question.isNullOrBlank()) {
                append("${structured.question}\n\n")
            }
            if (!structured.options.isNullOrEmpty()) {
                structured.options.forEach { option ->
                    append("- $option\n")
                }
                append("\n")
            }
            if (!structured.image_analysis.isNullOrBlank()) {
                append("> **图片补充分析**：${structured.image_analysis}\n")
            }
        }.trim()

        return content.ifBlank { "（内容提取为空，请检查截图或重试）" }
    }

    /**
     * 发送一条解析结果系统通知
     */
    fun sendResultNotification(
        context: Context,
        title: String,
        content: String,
        historyId: String? = null
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "展示解题的最终答案"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val viewIntent =
            Intent(context, com.tianhuiu.solvex.service.MainService::class.java).apply {
                if (historyId != null) {
                    action = ACTION_VIEW_HISTORY
                    putExtra(EXTRA_HISTORY_ID, historyId)
                }
            }
        val viewPendingIntent = PendingIntent.getService(
            context, (historyId ?: "error").hashCode(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val finalContent = if (hasLatex(content)) {
            "点击查看详情"
        } else {
            content
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(finalContent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalContent))
            .addAction(0, "查看", viewPendingIntent)
            .setContentIntent(viewPendingIntent)
            .build()

        notificationManager.notify(historyId.hashCode(), notification)
    }
}
