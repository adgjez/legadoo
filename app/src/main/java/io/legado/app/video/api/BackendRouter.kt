package io.legado.app.video.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backend Router - 统一的后端路由
 *
 * 借鉴 ArcReel 的 BackendRouter 模式：
 * - 自动选择最佳 provider
 * - 透明处理端点切换
 * - 支持请求级别的 provider 覆盖
 *
 * 使用 BackendProtocols.kt 中定义的数据类
 */

object BackendRouter {

    private val failoverRouter: FailoverRouter by lazy { FailoverRouter() }
    private val capabilityRouteTable: CapabilityRouteTable by lazy { CapabilityRouteTable() }
    private val healthChecker: ProviderHealthChecker by lazy { failoverRouter.getHealthChecker() }

    fun initDefaultRoutes() {
        val providers = getAvailableProviders()

        providers["text"]?.firstOrNull()?.let { primary ->
            capabilityRouteTable.registerRoute(
                CapabilityRouteTable.RouteEntry(
                    capability = ProviderCapability.TEXT_GENERATION,
                    preferredProvider = primary,
                    fallbackProviders = providers["text"]?.drop(1) ?: emptyList()
                )
            )
        }
        providers["image"]?.firstOrNull()?.let { primary ->
            capabilityRouteTable.registerRoute(
                CapabilityRouteTable.RouteEntry(
                    capability = ProviderCapability.IMAGE_GENERATION,
                    preferredProvider = primary,
                    fallbackProviders = providers["image"]?.drop(1) ?: emptyList()
                )
            )
        }
        providers["video"]?.firstOrNull()?.let { primary ->
            capabilityRouteTable.registerRoute(
                CapabilityRouteTable.RouteEntry(
                    capability = ProviderCapability.VIDEO_GENERATION,
                    preferredProvider = primary,
                    fallbackProviders = providers["video"]?.drop(1) ?: emptyList()
                )
            )
        }
    }

    suspend fun generateImage(request: ImageGenerationRequest): Result<ImageGenerationResult> = withContext(Dispatchers.IO) {
        val route = capabilityRouteTable.getRoute(ProviderCapability.IMAGE_GENERATION)
        val primaryKey = route?.preferredProvider
        val fallbackKeys = route?.fallbackProviders ?: emptyList()

        val candidates = listOfNotNull(primaryKey) + fallbackKeys
        var lastError: Exception? = null

        for (providerKey in candidates.ifEmpty { listOf(null) }) {
            val provider = if (providerKey != null && healthChecker.isAvailable(providerKey)) {
                ProviderRegistry.getActive(providerKey) as? ImageBackendProvider
            } else {
                resolveImageProvider(request.model)
            }

            if (provider == null) continue

            val startTime = System.currentTimeMillis()
            try {
                val result = provider.generate(request)
                result.fold(
                    onSuccess = {
                        healthChecker.recordSuccess(provider.providerKey, System.currentTimeMillis() - startTime)
                        return@withContext result
                    },
                    onFailure = { e ->
                        healthChecker.recordFailure(provider.providerKey)
                        lastError = e as? Exception ?: Exception(e.toString())
                    }
                )
            } catch (e: Exception) {
                healthChecker.recordFailure(provider.providerKey)
                lastError = e
            }
        }

        val directProvider = resolveImageProvider(request.model)
        if (directProvider != null && directProvider !in candidates.mapNotNull { if (it is String) null else directProvider }) {
            directProvider.generate(request)
        } else {
            Result.failure(lastError ?: IllegalStateException("No image provider configured"))
        }
    }

