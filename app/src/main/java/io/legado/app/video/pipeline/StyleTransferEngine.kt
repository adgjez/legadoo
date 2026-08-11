package io.legado.app.video.pipeline

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.ChatMessage
import io.legado.app.video.api.TextGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StyleTransferEngine - 风格迁移引擎
 *
 * 借鉴 ArcReel 的风格一致性保证：
 * - 帧间风格传递：确保连续帧的视觉风格统一
 * - 风格注入：将分析出的风格特征注入到每个分镜 prompt
 * - 风格记忆：在全项目范围内保持风格一致性
 * - 自适应风格强度：根据内容类型动态调整风格影响程度
 */

class StyleTransferEngine {

    private val styleMemory = mutableMapOf<String, StyleMemoryEntry>()

    data class StyleMemoryEntry(
        val projectId: String,
        val dominantColors: List<String>,
        val lightingPattern: String,
        val textureStyle: String,
        val compositionPatterns: List<String>,
        val intensityMap: Map<String, Float>,
        val updatedAt: Long
    )

    suspend fun injectStyleIntoPrompt(
        projectId: String,
        originalPrompt: String,
        frameIndex: Int,
        characterNames: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val memory = styleMemory[projectId]
        if (memory == null) {
            return@withContext originalPrompt
        }

        val styleIntensity = calculateIntensity(memory, frameIndex, characterNames)

        val styleModifiers = buildString {
            append(". Style: ")
            append(memory.textureStyle)
            append(". Lighting: ")
            append(memory.lightingPattern)
            append(". Color palette: ")
            append(memory.dominantColors.take(3).joinToString(", "))
            append(". Composition: ")
            append(memory.compositionPatterns.take(2).joinToString(", "))
            append(". Style intensity: ${(styleIntensity * 100).toInt()}%")
        }

        buildString {
            append(originalPrompt)
            append(styleModifiers)
        }
    }

    private fun calculateIntensity(
        memory: StyleMemoryEntry,
        frameIndex: Int,
        characterNames: List<String>
    ): Float {
        val baseIntensity = 0.75f

        val characterBoost = if (characterNames.isNotEmpty()) {
            characterNames.mapNotNull { memory.intensityMap[it] }.average().toFloat()
        } else baseIntensity

        val positionFactor = when {
            frameIndex < 3 -> 0.9f
            frameIndex < 7 -> 1.0f
            else -> 0.95f
        }

        return (characterBoost * positionFactor).coerceIn(0.3f, 1.0f)
    }

    suspend fun updateStyleMemory(
        projectId: String,
        frames: List<StoryboardFrame>,
        referenceStyle: String? = null
    ): StyleMemoryEntry = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) {
            return@withContext styleMemory[projectId] ?: createDefaultEntry(projectId)
        }

        val prompt = buildString {
            append("分析以下分镜序列的视觉风格特征：\n\n")
            frames.takeLast(5).forEachIndexed { index, frame ->
                append("Frame ${frame.index}: ${frame.prompt.take(150)}\n")
            }
            if (referenceStyle != null) {
                append("\n参考风格：$referenceStyle\n")
            }
            append("\n请输出JSON：\n")
            append("{\"dominant_colors\":[\"#RRGGBB\",...],\"lighting\":\"光照模式\",\"texture\":\"纹理风格\",\"compositions\":[\"构图1\",\"构图2\"],\"style_confidence\":0.0-1.0}")
        }

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = "你是一位视觉风格分析师。"),
                    ChatMessage(role = "user", content = prompt)
                ),
                temperature = 0.1f,
                maxTokens = 2048
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val content = textResult.content
                val entry = StyleMemoryEntry(
                    projectId = projectId,
                    dominantColors = extractList_(content, "dominant_colors").ifEmpty { listOf("#333333", "#666666", "#999999") },
                    lightingPattern = extractString_(content, "lighting") ?: "natural lighting",
                    textureStyle = extractString_(content, "texture") ?: "cinematic",
                    compositionPatterns = extractList_(content, "compositions").ifEmpty { listOf("standard composition") },
                    intensityMap = frames.map { it.characterRefs }.flatten().distinct().associateWith { 0.8f },
                    updatedAt = System.currentTimeMillis()
                )
                styleMemory[projectId] = entry
                entry
            },
            onFailure = {
                createDefaultEntry(projectId)
            }
        )
    }

    private fun createDefaultEntry(projectId: String): StyleMemoryEntry {
        val entry = StyleMemoryEntry(
            projectId = projectId,
            dominantColors = listOf("#333333", "#666666", "#999999"),
            lightingPattern = "natural lighting with soft shadows",
            textureStyle = "photorealistic",
            compositionPatterns = listOf("rule of thirds", "balanced framing"),
            intensityMap = emptyMap(),
            updatedAt = System.currentTimeMillis()
        )
        styleMemory[projectId] = entry
        return entry
    }

    private fun extractString_(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractList_(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull {
            it.trim().removeSurrounding("\"").ifBlank { null }
        }
    }

    fun getStyleMemory(projectId: String): StyleMemoryEntry? = styleMemory[projectId]

    fun clearStyleMemory(projectId: String) {
        styleMemory.remove(projectId)
    }

    fun applyGlobalStyle(
        prompts: List<String>,
        styleProfile: StyleProfile?,
        projectId: String
    ): List<String> {
        val baseStyle = styleProfile?.toPromptModifier("")?.trim() ?: ""
        val memory = styleMemory[projectId]

        return prompts.mapIndexed { index, prompt ->
            buildString {
                append(prompt)
                if (baseStyle.isNotBlank()) {
                    append(". ")
                    append(baseStyle)
                }
                if (memory != null) {
                    append(". Consistent visual style: ")
                    append(memory.textureStyle)
                    append(", ")
                    append(memory.lightingPattern)
                }
            }
        }
    }
}

