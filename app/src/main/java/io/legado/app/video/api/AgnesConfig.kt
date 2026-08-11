package io.legado.app.video.api

import android.content.Context
import android.content.SharedPreferences

object AgnesConfig {
    private const val PREF_NAME = "agnes_config"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_IMAGE_MODEL = "image_model"
    private const val KEY_VIDEO_MODEL = "video_model"
    private const val KEY_TIMEOUT = "timeout_seconds"
    private const val KEY_CHAT_MODEL = "chat_model"
    
    const val DEFAULT_BASE_URL = "https://apihub.agnes-ai.com"
    const val DEFAULT_IMAGE_MODEL = "agnes-image-2.1-flash"
    const val DEFAULT_VIDEO_MODEL = "agnes-video-v2.0"
    const val DEFAULT_CHAT_MODEL = "agnes-2.5-flash"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()
    
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()
    
    var imageModel: String
        get() = prefs.getString(KEY_IMAGE_MODEL, DEFAULT_IMAGE_MODEL) ?: DEFAULT_IMAGE_MODEL
        set(value) = prefs.edit().putString(KEY_IMAGE_MODEL, value).apply()
    
    var videoModel: String
        get() = prefs.getString(KEY_VIDEO_MODEL, DEFAULT_VIDEO_MODEL) ?: DEFAULT_VIDEO_MODEL
        set(value) = prefs.edit().putString(KEY_VIDEO_MODEL, value).apply()
    
    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT, 300)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()
    
    var chatModel: String
        get() = prefs.getString(KEY_CHAT_MODEL, DEFAULT_CHAT_MODEL) ?: DEFAULT_CHAT_MODEL
        set(value) = prefs.edit().putString(KEY_CHAT_MODEL, value).apply()
    
    fun isConfigured(): Boolean = apiKey.isNotBlank()
}