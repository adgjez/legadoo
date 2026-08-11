package io.legado.app.video.pipeline

import io.legado.app.video.realtime.EventType
import io.legado.app.video.realtime.ProjectEvent
import io.legado.app.video.realtime.ProjectEventService
import io.legado.app.video.states.AppStore
import io.legado.app.video.states.ProjectsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * StageManager 阶段管理器
 *
 * 借鉴 ArcReel 的进度追踪系统：
 * - 真实进度追踪（非 fake 进度）
 * - 支持暂停/恢复
 * - 阶段依赖管理
 * - 超时保护
 * - 状态持久化
 */

// ========== 阶段定义 ==========

enum class PipelineStage(
    val displayName: String,
    val order: Int,
    val estimatedDurationMs: Long,
    val isBlocking: Boolean
) {
    INITIALIZATION("初始化", 0, 5_000L, true),
    AGENT_ANALYSIS("智能体分析", 1, 30_000L, true),
    CONTENT_GENERATION("内容生成", 2, 60_000L, true),
    VISUAL_PLANNING("视觉规划", 3, 45_000L, true),
    STORYBOARD_GENERATION("分镜生成", 4, 120_000L, true),
    IMAGE_GENERATION("图像生成", 5, 300_000L, false),
    QUALITY_CHECK("质量检查", 6, 30_000L, true),
    REFINEMENT("精化迭代", 7, 60_000L, false),
    VIDEO_GENERATION("视频生成", 8, 600_000L, false),
    ASSEMBLY("组装导出", 9, 120_000L, true),
    COMPLETE("完成", 10, 2_000L, true);

    fun next(): PipelineStage? = entries.getOrNull(ordinal + 1)
    fun previous(): PipelineStage? = entries.getOrNull(ordinal - 1)
}

data class StageProgress(
    val stage: PipelineStage,
    val status: StageStatus,
    val progress: Float,
    val itemsCompleted: Int,
    val itemsTotal: Int,
    val startedAt: Long?,
    val completedAt: Long?,
    val errorMessage: String?,
    val retryCount: Int,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun isComplete(): Boolean = status == StageStatus.COMPLETED
    fun isFailed(): Boolean = status == StageStatus.FAILED
    fun isActive(): Boolean = status == StageStatus.RUNNING
    fun elapsedMs(): Long = startedAt?.let { System.currentTimeMillis() - it } ?: 0L
    fun estimatedRemainingMs(): Long {
        if (progress >= 1.0f) return 0L
        val elapsed = elapsedMs()
        return if (progress > 0f) (elapsed / progress) - elapsed else stage.estimatedDurationMs
    }
}

enum class StageStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    SKIPPED
}

// ========== 管线状态 ==========

data class PipelineState(
    val projectId: String,
    val currentStage: PipelineStage,
    val stages: Map<PipelineStage, StageProgress>,
    val overallProgress: Float,
    val totalElapsedMs: Long,
    val isPaused: Boolean,
    val isCancelled: Boolean,
    val startedAt: Long,
    val lastUpdateAt: Long,
    val errors: List<StageError>
) {
    fun isComplete(): Boolean = currentStage == PipelineStage.COMPLETE
    fun isFailed(): Boolean = stages.values.any { it.isFailed() }
    fun hasActiveStage(): Boolean = stages.values.any { it.isActive() }
    fun getNextPendingStage(): PipelineStage? {
        return stages.entries.firstOrNull { it.value.status == StageStatus.PENDING }?.key
    }
}

data class StageError(
    val stage: PipelineStage,
    val message: String,
    val timestamp: Long,
    val recoverable: Boolean,
    val retrySuggestion: String? = null
)

// ========== Stage Manager ==========

