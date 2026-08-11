package io.legado.app.video.pipeline

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import io.legado.app.video.realtime.ProjectEventService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MultiEpisodeOrchestrator - 多集编排系统
 *
 * 借鉴 ArcReel 的多集支持：
 * - 跨集角色连续性（同一角色在不同集中保持外观一致）
 * - 共享世界观/风格设定
 * - 集与集之间的叙事连贯性
 * - 角色成长追踪
 * - 全局风格一致性保证
 */

// ========== 世界观层 ==========

data class WorldBuilding(
    val worldId: String,
    val projectId: String,
    val era: String,
    val location: String,
    val socialContext: String,
    val technologyLevel: String,
    val culturalStyle: String,
    val coreThemes: List<String>,
    val visualStyle: String,
    val colorPalette: List<String>,
    val timeline: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toStyleGuideline(): String = buildString {
        append("世界观设定：\n")
        append("时代：$era\n")
        append("地点：$location\n")
        append("社会背景：$socialContext\n")
        append("科技水平：$technologyLevel\n")
        append("文化风格：$culturalStyle\n")
        append("核心主题：${coreThemes.joinToString(", ")}\n")
        append("视觉风格：$visualStyle\n")
        append("色调：${colorPalette.take(4).joinToString(", ")}")
    }

    fun toPromptModifier(): String = buildString {
        append("World context: ")
        append(era)
        append(" setting, ")
        append(location)
        append(". Visual style: ")
        append(visualStyle)
        append(". Color palette: ")
        append(colorPalette.take(3).joinToString(", "))
    }
}

// ========== 集间角色连续性 ==========

data class CharacterContinuityState(
    val characterId: String,
    val name: String,
    val lockedVisualDesc: String,
    val currentAppearanceVersion: Int,
    val costumeHistory: List<CostumeChange> = emptyList(),
    val appearanceArc: List<AppearanceMilestone> = emptyList(),
    val lastAppearanceEpisode: Int = 0,
    val consistencyScore: Float = 1.0f
)

data class CostumeChange(
    val episodeIndex: Int,
    val costumeName: String,
    val description: String,
    val reason: String? = null
)

data class AppearanceMilestone(
    val episodeIndex: Int,
    val description: String,
    val significance: String
)

class CharacterContinuityTracker {

    private val states = mutableMapOf<String, CharacterContinuityState>()

    fun registerCharacter(
        characterId: String,
        name: String,
        visualDesc: String
    ): CharacterContinuityState {
        val state = CharacterContinuityState(
            characterId = characterId,
            name = name,
            lockedVisualDesc = visualDesc,
            currentAppearanceVersion = 1
        )
        states[characterId] = state
        return state
    }

    fun getState(characterId: String): CharacterContinuityState? = states[characterId]

    fun getContinuityPrompt(
        characterId: String,
        episodeIndex: Int
    ): String {
        val state = states[characterId] ?: return ""
        val base = state.lockedVisualDesc
        val changes = state.costumeHistory
            .filter { it.episodeIndex <= episodeIndex }
            .takeLast(2)

        return buildString {
            append(base)
            if (changes.isNotEmpty()) {
                append(". Recent costume: ")
                append(changes.last().costumeName)
                append(" (${changes.last().description})")
            }
        }
    }

    fun updateAppearance(
        characterId: String,
        episodeIndex: Int,
        newCostume: String,
        description: String,
        reason: String? = null
    ) {
        val state = states[characterId] ?: return
        val updated = state.copy(
            costumeHistory = state.costumeHistory + CostumeChange(
                episodeIndex, newCostume, description, reason
            ),
            currentAppearanceVersion = state.currentAppearanceVersion + 1,
            lastAppearanceEpisode = episodeIndex,
            consistencyScore = (state.consistencyScore + 0.05f).coerceIn(0f, 1f)
        )
        states[characterId] = updated
    }

    fun updateMilestone(
        characterId: String,
        episodeIndex: Int,
        description: String,
        significance: String
    ) {
        val state = states[characterId] ?: return
        val updated = state.copy(
            appearanceArc = state.appearanceArc + AppearanceMilestone(
                episodeIndex, description, significance
            )
        )
        states[characterId] = updated
    }

    fun getAllStates(): List<CharacterContinuityState> = states.values.toList()

    fun calculateGlobalConsistencyScore(): Float {
        if (states.isEmpty()) return 1.0f
        return states.values.map { it.consistencyScore }.average().toFloat()
    }
}

// ========== 多集编排器 ==========