    suspend fun generateVideo(request: VideoGenerationRequest): Result<VideoGenerationResult> = withContext(Dispatchers.IO) {
        val route = capabilityRouteTable.getRoute(ProviderCapability.VIDEO_GENERATION)
        val primaryKey = route?.preferredProvider
        val fallbackKeys = route?.fallbackProviders ?: emptyList()

        val candidates = listOfNotNull(primaryKey) + fallbackKeys
        var lastError: Exception? = null

        for (providerKey in candidates.ifEmpty { listOf(null) }) {
            val provider = if (providerKey != null && healthChecker.isAvailable(providerKey)) {
                ProviderRegistry.getActive(providerKey) as? VideoBackendProvider
            } else {
                resolveVideoProvider(request.model)
            }

            if (provider == null) continue

            val startTime = System.currentTimeMillis()
            try {
                val result = provider.generate(request)
                result.fold(
                    onSuccess = {
                        healthChecker.recordSuccess(provider.providerKey, System.currentTimeMillis() - startTime)
                        return@withContext result
                    },
                    onFailure = { e ->
                        healthChecker.recordFailure(provider.providerKey)
                        lastError = e as? Exception ?: Exception(e.toString())
                    }
                )
            } catch (e: Exception) {
                healthChecker.recordFailure(provider.providerKey)
                lastError = e
            }
        }

        val directProvider = resolveVideoProvider(request.model)
        if (directProvider != null) {
            directProvider.generate(request)
        } else {
            Result.failure(lastError ?: IllegalStateException("No video provider configured"))
        }
    }

    suspend fun getVideoStatus(taskId: String, providerKey: String? = null): Result<VideoTaskStatus> = withContext(Dispatchers.IO) {
        val provider = if (providerKey != null) {
            ProviderRegistry.getActive(providerKey) as? VideoBackendProvider
        } else {
            resolveVideoProvider(null)
        }
        provider?.getStatus(taskId)
            ?: Result.failure(IllegalStateException("No video provider configured"))
    }

    suspend fun cancelVideo(taskId: String, providerKey: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val provider = if (providerKey != null) {
            ProviderRegistry.getActive(providerKey) as? VideoBackendProvider
        } else {
            resolveVideoProvider(null)
        }
        provider?.cancel(taskId)
            ?: Result.failure(IllegalStateException("No video provider configured"))
    }

    suspend fun generateText(request: TextGenerationRequest): Result<TextGenerationResult> = withContext(Dispatchers.IO) {
        val route = capabilityRouteTable.getRoute(ProviderCapability.TEXT_GENERATION)
        val primaryKey = route?.preferredProvider
        val fallbackKeys = route?.fallbackProviders ?: emptyList()

        val candidates = listOfNotNull(primaryKey) + fallbackKeys
        var lastError: Exception? = null

        for (providerKey in candidates.ifEmpty { listOf(null) }) {
            val provider = if (providerKey != null && healthChecker.isAvailable(providerKey)) {
                ProviderRegistry.getActive(providerKey) as? TextBackendProvider
            } else {
                resolveTextProvider(request.model)
            }

            if (provider == null) continue

            val startTime = System.currentTimeMillis()
            try {
                val result = provider.generate(request)
                result.fold(
                    onSuccess = {
                        healthChecker.recordSuccess(provider.providerKey, System.currentTimeMillis() - startTime)
                        return@withContext result
                    },
                    onFailure = { e ->
                        healthChecker.recordFailure(provider.providerKey)
                        lastError = e as? Exception ?: Exception(e.toString())
                    }
                )
            } catch (e: Exception) {
                healthChecker.recordFailure(provider.providerKey)
                lastError = e
            }
        }

        val directProvider = resolveTextProvider(request.model)
        if (directProvider != null) {
            directProvider.generate(request)
        } else {
            Result.failure(lastError ?: IllegalStateException("No text provider configured"))
        }
    }

