package io.legado.app.video.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class DalleApiClient : VideoApiClient {

    override val providerId: String = ProviderRegistry.DALL_E
    override val providerName: String = "DALL-E"

    private val gson: Gson = GsonBuilder().create()
    private val config get() = VideoApiConfigManager.getProviderConfig(providerId)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.ifBlank { "https://api.openai.com" }.trimEnd('/')
    private fun apiKey(): String = config.apiKey

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer $apiKey")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        val testUrl = "$baseUrl/models"

        val request = Request.Builder()
            .url(testUrl)
            .headers(buildHeaders())
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = client.newCall(request).execute()
            result.latencyMs = System.currentTimeMillis() - startTime
            result.httpCode = response.code

            if (response.isSuccessful) {
                result.success = true
                result.message = "DALL-E 连接成功"
            } else {
                result.success = false
                result.message = "连接失败 (HTTP ${response.code})"
            }
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = "DALL-E 连接错误: ${e.message}"
        }

        Result.success(result)
    }

    override suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String?,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        val modelName = model ?: config.chatModel ?: "gpt-4o"
        val url = "$baseUrl/chat/completions"
        val requestBody = mapOf(
            "model" to modelName,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to temperature,
            "max_tokens" to maxTokens
        )
        return executeAndParse(url, requestBody) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val content = (parsed["choices"] as? List<*>)?.firstOrNull()?.let { choice ->
                (choice as? Map<*, *>)?.get("message")?.let { msg ->
                    (msg as? Map<*, *>)?.get("content") as? String
                }
            } ?: ""
            ChatResponse(content = content, model = modelName)
        }
    }

    override suspend fun generateImage(
        prompt: String,
        model: String?,
        width: Int,
        height: Int,
        count: Int,
        style: String?
    ): Result<ImageResponse> {
        val modelName = model ?: config.imageModel ?: "dall-e-3"
        val url = "$baseUrl/images/generations"
        val requestBody = mapOf(
            "model" to modelName,
            "prompt" to prompt,
            "n" to count,
            "size" to "${width}x${height}",
            "style" to (style ?: "vivid"),
            "response_format" to "url"
        )
        return executeAndParse(url, requestBody) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val images = (parsed["data"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.get("url")?.let { url ->
                    GeneratedImage(url = url as String)
                }
            } ?: emptyList()
            ImageResponse(images = images, model = modelName)
        }
    }

    override suspend fun generateVideo(
        prompt: String,
        model: String?,
        imageUrl: String?,
        duration: Int,
        aspectRatio: String
    ): Result<VideoResponse> {
        return Result.failure(UnsupportedOperationException("DALL-E 暂不支持视频生成"))
    }

    override suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse> {
        return Result.failure(UnsupportedOperationException("DALL-E 暂不支持视频状态查询"))
    }

    override suspend fun cancelTask(taskId: String): Result<Boolean> {
        return Result.failure(UnsupportedOperationException("DALL-E 暂不支持任务取消"))
    }

    override fun isConfigured(): Boolean {
        return config.apiKey.isNotBlank()
    }

    private suspend fun <T> executeAndParse(
        url: String,
        body: Map<String, Any?>,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(body)
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $responseBody"))
            }

            Result.success(parser(responseBody ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
