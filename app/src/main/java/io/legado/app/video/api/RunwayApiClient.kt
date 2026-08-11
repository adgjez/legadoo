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

class RunwayApiClient : VideoApiClient {

    override val providerId: String = ProviderRegistry.RUNWAY
    override val providerName: String = "Runway"

    private val gson: Gson = GsonBuilder().create()
    private val config get() = VideoApiConfigManager.getProviderConfig(providerId)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.ifBlank { "https://api.dev.runwayml.com" }.trimEnd('/')
    private fun apiKey(): String = config.apiKey

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${apiKey()}")
        .add("Content-Type", "application/json")
        .add("X-Runway-Version", "2024-11-06")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        val request = Request.Builder()
            .url("${baseUrl()}/organization")
            .headers(buildHeaders())
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = client.newCall(request).execute()
            result.latencyMs = System.currentTimeMillis() - startTime
            result.httpCode = response.code

            result.success = response.isSuccessful
            result.message = if (response.isSuccessful) "Runway 连接成功" else "连接失败 (HTTP ${response.code})"
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = "Runway 连接错误: ${e.message}"
        }

        Result.success(result)
    }

    override suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String?,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        return Result.failure(UnsupportedOperationException("Runway 不支持对话功能"))
    }

    override suspend fun generateImage(
        prompt: String,
        model: String?,
        width: Int,
        height: Int,
        count: Int,
        style: String?
    ): Result<ImageResponse> {
        val modelName = model ?: config.imageModel ?: "gen3_image"
        val url = "${baseUrl()}/image/v1/text_to_image"
        val requestBody = mapOf(
            "model" to modelName,
            "promptText" to prompt,
            "ratio" to if (width > height) "1280:720" else "720:1280"
        )
        return executeAndParse(url, requestBody) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val images = (parsed["images"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.get("url")?.let { GeneratedImage(url = it as String) }
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
        val modelName = model ?: config.videoModel ?: "gen3_turbo"
        val url = "${baseUrl()}/image/to_video"

        val requestBody = if (imageUrl != null) {
            mapOf(
                "promptText" to prompt,
                "imageUrl" to imageUrl,
                "ratio" to aspectRatio,
                "duration" to duration
            )
        } else {
            mapOf(
                "promptText" to prompt,
                "ratio" to aspectRatio,
                "duration" to duration
            )
        }

        return executeAndParse(url, requestBody) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            VideoResponse(
                taskId = parsed["id"] as? String,
                status = parsed["status"] as? String,
                model = modelName
            )
        }
    }

    override suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse> {
        val url = "${baseUrl()}/tasks/$taskId"
        return executeAndParse(url, emptyMap()) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val status = parsed["status"] as? String ?: "unknown"
            val output = parsed["output"] as? List<*>
            VideoStatusResponse(
                taskId = taskId,
                status = status,
                progress = when (status) {
                    "SUCCEEDED" -> 100
                    "FAILED" -> 0
                    "PENDING" -> 10
                    else -> 50
                },
                videoUrl = output?.firstOrNull() as? String
            )
        }
    }

    override suspend fun cancelTask(taskId: String): Result<Boolean> {
        val url = "${baseUrl()}/tasks/$taskId/cancel"
        return try {
            val request = Request.Builder()
                .url(url)
                .headers(buildHeaders())
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isConfigured(): Boolean = config.apiKey.isNotBlank()

    suspend fun pollVideoResult(
        taskId: String,
        maxAttempts: Int = 60,
        intervalMs: Long = 5000
    ): Result<String> {
        repeat(maxAttempts) {
            val result = getVideoStatus(taskId)
            result.onSuccess { status ->
                when (status.status) {
                    "SUCCEEDED" -> return Result.success(status.videoUrl ?: "")
                    "FAILED" -> return Result.failure(IOException("Runway 视频生成失败"))
                    else -> delay(intervalMs)
                }
            }.onFailure {
                delay(intervalMs)
            }
        }
        return Result.failure(IOException("Runway 视频生成超时"))
    }

    private suspend fun <T> executeAndParse(
        url: String,
        body: Map<String, Any?>,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .headers(buildHeaders())

            val httpRequest = if (body.isEmpty()) {
                requestBuilder.get()
            } else {
                val json = gson.toJson(body)
                requestBuilder.post(json.toRequestBody("application/json".toMediaType()))
            }.build()

            val httpResponse = client.newCall(httpRequest).execute()
            val responseBody = httpResponse.body?.string()

            if (!httpResponse.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${httpResponse.code}: $responseBody"))
            }

            Result.success(parser(responseBody ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