    suspend fun testProvider(providerKey: String, capability: ProviderCapability): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val provider = ProviderRegistry.getActive(providerKey)
        when (capability) {
            ProviderCapability.IMAGE_GENERATION -> (provider as? ImageBackendProvider)?.testConnection()
            ProviderCapability.VIDEO_GENERATION -> (provider as? VideoBackendProvider)?.testConnection()
            ProviderCapability.TEXT_GENERATION -> (provider as? TextBackendProvider)?.testConnection()
            else -> Result.failure(IllegalArgumentException("Cannot test this capability"))
        } ?: Result.failure(IllegalStateException("Provider not found"))
    }

    private fun resolveImageProvider(model: String?): ImageBackendProvider? {
        val providers = ProviderRegistry.getActiveProviders()
        if (model != null) {
            return providers.filterIsInstance<ImageBackendProvider>()
                .firstOrNull { it.imageCapabilities.supportedModels.contains(model) }
        }
        return providers.filterIsInstance<ImageBackendProvider>().firstOrNull()
    }

    private fun resolveVideoProvider(model: String?): VideoBackendProvider? {
        val providers = ProviderRegistry.getActiveProviders()
        if (model != null) {
            return providers.filterIsInstance<VideoBackendProvider>()
                .firstOrNull { it.videoCapabilities.supportedModels.contains(model) }
        }
        return providers.filterIsInstance<VideoBackendProvider>().firstOrNull()
    }

    private fun resolveTextProvider(model: String?): TextBackendProvider? {
        val providers = ProviderRegistry.getActiveProviders()
        if (model != null) {
            return providers.filterIsInstance<TextBackendProvider>()
                .firstOrNull { it.textCapabilities.supportedModels.contains(model) }
        }
        return providers.filterIsInstance<TextBackendProvider>().firstOrNull()
    }

    fun isProviderConfigured(providerKey: String): Boolean {
        val provider = ProviderRegistry.getActive(providerKey) ?: return false
        return when (provider) {
            is ImageBackendProvider -> provider.isConfigured()
            is VideoBackendProvider -> provider.isConfigured()
            is TextBackendProvider -> provider.isConfigured()
            else -> false
        }
    }

    fun getAvailableProviders(): Map<String, List<String>> {
        val providers = ProviderRegistry.getActiveProviders()
        return mapOf(
            "image" to providers.filterIsInstance<ImageBackendProvider>().map { it.providerKey },
            "video" to providers.filterIsInstance<VideoBackendProvider>().map { it.providerKey },
            "text" to providers.filterIsInstance<TextBackendProvider>().map { it.providerKey }
        )
    }

    fun getHealthStatus(providerKey: String): ProviderHealthStatus {
        return healthChecker.getHealthStatus(providerKey)
    }

    fun getAllHealthStatuses(): Map<String, ProviderHealthStatus> {
        return healthChecker.getAllStatuses()
    }

    fun getHealthSummary(): Map<String, Any> {
        val statuses = getAllHealthStatuses()
        return mapOf(
            "total" to statuses.size,
            "healthy" to statuses.count { it.value.status == HealthStatus.HEALTHY },
            "degraded" to statuses.count { it.value.status == HealthStatus.DEGRADED },
            "unhealthy" to statuses.count { it.value.status == HealthStatus.UNHEALTHY },
            "circuit_open" to statuses.count { it.value.status == HealthStatus.CIRCUIT_OPEN }
        )
    }

    fun getFailoverRouter(): FailoverRouter = failoverRouter
    fun getCapabilityRouteTable(): CapabilityRouteTable = capabilityRouteTable
}

// ========== Provider 接口 ==========

interface ImageBackendProvider : ImageBackend {
    val supportsImages: Boolean get() = true
}

interface VideoBackendProvider : VideoBackend {
    val supportsVideos: Boolean get() = true
}

interface TextBackendProvider : TextBackend {
    val supportsText: Boolean get() = true
}

interface Provider : ImageBackendProvider, VideoBackendProvider, TextBackendProvider {
    override val providerKey: String
    override val providerName: String
}

// ========== 便捷扩展 ==========

fun ImageGenerationRequest.toMap(): Map<String, Any?> = mapOf(
    "prompt" to prompt,
    "model" to model,
    "width" to width,
    "height" to height,
    "count" to count,
    "style" to style,
    "referenceImages" to referenceImages.size
)

fun VideoGenerationRequest.toMap(): Map<String, Any?> = mapOf(
    "prompt" to prompt,
    "model" to model,
    "imageUrl" to imageUrl,
    "duration" to duration,
    "aspectRatio" to aspectRatio
)

fun TextGenerationRequest.toMap(): Map<String, Any?> = mapOf(
    "messageCount" to messages.size,
    "model" to model,
    "temperature" to temperature,
    "maxTokens" to maxTokens
)
