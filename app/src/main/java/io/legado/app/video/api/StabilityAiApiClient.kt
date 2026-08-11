package io.legado.app.video.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class StabilityAiApiClient : VideoApiClient {

    override val providerId: String = ProviderRegistry.STABILITY_AI
    override val providerName: String = "Stability AI"

    private val gson: Gson = GsonBuilder().create()
    private val config get() = VideoApiConfigManager.getProviderConfig(providerId)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.ifBlank { "https://api.stability.ai" }.trimEnd('/')
    private fun apiKey(): String = config.apiKey

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${apiKey()}")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        val request = Request.Builder()
            .url("${baseUrl()}/v1/user/account")
            .headers(buildHeaders())
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = client.newCall(request).execute()
            result.latencyMs = System.currentTimeMillis() - startTime
            result.httpCode = response.code
            result.success = response.isSuccessful
            result.message = if (response.isSuccessful) "Stability AI 连接成功" else "连接失败 (HTTP ${response.code})"
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = "Stability AI 连接错误: ${e.message}"
        }

        Result.success(result)
    }

    override suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String?,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        return Result.failure(UnsupportedOperationException("Stability AI 不支持对话功能"))
    }

    override suspend fun generateImage(
        prompt: String,
        model: String?,
        width: Int,
        height: Int,
        count: Int,
        style: String?
    ): Result<ImageResponse> {
        val modelName = model ?: config.imageModel ?: "stable-diffusion-xl-1.0"
        val url = "${baseUrl()}/v1/generation/$modelName/text-to-image"

        val requestBody = mapOf(
            "text_prompts" to listOf(mapOf("text" to prompt, "weight" to 1.0)),
            "cfg_scale" to 7,
            "height" to height,
            "width" to width,
            "samples" to count,
            "steps" to 30
        )

        return try {
            val json = gson.toJson(requestBody)
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP ${response.code}: $body"))
            }

            val parsed = gson.fromJson(body, Map::class.java)
            val images = (parsed["artifacts"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { artifact ->
                    val base64 = artifact["base64"] as? String
                    val seed = artifact["seed"] as? Int
                    if (base64 != null) GeneratedImage(base64 = base64) else null
                }
            } ?: emptyList()

            Result.success(ImageResponse(images = images, model = modelName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateVideo(
        prompt: String,
        model: String?,
        imageUrl: String?,
        duration: Int,
        aspectRatio: String
    ): Result<VideoResponse> {
        return Result.failure(UnsupportedOperationException("Stability AI 暂不支持视频生成"))
    }

    override suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse> {
        return Result.failure(UnsupportedOperationException("Stability AI 暂不支持视频功能"))
    }

    override suspend fun cancelTask(taskId: String): Result<Boolean> {
        return Result.failure(UnsupportedOperationException("Stability AI 暂不支持任务取消"))
    }

    override fun isConfigured(): Boolean = config.apiKey.isNotBlank()
}
