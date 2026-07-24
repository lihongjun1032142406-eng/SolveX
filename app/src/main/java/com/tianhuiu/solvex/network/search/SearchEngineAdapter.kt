package com.tianhuiu.solvex.network.search

import kotlinx.serialization.Serializable

/**
 * 搜索请求参数。
 */
data class SearchRequest(
    val apiKey: String,
    val baseUrl: String,
    val query: String,
    val maxResults: Int
)

/**
 * 单条搜索结果记录。
 */
@Serializable
data class SearchHit(
    val title: String,
    val content: String,
    val url: String? = null
)

/**
 * 搜索响应。
 */
data class SearchResponse(
    val hits: List<SearchHit>,
    val errorCode: Int = 0,
    val errorMessage: String? = null
)

/**
 * 搜索引擎适配器接口。
 */
interface SearchEngineAdapter {
    suspend fun search(request: SearchRequest): SearchResponse

    /**
     * 验证 API 有效性。
     */
    suspend fun validate(apiKey: String, baseUrl: String): SearchResponse = 
        SearchResponse(emptyList(), -1, "该引擎暂不支持独立验证")
}
