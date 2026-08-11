package io.legado.app.video.api

import android.content.Context

object ApiProviderFactory {

    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (!initialized) {
            AgnesConfig.init(context)
            VideoApiConfigManager.init(context)
            ProviderRegistry.initDefaults()
            initialized = true
        }
    }

    fun getActiveClient(): VideoApiClient? {
        val activeId = VideoApiConfigManager.activeProviderId
        return getClient(activeId)
    }

    fun getClient(providerId: String): VideoApiClient? {
        return when (providerId) {
            ProviderRegistry.AGNES -> AgnesApiClient()
            ProviderRegistry.DALL_E -> DalleApiClient()
            ProviderRegistry.RUNWAY -> RunwayApiClient()
            ProviderRegistry.PIKA -> PikaApiClient()
            ProviderRegistry.STABILITY_AI -> StabilityAiApiClient()
            else -> null
        }
    }

    fun getClientOrDefault(): VideoApiClient {
        val active = getActiveClient()
        if (active != null && active.isConfigured()) {
            return active
        }
        val defaultId = ProviderRegistry.getDefaultProviderId()
        return getClient(defaultId) ?: AgnesApiClient()
    }

    suspend fun <T> withClient(
        capability: ProviderCapability,
        block: suspend (VideoApiClient) -> Result<T>
    ): Result<T> {
        val activeClient = getActiveClient()
        if (activeClient != null && activeClient.isConfigured()) {
            return block(activeClient)
        }

        val fallbackIds = ProviderRegistry.getAllProviderIds()
        for (providerId in fallbackIds) {
            val client = getClient(providerId)
            if (client != null && client.isConfigured()) {
                val result = block(client)
                if (result.isSuccess) {
                    return result
                }
            }
        }

        return Result.failure(Exception("没有可用的 API Provider 支持 ${capability.name}"))
    }
}
