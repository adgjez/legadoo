package io.legado.app.video.api

import kotlin.math.min

/**
 * Provider SDK 工厂 + 端点自动推断
 *
 * 借鉴 ArcReel 的 provider 架构：
 * - 每个 provider 有 shared module
 * - 自动根据模型名推断端点
 * - 统一的请求/响应格式
 * - 支持自定义 endpoint 覆盖
 */

enum class ProviderEndpoint(val displayName: String) {
    GEMINI_IMAGE("Google Gemini Image"),
    GEMINI_VIDEO("Google Gemini Video"),
    ARK_IMAGE("Volcengine Ark (Seedream)"),
    ARK_SEEDANCE("Volcengine Ark (Seedance)"),
    ARK_WANX("Volcengine Ark (Wanx)"),
    OPENAI_IMAGE("OpenAI Image (DALL·E)"),
    OPENAI_VIDEO("OpenAI Video (Sora)"),
    GROK_IMAGE("xAI Grok Image (Aurora)"),
    GROK_VIDEO("xAI Grok Video"),
    V2_VIDEO("V2 Video Generations"),
    NEWAPI_UNIFIED_IMAGE("NewAPI Unified Image"),
    NEWAPI_UNIFIED_VIDEO("NewAPI Unified Video"),
    KLING_IMAGE("Kling Image"),
    KLING_VIDEO("Kling Video"),
    CUSTOM_IMAGE("Custom Image"),
    CUSTOM_VIDEO("Custom Video")
}

object EndpointRouter {

    private val modelToEndpoint = mapOf(
        // Google / Gemini
        "nano-banana" to ProviderEndpoint.GEMINI_IMAGE,
        "gemini-image" to ProviderEndpoint.GEMINI_IMAGE,
        "gemini-2.5-flash-image" to ProviderEndpoint.GEMINI_IMAGE,
        "gemini-2.0-flash-exp-image-generation" to ProviderEndpoint.GEMINI_IMAGE,
        "gemini-3-pro-image-preview" to ProviderEndpoint.GEMINI_IMAGE,

        // Volcengine / Ark
        "seedream" to ProviderEndpoint.ARK_IMAGE,
        "seedance" to ProviderEndpoint.ARK_SEEDANCE,
        "wanx" to ProviderEndpoint.ARK_WANX,
        "doubao-seedream" to ProviderEndpoint.ARK_IMAGE,
        "doubao-seedance" to ProviderEndpoint.ARK_SEEDANCE,

        // OpenAI
        "dall-e" to ProviderEndpoint.OPENAI_IMAGE,
        "gpt-image" to ProviderEndpoint.OPENAI_IMAGE,
        "sora" to ProviderEndpoint.OPENAI_VIDEO,

        // xAI / Grok
        "aurora" to ProviderEndpoint.GROK_IMAGE,
        "grok-image" to ProviderEndpoint.GROK_IMAGE,
        "grok-video" to ProviderEndpoint.GROK_VIDEO,

        // Kling
        "kling" to ProviderEndpoint.KLING_IMAGE,
        "kling-v2" to ProviderEndpoint.KLING_VIDEO,

        // NewAPI
        "newapi" to ProviderEndpoint.NEWAPI_UNIFIED_IMAGE,
        "newapi-video" to ProviderEndpoint.NEWAPI_UNIFIED_VIDEO,

        // V2
        "v2" to ProviderEndpoint.V2_VIDEO,
        "minimax" to ProviderEndpoint.V2_VIDEO
    )

    fun inferEndpoint(model: String, mediaType: MediaType): ProviderEndpoint {
        val modelLower = model.lowercase()

        // Exact match first
        modelToEndpoint.forEach { (key, endpoint) ->
            if (modelLower.contains(key.lowercase())) {
                return endpoint
            }
        }

        // Provider-based fallback
        return when {
            modelLower.contains("seedream") || modelLower.contains("aurora") || modelLower.contains("nano-banana") -> {
                if (mediaType == MediaType.VIDEO) ProviderEndpoint.ARK_SEEDANCE
                else ProviderEndpoint.ARK_IMAGE
            }
            modelLower.contains("seedance") || modelLower.contains("wanx") -> ProviderEndpoint.ARK_VIDEO
            modelLower.contains("kling") -> {
                if (mediaType == MediaType.VIDEO) ProviderEndpoint.KLING_VIDEO
                else ProviderEndpoint.KLING_IMAGE
            }
            modelLower.contains("sora") -> ProviderEndpoint.OPENAI_VIDEO
            modelLower.contains("grok") -> {
                if (mediaType == MediaType.VIDEO) ProviderEndpoint.GROK_VIDEO
                else ProviderEndpoint.GROK_IMAGE
            }
            modelLower.contains("doubao") -> {
                if (mediaType == MediaType.VIDEO) ProviderEndpoint.ARK_SEEDANCE
                else ProviderEndpoint.ARK_IMAGE
            }
            else -> {
                if (mediaType == MediaType.VIDEO) ProviderEndpoint.CUSTOM_VIDEO
                else ProviderEndpoint.CUSTOM_IMAGE
            }
        }
    }

