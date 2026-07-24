package com.tianhuiu.solvex

import android.content.Context
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.SolveXDatabase
import com.tianhuiu.solvex.network.ProcessingPipeline
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.utils.AppNotificationManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 应用依赖注入容器。
 * 负责全局单例组件的创建、持有以及网络栈的动态生命周期管理。
 * 
 * 核心功能：
 * 1. 网络栈持有：维护通用的 [OkHttpClient] 实例。
 * 2. 动态重置：支持根据“信任所有证书”配置实时重建整个网络协议栈。
 * 3. 业务组件分发：提供 [UnifiedLLMClient] 和 [ProcessingPipeline] 的最新实例给 Service 或 ViewModel。
 */
internal class AppContainer(private val appContext: Context) {
    
    /** 全局通用 OkHttp 客户端 */
    var okHttpClient: OkHttpClient = createOkHttpClient(false)
        private set

    /** 统一 LLM 客户端包装类 */
    var unifiedLLMClient: UnifiedLLMClient = UnifiedLLMClient(okHttpClient)
        private set

    /** 首页通知管理器 */
    var appNotificationManager: AppNotificationManager = AppNotificationManager()
        private set

    /** 核心业务处理管道 */
    var processingPipeline: ProcessingPipeline = ProcessingPipeline(appContext, unifiedLLMClient)
        private set

    /** 数据库实例 */
    val database = SolveXDatabase.getDatabase(appContext)
    
    /** 历史记录仓库 */
    val historyRepository = HistoryRepository(database.historyDao())

    /**
     * 重新初始化整个网络栈。
     * 当用户切换“信任所有 HTTPS 证书”设置时，必须调用此方法以使全局网络请求生效。
     * 
     * @param trustAll 是否开启非安全模式（信任所有自签名或过期的证书）
     */
    fun refreshNetworkStack(trustAll: Boolean) {
        okHttpClient = createOkHttpClient(trustAll)
        unifiedLLMClient = UnifiedLLMClient(okHttpClient)
        processingPipeline = ProcessingPipeline(appContext, unifiedLLMClient)
    }

    /**
     * 创建 OkHttpClient 实例。
     * 
     * @param trustAll 若为 true，则配置绕过所有 SSL 验证的 TrustManager。
     */
    private fun createOkHttpClient(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(appContext.cacheDir, "http_cache"), 10 * 1024 * 1024))

        if (trustAll) {
            try {
                // 实现一个信任所有证书的管理器
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                // 使用现代 TLS 协议
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return builder.build()
    }
}
