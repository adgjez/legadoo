package io.legado.app.video.pipeline

import android.content.Context
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 风格参考图统一应用机制
 *
 * 借鉴 ArcReel 的 Style Reference：
 * - 上传一张风格参考图
 * - AI 分析风格特征（色彩、光影、笔触、构图）
 * - 所有后续的图像/视频生成都自动应用该风格
 * - 支持风格强度调节
 */

data class StyleProfile(
    val profileId: String,
    val name: String,
    val referenceImageUrl: String?,
    val referenceImagePath: String?,
    val colorPalette: List<String>,
    val lightingStyle: String,
    val artStyle: String,
    val compositionNotes: String,
    val strength: Float = 0.8f,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toStylePrompt(): String {
        return buildString {
            append("Style reference: ")
            append(artStyle)
            append(". Color palette: ${colorPalette.take(3).joinToString(", ")}")
            append(". Lighting: $lightingStyle")
            append(". Composition: $compositionNotes")
            append(". Style strength: ${(strength * 100).toInt()}%")
        }
    }

    fun toPromptModifier(originalPrompt: String): String {
        return buildString {
            append(originalPrompt)
            append(". ")
            append(toStylePrompt())
        }
    }
}

class StyleAnalyzer(private val context: Context) {

    private val profiles = mutableMapOf<String, StyleProfile>()

    suspend fun analyzeStyle(
        imageUrl: String? = null,
        imagePath: String? = null,
        styleName: String? = null
    ): Result<StyleProfile> = withContext(Dispatchers.IO) {
        val prompt = """请分析这张图片的视觉风格，输出以下信息：

1. 主色调：提取3-5个主要颜色（用十六进制表示）
2. 光影风格：如"柔和自然光"、"硬光高对比"、"暖色调"等
3. 艺术风格：如"赛博朋克"、"水彩"、"油画"、"日系动漫"等
4. 构图特点：如"中心构图"、"三分法"、"对称构图"等

输出 JSON 格式：
{
  "color_palette": ["#RRGGBB", "#RRGGBB", ...],
  "lighting_style": "...",
  "art_style": "...",
  "composition_notes": "..."
}

风格参考图路径：${imagePath ?: imageUrl ?: "未提供"}"""

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位专业的视觉风格分析师。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.2f,
                maxTokens = 1024
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val profile = parseStyleProfile(textResult.content, imageUrl, imagePath, styleName)
                profiles[profile.profileId] = profile
                Result.success(profile)
            },
            onFailure = { error ->
                val fallback = createFallbackProfile(imageUrl, imagePath, styleName)
                Result.success(fallback)
            }
        )
    }

    private fun parseStyleProfile(
        content: String,
        imageUrl: String?,
        imagePath: String?,
        styleName: String?
    ): StyleProfile {
        val colors = extractArray(content, "color_palette")
        val lighting = extractString(content, "lighting_style") ?: "柔和自然光"
        val artStyle = extractString(content, "art_style") ?: "写实风格"
        val composition = extractString(content, "composition_notes") ?: "标准构图"

        return StyleProfile(
            profileId = "style_${System.currentTimeMillis()}",
            name = styleName ?: "自定义风格",
            referenceImageUrl = imageUrl,
            referenceImagePath = imagePath,
            colorPalette = colors.ifEmpty { listOf("#333333", "#666666", "#999999") },
            lightingStyle = lighting,
            artStyle = artStyle,
            compositionNotes = composition
        )
    }

    private fun createFallbackProfile(
        imageUrl: String?,
        imagePath: String?,
        styleName: String?
    ): StyleProfile {
        return StyleProfile(
            profileId = "style_fallback_${System.currentTimeMillis()}",
            name = styleName ?: "默认风格",
            referenceImageUrl = imageUrl,
            referenceImagePath = imagePath,
            colorPalette = listOf("#333333", "#666666", "#999999"),
            lightingStyle = "自然光照",
            artStyle = "写实风格",
            compositionNotes = "标准构图"
        )
    }

    private fun extractString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractArray(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"").ifBlank { null }
        }
    }

    fun getProfile(profileId: String): StyleProfile? = profiles[profileId]

    fun getAllProfiles(): List<StyleProfile> = profiles.values.toList()

    fun applyStyleToPrompt(profileId: String, originalPrompt: String): String {
        val profile = profiles[profileId] ?: return originalPrompt
        return profile.toPromptModifier(originalPrompt)
    }

    fun setStrength(profileId: String, strength: Float) {
        val profile = profiles[profileId] ?: return
        profiles[profileId] = profile.copy(strength = strength.coerceIn(0f, 1f))
    }

    fun deleteProfile(profileId: String) {
        profiles.remove(profileId)
    }
}