/**
 * 风格预设 - 常用风格配置
 */

object StylePresets {
    val CINEMATIC = TransferStylePreset(
        name = "电影风格",
        description = "好莱坞电影质感，冷暖对比，大气光影",
        lighting = "dramatic cinematic lighting with strong contrast",
        colorPalette = listOf("#1a1a2e", "#16213e", "#0f3460", "#e94560", "#eaeaea"),
        texture = "photorealistic, high detail, film grain",
        intensity = 0.85f
    )

    val ANIME = TransferStylePreset(
        name = "动漫风格",
        description = "日式动画风格，鲜艳色彩，流畅线条",
        lighting = "soft cel-shaded lighting",
        colorPalette = listOf("#ff6b6b", "#4ecdc4", "#ffe66d", "#95e1d3", "#f38181"),
        texture = "anime illustration, clean lines, flat shading",
        intensity = 0.9f
    )

    val CYBERPUNK = TransferStylePreset(
        name = "赛博朋克",
        description = "未来科技感，霓虹灯光，雨夜氛围",
        lighting = "neon lighting, pink and blue glow, rain reflections",
        colorPalette = listOf("#ff006e", "#3a86ff", "#8338ec", "#ffbe0b", "#fb5607"),
        texture = "cyberpunk aesthetic, neon lights, futuristic",
        intensity = 0.9f
    )

    val WATERCOLOR = TransferStylePreset(
        name = "水彩风格",
        description = "水彩画质感，柔和过渡，艺术气息",
        lighting = "soft watercolor lighting, gentle washes",
        colorPalette = listOf("#a8dadc", "#457b9d", "#f1faee", "#e63946", "#1d3557"),
        texture = "watercolor painting, soft edges, flowing colors",
        intensity = 0.8f
    )

    val NOIR = TransferStylePreset(
        name = "黑色电影",
        description = "黑白对比，硬朗光影，神秘氛围",
        lighting = "high contrast black and white lighting, dramatic shadows",
        colorPalette = listOf("#000000", "#333333", "#666666", "#999999", "#ffffff"),
        texture = "black and white film, noir aesthetic, dramatic shadows",
        intensity = 0.85f
    )

    val COMIC = TransferStylePreset(
        name = "漫画风格",
        description = "漫画质感，粗线条，分镜构图",
        lighting = "bold comic book lighting, halftone patterns",
        colorPalette = listOf("#ff0000", "#0000ff", "#ffff00", "#00ff00", "#000000"),
        texture = "comic book illustration, bold outlines, halftone dots",
        intensity = 0.9f
    )

    val DOCUMENTARY = TransferStylePreset(
        name = "纪录片风格",
        description = "真实纪实感，自然光，纪实美学",
        lighting = "natural documentary lighting, candid",
        colorPalette = listOf("#8B4513", "#D2691E", "#DAA520", "#696969", "#2F4F4F"),
        texture = "documentary footage, handheld camera, natural colors",
        intensity = 0.6f
    )

    val list = listOf(CINEMATIC, ANIME, CYBERPUNK, WATERCOLOR, NOIR, COMIC, DOCUMENTARY)

    fun getByName(name: String): TransferStylePreset? = list.find { it.name == name }
}

data class TransferStylePreset(
    val name: String,
    val description: String,
    val lighting: String,
    val colorPalette: List<String>,
    val texture: String,
    val intensity: Float
) {
    fun toPromptModifier(): String = buildString {
        append("$texture. $lighting. Color palette: ${colorPalette.take(3).joinToString(", ")}. Style preset: $name.")
    }
}
