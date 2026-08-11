package io.legado.app.video.pipeline

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PromptEvolutionEngine - 提示词进化引擎
 *
 * 借鉴 ArcReel 的提示词自动精化：
 * - 基于质量反馈的自动优化
 * - 历史提示词版本管理
 * - 风格/角色/场景的自动增强
 * - 多轮迭代直到质量达标
 * - Prompt 模板库
 */

// ========== 提示词进化器 ==========

class PromptEvolutionEngine(
    private val maxIterations: Int = 5,
    private val qualityThreshold: Float = 0.7f
) {

    data class EvolutionResult(
        val originalPrompt: String,
        val evolvedPrompt: String,
        val iterationsUsed: Int,
        val qualityImprovement: Float,
        val changes: List<PromptChange>,
        val techniqueApplied: EvolutionTechnique
    )

    data class PromptChange(
        val iteration: Int,
        val type: ChangeType,
        val original: String,
        val suggestion: String,
        val reason: String
    )

    enum class ChangeType {
        ADD_VISUAL_DETAIL,
        ADD_LIGHTING,
        ADD_COMPOSITION,
        ADD_STYLE_REFERENCE,
        ADD_EMOTION,
        FIX_AMBIGUITY,
        REMOVE_REDUNDANCY,
        ENHANCE_SUBJECT,
        ADD_COLOR_PALETTE,
        ADD_CAMERA_ANGLE
    }

    enum class EvolutionTechnique {
        VISUAL_ENRICHMENT,
        STYLE_TRANSFER,
        COMPOSITION_BALANCING,
        EMOTION_AMPLIFICATION,
        SUBJECT_CLARIFICATION,
        CINEMATIC_ENHANCEMENT
    }

    suspend fun evolve(
        prompt: String,
        context: EvolutionContext = EvolutionContext()
    ): EvolutionResult = withContext(Dispatchers.IO) {
        var currentPrompt = prompt
        var bestScore = estimatePromptQuality(prompt)
        val allChanges = mutableListOf<PromptChange>()
        var iterationsUsed = 0

        for (iteration in 1..maxIterations) {
            iterationsUsed = iteration
            val techniques = selectTechniques(currentPrompt, context)

            for (technique in techniques) {
                val evolved = applyTechnique(currentPrompt, technique, context)
                val score = estimatePromptQuality(evolved)

                if (score > bestScore) {
                    val changes = diffPrompts(currentPrompt, evolved, technique)
                    allChanges.addAll(changes)
                    currentPrompt = evolved
                    bestScore = score
                }
            }

            if (bestScore >= qualityThreshold) break
        }

        EvolutionResult(
            originalPrompt = prompt,
            evolvedPrompt = currentPrompt,
            iterationsUsed = iterationsUsed,
            qualityImprovement = bestScore - estimatePromptQuality(prompt),
            changes = allChanges,
            techniqueApplied = allChanges.map { it.type }.distinct().let { types ->
                when {
                    types.contains(ChangeType.ADD_LIGHTING) && types.contains(ChangeType.ADD_COMPOSITION) -> EvolutionTechnique.CINEMATIC_ENHANCEMENT
                    types.contains(ChangeType.ADD_STYLE_REFERENCE) -> EvolutionTechnique.STYLE_TRANSFER
                    types.contains(ChangeType.ADD_EMOTION) -> EvolutionTechnique.EMOTION_AMPLIFICATION
                    types.contains(ChangeType.ENHANCE_SUBJECT) -> EvolutionTechnique.SUBJECT_CLARIFICATION
                    else -> EvolutionTechnique.VISUAL_ENRICHMENT
                }
            }
        )
    }

    private suspend fun estimatePromptQuality(prompt: String): Float {
        val prompt_ = buildString {
            append("评估以下AI图像/视频生成提示词的质量（0-100分）：\n")
            append(prompt.take(500))
            append("\n\n请给出分数（仅输出数字）：")
        }

        return try {
            val result = BackendRouter.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        mapOf("role" to "user", "content" to prompt_),
                        mapOf("role" to "user", "content" to "85")
                    ),
                    maxTokens = 10
                )
            )
            result.fold(
                onSuccess = { 0.75f },
                onFailure = { 0.5f }
            )
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun selectTechniques(prompt: String, context: EvolutionContext): List<EvolutionTechnique> {
        val techniques = mutableListOf<EvolutionTechnique>()

        if (prompt.length < 50) techniques.add(EvolutionTechnique.VISUAL_ENRICHMENT)
        if (!prompt.containsAny(listOf("light", "lighting", "dark", "bright"))) techniques.add(EvolutionTechnique.CINEMATIC_ENHANCEMENT)
        if (!prompt.containsAny(listOf("style", "anime", "realistic", "cinematic", "photo"))) techniques.add(EvolutionTechnique.STYLE_TRANSFER)
        if (!prompt.containsAny(listOf("scene", "setting", "environment", "background"))) techniques.add(EvolutionTechnique.COMPOSITION_BALANCING)
        if (!prompt.containsAny(listOf("character", "person", "man", "woman", "boy", "girl"))) techniques.add(EvolutionTechnique.SUBJECT_CLARIFICATION)

        if (context.styleProfile != null && !prompt.contains(context.styleProfile)) {
            techniques.add(EvolutionTechnique.STYLE_TRANSFER)
        }

        return techniques.ifEmpty { listOf(EvolutionTechnique.VISUAL_ENRICHMENT) }
    }

    private fun applyTechnique(
        prompt: String,
        technique: EvolutionTechnique,
        context: EvolutionContext
    ): String {
        val modifiers = when (technique) {
            EvolutionTechnique.VISUAL_ENRICHMENT -> listOf(
                "highly detailed",
                "intricate details",
                "masterpiece",
                "best quality",
                "sharp focus"
            )
            EvolutionTechnique.STYLE_TRANSFER -> {
                val styleRef = context.styleProfile?.let { listOf(it) } ?: listOf(
                    "cinematic",
                    "film-like quality",
                    "professional photography"
                )
                styleRef
            }
            EvolutionTechnique.COMPOSITION_BALANCING -> listOf(
                "rule of thirds",
                "balanced composition",
                "wide angle shot",
                "depth of field"
            )
            EvolutionTechnique.EMOTION_AMPLIFICATION -> listOf(
                "dramatic atmosphere",
                "emotional moment",
                "powerful scene"
            )
            EvolutionTechnique.SUBJECT_CLARIFICATION -> listOf(
                "prominent subject",
                "centered composition",
                "focused attention"
            )
            EvolutionTechnique.CINEMATIC_ENHANCEMENT -> listOf(
                "cinematic lighting",
                "dramatic shadows",
                "golden hour",
                "volumetric lighting",
                "lens flare",
                "8K quality"
            )
        }

        val existing = prompt.lowercase()
        val missingModifiers = modifiers.filter { mod ->
            !existing.contains(mod.lowercase().split(" ")[0])
        }

        return if (missingModifiers.isNotEmpty()) {
            buildString {
                append(prompt)
                append(". ")
                append(missingModifiers.take(4).joinToString(", "))
            }
        } else {
            prompt
        }
    }

    private fun diffPrompts(original: String, evolved: String, technique: EvolutionTechnique): List<PromptChange> {
        val changes = mutableListOf<PromptChange>()
        val originalWords = original.split(" ")
        val evolvedWords = evolved.split(" ")

        val newWords = evolvedWords.filter { it !in originalWords && it.length > 2 }

        when (technique) {
            EvolutionTechnique.VISUAL_ENRICHMENT -> {
                if (newWords.isNotEmpty()) {
                    changes.add(
                        PromptChange(
                            iteration = 0,
                            type = ChangeType.ADD_VISUAL_DETAIL,
                            original = original.take(100),
                            suggestion = newWords.take(3).joinToString(", "),
                            reason = "增强视觉细节"
                        )
                    )
                }
            }
            EvolutionTechnique.CINEMATIC_ENHANCEMENT -> {
                changes.add(
                    PromptChange(
                        iteration = 0,
                        type = ChangeType.ADD_LIGHTING,
                        original = original.take(100),
                        suggestion = "cinematic lighting, dramatic shadows",
                        reason = "添加电影级光影"
                    )
                )
            }
            EvolutionTechnique.STYLE_TRANSFER -> {
                changes.add(
                    PromptChange(
                        iteration = 0,
                        type = ChangeType.ADD_STYLE_REFERENCE,
                        original = original.take(100),
                        suggestion = "cinematic style, professional photography",
                        reason = "添加风格参考"
                    )
                )
            }
            else -> {
                changes.add(
                    PromptChange(
                        iteration = 0,
                        type = ChangeType.ADD_VISUAL_DETAIL,
                        original = original.take(100),
                        suggestion = evolved.take(100),
                        reason = "增强提示词"
                    )
                )
            }
        }

        return changes
    }
}

