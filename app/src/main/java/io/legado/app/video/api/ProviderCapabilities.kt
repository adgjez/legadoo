package io.legado.app.video.api

object ProviderCapabilities {
    
    private val capabilities = mapOf(
        ProviderRegistry.AGNES to CapabilityConfig(
            supportsChat = true,
            supportsImage = true,
            supportsVideo = true,
            maxImageResolution = "4096x4096",
            maxVideoDuration = 30,
            supportsImageToVideo = true,
            supportsVideoToVideo = false,
            maxConcurrentRequests = 5,
            rateLimitPerMinute = 20,
            responseFormats = listOf(ResponseFormat.URL, ResponseFormat.BASE64),
            features = listOf(
                Feature.STREAMING,
                Feature.BATCH_GENERATION,
                Feature.NEGATIVE_PROMPT,
                Feature.STYLE_TRANSFER,
                Feature.CHARACTER_CONSISTENCY
            )
        ),
        ProviderRegistry.DALL_E to CapabilityConfig(
            supportsChat = true,
            supportsImage = true,
            supportsVideo = false,
            maxImageResolution = "1024x1024",
            maxVideoDuration = 0,
            supportsImageToVideo = false,
            supportsVideoToVideo = false,
            maxConcurrentRequests = 10,
            rateLimitPerMinute = 50,
            responseFormats = listOf(ResponseFormat.URL, ResponseFormat.BASE64),
            features = listOf(
                Feature.HD_QUALITY,
                Feature.MULTIPLE_STYLES,
                Feature.STYLE_TRANSFER
            )
        ),
        ProviderRegistry.RUNWAY to CapabilityConfig(
            supportsChat = false,
            supportsImage = true,
            supportsVideo = true,
            maxImageResolution = "1280x720",
            maxVideoDuration = 10,
            supportsImageToVideo = true,
            supportsVideoToVideo = true,
            maxConcurrentRequests = 3,
            rateLimitPerMinute = 10,
            responseFormats = listOf(ResponseFormat.URL),
            features = listOf(
                Feature.IMAGE_TO_VIDEO,
                Feature.VIDEO_TO_VIDEO,
                Feature.HD_QUALITY,
                Feature.MOTION_TRANSFER
            )
        ),
        ProviderRegistry.PIKA to CapabilityConfig(
            supportsChat = false,
            supportsImage = false,
            supportsVideo = true,
            maxImageResolution = "",
            maxVideoDuration = 8,
            supportsImageToVideo = true,
            supportsVideoToVideo = false,
            maxConcurrentRequests = 2,
            rateLimitPerMinute = 5,
            responseFormats = listOf(ResponseFormat.URL),
            features = listOf(
                Feature.IMAGE_TO_VIDEO,
                Feature.FAST_GENERATION,
                Feature.TEXT_TO_VIDEO
            )
        ),
        ProviderRegistry.STABILITY_AI to CapabilityConfig(
            supportsChat = false,
            supportsImage = true,
            supportsVideo = false,
            maxImageResolution = "2048x2048",
            maxVideoDuration = 0,
            supportsImageToVideo = false,
            supportsVideoToVideo = false,
            maxConcurrentRequests = 8,
            rateLimitPerMinute = 25,
            responseFormats = listOf(ResponseFormat.BASE64),
            features = listOf(
                Feature.HD_QUALITY,
                Feature.BATCH_GENERATION,
                Feature.NEGATIVE_PROMPT,
                Feature.HIGH_RESOLUTION
            )
        )
    )
    
    fun getCapability(providerId: String): CapabilityConfig? = capabilities[providerId]
    
    fun supportsChat(providerId: String): Boolean = capabilities[providerId]?.supportsChat == true
    
    fun supportsImage(providerId: String): Boolean = capabilities[providerId]?.supportsImage == true
    
    fun supportsVideo(providerId: String): Boolean = capabilities[providerId]?.supportsVideo == true
    
    fun supportsFeature(providerId: String, feature: Feature): Boolean {
        return capabilities[providerId]?.features?.contains(feature) == true
    }
    
    fun getAllProvidersSupportingChat(): List<String> {
        return capabilities.filter { it.value.supportsChat }.keys.toList()
    }
    
    fun getAllProvidersSupportingImage(): List<String> {
        return capabilities.filter { it.value.supportsImage }.keys.toList()
    }
    
    fun getAllProvidersSupportingVideo(): List<String> {
        return capabilities.filter { it.value.supportsVideo }.keys.toList()
    }
    
    fun getAllProvidersSupportingFeature(feature: Feature): List<String> {
        return capabilities.filter { it.value.features.contains(feature) }.keys.toList()
    }
}

data class CapabilityConfig(
    val supportsChat: Boolean,
    val supportsImage: Boolean,
    val supportsVideo: Boolean,
    val maxImageResolution: String,
    val maxVideoDuration: Int,
    val supportsImageToVideo: Boolean,
    val supportsVideoToVideo: Boolean,
    val maxConcurrentRequests: Int,
    val rateLimitPerMinute: Int,
    val responseFormats: List<ResponseFormat>,
    val features: List<Feature>
)

enum class ResponseFormat {
    URL,
    BASE64,
    FILE_PATH
}

enum class Feature {
    STREAMING,
    BATCH_GENERATION,
    NEGATIVE_PROMPT,
    STYLE_TRANSFER,
    CHARACTER_CONSISTENCY,
    IMAGE_TO_VIDEO,
    VIDEO_TO_VIDEO,
    MOTION_TRANSFER,
    HD_QUALITY,
    MULTIPLE_STYLES,
    FAST_GENERATION,
    TEXT_TO_VIDEO,
    HIGH_RESOLUTION,
    FACE_CONSISTENCY,
    VOICE_CLONE,
    BACKGROUND_REMOVAL
}
