package io.legado.app.video.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class PikaApiClient : VideoApiClient {

    override val providerId: String = ProviderRegistry.PIKA
    override val providerName: String = "Pika"

    private val gson: Gson = GsonBuilder().create()
    private val config get() = VideoApiConfigManager.getProviderConfig(providerId)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.ifBlank { "https://api.pika-labs.com" }.trimEnd('/')
    private fun apiKey(): String = config.apiKey

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer $apiKey")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .headers(buildHeaders())
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = client.newCall(request).execute()
            result.latencyMs = System.currentTimeMillis() - startTime
            result.httpCode = response.code
            result.success = response.isSuccessful
            result.message = if (response.isSuccessful) "Pika 连接成功" else "连接失败 (HTTP ${response.code})"
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = "Pika 连接错误: ${e.message}"
        }

        Result.success(result)
    }

    override suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String?,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        return Result.failure(UnsupportedOperationException("Pika 不支持对话功能"))
    }

    override suspend fun generateImage(
        prompt: String,
        model: String?,
        width: Int,
        height: Int,
        count: Int,
        style: String?
    ): Result<ImageResponse> {
        return Result.failure(UnsupportedOperationException("Pika 暂不支持图片生成"))
    }

    override suspend fun generateVideo(
        prompt: String,
        model: String?,
        imageUrl: String?,
        duration: Int,
        aspectRatio: String
    ): Result<VideoResponse> {
        val modelName = model ?: config.videoModel ?: "pika-1.5"
        val url = "$baseUrl/v1/videos/generate"
        
        val requestBody = mutableMapOf<String, Any?>(
            "model" to modelName,
            "prompt" to prompt,
            "duration" to duration
        )
        if (imageUrl != null) {
            requestBody["image"] = imageUrl
        }
        requestBody["aspect_ratio"] = aspectRatio

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
            Result.success(
                VideoResponse(
                    taskId = parsed["id"] as? String,
                    status = parsed["status"] as? String
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse> {
        val url = "$baseUrl/v1/videos/$taskId"
        return try {
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP ${response.code}"))
            }

            val parsed = gson.fromJson(body, Map::class.java)
            val data = parsed["data"] as? Map<*, *>
            Result.success(
                VideoStatusResponse(
                    taskId = taskId,
                    status = data?.get("status") as? String ?: "unknown",
                    progress = data?.get("progress") as? Int ?: 0,
                    videoUrl = data?.get("url") as? String
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelTask(taskId: String): Result<Boolean> {
        return Result.success(true)
    }

    override fun isConfigured(): Boolean = config.apiKey.isNotBlank()

    suspend fun pollVideoResult(
        taskId: String,
        maxAttempts: Int = 60,
        initialIntervalMs: Long = 3000
    ): Result<String> {
        var interval = initialIntervalMs
        repeat(maxAttempts) {
            val result = getVideoStatus(taskId)
            result.onSuccess { status ->
                when (status.status) {
                    "succeeded", "completed" -> return Result.success(status.videoUrl ?: "")
                    "failed" -> return Result.failure(IOException("Pika 视频生成失败"))
                    else -> {
                        delay(interval)
                        interval = (interval * 1.5).coerceAtMost(10000)
                    }
                }
            }.onFailure {
                delay(interval)
            }
        }
        return Result.failure(IOException("Pika 视频生成超时"))
    }
}