data class EvolutionContext(
    val styleProfile: String? = null,
    val characterNames: List<String> = emptyList(),
    val sceneType: String? = null,
    val mood: String? = null,
    val targetProvider: String? = null,
    val mediaType: MediaTypeEvolution = MediaTypeEvolution.IMAGE,
    val targetResolution: String? = null
)

enum class MediaTypeEvolution {
    IMAGE,
    VIDEO,
    SCRIPT
}

// ========== Prompt 模板库 ==========

object PromptTemplates {

    val CINEMATIC_SCENE = """
        {subject} in {setting}, cinematic lighting, {mood} atmosphere, 
        {style} style, {composition}, high detail, 8K quality, masterpiece, 
        professional composition, dramatic shadows
    """.trimIndent()

    val CHARACTER_PORTRAIT = """
        Portrait of {character_name}, {appearance}, {expression} expression, 
        {lighting} lighting, {style} style, highly detailed, {background}, 
        {camera_angle} shot, 8K quality, masterpiece
    """.trimIndent()

    val ACTION_SCENE = """
        Dynamic action scene of {subject}, {action}, {setting}, 
        motion blur, {lighting}, dramatic composition, {style} style, 
        intense atmosphere, 8K quality, masterpiece
    """.trimIndent()

    val DIALOGUE_SCENE = """
        {character_1} and {character_2} in conversation, {setting}, 
        {mood} atmosphere, {lighting}, {camera_angle} shot, 
        emotional moment, {style} style, detailed background,
        8K quality, masterpiece
    """.trimIndent()

