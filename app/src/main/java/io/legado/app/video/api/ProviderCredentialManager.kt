package io.legado.app.video.api

import android.content.Context
import android.content.SharedPreferences

object ProviderCredentialManager {
    
    private const val PREF_NAME = "provider_credentials"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_CREDENTIAL_PREFIX = "cred_"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun getActiveProviderId(): String {
        val saved = prefs.getString(KEY_ACTIVE_PROVIDER, null)
        if (saved != null && isProviderConfigured(saved)) {
            return saved
        }
        return ProviderRegistry.getDefaultProviderFor(MediaCapability.IMAGE)
    }
    
    fun setActiveProviderId(providerId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, providerId).apply()
    }
    
    fun getCredentials(providerId: String): ProviderCredentialConfig {
        val prefix = "$KEY_CREDENTIAL_PREFIX$providerId"
        val extraFields = mutableMapOf<String, String>()
        
        val allKeys = prefs.all.keys
        allKeys.filter { it.startsWith("$prefix/") }.forEach { key ->
            val fieldName = key.removePrefix("$prefix/")
            val value = prefs.getString(key, "") ?: ""
            if (value.isNotBlank()) {
                extraFields[fieldName] = value
            }
        }
        
        return ProviderCredentialConfig(
            providerKey = providerId,
            apiKey = extraFields["api_key"] ?: "",
            baseUrl = extraFields["base_url"] ?: getDefaultBaseUrl(providerId),
            extraFields = extraFields
        )
    }
    
    fun saveCredentials(providerId: String, fields: Map<String, String>) {
        val prefix = "$KEY_CREDENTIAL_PREFIX$providerId"
        val editor = prefs.edit()
        
        ProviderRegistry.get(providerId)?.credentialFields?.forEach { field ->
            val key = "$prefix/${field.name}"
            val value = fields[field.name] ?: ""
            if (value.isNotBlank()) {
                editor.putString(key, value)
            } else if (field.defaultValue != null) {
                editor.putString(key, field.defaultValue)
            } else {
                editor.remove(key)
            }
        }
        editor.apply()
    }
    
    fun isProviderConfigured(providerId: String): Boolean {
        val config = getCredentials(providerId)
        return config.apiKey.isNotBlank()
    }
    
    fun getDefaultBaseUrl(providerId: String): String {
        return when (providerId) {
            ProviderRegistry.AGNES -> "https://apihub.agnes-ai.com"
            ProviderRegistry.DALL_E -> "https://api.openai.com"
            ProviderRegistry.RUNWAY -> "https://api.dev.runwayml.com"
            ProviderRegistry.PIKA -> "https://api.pika-labs.com"
            ProviderRegistry.STABILITY_AI -> "https://api.stability.ai"
            ProviderRegistry.NEWAPI -> "https://newapi.one"
            else -> ""
        }
    }
    
    fun getCredentialFields(providerId: String): List<CredentialField> {
        return ProviderRegistry.get(providerId)?.credentialFields ?: emptyList()
    }
    
    fun clearCredentials(providerId: String) {
        val prefix = "$KEY_CREDENTIAL_PREFIX$providerId"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$prefix/") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
