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

class AgnesApiClient(
    private val config: AgnesConfigProvider = AgnesConfigProvider()
) : VideoApiClient {

    override val providerId: String = ProviderRegistry.AGNES
    override val providerName: String = "Agnes AI"

    private val gson: Gson = GsonBuilder().create()

    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout((config.timeoutSeconds * 3).toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(LoggingInterceptor())
            .addInterceptor(RateLimitInterceptor(rateLimiters))
            .addInterceptor(ErrorInterceptor())
            .build()
    }

    private fun baseUrl(): String = config.baseUrl.trimEnd('/')
    private fun authToken(): String = config.apiKey

    private suspend fun executeRequest(request: Request): Response = withContext(Dispatchers.IO) {
        client.newCall(request).execute()
    }

    private fun buildHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${authToken()}")
        .add("Content-Type", "application/json")
        .add("Accept", "application/json")
        .add("User-Agent", "LegadoVideo/1.0")
        .build()

    override suspend fun testConnection(): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val result = ConnectionTestResult()
        val testUrl = "${baseUrl()}/models"

        val request = Request.Builder()
            .url(testUrl)
            .headers(buildHeaders())
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            result.latencyMs = latencyMs
            result.httpCode = response.code

            if (response.isSuccessful) {
                result.success = true
                result.message = "连接成功"
                response.close()
            } else when (response.code) {
                401 -> {
                    result.success = false
                    result.message = "API Key 无效"
                }
                403 -> {
                    result.success = false
                    result.message = "API Key 权限不足"
                }
                429 -> {
                    result.success = false
                    result.message = "请求频率超限"
                }
                in 500..599 -> {
                    result.success = false
                    result.message = "服务器错误 (${response.code})"
                }
                else -> {
                    result.success = false
                    result.message = "连接失败 (HTTP ${response.code})"
                }
            }
            response.close()
        } catch (e: IOException) {
            result.success = false
            result.message = when {
                e.message?.contains("timeout") == true -> "连接超时"
                e.message?.contains("UnknownHost") == true -> "网络错误：无法连接到服务器"
                e.message?.contains("SSL") == true -> "SSL/TLS 错误"
                else -> "网络连接失败：${e.message}"
            }
        }

        Result.success(result)
    }

    override suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String?,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        val url = "${baseUrl()}/chat/completions"
        val agnesMessages = messages.map { AgnesChatMessage(role = it.role, content = it.content) }
        val request = AgnesChatRequest(
            model = model ?: config.chatModel,
            messages = agnesMessages,
            temperature = temperature.toDouble(),
            maxTokens = maxTokens,
            stream = false
        )
        val body = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = executeRequest(httpRequest)
            val parsed = parseResponse(response, AgnesChatResponse::class.java).getOrThrow()
            val content = parsed.choices?.firstOrNull()?.message?.content ?: ""
            Result.success(
                ChatResponse(
                    content = content,
                    model = model ?: config.chatModel,
                    usage = parsed.usage?.let { TokenUsage(promptTokens = it.promptTokens, completionTokens = it.completionTokens, totalTokens = it.totalTokens) }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
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
        val url = "${baseUrl()}/images/generations"
        val request = AgnesImageRequest(
            model = model ?: config.imageModel,
            prompt = prompt,
            size = "${width}x${height}",
            n = count
        )
        val body = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = executeRequest(httpRequest)
            val parsed = parseResponse(response, AgnesImageResponse::class.java).getOrThrow()
            val images = parsed.data?.map { data ->
                GeneratedImage(
                    url = data.url,
                    base64 = data.b64Json
                )
            } ?: emptyList()
            Result.success(ImageResponse(images = images, model = model ?: config.imageModel))
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
        val url = "${baseUrl()}/videos"
        val request = AgnesVideoRequest(
            model = model ?: config.videoModel,
            prompt = prompt,
            imageUrls = imageUrl?.let { listOf(it) },
            duration = duration,
            aspectRatio = aspectRatio
        )
        val body = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = executeRequest(httpRequest)
            val parsed = parseResponse(response, AgnesVideoResponse::class.java).getOrThrow()
            Result.success(
                VideoResponse(
                    taskId = parsed.id ?: parsed.taskId,
                    videoId = parsed.videoId,
                    status = parsed.status,
                    progress = parsed.progress,
                    videoUrl = parsed.data?.firstOrNull()?.url,
                    duration = parsed.seconds?.toFloatOrNull(),
                    error = parsed.error?.toApiError()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse> {
        val url = "${baseUrl()}/videos/$taskId"
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .get()
            .build()

        return try {
            val response = executeRequest(httpRequest)
            val parsed = parseResponse(response, AgnesVideoStatusResponse::class.java).getOrThrow()
            Result.success(
                VideoStatusResponse(
                    taskId = taskId,
                    status = parsed.status ?: "unknown",
                    progress = parsed.progress ?: 0,
                    videoUrl = parsed.data?.firstOrNull()?.url,
                    duration = parsed.seconds?.toFloatOrNull(),
                    error = parsed.error?.toApiError()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelTask(taskId: String): Result<Boolean> {
        val url = "${baseUrl()}/videos/$taskId/cancel"
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = executeRequest(httpRequest)
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isConfigured(): Boolean {
        return config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
    }

    suspend fun generateImageLegacy(
        request: AgnesImageRequest,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<AgnesImageResponse> {
        val url = "${baseUrl()}/images/generations"
        val body = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        onProgress?.invoke(10, "发送请求")
        return try {
            val response = executeRequest(httpRequest)
            onProgress?.invoke(80, "解析响应")
            val result = parseResponse(response, AgnesImageResponse::class.java)
            onProgress?.invoke(100, "完成")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateVideoLegacy(
        request: AgnesVideoRequest,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<AgnesVideoResponse> {
        val url = "${baseUrl()}/videos"
        val body = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        onProgress?.invoke(5, "提交视频生成请求")
        return try {
            val response = executeRequest(httpRequest)
            onProgress?.invoke(50, "处理响应")
            val result = parseResponse(response, AgnesVideoResponse::class.java)
            onProgress?.invoke(100, "完成")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollVideoCompletion(
        videoId: String,
        maxAttempts: Int = 120,
        initialIntervalMs: Long = 3000,
        maxIntervalMs: Long = 10000,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<AgnesVideoData> {
        var interval = initialIntervalMs
        var consecutiveErrors = 0

        repeat(maxAttempts) { attempt ->
            val result = getVideoStatusLegacy(videoId)
            result.onSuccess { response ->
                consecutiveErrors = 0
                val status = response.status
                val progress = response.progress ?: 0

                when (status) {
                    "completed", "succeeded", "success" -> {
                        val videoData = response.data?.firstOrNull()
                        if (videoData != null) {
                            onProgress?.invoke(100, "生成完成")
                            return Result.success(videoData)
                        } else {
                            onProgress?.invoke(progress, "无视频数据")
                        }
                    }
                    "failed", "cancelled", "error" -> {
                        val errorMsg = response.error?.message ?: status
                        onProgress?.invoke(progress, "生成失败: $errorMsg")
                        return Result.failure(IOException("Video generation failed: $errorMsg"))
                    }
                    "processing", "queued", "pending", "running" -> {
                        onProgress?.invoke(progress.coerceIn(0, 99), "生成中 ($progress%)")
                    }
                    else -> {
                        onProgress?.invoke(progress.coerceIn(0, 99), status ?: "处理中")
                    }
                }
            }.onFailure {
                consecutiveErrors++
                onProgress?.invoke(0, "轮询错误 ($consecutiveErrors)")
                if (consecutiveErrors >= 5) {
                    return Result.failure(IOException("连续5次轮询失败"))
                }
            }

            interval = (interval * 1.2).coerceAtMost(maxIntervalMs.toDouble()).toLong()
            delay(interval)
        }
        return Result.failure(IOException("视频生成超时（${maxAttempts}次轮询）"))
    }

    private suspend fun getVideoStatusLegacy(videoId: String): Result<AgnesVideoStatusResponse> {
        val url = "${baseUrl()}/videos/$videoId"
        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .get()
            .build()

        return try {
            val response = executeRequest(httpRequest)
            parseResponse(response, AgnesVideoStatusResponse::class.java)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(url: String, destination: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(IOException("下载失败: HTTP ${response.code}"))
                }
                val body = response.body ?: return@use Result.failure(IOException("空响应体"))
                FileOutputStream(destination).use { fos ->
                    body.byteStream().use { input ->
                        input.copyTo(fos)
                    }
                }
                Result.success(destination)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> parseResponse(response: Response, clazz: Class<T>): Result<T> {
        val body = response.body?.string()
        if (!response.isSuccessful) {
            val error = try {
                gson.fromJson(body, AgnesError::class.java)
            } catch (_: Exception) {
                null
            }
            val message = error?.message ?: "HTTP ${response.code}: ${body?.take(500)}"
            return Result.failure(IOException(message))
        }
        return try {
            val parsed = gson.fromJson(body, clazz)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(IOException("解析错误: ${e.message}. 响应: ${body?.take(500)}"))
        }
    }

    private fun AgnesError.toApiError(): ApiError = ApiError(
        code = this.code,
        message = this.message,
        type = this.type
    )

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

internal class RateLimiter(private val maxRequests: Int = 20, private val windowMs: Long = 60000) {
    private val timestamps = mutableListOf<Long>()
    private val lock = Any()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            timestamps.removeAll { now - it > windowMs }
            if (timestamps.size < maxRequests) {
                timestamps.add(now)
                return true
            }
            return false
        }
    }
}

internal class RateLimitInterceptor(
    private val rateLimiters: ConcurrentHashMap<String, RateLimiter>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        val limiterKey = when {
            path.contains("/videos") -> "video"
            path.contains("/images") -> "image"
            path.contains("/chat") -> "chat"
            else -> "default"
        }

        val limiter = rateLimiters.getOrPut(limiterKey) { RateLimiter() }

        if (!limiter.tryAcquire()) {
            Thread.sleep(2000)
            if (!limiter.tryAcquire()) {
                throw IOException("请求过于频繁，请稍后重试")
            }
        }

        return chain.proceed(chain.request())
    }
}

internal class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val startTime = System.currentTimeMillis()

        Log.d("AgnesAPI", "→ $method $url")

        val response = chain.proceed(request)
        val elapsed = System.currentTimeMillis() - startTime

        val bodySize = response.body?.contentLength() ?: -1
        Log.d("AgnesAPI", "← ${response.code} ${response.message} (${elapsed}ms, ${bodySize} bytes)")

        return response
    }
}

private class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!response.isSuccessful) {
            val code = response.code
            val body = response.body?.string()?.take(200)

            Log.e("AgnesAPI", "Error $code: $body")

            when (code) {
                401 -> Log.e("AgnesAPI", "认证失败 - 请检查API Key")
                429 -> Log.e("AgnesAPI", "速率限制 - 请稍后重试")
                500, 502, 503 -> Log.e("AgnesAPI", "服务器错误 - 服务可能暂时不可用")
            }

            val newBody = ResponseBody.create(
                "application/json".toMediaType(),
                body ?: "{}"
            )
            return response.newBuilder().body(newBody).build()
        }

        return response
    }
}

class AgnesConfigProvider {
    val apiKey: String get() = AgnesConfig.apiKey
    val baseUrl: String get() = AgnesConfig.baseUrl
    val imageModel: String get() = AgnesConfig.imageModel
    val videoModel: String get() = AgnesConfig.videoModel
    val chatModel: String get() = AgnesConfig.chatModel
    val timeoutSeconds: Int get() = AgnesConfig.timeoutSeconds
}

data class AgnesStreamChunk(
    val choices: List<AgnesStreamChoice>? = null
)

data class AgnesStreamChoice(
    val delta: AgnesStreamDelta? = null
)

data class AgnesStreamDelta(
    val role: String? = null,
    val content: String? = null
)
