package com.tianhuiu.solvex.network.search

import com.tianhuiu.solvex.data.models.SearchProviderKind
import com.tianhuiu.solvex.data.models.WebSearchSettings
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * 搜索结果与状态。
 */
data class SearchOutcome(
    val results: List<SearchHit>,
    val formattedText: String,
    val error: String? = null
)

/**
 * 搜索编排器。
 */
class SearchOrchestrator(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun execute(query: String, settings: WebSearchSettings): SearchOutcome {
        if (!settings.enabled) {
            return SearchOutcome(emptyList(), "联网搜索未开启")
        }
        val provider = settings.providers.find { it.id == settings.selectedProviderId }
            ?: return SearchOutcome(emptyList(), "未配置首选搜索提供商")

        if (provider.apiKey.isBlank()) {
            return SearchOutcome(emptyList(), "未配置搜索 API Key")
        }

        val adapter: SearchEngineAdapter = getAdapter(provider.kind)

        val response = adapter.search(
            SearchRequest(
                apiKey = provider.apiKey,
                baseUrl = provider.baseUrl,
                query = query,
                maxResults = settings.maxResults
            )
        )

        if (response.errorCode != 0) {
            return SearchOutcome(emptyList(), "搜索失败: ${response.errorMessage}", response.errorMessage)
        }

        if (response.hits.isEmpty()) {
            return SearchOutcome(emptyList(), "未找到相关搜索结果")
        }

        val formatted = formatResults(response.hits)
        return SearchOutcome(response.hits, formatted)
    }

    suspend fun validate(config: com.tianhuiu.solvex.data.models.SearchProviderConfig): SearchResponse {
        val adapter = getAdapter(config.kind)
        return adapter.validate(config.apiKey, config.baseUrl)
    }

    private fun getAdapter(kind: SearchProviderKind): SearchEngineAdapter = when (kind) {
        SearchProviderKind.TAVILY, SearchProviderKind.CUSTOM -> TavilySearchAdapter(client, json)
        SearchProviderKind.SERPER -> SerperSearchAdapter(client, json)
        SearchProviderKind.BRAVE -> BraveSearchAdapter(client, json)
    }

    private fun formatResults(hits: List<SearchHit>): String {
        val sb = StringBuilder()
        sb.append("以下是网络搜索结果（供参考，可能不准确）：")
        hits.forEachIndexed { index, hit ->
            sb.append("\n[").append(index + 1).append("] ").append(hit.title)
            sb.append("\n").append(hit.content)
            sb.append("\n")
        }
        return sb.toString()
    }
}
