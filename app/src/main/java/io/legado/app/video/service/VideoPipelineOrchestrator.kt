package io.legado.app.video.service

import android.content.Context
import io.legado.app.video.agent.AgentTeamCoordinator
import io.legado.app.video.agent.CharacterAnalystAgent
import io.legado.app.video.agent.StoryboardPlannerAgent
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.ImageGenerationRequest
import io.legado.app.video.api.ProviderCapability
import io.legado.app.video.api.VideoGenerationRequest
import io.legado.app.video.config.ProjectDefaults
import io.legado.app.video.config.ProjectType
import io.legado.app.video.config.SmartDefaults
import io.legado.app.video.data.dao.appDb
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.pipeline.ProductionPipeline
import io.legado.app.video.pipeline.PromptEvolutionEngine
import io.legado.app.video.pipeline.StageManager
import io.legado.app.video.pipeline.TemplateApplyResult
import io.legado.app.video.pipeline.TemplateEngine
import io.legado.app.video.quality.FrameData
import io.legado.app.video.quality.QualityReport
import io.legado.app.video.quality.QualityScorer
import io.legado.app.video.realtime.ProjectEventService
import io.legado.app.video.states.AppStore
import io.legado.app.video.states.CostRecord
import io.legado.app.video.states.CostStore
import io.legado.app.video.states.ProjectsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * VideoPipelineOrchestrator - 桥接 Room 数据层与新管线架构
 *
 * 借鉴 ArcReel 的后端服务模式：
 * - 统一入口协调所有管线阶段
 * - 管理 Room 实体与管线数据的转换
 * - 暴露进度/状态 Flow 供 UI 订阅
 */

