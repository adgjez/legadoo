package io.legado.app.video.api

object ProviderModelMapper {
    
    private val modelMappings = mapOf(
        ProviderRegistry.AGNES to ProviderModels(
            chatModels = listOf("agnes-2.5-flash", "agnes-2.5-pro", "agnes-2.5-ultra"),
            imageModels = listOf("agnes-image-2.1-flash", "agnes-image-2.0", "agnes-image-1.5"),
            videoModels = listOf("agnes-video-v2.0", "agnes-video-v1.5", "agnes-video-v1.0"),
            defaultChatModel = "agnes-2.5-flash",
            defaultImageModel = "agnes-image-2.1-flash",
            defaultVideoModel = "agnes-video-v2.0"
        ),
        ProviderRegistry.DALL_E to ProviderModels(
            chatModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
            imageModels = listOf("dall-e-3", "dall-e-2"),
            videoModels = emptyList(),
            defaultChatModel = "gpt-4o",
            defaultImageModel = "dall-e-3",
            defaultVideoModel = null
        ),
        ProviderRegistry.RUNWAY to ProviderModels(
            chatModels = emptyList(),
            imageModels = listOf("gen3_image", "gen4_image"),
            videoModels = listOf("gen3_turbo", "gen3", "gen4_turbo", "gen4"),
            defaultChatModel = null,
            defaultImageModel = "gen3_image",
            defaultVideoModel = "gen3_turbo"
        ),
        ProviderRegistry.PIKA to ProviderModels(
            chatModels = emptyList(),
            imageModels = emptyList(),
            videoModels = listOf("pika-1.5", "pika-1.0", "pika-lite"),
            defaultChatModel = null,
            defaultImageModel = null,
            defaultVideoModel = "pika-1.5"
        ),
        ProviderRegistry.STABILITY_AI to ProviderModels(
            chatModels = emptyList(),
            imageModels = listOf(
                "stable-diffusion-xl-1.0",
                "stable-diffusion-xl-base-1.0",
                "stable-diffusion-1.6",
                "stable-diffusion-3.0",
                "ultra"
            ),
            videoModels = emptyList(),
            defaultChatModel = null,
            defaultImageModel = "stable-diffusion-xl-1.0",
            defaultVideoModel = null
        )
    )
    
    fun getModels(providerId: String): ProviderModels? = modelMappings[providerId]
    
    fun getDefaultChatModel(providerId: String): String? = modelMappings[providerId]?.defaultChatModel
    
    fun getDefaultImageModel(providerId: String): String? = modelMappings[providerId]?.defaultImageModel
    
    fun getDefaultVideoModel(providerId: String): String? = modelMappings[providerId]?.defaultVideoModel
    
    fun supportsModel(providerId: String, modelType: ModelType, modelName: String): Boolean {
        val models = when (modelType) {
            ModelType.CHAT -> modelMappings[providerId]?.chatModels
            ModelType.IMAGE -> modelMappings[providerId]?.imageModels
            ModelType.VIDEO -> modelMappings[providerId]?.videoModels
        }
        return models?.contains(modelName) == true
    }
    
    fun mapModel(
        providerId: String,
        modelType: ModelType,
        requestedModel: String?
    ): String? {
        val models = when (modelType) {
            ModelType.CHAT -> modelMappings[providerId]?.chatModels
            ModelType.IMAGE -> modelMappings[providerId]?.imageModels
            ModelType.VIDEO -> modelMappings[providerId]?.videoModels
        }
        
        if (requestedModel != null && models?.contains(requestedModel) == true) {
            return requestedModel
        }
        
        return when (modelType) {
            ModelType.CHAT -> modelMappings[providerId]?.defaultChatModel
            ModelType.IMAGE -> modelMappings[providerId]?.defaultImageModel
            ModelType.VIDEO -> modelMappings[providerId]?.defaultVideoModel
        }
    }
    
    fun listAvailableModels(providerId: String, modelType: ModelType): List<String> {
        return when (modelType) {
            ModelType.CHAT -> modelMappings[providerId]?.chatModels
            ModelType.IMAGE -> modelMappings[providerId]?.imageModels
            ModelType.VIDEO -> modelMappings[providerId]?.videoModels
        } ?: emptyList()
    }
}

data class ProviderModels(
    val chatModels: List<String>,
    val imageModels: List<String>,
    val videoModels: List<String>,
    val defaultChatModel: String?,
    val defaultImageModel: String?,
    val defaultVideoModel: String?
)

enum class ModelType {
    CHAT,
    IMAGE,
    VIDEO
}
