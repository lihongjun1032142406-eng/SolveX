package com.tianhuiu.solvex.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 历史记录的解析状态。
 */
@Serializable
enum class AnalysisStatus(val displayName: String) {
    SUCCESS("已完成"),
    FAILURE("失败"),
    CANCELLED("已取消"),
    PROCESSING("处理中"),
}

/**
 * 工具调用记录。
 *
 * @property id 工具调用唯一 ID (API 返回)
 * @property name 工具名称
 * @property arguments 调用参数，JSON 字符串
 * @property result 工具执行结果摘要，空表示尚未执行或正在执行
 * @property metadata 结构化详情数据 (如搜索结果 JSON)，供 UI 模板化显示
 */
@Serializable
data class ToolCallRecord(
    val id: String = "",
    val name: String,
    val arguments: String = "",
    val result: String = "",
    val metadata: String? = null
)

/**
 * 历史记录条目数据模型。
 */
@Serializable
@Entity(tableName = "history_items")
data class HistoryItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String? = null,
    val summary: String? = null,
    val query: String,
    val result: String,
    val imagePath: String? = null,
    val mode: String? = null,
    val assistantName: String? = null,
    val providerName: String? = null,
    val modelName: String? = null,
    val engineName: String? = null,
    val status: AnalysisStatus = AnalysisStatus.SUCCESS,
    val toolCalls: List<ToolCallRecord> = emptyList(),
)