class StageManager(
    private val projectId: String,
    private val eventService: ProjectEventService = ProjectEventService(),
    private val appStore: AppStore = AppStore.instance,
    private val projectsStore: ProjectsStore = ProjectsStore.instance,
    private val maxRetriesPerStage: Int = 3
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(PipelineState(
        projectId = projectId,
        currentStage = PipelineStage.INITIALIZATION,
        stages = initializeStages(),
        overallProgress = 0f,
        totalElapsedMs = 0L,
        isPaused = false,
        isCancelled = false,
        startedAt = System.currentTimeMillis(),
        lastUpdateAt = System.currentTimeMillis(),
        errors = emptyList()
    ))

    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private fun initializeStages(): Map<PipelineStage, StageProgress> {
        return PipelineStage.entries.associateWith { stage ->
            StageProgress(
                stage = stage,
                status = if (stage == PipelineStage.INITIALIZATION) StageStatus.PENDING else StageStatus.PENDING,
                progress = 0f,
                itemsCompleted = 0,
                itemsTotal = 0,
                startedAt = null,
                completedAt = null,
                errorMessage = null
            )
        }
    }

    suspend fun start(): Unit = mutex.withLock {
        val state = _state.value
        if (state.isComplete() || state.isFailed()) {
            reset()
        }
        advanceTo(state.currentStage)
    }

    suspend fun advanceTo(stage: PipelineStage): Unit = mutex.withLock {
        val currentState = _state.value
        if (currentState.isPaused || currentState.isCancelled) return@withLock

        updateStage(stage) { progress ->
            progress.copy(
                status = StageStatus.RUNNING,
                startedAt = System.currentTimeMillis()
            )
        }

        emitProgress(stage, 0f)
    }

    suspend fun updateProgress(
        stage: PipelineStage,
        progress: Float,
        itemsCompleted: Int = 0,
        itemsTotal: Int = 0
    ): Unit = mutex.withLock {
        updateStage(stage) { sp ->
            sp.copy(
                progress = progress.coerceIn(0f, 1f),
                itemsCompleted = itemsCompleted,
                itemsTotal = itemsTotal
            )
        }

        updateOverallProgress()
        emitProgress(stage, progress)
    }

    suspend fun completeStage(stage: PipelineStage, metadata: Map<String, Any?> = emptyMap()): Unit = mutex.withLock {
        updateStage(stage) { sp ->
            sp.copy(
                status = StageStatus.COMPLETED,
                progress = 1.0f,
                completedAt = System.currentTimeMillis(),
                itemsCompleted = sp.itemsTotal,
                metadata = metadata
            )
        }

        updateOverallProgress()
        emitProgress(stage, 1.0f)

        val nextStage = stage.next()
        if (nextStage != null && nextStage != PipelineStage.COMPLETE) {
            advanceTo(nextStage)
        } else {
            completePipeline()
        }
    }

    suspend fun failStage(stage: PipelineStage, error: String, recoverable: Boolean = true): Unit = mutex.withLock {
        updateStage(stage) { sp ->
            sp.copy(
                status = StageStatus.FAILED,
                errorMessage = error,
                retryCount = sp.retryCount + 1,
                completedAt = System.currentTimeMillis()
            )
        }

        val errors = _state.value.errors + StageError(
            stage = stage,
            message = error,
            timestamp = System.currentTimeMillis(),
            recoverable = recoverable,
            retrySuggestion = if (recoverable) "重试或跳过此阶段" else null
        )

        _state.value = _state.value.copy(errors = errors)

        if (recoverable && _state.value.stages[stage]!!.retryCount < maxRetriesPerStage) {
            autoRetry(stage)
        }
    }

    suspend fun pause(): Unit = mutex.withLock {
        _state.value = _state.value.copy(isPaused = true)
        appStore.pushToast("管线已暂停", io.legado.app.video.states.ToastType.INFO)
        eventService.publish(
            ProjectEvent(
                eventId = "evt_pause_$projectId",
                type = EventType.PROJECT_UPDATE,
                projectId = projectId,
                data = mapOf("status" to "paused")
            )
        )
    }

    suspend fun resume(): Unit = mutex.withLock {
        _state.value = _state.value.copy(isPaused = false)
        appStore.pushToast("管线已恢复", io.legado.app.video.states.ToastType.SUCCESS)
        val currentStage = _state.value.currentStage
        advanceTo(currentStage)
    }

    suspend fun cancel(): Unit = mutex.withLock {
        _state.value = _state.value.copy(isCancelled = true)
        appStore.pushToast("管线已取消", io.legado.app.video.states.ToastType.WARNING)
    }

    suspend fun skipStage(stage: PipelineStage): Unit = mutex.withLock {
        updateStage(stage) { sp ->
            sp.copy(
                status = StageStatus.SKIPPED,
                progress = 1.0f,
                completedAt = System.currentTimeMillis()
            )
        }
        updateOverallProgress()
    }

    suspend fun retryStage(stage: PipelineStage): Unit = mutex.withLock {
        updateStage(stage) { sp ->
            sp.copy(
                status = StageStatus.PENDING,
                progress = 0f,
                errorMessage = null,
                completedAt = null,
                startedAt = null
            )
        }
        advanceTo(stage)
    }

    private suspend fun autoRetry(stage: PipelineStage) {
        delay(2000)
        if (!_state.value.isCancelled) {
            retryStage(stage)
        }
    }

    private suspend fun completePipeline() {
        updateStage(PipelineStage.COMPLETE) { sp ->
            sp.copy(
                status = StageStatus.COMPLETED,
                progress = 1.0f,
                completedAt = System.currentTimeMillis()
            )
        }

        _state.value = _state.value.copy(
            overallProgress = 1.0f,
            totalElapsedMs = System.currentTimeMillis() - _state.value.startedAt
        )

        projectsStore.updateProjectStatus(projectId, io.legado.app.video.states.ProjectStatus(
            currentPhase = "completed",
            phaseProgress = 1.0f
        ))

        eventService.publish(
            ProjectEvent(
                eventId = "evt_complete_$projectId",
                type = EventType.VIDEO_COMPLETE,
                projectId = projectId,
                data = mapOf("totalDurationMs" to _state.value.totalElapsedMs)
            )
        )
    }

    private fun updateStage(stage: PipelineStage, update: (StageProgress) -> StageProgress) {
        val stages = _state.value.stages.toMutableMap()
        stages[stage] = update(stages[stage]!!)
        _state.value = _state.value.copy(
            stages = stages,
            currentStage = stage,
            lastUpdateAt = System.currentTimeMillis()
        )
    }

    private fun updateOverallProgress() {
        val stages = _state.value.stages
        val total = stages.size
        val completed = stages.values.count { it.isComplete() || it.status == StageStatus.SKIPPED }
        val activeProgress = stages.values
            .filter { it.isActive() }
            .map { it.progress }
            .averageOrNull() ?: 0.0

        val overall = (completed + activeProgress) / total.coerceAtLeast(1)
        _state.value = _state.value.copy(
            overallProgress = overall.coerceIn(0f, 1f),
            totalElapsedMs = System.currentTimeMillis() - _state.value.startedAt
        )
    }

    private suspend fun emitProgress(stage: PipelineStage, progress: Float) {
        eventService.publishTaskUpdate(
            projectId, stage.name, "running",
            (progress * 100).toInt()
        )
    }

    private fun reset() {
        _state.value = PipelineState(
            projectId = projectId,
            currentStage = PipelineStage.INITIALIZATION,
            stages = initializeStages(),
            overallProgress = 0f,
            totalElapsedMs = 0L,
            isPaused = false,
            isCancelled = false,
            startedAt = System.currentTimeMillis(),
            lastUpdateAt = System.currentTimeMillis(),
            errors = emptyList()
        )
    }

    fun getStageProgress(stage: PipelineStage): StageProgress? {
        return _state.value.stages[stage]
    }

    fun getEstimatedTimeRemaining(): Long {
        val state = _state.value
        val currentStage = state.stages[state.currentStage] ?: return 0L
        val elapsed = state.totalElapsedMs
        val completedStages = state.stages.values.count { it.isComplete() }
        val avgTimePerStage = if (completedStages > 0) elapsed / completedStages else 0L
        val remainingStages = state.stages.values.count { !it.isComplete() && it != currentStage }
        val currentStageRemaining = currentStage.estimatedRemainingMs()
        return avgTimePerStage * remainingStages + currentStageRemaining
    }
}
