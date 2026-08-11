package io.legado.app.video.api

import android.content.Context
import android.content.SharedPreferences

object VideoApiConfigManager {
    
    private const val PREF_NAME = "video_api_config"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_PROVIDER_PREFIX = "provider_"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    var activeProviderId: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, ProviderRegistry.AGNES) ?: ProviderRegistry.AGNES
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()
    
    fun getProviderConfig(providerId: String): VideoApiConfig {
        val prefix = "$KEY_PROVIDER_PREFIX$providerId"
        return VideoApiConfig(
            providerId = providerId,
            providerName = prefs.getString("${prefix}_name", providerId) ?: providerId,
            apiKey = prefs.getString("${prefix}_api_key", "") ?: "",
            baseUrl = prefs.getString("${prefix}_base_url", "") ?: "",
            timeoutSeconds = prefs.getInt("${prefix}_timeout", 300),
            imageModel = prefs.getString("${prefix}_image_model", null),
            videoModel = prefs.getString("${prefix}_video_model", null),
            chatModel = prefs.getString("${prefix}_chat_model", null),
            isEnabled = prefs.getBoolean("${prefix}_enabled", true)
        )
    }
    
    fun saveProviderConfig(config: VideoApiConfig) {
        val prefix = "$KEY_PROVIDER_PREFIX${config.providerId}"
        prefs.edit().apply {
            putString("${prefix}_name", config.providerName)
            putString("${prefix}_api_key", config.apiKey)
            putString("${prefix}_base_url", config.baseUrl)
            putInt("${prefix}_timeout", config.timeoutSeconds)
            putString("${prefix}_image_model", config.imageModel)
            putString("${prefix}_video_model", config.videoModel)
            putString("${prefix}_chat_model", config.chatModel)
            putBoolean("${prefix}_enabled", config.isEnabled)
        }.apply()
    }
    
    fun isProviderConfigured(providerId: String): Boolean {
        val config = getProviderConfig(providerId)
        return config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
    }
    
    fun getAllProviderIds(): List<String> = ProviderRegistry.getAllProviderIds()
    
    fun getActiveConfig(): VideoApiConfig = getProviderConfig(activeProviderId)
}
