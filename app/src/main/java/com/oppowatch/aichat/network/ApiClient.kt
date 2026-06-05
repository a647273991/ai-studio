package com.oppowatch.aichat.network

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ──────────────────────────────────────────
// 数据类：请求体
// ──────────────────────────────────────────

data class ChatMessage(
    @SerializedName("role") val role: String,      // "system" | "user" | "assistant"
    @SerializedName("content") val content: String
)

data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 1024
)

// ──────────────────────────────────────────
// 数据类：响应体
// ──────────────────────────────────────────

data class Choice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class ChatResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("object") val obj: String? = null,
    @SerializedName("created") val created: Long? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("choices") val choices: List<Choice> = emptyList(),
    @SerializedName("usage") val usage: Usage? = null
) {

    /** 快捷访问第一个 choice 的 message.content，无结果时返回空字符串。 */
    val content: String
        get() = choices.firstOrNull()?.message?.content.orEmpty()
}

// ──────────────────────────────────────────
// Retrofit 接口：OpenAI 兼容 Chat Completions
// ──────────────────────────────────────────

interface OpenAiApi {

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Body request: ChatRequest
    ): Response<ChatResponse>
}

// ──────────────────────────────────────────
// 单例：ApiClient
// ──────────────────────────────────────────

class ApiClient private constructor(
    private val baseUrl: String,
    private val apiKey: String,
    private val isDebug: Boolean = false
) {

    /** 对外暴露的 API 服务实例。 */
    val api: OpenAiApi

    private val okHttpClient: OkHttpClient

    init {
        // Auth 拦截器：每个请求自动附加 Bearer Token
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (isDebug) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        okHttpClient = builder.build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    // ──────────────────────────────────────
    // Companion：全局单例入口
    // ──────────────────────────────────────

    companion object {

        @Volatile
        private var instance: ApiClient? = null

        /**
         * 初始化单例。建议在 [com.oppowatch.aichat.App.onCreate] 中调用一次。
         *
         * @param baseUrl  OpenAI 兼容 API 的 base URL，例如 "https://api.openai.com/v1/"
         * @param apiKey   API Key
         * @param isDebug  是否开启 HTTP 日志（调试用）
         */
        fun init(baseUrl: String, apiKey: String, isDebug: Boolean = false): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient(baseUrl, apiKey, isDebug).also {
                    instance = it
                }
            }
        }

        /**
         * 获取已初始化的单例。未初始化时抛出 [IllegalStateException]。
         */
        fun getInstance(): ApiClient {
            return instance ?: throw IllegalStateException(
                "ApiClient 尚未初始化，请先调用 ApiClient.init(baseUrl, apiKey)"
            )
        }

        /**
         * 便捷方法：直接获取 [OpenAiApi] 实例。
         */
        fun getApi(): OpenAiApi = getInstance().api
    }
}

// ──────────────────────────────────────────
// 工具扩展
// ──────────────────────────────────────────

/** 确保 baseUrl 以 "/" 结尾，Retrofit 要求如此。 */
private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"