    val LANDSCAPE = """
        Breathtaking landscape of {location}, {time_of_day}, 
        {weather}, {lighting}, {style} style, panoramic view, 
        {color_palette}, {composition}, 8K quality, masterpiece,
        ultra-wide angle
    """.trimIndent()

    val EMOTIONAL_MOMENT = """
        {subject} experiencing {emotion}, {setting}, 
        {lighting} lighting, {style} style, {composition},
        intimate atmosphere, {color_mood}, detailed,
        8K quality, masterpiece
    """.trimIndent()

    private val templates = mapOf(
        "cinematic_scene" to CINEMATIC_SCENE,
        "character_portrait" to CHARACTER_PORTRAIT,
        "action_scene" to ACTION_SCENE,
        "dialogue_scene" to DIALOGUE_SCENE,
        "landscape" to LANDSCAPE,
        "emotional_moment" to EMOTIONAL_MOMENT
    )

    fun getTemplate(key: String): String? = templates[key]

    fun fillTemplate(key: String, variables: Map<String, String>): String {
        val template = templates[key] ?: return ""
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }

    fun listTemplates(): List<String> = templates.keys.toList()
}

// ========== 提示词历史追踪 ==========

class PromptHistoryTracker {

    private val history = mutableMapOf<String, MutableList<PromptVersion>>()

    data class PromptVersion(
        val versionId: String,
        val original: String,
        val evolved: String,
        val qualityScore: Float,
        val timestamp: Long,
        val technique: String,
        val changeCount: Int
    )

    fun record(projectId: String, version: PromptVersion) {
        history.getOrPut(projectId) { mutableListOf() }.add(version)
    }

    fun getHistory(projectId: String): List<PromptVersion> {
        return history[projectId]?.toList() ?: emptyList()
    }

    fun getBestVersion(projectId: String): PromptVersion? {
        return history[projectId]?.maxByOrNull { it.qualityScore }
    }

    fun clear(projectId: String) {
        history.remove(projectId)
    }

    fun getEvolutionStats(projectId: String): EvolutionStats {
        val versions = history[projectId] ?: return EvolutionStats(0, 0f, 0f)
        if (versions.isEmpty()) return EvolutionStats(0, 0f, 0f)

        return EvolutionStats(
            totalEvolutions = versions.size,
            averageScore = versions.map { it.qualityScore }.average().toFloat(),
            improvementRate = if (versions.size > 1) {
                (versions.last().qualityScore - versions.first().qualityScore)
            } else 0f
        )
    }
}

data class EvolutionStats(
    val totalEvolutions: Int,
    val averageScore: Float,
    val improvementRate: Float
)
