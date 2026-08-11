package io.legado.app.video.pipeline

import android.content.Context
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import io.legado.app.video.api.ImageGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class CharacterAnalyzer(private val context: Context) {

    suspend fun analyze(novelText: String): Result<List<CharacterSheet>> = withContext(Dispatchers.IO) {
        val prompt = """你是一位专业的小说角色分析师。请从以下小说文本中提取所有重要角色，并输出结构化的角色信息。

要求：
1. 只提取视觉相关的信息：外貌、服装、标志性特征、色彩关键词
2. 不要包含性格、关系、剧情等非视觉描述
3. 每个角色的描述应该足够详细，用于AI生成角色设计图
4. 角色列表按照重要性排序

小说文本（前3000字）：
${novelText.take(3000)}

输出格式（JSON）：
```json
[
  {
    "name": "角色姓名",
    "visual_description": "详细的视觉描述：年龄、性别、五官、身材、气质等",
    "traits": [
      {"name": "特征名", "description": "特征描述", "visual_weight": 1.0}
    ],
    "costumes": [
      {"name": "日常服装", "color": "主色调", "material": "材质", "is_default": true}
    ],
    "accessories": ["配饰1", "配饰2"],
    "color_palette": ["主色", "辅色1", "辅色2"],
    "style_tags": ["风格标签1", "风格标签2"],
    "voice_style": "声音风格描述"
  }
]
```"""

        try {
            val result = BackendRouter.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        mapOf("role" to "system", "content" to "你是一位专业的小说角色视觉分析师。"),
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    temperature = 0.3f,
                    maxTokens = 4096
                )
            )

            result.fold(
                onSuccess = { textResult ->
                    val characters = parseCharactersFromText(textResult.content)
                    Result.success(characters)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCharactersFromText(content: String): List<CharacterSheet> {
        val characters = mutableListOf<CharacterSheet>()

        try {
            val jsonStart = content.indexOf('[')
            val jsonEnd = content.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1) {
                return parseFallbackCharacters(content)
            }

            val jsonArray = content.substring(jsonStart, jsonEnd + 1)
            val characterBlocks = jsonArray.split("},{")

            for ((index, block) in characterBlocks.withIndex()) {
                val adjusted = buildString {
                    if (index > 0) append("{")
                    append(block.trim())
                    if (index < characterBlocks.size - 1 && !block.trim().endsWith("}")) append("}")
                }

                val name = extractField(adjusted, "name") ?: "角色${index + 1}"
                val visualDesc = extractField(adjusted, "visual_description") ?: ""

                characters.add(
                    CharacterSheet(
                        characterId = "char_${System.currentTimeMillis()}_$index",
                        name = name,
                        locked = false,
                        referenceImageUrl = null,
                        referenceImagePath = null,
                        visualDescription = visualDesc,
                        traits = extractTraits(adjusted),
                        costumes = extractCostumes(adjusted),
                        accessories = extractStringArray(adjusted, "accessories"),
                        colorPalette = extractStringArray(adjusted, "color_palette"),
                        styleTags = extractStringArray(adjusted, "style_tags"),
                        voiceStyle = extractField(adjusted, "voice_style"),
                        lockedAt = null
                    )
                )
            }
        } catch (e: Exception) {
            return parseFallbackCharacters(content)
        }

        return characters
    }

    private fun parseFallbackCharacters(content: String): List<CharacterSheet> {
        val sentences = content.split(Regex("[。！？\\n]"))
            .filter { it.length > 10 }
            .take(5)

        return sentences.mapIndexed { index, sentence ->
            CharacterSheet(
                characterId = "char_fallback_$index",
                name = "角色${index + 1}",
                locked = false,
                referenceImageUrl = null,
                referenceImagePath = null,
                visualDescription = sentence.take(100),
                traits = emptyList(),
                costumes = emptyList(),
                accessories = emptyList(),
                colorPalette = emptyList(),
                styleTags = emptyList(),
                voiceStyle = null,
                lockedAt = null
            )
        }
    }

    suspend fun generateDesignSheet(character: CharacterSheet): Result<CharacterSheet> = withContext(Dispatchers.IO) {
        if (character.referenceImagePath != null) {
            return@withContext Result.success(character)
        }

        val prompt = buildString {
            append("Character design sheet. ")
            append(character.visualDescription)
            if (character.costumes.isNotEmpty()) {
                append(". Wearing: ${character.costumes.joinToString(", ") { "${it.name} (${it.color})" }}")
            }
            if (character.accessories.isNotEmpty()) {
                append(". Accessories: ${character.accessories.joinToString(", ")}")
            }
            if (character.styleTags.isNotEmpty()) {
                append(". Style: ${character.styleTags.joinToString(", ")}")
            }
            append(". Full body shot, character sheet, white background, front view")
        }

        try {
            val result = BackendRouter.generateImage(
                ImageGenerationRequest(
                    prompt = prompt,
                    width = 1024,
                    height = 1024,
                    count = 1
                )
            )

            result.fold(
                onSuccess = { imageResult ->
                    val imageUrl = imageResult.images.firstOrNull()?.url
                    Result.success(
                        character.copy(
                            referenceImageUrl = imageUrl,
                            version = character.version + 1
                        )
                    )
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractField(json: String, field: String): String? {
        val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractTraits(json: String): List<CharacterTrait> {
        val traits = mutableListOf<CharacterTrait>()
        val regex = "\\{\"name\":\"([^\"]*)\",\"description\":\"([^\"]*)\",\"visual_weight\":([\\d.]+)\\}".toRegex()
        regex.findAll(json).forEach { match ->
            traits.add(
                CharacterTrait(
                    name = match.groupValues[1],
                    description = match.groupValues[2],
                    visualWeight = match.groupValues[3].toFloatOrNull() ?: 1.0f
                )
            )
        }
        if (traits.isEmpty()) {
            val nameRegex = "\"name\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val descRegex = "\"description\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val names = nameRegex.findAll(json).map { it.groupValues[1] }.toList()
            val descs = descRegex.findAll(json).map { it.groupValues[1] }.toList()
            names.forEachIndexed { index, name ->
                if (name != "角色姓名" && name != "特征名") {
                    traits.add(
                        CharacterTrait(
                            name = name,
                            description = descs.getOrElse(index) { "" },
                            visualWeight = 1.0f
                        )
                    )
                }
            }
        }
        return traits
    }

    private fun extractCostumes(json: String): List<CharacterCostume> {
        val costumes = mutableListOf<CharacterCostume>()
        val regex = "\\{\"name\":\"([^\"]*)\",\"color\":\"([^\"]*)\"(?:,\"material\":\"([^\"]*)\")?(?:,\"is_default\":(true|false))?\\}".toRegex()
        regex.findAll(json).forEach { match ->
            costumes.add(
                CharacterCostume(
                    name = match.groupValues[1],
                    color = match.groupValues[2],
                    material = match.groupValues[3].ifBlank { null },
                    isDefault = match.groupValues[4] != "false"
                )
            )
        }
        if (costumes.isEmpty()) {
            val nameRegex = "\"name\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val colorRegex = "\"color\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val names = nameRegex.findAll(json).map { it.groupValues[1] }.toList()
            val colors = colorRegex.findAll(json).map { it.groupValues[1] }.toList()
            names.forEachIndexed { index, name ->
                if (name.contains("服装") || name.contains("装") || name.contains("outfit", ignoreCase = true)) {
                    costumes.add(
                        CharacterCostume(
                            name = name,
                            color = colors.getOrElse(index) { "未知" },
                            isDefault = true
                        )
                    )
                }
            }
        }
        if (costumes.isEmpty()) {
            costumes.add(
                CharacterCostume(
                    name = "日常服装",
                    color = "黑色",
                    isDefault = true
                )
            )
        }
        return costumes
    }

    private fun extractStringArray(json: String, field: String): List<String> {
        val regex = "\"$field\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        val content = match.groupValues[1]
        return content.split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"").ifBlank { null }
        }
    }
}
