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

class NewApiImageBackend(
    private val config: ProviderCredentialConfig = ProviderCredentialConfig(
        providerKey = ProviderRegistry.NEWAPI,
        apiKey = "",
        baseUrl = "https://newapi.one"
    )
) : ImageBackend {

    override val providerKey: String = ProviderRegistry.NEWAPI
    override val providerName: String = "NewAPI"

    override val imageCapabilities = ImageCapabilities(
        supportedModels = listOf("gpt-image-1", "dall-e-3", "dall-e-2", "stable-diffusion-xl"),
        maxResolution = "4096x4096",
        supportsImageEdit = true,
        supportsNegativePrompt = true,
        supportsBatch = true,
        responseFormats = listOf(ResponseFormat.URL, ResponseFormat.BASE64),
        defaultModel = "gpt-image-1"
    )

    private val gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = config.baseUrl.trimEnd('/')
    private fun headers() = Headers.Builder()
        .add("Authorization", "Bearer ${config.apiKey}")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder().url("$baseUrl/models").headers(headers()).get().build()
            ).execute()
            Result.success(ConnectionTestResult(
                success = response.isSuccessful,
                message = if (response.isSuccessful) "NewAPI 连接成功" else "HTTP ${response.code}",
                httpCode = response.code
            ))
        } catch (e: IOException) {
            Result.success(ConnectionTestResult(success = false, message = e.message ?: "连接失败"))
        }
    }

    override fun isConfigured() = config.apiKey.isNotBlank()

    override suspend fun generate(request: ImageGenerationRequest): Result<ImageGenerationResult> {
        val model = request.model ?: imageCapabilities.defaultModel
        val body = mapOf(
            "model" to model,
            "prompt" to request.prompt,
            "size" to "${request.width}x${request.height}",
            "n" to request.count,
            "response_format" to "url"
        )
        return execute("$baseUrl/v1/images/generations", body) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val images = (parsed["data"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { GeneratedImage(url = it["url"] as? String) }
            } ?: emptyList()
            ImageGenerationResult(images = images, model = model, providerKey = providerKey)
        }
    }

    override suspend fun edit(request: ImageEditRequest): Result<ImageGenerationResult> {
        val model = request.model ?: imageCapabilities.defaultModel
        val body = mapOf(
            "model" to model,
            "prompt" to request.prompt,
            "image" to request.imageUrl
        )
        return execute("$baseUrl/v1/images/edits", body) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val images = (parsed["data"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { GeneratedImage(url = it["url"] as? String) }
            } ?: emptyList()
            ImageGenerationResult(images = images, model = model, providerKey = providerKey)
        }
    }

    private suspend fun <T> execute(url: String, body: Map<String, Any?>, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .headers(headers())
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
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

class NewApiVideoBackend(
    private val config: ProviderCredentialConfig = ProviderCredentialConfig(
        providerKey = ProviderRegistry.NEWAPI,
        apiKey = "",
        baseUrl = "https://newapi.one"
    )
) : VideoBackend {

    override val providerKey: String = ProviderRegistry.NEWAPI
    override val providerName: String = "NewAPI"

    override val videoCapabilities = VideoCapabilities(
        supportedModels = listOf("sora-1", "veo-3", "seedance-1"),
        maxDurationSeconds = 30,
        supportsTextToVideo = true,
        supportsImageToVideo = true,
        maxConcurrentTasks = 3,
        supportsCancel = true,
        defaultModel = "sora-1"
    )

    private val gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = config.baseUrl.trimEnd('/')
    private fun headers() = Headers.Builder()
        .add("Authorization", "Bearer ${config.apiKey}")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder().url("$baseUrl/models").headers(headers()).get().build()
            ).execute()
            Result.success(ConnectionTestResult(
                success = response.isSuccessful,
                message = if (response.isSuccessful) "NewAPI 连接成功" else "HTTP ${response.code}"
            ))
        } catch (e: IOException) {
            Result.success(ConnectionTestResult(success = false, message = e.message ?: "连接失败"))
        }
    }

    override fun isConfigured() = config.apiKey.isNotBlank()

    override suspend fun generate(request: VideoGenerationRequest): Result<VideoGenerationResult> {
        val model = request.model ?: videoCapabilities.defaultModel
        val body = buildMap {
            put("model", model)
            put("prompt", request.prompt)
            put("duration", request.duration)
            request.imageUrl?.let { put("image", it) }
        }
        return execute("$baseUrl/v1/videos/generations", body) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            VideoGenerationResult(
                taskId = parsed["id"] as? String,
                status = parsed["status"] as? String,
                providerKey = providerKey
            )
        }
    }

    override suspend fun getStatus(taskId: String): Result<VideoTaskStatus> {
        return execute("$baseUrl/v1/videos/$taskId", emptyMap()) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            VideoTaskStatus(
                taskId = taskId,
                status = parsed["status"] as? String ?: "unknown",
                progress = (parsed["progress"] as? Number)?.toInt() ?: 0,
                videoUrl = parsed["video_url"] as? String
            )
        }
    }

    override suspend fun cancel(taskId: String): Result<Boolean> {
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/videos/$taskId/cancel")
                    .headers(headers())
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> execute(url: String, body: Map<String, Any?>, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = if (body.isEmpty()) {
                Request.Builder().url(url).headers(headers()).get().build()
            } else {
                Request.Builder()
                    .url(url)
                    .headers(headers())
                    .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                    .build()
            }
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

class NewApiTextBackend(
    private val config: ProviderCredentialConfig = ProviderCredentialConfig(
        providerKey = ProviderRegistry.NEWAPI,
        apiKey = "",
        baseUrl = "https://newapi.one"
    )
) : TextBackend {

    override val providerKey: String = ProviderRegistry.NEWAPI
    override val providerName: String = "NewAPI"

    override val textCapabilities = TextCapabilities(
        supportedModels = listOf("gpt-4o", "gpt-4o-mini", "claude-3.5-sonnet", "deepseek-chat"),
        maxContextWindow = 128000,
        supportsStreaming = true,
        supportsJsonSchema = true,
        supportsFunctionCalling = true,
        defaultModel = "gpt-4o"
    )

    private val gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = config.baseUrl.trimEnd('/')
    private fun headers() = Headers.Builder()
        .add("Authorization", "Bearer ${config.apiKey}")
        .add("Content-Type", "application/json")
        .build()

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder().url("$baseUrl/models").headers(headers()).get().build()
            ).execute()
            Result.success(ConnectionTestResult(
                success = response.isSuccessful,
                message = if (response.isSuccessful) "NewAPI 连接成功" else "HTTP ${response.code}"
            ))
        } catch (e: IOException) {
            Result.success(ConnectionTestResult(success = false, message = e.message ?: "连接失败"))
        }
    }

    override fun isConfigured() = config.apiKey.isNotBlank()

    override suspend fun generate(request: TextGenerationRequest): Result<TextGenerationResult> {
        val model = request.model ?: textCapabilities.defaultModel
        val body = buildMap {
            put("model", model)
            put("messages", request.messages.map { mapOf("role" to it.role, "content" to it.content) })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            request.systemPrompt?.let { put("system_prompt", it) }
        }
        return execute("$baseUrl/v1/chat/completions", body) { body ->
            val parsed = gson.fromJson(body, Map::class.java)
            val content = (parsed["choices"] as? List<*>)?.firstOrNull()?.let { choice ->
                (choice as? Map<*, *>)?.get("message")?.let { msg ->
                    (msg as? Map<*, *>)?.get("content") as? String
                }
            } ?: ""
            TextGenerationResult(content = content, model = model, providerKey = providerKey)
        }
    }

    private suspend fun <T> execute(url: String, body: Map<String, Any?>, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .headers(headers())
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
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
