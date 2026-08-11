package io.legado.app.video.api

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val content: String,
    val model: String? = null,
    val usage: TokenUsage? = null,
    val error: ApiError? = null
)

data class ImageResponse(
    val images: List<GeneratedImage>,
    val model: String? = null,
    val error: ApiError? = null
)

data class GeneratedImage(
    val url: String? = null,
    val base64: String? = null,
    val localPath: String? = null,
    val width: Int = 0,
    val height: Int = 0
)

data class VideoResponse(
    val taskId: String? = null,
    val videoId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Float? = null,
    val error: ApiError? = null
)

data class VideoStatusResponse(
    val taskId: String,
    val status: String,
    val progress: Int,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Float? = null,
    val error: ApiError? = null
)

data class ConnectionTestResult(
    var success: Boolean = false,
    var message: String = "",
    var latencyMs: Long = 0,
    var httpCode: Int = 0
)

data class ApiError(
    val code: String? = null,
    val message: String? = null,
    val type: String? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

data class VideoApiConfig(
    val providerId: String,
    val providerName: String,
    val apiKey: String,
    val baseUrl: String,
    val timeoutSeconds: Int = 300,
    val imageModel: String? = null,
    val videoModel: String? = null,
    val chatModel: String? = null,
    val isEnabled: Boolean = true,
    val extraConfig: Map<String, String> = emptyMap()
)
