package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProcessingStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}

@Serializable
enum class ProcessingRoute {
    OCR_THEN_LLM,
    MULTIMODAL_DIRECT
}

@Serializable
data class ProcessingEvent(
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class ExtractedQuestion(
    val type: String? = null,
    val question: String? = null,
    val options: List<String>? = null,
    val image_analysis: String? = null
)

@Serializable
data class ProcessingResult(
    val id: String,
    val assistantName: String,
    val route: ProcessingRoute,
    val status: ProcessingStatus,
    val modelSummary: String,
    val detail: String,
    val extractedText: String? = null,
    val answer: String? = null,
    val screenshotPath: String? = null,
    val screenshotPaths: List<String> = emptyList(),
    val events: List<ProcessingEvent> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCallRecord> = emptyList(),
)
