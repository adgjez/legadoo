package io.legado.app.video.api

import android.content.Context

object ApiProviderFactory {
    
    private var initialized = false
    
    fun ensureInitialized(context: Context) {
        if (!initialized) {
            ProviderRegistry.init(context)
            initialized = true
        }
    }
    
    fun getActiveClient(): VideoApiClient? {
        val activeId = VideoApiConfigManager.activeProviderId
        return getClient(activeId)
    }
    
    fun getClient(providerId: String): VideoApiClient? {
        return ProviderRegistry.getProvider(providerId)
    }
    
    fun getClientOrDefault(): VideoApiClient {
        val active = getActiveClient()
        if (active != null && active.isConfigured()) {
            return active
        }
        val defaultId = ProviderRegistry.getDefaultProviderId()
        return ProviderRegistry.getProvider(defaultId) ?: AgnesApiClient()
    }
    
    suspend fun <T> withClient(
        capability: Capability,
        block: suspend (VideoApiClient) -> Result<T>
    ): Result<T> {
        val activeClient = getActiveClient()
        if (activeClient != null && activeClient.isConfigured()) {
            val activeInfo = ProviderRegistry.getProviderInfo(activeClient.providerId)
            if (activeInfo?.capabilities?.contains(capability) == true) {
                return block(activeClient)
            }
        }
        
        val fallbackProviders = ProviderRegistry.getProvidersByCapability(capability)
            .filter { VideoApiConfigManager.isProviderConfigured(it.id) }
        
        for (providerInfo in fallbackProviders) {
            val client = ProviderRegistry.getProvider(providerInfo.id)
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