    fun isVideoEndpoint(endpoint: ProviderEndpoint): Boolean {
        return endpoint == ProviderEndpoint.ARK_SEEDANCE ||
                endpoint == ProviderEndpoint.OPENAI_VIDEO ||
                endpoint == ProviderEndpoint.GROK_VIDEO ||
                endpoint == ProviderEndpoint.V2_VIDEO ||
                endpoint == ProviderEndpoint.KLING_VIDEO ||
                endpoint == ProviderEndpoint.NEWAPI_UNIFIED_VIDEO ||
                endpoint == ProviderEndpoint.CUSTOM_VIDEO
    }

    fun isImageEndpoint(endpoint: ProviderEndpoint): Boolean {
        return !isVideoEndpoint(endpoint) ||
                endpoint == ProviderEndpoint.ARK_IMAGE ||
                endpoint == ProviderEndpoint.GEMINI_IMAGE ||
                endpoint == ProviderEndpoint.OPENAI_IMAGE ||
                endpoint == ProviderEndpoint.GROK_IMAGE ||
                endpoint == ProviderEndpoint.KLING_IMAGE ||
                endpoint == ProviderEndpoint.NEWAPI_UNIFIED_IMAGE ||
                endpoint == ProviderEndpoint.CUSTOM_IMAGE
    }
}

enum class MediaType {
    IMAGE,
    VIDEO,
    TEXT
}

/**
 * 统一的请求模型 - 参考 ArcReel 的 API 规范
 */

data class UnifiedImageRequest(
    val model: String,
    val prompt: String,
    val referenceImages: List<ReferenceImage> = emptyList(),
    val width: Int = 1024,
    val height: Int = 1024,
    val count: Int = 1,
    val style: String? = null,
    val responseFormat: ResponseFormat = ResponseFormat.URL
)

data class ReferenceImage(
    val url: String? = null,
    val localPath: String? = null,
    val label: String? = null,
    val description: String? = null,
    val role: ReferenceRole = ReferenceRole.REFERENCE
)

enum class ReferenceRole {
    CHARACTER,
    SCENE,
    PROP,
    STYLE,
    REFERENCE,
    PREVIOUS_FRAME,
    GRID
}

data class UnifiedVideoRequest(
    val model: String,
    val prompt: String,
    val referenceImages: List<ReferenceImage> = emptyList(),
    val gridImages: List<ReferenceImage> = emptyList(),
    val duration: Int = 5,
    val aspectRatio: String = "16:9",
    val promptExtend: Boolean = false,
    val watermark: Boolean = true,
    val resolution: String = "720p"
)

data class UnifiedTextRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val stream: Boolean = false
)

enum class ResponseFormat {
    URL,
    BASE64,
    FILE_PATH
}

/**
 * 分辨率解析器 - 根据 provider 能力选择最佳分辨率
 */
object ResolutionResolver {

    private val providerMaxResolutions = mapOf(
        ProviderEndpoint.GEMINI_IMAGE to "2K",
        ProviderEndpoint.GEMINI_VIDEO to "1080p",
        ProviderEndpoint.ARK_IMAGE to "4K",
        ProviderEndpoint.ARK_SEEDANCE to "1080p",
        ProviderEndpoint.OPENAI_IMAGE to "1024x1024",
        ProviderEndpoint.OPENAI_VIDEO to "1080p",
        ProviderEndpoint.GROK_IMAGE to "4K",
        ProviderEndpoint.GROK_VIDEO to "1080p",
        ProviderEndpoint.V2_VIDEO to "4K",
        ProviderEndpoint.KLING_IMAGE to "1080p",
        ProviderEndpoint.KLING_VIDEO to "1080p"
    )

    private val videoBitrates = mapOf(
        "720p" to 4_000_000,
        "1080p" to 8_000_000,
        "4K" to 20_000_000
    )

    fun resolveForImage(endpoint: ProviderEndpoint, requestedWidth: Int, requestedHeight: Int): Pair<Int, Int> {
        val maxRes = providerMaxResolutions[endpoint] ?: "1080p"
        val (maxW, maxH) = parseResolution(maxRes)
        val w = min(requestedWidth, maxW)
        val h = min(requestedHeight, maxH)
        return w to h
    }

    fun resolveForVideo(endpoint: ProviderEndpoint, requestedResolution: String): String {
        val maxRes = providerMaxResolutions[endpoint] ?: "720p"
        val maxOrder = resolutionOrder(maxRes)
        val requestedOrder = resolutionOrder(requestedResolution)
        return if (requestedOrder <= maxOrder) requestedResolution else maxRes
    }

    private fun parseResolution(res: String): Pair<Int, Int> {
        return when (res) {
            "720p" -> 1280 to 720
            "1080p" -> 1920 to 1080
            "2K" -> 2560 to 1440
            "4K" -> 3840 to 2160
            "1024x1024" -> 1024 to 1024
            else -> {
                val parts = res.split("x")
                if (parts.size == 2) {
                    parts[0].toIntOrNull() ?: 1024 to (parts[1].toIntOrNull() ?: 1024)
                } else 1024 to 1024
            }
        }
    }

    private fun resolutionOrder(res: String): Int {
        return when (res) {
            "4K" -> 4
            "2K" -> 3
            "1080p" -> 2
            "720p" -> 1
            else -> 0
        }
    }

    fun getBitrate(resolution: String): Int {
        return videoBitrates[resolution] ?: 4_000_000
    }
}
