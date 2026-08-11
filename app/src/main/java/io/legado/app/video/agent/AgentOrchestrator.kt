package io.legado.app.video.agent

import io.legado.app.video.api.AgentContext
import io.legado.app.video.api.AgentResult
import io.legado.app.video.api.AgnesApiClient
import io.legado.app.video.api.AgnesChatMessage
import io.legado.app.video.api.AgnesChatRequest
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class OrchestrationConfig(
    val enableCharacterConsistency: Boolean = true,
    val enableSceneContinuity: Boolean = true,
    val enableStyleTransfer: Boolean = true,
    val qualityLevel: Int = 3,
    val parallelism: Int = 2,
    val maxRetries: Int = 2,
    val stylePreset: String = "cinematic",
    val customInstructions: String = ""
)

data class OrchestrationPhase(
    val name: String,
    val status: String = "pending",
    val progress: Float = 0f,
    val description: String = ""
)

class AgentOrchestrator(
    private val apiClient: AgnesApiClient,
    private val config: OrchestrationConfig = OrchestrationConfig()
) {

    private val _phases = MutableStateFlow<List<OrchestrationPhase>>(emptyList())
    val phases: StateFlow<List<OrchestrationPhase>> = _phases

    private val _orchestrationProgress = MutableStateFlow(0f)
    val orchestrationProgress: StateFlow<Float> = _orchestrationProgress

    private val memory = AgentMemory()

    suspend fun orchestrate(
        project: VideoProject,
        scenes: List<VideoScene>
    ): OrchestrationResult = coroutineScope {
        val phaseList = mutableListOf(
            OrchestrationPhase("小说解析", "pending", 0f, "分析原著内容，提取角色和场景"),
            OrchestrationPhase("角色设计", "pending", 0f, "设计角色形象，确保一致性"),
            OrchestrationPhase("分镜规划", "pending", 0f, "规划每个分镜的镜头语言"),
            OrchestrationPhase("提示词优化", "pending", 0f, "增强视觉提示词，融合风格"),
            OrchestrationPhase("质量检查", "pending", 0f, "检查连贯性和角色一致性")
        )
        _phases.value = phaseList

        try {
            _phases.update(0) { it.copy(status = "running", progress = 0.1f) }

            val parser = NovelParserAgent(apiClient)
            val parserResult = parser.parseNovel(
                AgentContext(
                    projectId = project.id,
                    input = scenes.joinToString("\n") { it.novelText },
                    metadata = mapOf("style" to project.style, "genre" to (project.genre ?: ""))
                )
            )

            if (!parserResult.success) {
                return@coroutineScope OrchestrationResult(
                    success = false,
                    error = "小说解析失败: ${parserResult.error}"
                )
            }

            val analysis = parserResult.structuredData as? NovelAnalysisResult
            memory.storeAnalysis(analysis)

            _phases.update(0) { it.copy(status = "completed", progress = 1f) }
            _phases.update(1) { it.copy(status = "running", progress = 0.1f) }
            _orchestrationProgress.value = 0.2f

            val characterDesigner = CharacterDesignAgent(apiClient)
            val characters = analysis?.characters.orEmpty()

            val characterPrompts = mutableMapOf<String, String>()
            characters.forEachIndexed { index, character ->
                val designResult = characterDesigner.designCharacter(
                    AgentContext(
                        projectId = project.id,
                        input = character.personality,
                        metadata = mapOf(
                            "name" to character.name,
                            "appearance" to character.appearance,
                            "style" to project.style
                        )
                    )
                )
                if (designResult.success) {
                    characterPrompts[character.name] = designResult.output
                }
                _phases.update(1) {
                    it.copy(progress = (index + 1).toFloat() / characters.size.coerceAtLeast(1))
                }
            }

            memory.storeCharacterPrompts(characterPrompts)

            _phases.update(1) { it.copy(status = "completed", progress = 1f) }
            _phases.update(2) { it.copy(status = "running", progress = 0.1f) }
            _orchestrationProgress.value = 0.4f

            val storyboardPlanner = StoryboardPlannerAgent(apiClient)
            val enhancedScenes = mutableListOf<VideoScene>()

            scenes.forEachIndexed { index, scene ->
                val charactersInScene = memory.getCharactersForScene(scene.novelText)
                val characterContext = characterPrompts.filterKeys { it in charactersInScene }

                val planResult = storyboardPlanner.planScene(
                    AgentContext(
                        projectId = project.id,
                        input = scene.novelText,
                        metadata = mapOf(
                            "style" to project.style,
                            "characters" to characterContext.values.joinToString("\n"),
                            "previous_scene_summary" to enhancedScenes.lastOrNull()?.summary.orEmpty(),
                            "scene_type" to scene.sceneType,
                            "shot_type" to scene.shotType
                        )
                    )
                )

                if (planResult.success) {
                    val plan = planResult.structuredData as? StoryboardScene
                    enhancedScenes.add(scene.copy(
                        visualPrompt = plan?.visualPrompt.orEmpty(),
                        videoPrompt = plan?.videoPrompt.orEmpty(),
                        shotType = plan?.shotType ?: scene.shotType,
                        cameraMovement = plan?.cameraMovement ?: scene.cameraMovement,
                        durationSeconds = plan?.durationSeconds ?: scene.durationSeconds,
                        mood = plan?.mood ?: scene.mood
                    ))
                } else {
                    enhancedScenes.add(scene)
                }

                _phases.update(2) {
                    it.copy(progress = (index + 1).toFloat() / scenes.size.coerceAtLeast(1))
                }
            }

            _phases.update(2) { it.copy(status = "completed", progress = 1f) }
            _phases.update(3) { it.copy(status = "running", progress = 0.1f) }
            _orchestrationProgress.value = 0.6f

            val promptOptimizer = PromptOptimizerAgent(apiClient)
            val optimizedScenes = mutableListOf<VideoScene>()

            enhancedScenes.forEachIndexed { index, scene ->
                val consistencyContext = if (config.enableCharacterConsistency) {
                    memory.getCharacterConsistencyPrompt(scene.characterIds)
                } else ""

                val continuityContext = if (config.enableSceneContinuity && index > 0) {
                    memory.getSceneContinuityPrompt(enhancedScenes[index - 1], scene)
                } else ""

                val optimizeResult = promptOptimizer.optimizePrompt(
                    AgentContext(
                        projectId = project.id,
                        input = scene.visualPrompt,
                        metadata = mapOf(
                            "style" to project.style,
                            "consistency" to consistencyContext,
                            "continuity" to continuityContext,
                            "custom" to config.customInstructions
                        )
                    )
                )

                if (optimizeResult.success) {
                    optimizedScenes.add(scene.copy(
                        visualPrompt = optimizeResult.output,
                        videoPrompt = optimizeResult.structuredData?.let {
                            (it as? Map<*, *>)?.get("videoPrompt") as? String
                        } ?: scene.videoPrompt
                    ))
                } else {
                    optimizedScenes.add(scene)
                }

                _phases.update(3) {
                    it.copy(progress = (index + 1).toFloat() / enhancedScenes.size.coerceAtLeast(1))
                }
            }

            _phases.update(3) { it.copy(status = "completed", progress = 1f) }
            _phases.update(4) { it.copy(status = "running", progress = 0.5f) }
            _orchestrationProgress.value = 0.8f

            val qcResult = performQualityCheck(optimizedScenes, memory)

            _phases.update(4) { it.copy(status = "completed", progress = 1f) }
            _orchestrationProgress.value = 1f

            OrchestrationResult(
                success = true,
                scenes = optimizedScenes,
                analysis = analysis,
                characterPrompts = characterPrompts,
                qcReport = qcResult
            )
        } catch (e: Exception) {
            OrchestrationResult(
                success = false,
                error = e.message ?: "编排失败"
            )
        }
    }

    private suspend fun performQualityCheck(
        scenes: List<VideoScene>,
        memory: AgentMemory
    ): QcReport = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        scenes.forEachIndexed { index, scene ->
            if (scene.visualPrompt.isBlank() && scene.videoPrompt.isBlank()) {
                issues.add("分镜${index + 1}（${scene.title}）缺少提示词")
            }
            if (scene.durationSeconds < 3) {
                suggestions.add("分镜${index + 1}时长过短，建议延长至5秒以上")
            }
        }

        val characterConsistency = memory.checkCharacterConsistency(scenes)
        if (characterConsistency.isNotEmpty()) {
            suggestions.addAll(characterConsistency)
        }

        QcReport(
            totalScenes = scenes.size,
            issues = issues,
            suggestions = suggestions,
            qualityScore = calculateQualityScore(scenes, issues, suggestions)
        )
    }

    private fun calculateQualityScore(
        scenes: List<VideoScene>,
        issues: List<String>,
        suggestions: List<String>
    ): Float {
        val baseScore = 100f
        val deduction = issues.size * 15f + suggestions.size * 5f
        return (baseScore - deduction).coerceIn(0f, 100f)
    }

    private fun MutableStateFlow<List<OrchestrationPhase>>.update(
        index: Int,
        transform: (OrchestrationPhase) -> OrchestrationPhase
    ) {
        value = value.mapIndexed { i, phase ->
            if (i == index) transform(phase) else phase
        }
    }
}

data class OrchestrationResult(
    val success: Boolean,
    val scenes: List<VideoScene> = emptyList(),
    val analysis: NovelAnalysisResult? = null,
    val characterPrompts: Map<String, String> = emptyMap(),
    val qcReport: QcReport = QcReport(),
    val error: String = ""
)

data class QcReport(
    val totalScenes: Int = 0,
    val issues: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val qualityScore: Float = 100f
)
