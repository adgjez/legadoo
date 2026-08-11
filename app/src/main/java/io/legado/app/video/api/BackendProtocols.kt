package io.legado.app.video.api

interface ImageBackend {
    val providerKey: String
    val providerName: String
    val imageCapabilities: ImageCapabilities
    
    suspend fun generate(request: ImageGenerationRequest): Result<ImageGenerationResult>
    suspend fun edit(request: ImageEditRequest): Result<ImageGenerationResult>
    suspend fun testConnection(): Result<ConnectionTestResult>
    fun isConfigured(): Boolean
}

interface VideoBackend {
    val providerKey: String
    val providerName: String
    val videoCapabilities: VideoCapabilities
    
    suspend fun generate(request: VideoGenerationRequest): Result<VideoGenerationResult>
    suspend fun getStatus(taskId: String): Result<VideoTaskStatus>
    suspend fun cancel(taskId: String): Result<Boolean>
    suspend fun testConnection(): Result<ConnectionTestResult>
    fun isConfigured(): Boolean
}

interface TextBackend {
    val providerKey: String
    val providerName: String
    val textCapabilities: TextCapabilities
    
    suspend fun generate(request: TextGenerationRequest): Result<TextGenerationResult>
    suspend fun testConnection(): Result<ConnectionTestResult>
    fun isConfigured(): Boolean
}

data class ImageCapabilities(
    val supportedModels: List<String>,
    val maxResolution: String,
    val supportsImageEdit: Boolean = false,
    val supportsInpainting: Boolean = false,
    val supportsStyleTransfer: Boolean = false,
    val supportsMultipleReferences: Boolean = false,
    val maxReferences: Int = 1,
    val supportsNegativePrompt: Boolean = false,
    val supportsBatch: Boolean = false,
    val supportsStreaming: Boolean = false,
    val responseFormats: List<ResponseFormat> = listOf(ResponseFormat.URL),
    val defaultModel: String
)

data class VideoCapabilities(
    val supportedModels: List<String>,
    val maxDurationSeconds: Int,
    val supportsTextToVideo: Boolean = true,
    val supportsImageToVideo: Boolean = false,
    val supportsVideoToVideo: Boolean = false,
    val supportsGridToVideo: Boolean = false,
    val supportsReferenceToVideo: Boolean = false,
    val maxConcurrentTasks: Int = 3,
    val supportsStreaming: Boolean = false,
    val supportsCancel: Boolean = true,
    val defaultModel: String
)

data class TextCapabilities(
    val supportedModels: List<String>,
    val maxContextWindow: Int,
    val supportsStreaming: Boolean = false,
    val supportsJsonSchema: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val defaultModel: String
)

data class ImageGenerationRequest(
    val prompt: String,
    val model: String? = null,
    val width: Int = 1024,
    val height: Int = 1024,
    val count: Int = 1,
    val style: String? = null,
    val negativePrompt: String? = null,
    val referenceImages: List<String> = emptyList(),
    val seed: Long? = null,
    val extra: Map<String, Any?> = emptyMap()
)

data class ImageEditRequest(
    val imageUrl: String,
    val prompt: String,
    val model: String? = null,
    val maskUrl: String? = null,
    val extra: Map<String, Any?> = emptyMap()
)

data class ImageGenerationResult(
    val images: List<GeneratedImage>,
    val model: String? = null,
    val providerKey: String = "",
    val error: ApiError? = null
)

data class VideoGenerationRequest(
    val prompt: String,
    val model: String? = null,
    val imageUrl: String? = null,
    val videoUrls: List<String> = emptyList(),
    val duration: Int = 5,
    val aspectRatio: String = "16:9",
    val gridImages: List<String> = emptyList(),
    val referenceImages: List<String> = emptyList(),
    val extra: Map<String, Any?> = emptyMap()
)

data class VideoGenerationResult(
    val taskId: String? = null,
    val videoId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Float? = null,
    val providerKey: String = "",
    val error: ApiError? = null
)

data class VideoTaskStatus(
    val taskId: String,
    val status: String,
    val progress: Int,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Float? = null,
    val error: ApiError? = null
)

data class TextGenerationRequest(
    val messages: List<ChatMessage>,
    val model: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String? = null,
    val jsonSchema: String? = null,
    val functions: List<Map<String, Any>> = emptyList(),
    val stream: Boolean = false,
    val extra: Map<String, Any?> = emptyMap()
)

data class TextGenerationResult(
    val content: String,
    val model: String? = null,
    val providerKey: String = "",
    val usage: TokenUsage? = null,
    val error: ApiError? = null
)
