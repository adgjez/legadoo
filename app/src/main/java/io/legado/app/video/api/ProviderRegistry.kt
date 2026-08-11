package io.legado.app.video.api

object ProviderRegistry {
    
    const val AGNES = "agnes"
    const val DALL_E = "dalle"
    const val RUNWAY = "runway"
    const val PIKA = "pika"
    const val STABILITY_AI = "stability_ai"
    const val GEMINI = "gemini"
    const val GROK = "grok"
    const val ARK = "ark"
    const val KLING = "kling"
    const val NEWAPI = "newapi"
    const val CUSTOM = "custom"
    
    private val providers = mutableMapOf<String, ProviderDescriptor>()
    private val activeProviders = mutableMapOf<String, Provider>()

    fun register(descriptor: ProviderDescriptor) {
        providers[descriptor.key] = descriptor
    }

    fun registerActive(provider: Provider) {
        activeProviders[provider.providerKey] = provider
    }

    fun unregisterActive(providerKey: String) {
        activeProviders.remove(providerKey)
    }

    fun get(key: String): ProviderDescriptor? = providers[key]

    fun getActive(key: String): Provider? = activeProviders[key]

    fun getAll(): List<ProviderDescriptor> = providers.values.toList()

    fun getActiveProviders(): List<Provider> = activeProviders.values.toList()

    fun getAllProviderIds(): List<String> = providers.keys.toList()

    fun getImageProviders(): List<ProviderDescriptor> =
        providers.values.filter { it.capabilities.supportsImage }

    fun getVideoProviders(): List<ProviderDescriptor> =
        providers.values.filter { it.capabilities.supportsVideo }

    fun getTextProviders(): List<ProviderDescriptor> =
        providers.values.filter { it.capabilities.supportsText }
    
    fun getByCapability(capability: MediaCapability): List<ProviderDescriptor> =
        when (capability) {
            MediaCapability.IMAGE -> getImageProviders()
            MediaCapability.VIDEO -> getVideoProviders()
            MediaCapability.TEXT -> getTextProviders()
        }
    
    fun createImageBackend(key: String): ImageBackend? {
        val descriptor = providers[key] ?: return null
        return descriptor.imageBackendFactory?.invoke()
    }
    
    fun createVideoBackend(key: String): VideoBackend? {
        val descriptor = providers[key] ?: return null
        return descriptor.videoBackendFactory?.invoke()
    }
    
    fun createTextBackend(key: String): TextBackend? {
        val descriptor = providers[key] ?: return null
        return descriptor.textBackendFactory?.invoke()
    }
    
    fun getDefaultProviderFor(capability: MediaCapability): String {
        val providers = getByCapability(capability)
        return providers.firstOrNull { it.isDefault }?.key 
            ?: providers.firstOrNull()?.key 
            ?: AGNES
    }

    fun getDefaultProviderId(): String {
        return providers.values.firstOrNull { it.isDefault }?.key ?: AGNES
    }
    
    fun initDefaults() {
        register(ProviderDescriptor(
            key = AGNES,
            displayName = "Agnes AI",
            description = "国内 AI 服务商，支持图像/视频/文本生成",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = true
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = true,
            isBuiltin = true,
            imageBackendFactory = { AgnesBackend() },
            videoBackendFactory = { AgnesBackend() },
            textBackendFactory = { AgnesBackend() }
        ))
        
        register(ProviderDescriptor(
            key = DALL_E,
            displayName = "DALL-E",
            description = "OpenAI 图像生成模型",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = false,
                supportsText = true
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))
        
        register(ProviderDescriptor(
            key = RUNWAY,
            displayName = "Runway",
            description = "专业视频生成平台",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = false
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))
        
        register(ProviderDescriptor(
            key = PIKA,
            displayName = "Pika",
            description = "快速视频生成",
            capabilities = ProviderCapabilityInfo(
                supportsImage = false,
                supportsVideo = true,
                supportsText = false
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))
        
        register(ProviderDescriptor(
            key = STABILITY_AI,
            displayName = "Stability AI",
            description = "Stable Diffusion 服务商",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = false,
                supportsText = false
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))
        
        register(ProviderDescriptor(
            key = NEWAPI,
            displayName = "NewAPI",
            description = "统一视频 API 中转（兼容多家服务商）",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = true
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true),
                CredentialField("base_url", "API 地址", isSecret = false, defaultValue = "https://newapi.one")
            ),
            isDefault = false,
            isBuiltin = true,
            imageBackendFactory = { NewApiImageBackend() },
            videoBackendFactory = { NewApiVideoBackend() },
            textBackendFactory = { NewApiTextBackend() }
        ))

        register(ProviderDescriptor(
            key = GEMINI,
            displayName = "Google Gemini",
            description = "Google 多模态模型（图像/视频/文本）",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = true
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))

        register(ProviderDescriptor(
            key = GROK,
            displayName = "xAI Grok",
            description = "xAI Grok 模型（Aurora 图像 + Grok Video）",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = true
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))

        register(ProviderDescriptor(
            key = ARK,
            displayName = "火山方舟 (Seedream/Seedance)",
            description = "字节跳动视频生成模型",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = false
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true),
                CredentialField("base_url", "API 地址", isSecret = false, defaultValue = "https://ark.cn-beijing.volces.com")
            ),
            isDefault = false,
            isBuiltin = true
        ))

        register(ProviderDescriptor(
            key = KLING,
            displayName = "可灵 Kling",
            description = "快手可灵视频生成模型",
            capabilities = ProviderCapabilityInfo(
                supportsImage = true,
                supportsVideo = true,
                supportsText = false
            ),
            credentialFields = listOf(
                CredentialField("api_key", "API Key", isSecret = true)
            ),
            isDefault = false,
            isBuiltin = true
        ))
    }
}

data class ProviderDescriptor(
    val key: String,
    val displayName: String,
    val description: String,
    val capabilities: ProviderCapabilityInfo,
    val credentialFields: List<CredentialField>,
    val isDefault: Boolean = false,
    val isBuiltin: Boolean = true,
    val imageBackendFactory: (() -> ImageBackend)? = null,
    val videoBackendFactory: (() -> VideoBackend)? = null,
    val textBackendFactory: (() -> TextBackend)? = null
)

data class ProviderCapabilityInfo(
    val supportsImage: Boolean,
    val supportsVideo: Boolean,
    val supportsText: Boolean
)

data class CredentialField(
    val name: String,
    val displayName: String,
    val isSecret: Boolean = false,
    val defaultValue: String? = null,
    val isRequired: Boolean = true
)

enum class MediaCapability {
    IMAGE,
    VIDEO,
    TEXT
}
