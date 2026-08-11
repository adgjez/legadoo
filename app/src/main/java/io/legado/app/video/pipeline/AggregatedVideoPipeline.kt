package io.legado.app.video.pipeline

import io.legado.app.video.api.*
import io.legado.app.video.realtime.ProjectEventService
import io.legado.app.video.states.AppStore
import io.legado.app.video.states.CostStore
import io.legado.app.video.states.ProjectsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * 聚合视频生成管线
 *
 * 借鉴 ArcReel 的 aggregated pipeline：
 * - 统一调度入口
 * - 协同 Subagent / 工作流 / 状态管理
 * - 完整的事件驱动架构
 */

class AggregatedVideoPipeline(
    private val eventService: ProjectEventService = ProjectEventService(),
    private val projectsStore: ProjectsStore = ProjectsStore.instance,
    private val costStore: CostStore = CostStore.instance,
    private val appStore: AppStore = AppStore.instance,
    private val generationQueue: GenerationQueue = GenerationQueue(),
    private val taskDedup: TaskDeduplicationManager = TaskDeduplicationManager()
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val modeRouter = GenerationModeRouter()
    private val stateManager = TaskStateManager()

    suspend fun execute(plan: VideoGenerationPlan): VideoGenerationResult = withContext(Dispatchers.IO) {
        appStore.setIsGenerating(true)
        appStore.setAIThinking(true)

        eventService.publish(
            ProjectEvent(
                eventId = "evt_start_${plan.projectId}",
                type = EventType.PROJECT_UPDATE,
                projectId = plan.projectId,
                data = mapOf("status" to "started", "plan" to plan.toMap())
            )
        )

        try {
            val characterRefs = plan.characterImages
            val clueRefs = plan.clueImages
            val styleRef = plan.styleImage

            val frames = plan.storyboardFrames.toMutableList()

            val modeConfig = GenerationConfig(
                mode = mapWorkflowMode(plan.workflow),
                usePreviousFrameAsRef = true,
                crossShotReference = true
            )

            val sequencedFrames = StoryboardSequencer().buildSequence(
                frames, characterRefs, clueRefs, styleRef
            )

            val result = modeRouter.run(
                modeConfig, sequencedFrames, characterRefs, styleRef
            )

            result.fold(
                onSuccess = { processedFrames ->
                    processedFrames.forEach { frame ->
                        eventService.publishTaskUpdate(
                            plan.projectId, frame.frameId, "completed", 100, frame.imageUrl
                        )
                    }

                    projectsStore.updateProjectStatus(plan.projectId, ProjectStatus(
                        currentPhase = "completed",
                        phaseProgress = 1.0f,
                        completedSegments = processedFrames.count { it.imageUrl != null },
                        totalSegments = processedFrames.size
                    ))

                    eventService.publish(
                        ProjectEvent(
                            eventId = "evt_complete_${plan.projectId}",
                            type = EventType.VIDEO_COMPLETE,
                            projectId = plan.projectId,
                            data = mapOf("frameCount" to processedFrames.size)
                        )
                    )

                    VideoGenerationResult(
                        success = true,
                        frames = processedFrames,
                        totalDuration = processedFrames.size * 5
                    )
                },
                onFailure = { error ->
                    appStore.pushToast("生成失败: ${error.message}", ToastType.ERROR)
                    VideoGenerationResult(
                        success = false,
                        error = error.message
                    )
                }
            )
        } finally {
            appStore.setIsGenerating(false)
            appStore.setAIThinking(false)
        }
    }

    private fun mapWorkflowMode(workflow: VideoWorkflow): GenerationMode {
        return when (workflow) {
            VideoWorkflow.IMAGE_TO_VIDEO -> GenerationMode.SINGLE
            VideoWorkflow.SHEET_TO_VIDEO -> GenerationMode.GRID
            VideoWorkflow.REFERENCE_TO_VIDEO -> GenerationMode.REFERENCE_VIDEO
        }
    }
}

data class VideoGenerationPlan(
    val projectId: String,
    val storyboardFrames: List<StoryboardFrame>,
    val characterImages: Map<String, String> = emptyMap(),
    val clueImages: Map<String, String> = emptyMap(),
    val styleImage: String? = null,
    val workflow: VideoWorkflow = VideoWorkflow.IMAGE_TO_VIDEO,
    val providerKey: String? = null,
    val model: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "projectId" to projectId,
        "frameCount" to storyboardFrames.size,
        "workflow" to workflow.name,
        "hasCharacterRefs" to characterImages.isNotEmpty(),
        "hasStyleRef" to (styleImage != null)
    )
}

data class VideoGenerationResult(
    val success: Boolean,
    val frames: List<StoryboardFrame> = emptyList(),
    val totalDuration: Int = 0,
    val error: String? = null
)