class VideoPipelineOrchestrator(
    private val context: Context,
    private val projectEventService: ProjectEventService = ProjectEventService(),
    private val projectsStore: ProjectsStore = ProjectsStore.instance,
    private val appStore: AppStore = AppStore.instance
) {
    private val agentCoordinator = AgentTeamCoordinator()
    private val productionPipeline = ProductionPipeline(context, projectEventService)
    private val promptEngine = PromptEvolutionEngine()
    private val qualityScorer = QualityScorer()

    private val projectDao by lazy { appDb.videoProjectDao() }
    private val sceneDao by lazy { appDb.videoSceneDao() }
    private val characterDao by lazy { appDb.videoCharacterDao() }

    suspend fun runFullPipeline(projectId: String): PipelineRunResult = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId)
            ?: return@withContext PipelineRunResult(success = false, error = "项目不存在")

        val defaults = SmartDefaults.getDefaults(ProjectType.NOVEL_ADAPTATION)
        val stageManager = StageManager(projectId, projectEventService, appStore, projectsStore)

        try {
            stageManager.start()

            // Stage 1: Agent Analysis
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.AGENT_ANALYSIS)

            val coordinationResult = agentCoordinator.coordinate(
                projectId, project.sourceContent, emptyMap()
            )

            stageManager.updateProgress(
                io.legado.app.video.pipeline.PipelineStage.AGENT_ANALYSIS, 1.0f
            )
            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.AGENT_ANALYSIS,
                mapOf("agentScore" to coordinationResult.finalScore)
            )

            // Stage 2: Content Generation
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.CONTENT_GENERATION)

            val characters = parseCharactersFromAnalysis(coordinationResult.characterAnalysis.output)
            characters.forEach { characterDao.insert(it.copy(projectId = projectId)) }

            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.CONTENT_GENERATION,
                mapOf("characterCount" to characters.size)
            )

            // Stage 3: Visual Planning
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.VISUAL_PLANNING)

            val storyboardOutput = coordinationResult.storyboard.output
            val scenes = parseScenesFromStoryboard(storyboardOutput, projectId)
            scenes.forEach { sceneDao.insert(it) }

            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.VISUAL_PLANNING,
                mapOf("sceneCount" to scenes.size)
            )

            // Stage 4: Storyboard Generation
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.STORYBOARD_GENERATION)

            val characterMap = characters.associateBy { it.name }

            scenes.forEachIndexed { index, scene ->
                stageManager.updateProgress(
                    io.legado.app.video.pipeline.PipelineStage.STORYBOARD_GENERATION,
                    (index.toFloat() / scenes.size),
                    itemsCompleted = index,
                    itemsTotal = scenes.size
                )

                val evolvedPrompt = promptEngine.evolve(scene.visualPrompt)

                val updatedScene = scene.copy(
                    visualPrompt = evolvedPrompt.evolvedPrompt,
                    videoStatus = VideoScene.STATUS_GENERATING_STORYBOARD
                )
                sceneDao.update(updatedScene)

                try {
                    val imageResult = BackendRouter.generateImage(
                        ImageGenerationRequest(
                            prompt = evolvedPrompt.evolvedPrompt,
                            width = 1280,
                            height = 720,
                            count = 1
                        )
                    )
                    imageResult.getOrNull()?.images?.firstOrNull()?.let { img ->
                        sceneDao.update(
                            updatedScene.copy(
                                generatedStoryboardPath = img.url.orEmpty(),
                                videoStatus = VideoScene.STATUS_STORYBOARD_READY
                            )
                        )
                    }
                } catch (_: Exception) {
                    sceneDao.update(
                        updatedScene.copy(videoStatus = VideoScene.STATUS_STORYBOARD_READY)
                    )
                }
            }

            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.STORYBOARD_GENERATION
            )

            // Stage 5: Quality Check
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.QUALITY_CHECK)

            val frameDataList = scenes.map { scene ->
                FrameData(
                    frameId = scene.id,
                    index = scene.order,
                    prompt = scene.visualPrompt,
                    narrativeSummary = scene.summary.take(100),
                    referencedCharacters = scene.characterIds.mapNotNull { id ->
                        characters.find { it.id == id }?.name
                    },
                    duration = scene.durationSeconds
                )
            }

            val qualityReport = qualityScorer.scoreProject(
                projectId, frameDataList, emptyMap(), project.style
            )

            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.QUALITY_CHECK,
                mapOf("qualityScore" to qualityReport.overallScore)
            )

            // Stage 6: Video Generation
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.VIDEO_GENERATION)

            val readyScenes = sceneDao.getByProject(projectId).filter {
                it.videoStatus == VideoScene.STATUS_STORYBOARD_READY
            }

            readyScenes.forEachIndexed { index, scene ->
                stageManager.updateProgress(
                    io.legado.app.video.pipeline.PipelineStage.VIDEO_GENERATION,
                    (index.toFloat() / readyScenes.size),
                    itemsCompleted = index,
                    itemsTotal = readyScenes.size
                )

                sceneDao.update(
                    scene.copy(videoStatus = VideoScene.STATUS_GENERATING_VIDEO)
                )

                try {
                    val videoResult = BackendRouter.generateVideo(
                        VideoGenerationRequest(
                            prompt = scene.videoPrompt.ifBlank { scene.visualPrompt },
                            duration = scene.durationSeconds,
                            aspectRatio = "16:9"
                        )
                    )
                    videoResult.getOrNull()?.videoUrl?.let { url ->
                        sceneDao.update(
                            scene.copy(
                                generatedVideoPath = url,
                                videoStatus = VideoScene.STATUS_COMPLETED
                            )
                        )
                    } ?: sceneDao.update(
                        scene.copy(videoStatus = VideoScene.STATUS_FAILED)
                    )
                } catch (_: Exception) {
                    sceneDao.update(
                        scene.copy(videoStatus = VideoScene.STATUS_FAILED)
                    )
                }
            }

            stageManager.completeStage(
                io.legado.app.video.pipeline.PipelineStage.VIDEO_GENERATION
            )

            // Stage 7: Assembly
            stageManager.advanceTo(io.legado.app.video.pipeline.PipelineStage.ASSEMBLY)
            stageManager.completeStage(io.legado.app.video.pipeline.PipelineStage.ASSEMBLY)

            // Update project
            projectDao.update(
                project.copy(
                    status = VideoProject.STATUS_COMPLETED,
                    progress = 100,
                    completedScenes = readyScenes.size
                )
            )

            projectsStore.addProject(
                io.legado.app.video.states.ProjectSummary(
                    projectId = projectId,
                    name = project.name,
                    overview = project.description,
                    status = io.legado.app.video.states.ProjectStatus(
                        currentPhase = "completed",
                        phaseProgress = 1.0f
                    )
                )
            )

            PipelineRunResult(
                success = true,
                projectId = projectId,
                qualityScore = qualityReport.overallScore,
                sceneCount = scenes.size,
                characterCount = characters.size
            )
        } catch (e: Exception) {
            stageManager.failStage(
                stageManager.state.value.currentStage,
                e.message ?: "Unknown error"
            )
            projectDao.update(
                project.copy(
                    status = VideoProject.STATUS_FAILED,
                    errorMessage = e.message
                )
            )
            PipelineRunResult(success = false, error = e.message)
        }
    }

    private fun parseCharactersFromAnalysis(output: String): List<VideoCharacter> {
        val characters = mutableListOf<VideoCharacter>()
        try {
            val nameRegex = "\"name\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val descRegex = "\"visual_prompt\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val names = nameRegex.findAll(output).map { it.groupValues[1] }.toList()
            val descs = descRegex.findAll(output).map { it.groupValues[1] }.toList()

            names.forEachIndexed { index, name ->
                characters.add(
                    VideoCharacter(
                        id = UUID.randomUUID().toString(),
                        projectId = "",
                        name = name,
                        description = descs.getOrNull(index).orEmpty(),
                        appearance = descs.getOrNull(index).orEmpty(),
                        identityPrompt = descs.getOrNull(index).orEmpty(),
                        order = index
                    )
                )
            }
        } catch (_: Exception) { }
        return characters
    }

    private fun parseScenesFromStoryboard(output: String, projectId: String): List<VideoScene> {
        val scenes = mutableListOf<VideoScene>()
        try {
            val indexRegex = "\"index\"\\s*:\\s*(\\d+)".toRegex()
            val promptRegex = "\"image_prompt\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val durationRegex = "\"duration\"\\s*:\\s*(\\d+)".toRegex()
            val moodRegex = "\"mood\"\\s*:\\s*\"([^\"]*)\"".toRegex()

            val indices = indexRegex.findAll(output).map { it.groupValues[1].toInt() }.toList()
            val prompts = promptRegex.findAll(output).map { it.groupValues[1] }.toList()
            val durations = durationRegex.findAll(output).map { it.groupValues[1].toInt() }.toList()
            val moods = moodRegex.findAll(output).map { it.groupValues[1] }.toList()

            indices.forEachIndexed { index, idx ->
                scenes.add(
                    VideoScene(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        order = idx,
                        title = "分镜 $idx",
                        visualPrompt = prompts.getOrNull(index).orEmpty(),
                        videoPrompt = prompts.getOrNull(index).orEmpty(),
                        durationSeconds = durations.getOrNull(index) ?: 5,
                        mood = moods.getOrNull(index).orEmpty(),
                        shotType = VideoScene.SHOT_MEDIUM
                    )
                )
            }
        } catch (_: Exception) { }
        return scenes
    }

    suspend fun getPipelineState(projectId: String) = projectEventService.subscribe(projectId)

    suspend fun pauseProject(projectId: String) {
        projectEventService.publish(
            io.legado.app.video.realtime.ProjectEvent(
                eventId = "evt_pause_$projectId",
                type = io.legado.app.video.realtime.EventType.PROJECT_UPDATE,
                projectId = projectId,
                data = mapOf("status" to "paused")
            )
        )
    }

    suspend fun cancelProject(projectId: String) {
        val project = projectDao.getById(projectId)
        project?.let {
            projectDao.update(it.copy(status = VideoProject.STATUS_FAILED))
        }
    }

    // ========== 模板创建项目 ==========

    private val templateEngine = TemplateEngine()
    private val costStore = CostStore.instance

    suspend fun createProjectFromTemplate(
        templateId: String,
        projectName: String? = null,
        sourceContent: String = ""
    ): Result<VideoProject> = withContext(Dispatchers.IO) {
        try {
            val template = templateEngine.getTemplateById(templateId)
                ?: return@withContext Result.failure(IllegalArgumentException("模板不存在: $templateId"))

            val applied = templateEngine.applyTemplate(template, projectName)
            val projectId = UUID.randomUUID().toString()

            val project = VideoProject(
                id = projectId,
                name = applied.projectName,
                sourceContent = sourceContent,
                style = applied.visualStyle.styleName,
                targetAspectRatio = applied.aspectRatio,
                targetResolution = applied.resolution,
                targetSegments = applied.defaultSceneCount,
                targetDurationSeconds = applied.sceneDurationSeconds,
                status = VideoProject.STATUS_DRAFT,
                totalScenes = applied.defaultSceneCount
            )

            projectDao.insert(project)

            projectsStore.addProject(
                io.legado.app.video.states.ProjectSummary(
                    projectId = projectId,
                    name = applied.projectName,
                    overview = template.description,
                    status = io.legado.app.video.states.ProjectStatus(
                        currentPhase = "draft",
                        phaseProgress = 0f
                    )
                )
            )

            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 成本追踪 ==========

    private fun recordCost(
        projectId: String,
        operation: String,
        providerKey: String,
        model: String,
        estimatedCost: Double,
        actualCost: Double? = null
    ) {
        val record = CostRecord(
            recordId = UUID.randomUUID().toString(),
            projectId = projectId,
            providerKey = providerKey,
            model = model,
            operation = operation,
            estimatedCost = estimatedCost,
            actualCost = actualCost
        )
        costStore.addRecord(record)
    }

    suspend fun getProjectCostSummary(projectId: String) = costStore.getProjectCost(projectId)
}

data class PipelineRunResult(
    val success: Boolean,
    val projectId: String? = null,
    val error: String? = null,
    val qualityScore: Float = 0f,
    val sceneCount: Int = 0,
    val characterCount: Int = 0
)
