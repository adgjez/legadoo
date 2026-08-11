package io.legado.app.video.api

interface VideoApiClient {
    
    val providerId: String
    
    val providerName: String
    
    suspend fun testConnection(): Result<ConnectionTestResult>
    
    suspend fun generateChat(
        messages: List<ChatMessage>,
        model: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048
    ): Result<ChatResponse>
    
    suspend fun generateImage(
        prompt: String,
        model: String? = null,
        width: Int = 1024,
        height: Int = 1024,
        count: Int = 1,
        style: String? = null
    ): Result<ImageResponse>
    
    suspend fun generateVideo(
        prompt: String,
        model: String? = null,
        imageUrl: String? = null,
        duration: Int = 5,
        aspectRatio: String = "16:9"
    ): Result<VideoResponse>
    
    suspend fun getVideoStatus(taskId: String): Result<VideoStatusResponse>
    
    suspend fun cancelTask(taskId: String): Result<Boolean>
    
    fun isConfigured(): Boolean
}
