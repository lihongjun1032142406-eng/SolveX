package com.tianhuiu.solvex.network.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * 根据 HTTP 状态码生成中文错误描述。
 */
private fun errorCodeToMessage(code: Int): String = when (code) {
    401 -> "API Key 错误或已失效"
    403 -> "权限不足或额度已耗尽"
    429 -> "请求频率超限，请稍后再试"
    in 500..599 -> "搜索引擎服务暂时不可用"
    else -> "网络请求失败 (HTTP $code)"
}

/**
 * Tavily 搜索适配器。
 */
class TavilySearchAdapter(
    private val client: OkHttpClient,
    private val json: Json
) : SearchEngineAdapter {
    override suspend fun search(request: SearchRequest): SearchResponse = withContext(Dispatchers.IO) {
        val url = request.baseUrl.ifBlank { "https://api.tavily.com/search" }
        val payload = buildJsonObject {
            put("api_key", request.apiKey)
            put("query", request.query)
            put("max_results", request.maxResults)
            put("search_depth", "basic")
        }

        val httpRequest = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SearchResponse(emptyList(), response.code, errorCodeToMessage(response.code))
                }
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val hits = root["results"]?.jsonArray?.mapNotNull { el ->
                    val obj = el.jsonObject
                    SearchHit(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "无标题",
                        content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        url = obj["url"]?.jsonPrimitive?.contentOrNull
                    )
                } ?: emptyList()
                SearchResponse(hits)
            }
        } catch (e: Exception) {
            SearchResponse(emptyList(), -1, e.message)
        }
    }

    override suspend fun validate(apiKey: String, baseUrl: String): SearchResponse = withContext(Dispatchers.IO) {
        // 强制使用官方 usage 接口进行验证，忽略用户配置的 search endpoint
        val usageUrl = "https://api.tavily.com/usage"

        val request = Request.Builder()
            .url(usageUrl)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SearchResponse(emptyList()) // 验证通过
                } else {
                    // 如果 Bearer 失败，尝试作为 query param (兼容部分代理)
                    val retryUrl = usageUrl.toHttpUrlOrNull()?.newBuilder()
                        ?.addQueryParameter("api_key", apiKey)
                        ?.build()?.toString() ?: return@withContext SearchResponse(emptyList(), response.code, errorCodeToMessage(response.code))
                    
                    val retryRequest = Request.Builder().url(retryUrl).get().build()
                    client.newCall(retryRequest).execute().use { retryResp ->
                        if (retryResp.isSuccessful) {
                            SearchResponse(emptyList())
                        } else {
                            SearchResponse(emptyList(), retryResp.code, errorCodeToMessage(retryResp.code))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SearchResponse(emptyList(), -1, e.message ?: "验证请求异常")
        }
    }
}

/**
 * Serper.dev 搜索适配器。
 */
class SerperSearchAdapter(
    private val client: OkHttpClient,
    private val json: Json
) : SearchEngineAdapter {
    override suspend fun search(request: SearchRequest): SearchResponse = withContext(Dispatchers.IO) {
        val url = request.baseUrl.ifBlank { "https://google.serper.dev/search" }
        val payload = buildJsonObject {
            put("q", request.query)
            put("num", request.maxResults)
        }

        val httpRequest = Request.Builder()
            .url(url)
            .header("X-API-KEY", request.apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SearchResponse(emptyList(), response.code, errorCodeToMessage(response.code))
                }
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val hits = root["organic"]?.jsonArray?.mapNotNull { el ->
                    val obj = el.jsonObject
                    SearchHit(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "无标题",
                        content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        url = obj["url"]?.jsonPrimitive?.contentOrNull
                    )
                } ?: emptyList()
                SearchResponse(hits)
            }
        } catch (e: Exception) {
            SearchResponse(emptyList(), -1, e.message)
        }
    }

    override suspend fun validate(apiKey: String, baseUrl: String): SearchResponse {
        return SearchResponse(emptyList(), -1, "Serper 暂不支持独立验证，请直接尝试搜索")
    }
}

/**
 * Brave Search 适配器。
 */
class BraveSearchAdapter(
    private val client: OkHttpClient,
    private val json: Json
) : SearchEngineAdapter {
    override suspend fun search(request: SearchRequest): SearchResponse = withContext(Dispatchers.IO) {
        val baseUrl = request.baseUrl.ifBlank { "https://api.search.brave.com/res/v1/web/search" }
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", request.query)
            ?.addQueryParameter("count", request.maxResults.coerceIn(1, 20).toString())
            ?.build() ?: return@withContext SearchResponse(emptyList(), -1, "无效的 Base URL")

        val httpRequest = Request.Builder()
            .url(url)
            .header("X-Subscription-Token", request.apiKey)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SearchResponse(emptyList(), response.code, errorCodeToMessage(response.code))
                }
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val web = root["web"]?.jsonObject
                val hits = web?.get("results")?.jsonArray?.mapNotNull { el ->
                    val obj = el.jsonObject
                    SearchHit(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "无标题",
                        content = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        url = obj["url"]?.jsonPrimitive?.contentOrNull
                    )
                } ?: emptyList()
                SearchResponse(hits)
            }
        } catch (e: Exception) {
            SearchResponse(emptyList(), -1, e.message)
        }
    }

    override suspend fun validate(apiKey: String, baseUrl: String): SearchResponse {
        return SearchResponse(emptyList(), -1, "Brave Search 暂不支持独立验证，请直接尝试搜索")
    }
}