class MultiEpisodeOrchestrator(
    private val continuityTracker: CharacterContinuityTracker = CharacterContinuityTracker(),
    private val eventService: ProjectEventService = ProjectEventService()
) {
    private val episodes = mutableMapOf<String, EpisodeState>()
    private var worldBuilding: WorldBuilding? = null

    data class EpisodeState(
        val episodeId: String,
        val index: Int,
        val title: String,
        val status: EpisodeStatus,
        val script: Any? = null,
        val storyboardFrames: List<Any> = emptyList(),
        val videoSegments: List<Any> = emptyList(),
        val qualityScore: Float = 0f
    )

    enum class EpisodeStatus {
        PLANNED,
        SCRIPTING,
        VISUAL_PLANNING,
        GENERATING,
        REVIEWING,
        COMPLETE,
        ARCHIVED
    }

    fun setWorldBuilding(world: WorldBuilding) {
        worldBuilding = world
    }

    fun getWorldBuilding(): WorldBuilding? = worldBuilding

    suspend fun planEpisodes(
        projectId: String,
        novelText: String,
        episodeCount: Int
    ): List<EpisodePlan> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("你是一位专业的小说改编编剧。\n")
            append("请将以下小说内容规划为$episodeCount 集的动画剧集。\n\n")
            append("小说内容：\n${novelText.take(5000)}\n\n")
            append(worldBuilding?.toStyleGuideline()?.let { "\n\n世界观：\n$_" } ?: "")
            append("\n\n要求：\n")
            append("1. 每集应有明确的主题和情感弧线\n")
            append("2. 集与集之间应有悬念或过渡\n")
            append("3. 每集约3-5分钟时长\n")
            append("4. 主要角色应贯穿全季\n\n")
            append("输出JSON：\n")
            append("[{\"episode\":1,\"title\":\"标题\",\"theme\":\"主题\",\"summary\":\"简要描述\",\"key_characters\":[\"角色\"],\"cliffhanger\":\"结尾悬念\"}]")
        }

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位专业的小说改编编剧。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.4f,
                maxTokens = 4096
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val plans = parseEpisodePlans(textResult.content)
                plans.forEach { plan ->
                    episodes[plan.episodeId] = EpisodeState(
                        episodeId = plan.episodeId,
                        index = plan.index,
                        title = plan.title,
                        status = EpisodeStatus.PLANNED
                    )
                }
                eventService.publish(
                    io.legado.app.video.realtime.ProjectEvent(
                        eventId = "evt_plan_$projectId",
                        type = io.legado.app.video.realtime.EventType.PROJECT_UPDATE,
                        projectId = projectId,
                        data = mapOf("episodeCount" to plans.size)
                    )
                )
                plans
            },
            onFailure = {
                createFallbackPlans(projectId, episodeCount)
            }
        )
    }

    suspend fun generateEpisode(
        projectId: String,
        episodeIndex: Int,
        episodePlan: EpisodePlan,
        scriptContent: String,
        characterProfiles: Map<String, String>
    ): EpisodeResult = withContext(Dispatchers.IO) {
        val continuityPrompts = characterProfiles.map { (name, _) ->
            val state = continuityTracker.getState(name)
            val baseDesc = state?.lockedVisualDesc ?: characterProfiles[name].orEmpty()
            val episodeContext = if (state != null) {
                continuityTracker.getContinuityPrompt(name, episodeIndex)
            } else baseDesc
            name to episodeContext
        }.toMap()

        val enhancedPrompt = buildString {
            append(scriptContent)
            if (worldBuilding != null) {
                append(". ")
                append(worldBuilding!!.toPromptModifier())
            }
            val charRefs = continuityPrompts.entries.take(5).joinToString("; ") { (name, desc) ->
                "$name: ${desc.take(100)}"
            }
            if (charRefs.isNotBlank()) {
                append(". Character references: ")
                append(charRefs)
            }
        }

        val episodeId = "ep_${projectId}_$episodeIndex"
        episodes[episodeId] = EpisodeState(
            episodeId = episodeId,
            index = episodeIndex,
            title = episodePlan.title,
            status = EpisodeStatus.GENERATING
        )

        eventService.publish(
            io.legado.app.video.realtime.ProjectEvent(
                eventId = "evt_ep_$episodeIndex",
                type = io.legado.app.video.realtime.EventType.TASK_UPDATE,
                projectId = projectId,
                data = mapOf(
                    "episodeIndex" to episodeIndex,
                    "status" to "generating",
                    "progress" to 50
                )
            )
        )

        EpisodeResult(
            episodeId = episodeId,
            episodeIndex = episodeIndex,
            title = episodePlan.title,
            enhancedPrompt = enhancedPrompt,
            characterContinuity = continuityPrompts,
            worldApplied = worldBuilding != null,
            status = "ready"
        )
    }

    private fun parseEpisodePlans(content: String): List<EpisodePlan> {
        val plans = mutableListOf<EpisodePlan>()
        try {
            val jsonArray = content.substringAfter("[").substringBeforeLast("]")
            val blocks = jsonArray.split("},{")

            blocks.forEachIndexed { index, block ->
                val adjusted = buildString {
                    if (index > 0) append("{")
                    append(block.trim())
                    if (index < blocks.size - 1 && !block.trim().endsWith("}")) append("}")
                }

                plans.add(
                    EpisodePlan(
                        episodeId = "ep_plan_$index",
                        index = index + 1,
                        title = extractString(adjusted, "title") ?: "第${index + 1}集",
                        theme = extractString(adjusted, "theme") ?: "",
                        summary = extractString(adjusted, "summary") ?: "",
                        keyCharacters = extractStringList(adjusted, "key_characters"),
                        cliffhanger = extractString(adjusted, "cliffhanger")
                    )
                )
            }
        } catch (e: Exception) {
            return createFallbackPlans("fallback", 3)
        }
        return plans
    }

    private fun createFallbackPlans(projectId: String, count: Int): List<EpisodePlan> {
        return (1..count).map { i ->
            EpisodePlan(
                episodeId = "ep_${projectId}_plan_$i",
                index = i,
                title = "第${i}集",
                theme = "第${i}集主题",
                summary = "第${i}集剧情摘要",
                keyCharacters = emptyList(),
                cliffhanger = null
            )
        }
    }

    private fun extractString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull {
            it.trim().removeSurrounding("\"").ifBlank { null }
        }
    }
}

data class EpisodePlan(
    val episodeId: String,
    val index: Int,
    val title: String,
    val theme: String,
    val summary: String,
    val keyCharacters: List<String>,
    val cliffhanger: String?
)

data class EpisodeResult(
    val episodeId: String,
    val episodeIndex: Int,
    val title: String,
    val enhancedPrompt: String,
    val characterContinuity: Map<String, String>,
    val worldApplied: Boolean,
    val status: String
)
