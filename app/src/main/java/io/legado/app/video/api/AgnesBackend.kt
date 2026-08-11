package io.legado.app.video.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AgnesBackend(
    private val config: ProviderCredentialConfig = ProviderCredentialConfig(
        providerKey = ProviderRegistry.AGNES,
        apiKey = AgnesConfig.apiKey,
        baseUrl = AgnesConfig.baseUrl
    )
) : ImageBackend, VideoBackend, TextBackend {

    override val providerKey: String = ProviderRegistry.AGNES
    override val providerName: String = "Agnes AI"

    override val imageCapabilities = ImageCapabilities(
        supportedModels = listOf("agnes-image-2.1-flash", "agnes-image-2.0"),
        maxResolution = "4096x4096",
        supportsImageEdit = true,
        supportsStyleTransfer = true,
        supportsMultipleReferences = true,
        maxReferences = 4,
        supportsNegativePrompt = true,
        supportsBatch = true,
        responseFormats = listOf(ResponseFormat.URL, ResponseFormat.BASE64),
        defaultModel = "agnes-image-2.1-flash"
    )

    override val videoCapabilities = VideoCapabilities(
        supportedModels = listOf("agnes-video-v2.0", "agnes-video-v1.5"),
        maxDurationSeconds = 30,
        supportsTextToVideo = true,
        supportsImageToVideo = true,
        supportsReferenceToVideo = true,
        maxConcurrentTasks = 5,
        supportsCancel = true,
        defaultModel = "agnes-video-v2.0"
    )

    override val textCapabilities = TextCapabilities(
        supportedModels = listOf("agnes-2.5-flash", "agnes-2.5-pro", "agnes-2.5-ultra"),
        maxContextWindow = 128000,
        supportsStreaming = true,
        supportsJsonSchema = true,
        supportsFunctionCalling = true,
        supportsVision = false,
        defaultModel = "agnes-2.5-flash"
    )

    private val gson: Gson = GsonBuilder().create()
    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(LoggingInterceptor())
            .addInterceptor(RateLimitInterceptor(rateLimiters))
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.trimEnd('/')
    private fun authToken(): String = config.apiKey

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${authToken()}")
        .add("Content-Type", "application/json")
        .add("Accept", "application/json")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("${baseUrl()}/models")
                    .headers(buildHeaders())
                    .get()
                    .build()
            ).execute()

            result.httpCode = response.code
            result.success = response.isSuccessful
            result.message = if (response.isSuccessful) "连接成功" else "连接失败 (HTTP ${response.code})"
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = "网络错误: ${e.message}"
        }
        Result.success(result)
    }

    override fun isConfigured(): Boolean = config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()

    override suspend fun generate(request: ImageGenerationRequest): Result<ImageGenerationResult> {
        val model = request.model ?: imageCapabilities.defaultModel
        val url = "${baseUrl()}/images/generations"
        val body = mapOf(
            "model" to model,
            "prompt" to request.prompt,
            "size" to "${request.width}x${request.height}",
            "n" to request.count
        )
        return executeAndParse(url, body) { responseBody ->
            val parsed = gson.fromJson(responseBody, Map::class.java)
            val images = (parsed["data"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { GeneratedImage(url = it["url"] as? String) }
            } ?: emptyList()
            ImageGenerationResult(images = images, model = model, providerKey = providerKey)
        }
    }

    override suspend fun edit(request: ImageEditRequest): Result<ImageGenerationResult> {
        val model = request.model ?: imageCapabilities.defaultModel
        val url = "${baseUrl()}/images/edits"
        val body = mapOf(
            "model" to model,
            "prompt" to request.prompt,
            "image" to request.imageUrl
        )
        return executeAndParse(url, body) { responseBody ->
            val parsed = gson.fromJson(responseBody, Map::class.java)
            val images = (parsed["data"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { GeneratedImage(url = it["url"] as? String) }
            } ?: emptyList()
            ImageGenerationResult(images = images, model = model, providerKey = providerKey)
        }
    }

    override suspend fun generate(request: VideoGenerationRequest): Result<VideoGenerationResult> {
        val model = request.model ?: videoCapabilities.defaultModel
        val url = "${baseUrl()}/videos"
        val body = buildMap {
            put("model", model)
            put("prompt", request.prompt)
            put("duration_seconds", request.duration)
            put("aspect_ratio", request.aspectRatio)
            request.imageUrl?.let { put("image_url", it) }
            if (request.gridImages.isNotEmpty()) put("grid_images", request.gridImages)
        }
        return executeAndParse(url, body) { responseBody ->
            val parsed = gson.fromJson(responseBody, Map::class.java)
            VideoGenerationResult(
                taskId = parsed["id"] as? String,
                videoId = parsed["video_id"] as? String,
                status = parsed["status"] as? String,
                progress = (parsed["progress"] as? Number)?.toInt(),
                videoUrl = (parsed["data"] as? List<*>)?.firstOrNull()?.let {
                    (it as? Map<*, *>)?.get("url") as? String
                },
                providerKey = providerKey
            )
        }
    }

    override suspend fun getStatus(taskId: String): Result<VideoTaskStatus> {
        val url = "${baseUrl()}/videos/$taskId"
        return executeAndParse(url, emptyMap()) { responseBody ->
            val parsed = gson.fromJson(responseBody, Map::class.java)
            VideoTaskStatus(
                taskId = taskId,
                status = parsed["status"] as? String ?: "unknown",
                progress = (parsed["progress"] as? Number)?.toInt() ?: 0,
                videoUrl = (parsed["data"] as? List<*>)?.firstOrNull()?.let {
                    (it as? Map<*, *>)?.get("url") as? String
                },
                duration = (parsed["seconds"] as? String)?.toFloatOrNull()
            )
        }
    }

    override suspend fun cancel(taskId: String): Result<Boolean> {
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url("${baseUrl()}/videos/$taskId/cancel")
                    .headers(buildHeaders())
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generate(request: TextGenerationRequest): Result<TextGenerationResult> {
        val model = request.model ?: textCapabilities.defaultModel
        val url = "${baseUrl()}/chat/completions"
        val body = buildMap {
            put("model", model)
            put("messages", request.messages.map { mapOf("role" to it.role, "content" to it.content) })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            request.systemPrompt?.let { sys ->
                put("system_prompt", sys)
            }
            if (request.functions.isNotEmpty()) put("functions", request.functions)
        }
        return executeAndParse(url, body) { responseBody ->
            val parsed = gson.fromJson(responseBody, Map::class.java)
            val content = (parsed["choices"] as? List<*>)?.firstOrNull()?.let { choice ->
                (choice as? Map<*, *>)?.get("message")?.let { msg ->
                    (msg as? Map<*, *>)?.get("content") as? String
                }
            } ?: ""
            val usage = parsed["usage"] as? Map<*, *>
            TextGenerationResult(
                content = content,
                model = model,
                providerKey = providerKey,
                usage = usage?.let {
                    TokenUsage(
                        promptTokens = (it["prompt_tokens"] as? Number)?.toInt() ?: 0,
                        completionTokens = (it["completion_tokens"] as? Number)?.toInt() ?: 0,
                        totalTokens = (it["total_tokens"] as? Number)?.toInt() ?: 0
                    )
                }
            )
        }
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

            val response = if (body.isEmpty()) {
                requestBuilder.get()
            } else {
                val json = gson.toJson(body)
                requestBuilder.post(json.toRequestBody("application/json".toMediaType()))
            }.build()

            val httpResponse = client.newCall(response).execute()
            val responseBody = httpResponse.body?.string()

            if (!httpResponse.isSuccessful) {
                val error = try { gson.fromJson(responseBody, Map::class.java) } catch (_: Exception) { null }
                val message = (error?.get("message") as? String) ?: "HTTP ${httpResponse.code}"
                return@withContext Result.failure(IOException(message))
            }

            Result.success(parser(responseBody ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollVideoCompletion(
        taskId: String,
        maxAttempts: Int = 120,
        initialIntervalMs: Long = 3000
    ): Result<String> {
        var interval = initialIntervalMs
        repeat(maxAttempts) {
            val status = getStatus(taskId)
            status.onSuccess { s ->
                when (s.status) {
                    "completed", "succeeded", "success" -> {
                        return Result.success(s.videoUrl ?: "")
                    }
                    "failed", "cancelled" -> {
                        return Result.failure(IOException("视频生成失败: ${s.status}"))
                    }
                    else -> delay(interval)
                }
            }.onFailure { delay(interval) }
            interval = (interval * 1.2).toLong().coerceAtMost(10000)
        }
        return Result.failure(IOException("视频生成超时"))
    }

    suspend fun downloadFile(url: String, destination: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder().url(url).get().build()
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("下载失败: HTTP ${response.code}"))
            }
            val body = response.body ?: return@withContext Result.failure(IOException("空响应体"))
            FileOutputStream(destination).use { fos ->
                body.byteStream().use { it.copyTo(fos) }
            }
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ProviderCredentialConfig(
    val providerKey: String,
    val apiKey: String,
    val baseUrl: String,
    val extraFields: Map<String, String> = emptyMap()
)
