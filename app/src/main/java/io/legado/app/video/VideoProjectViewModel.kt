package io.legado.app.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.BudgetTier
import io.legado.app.video.api.GenerationConfig
import io.legado.app.video.api.GenerationDryRunReport
import io.legado.app.video.api.GenerationMode
import io.legado.app.video.api.GenerationModeRouter
import io.legado.app.video.api.ImageGenerationRequest
import io.legado.app.video.api.ModeCapabilityCatalog
import io.legado.app.video.api.VideoGenerationRequest
import io.legado.app.data.appDb
import io.legado.app.video.data.entities.*
import io.legado.app.video.pipeline.PipelineStage
import io.legado.app.video.pipeline.StageProgress
import io.legado.app.video.pipeline.StageStatus
import io.legado.app.video.pipeline.TemplateApplyResult
import io.legado.app.video.quality.QualityReport
import io.legado.app.video.service.VideoPipelineOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class VideoProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val orchestrator = VideoPipelineOrchestrator(application)

    private val _projects = MutableStateFlow<List<VideoProject>>(emptyList())
    val projects: StateFlow<List<VideoProject>> = _projects.asStateFlow()

    private val _currentProject = MutableStateFlow<VideoProject?>(null)
    val currentProject: StateFlow<VideoProject?> = _currentProject.asStateFlow()

    private val _currentScenes = MutableStateFlow<List<VideoScene>>(emptyList())
    val currentScenes: StateFlow<List<VideoScene>> = _currentScenes.asStateFlow()

    private val _currentCharacters = MutableStateFlow<List<VideoCharacter>>(emptyList())
    val currentCharacters: StateFlow<List<VideoCharacter>> = _currentCharacters.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pipelineStages = MutableStateFlow<Map<PipelineStage, StageProgress>>(emptyMap())
    val pipelineStages: StateFlow<Map<PipelineStage, StageProgress>> = _pipelineStages.asStateFlow()

    private val _pipelineProgress = MutableStateFlow(0f)
    val pipelineProgress: StateFlow<Float> = _pipelineProgress.asStateFlow()

    private val _isPipelineRunning = MutableStateFlow(false)
    val isPipelineRunning: StateFlow<Boolean> = _isPipelineRunning.asStateFlow()

    private val _qualityReport = MutableStateFlow<QualityReport?>(null)
    val qualityReport: StateFlow<QualityReport?> = _qualityReport.asStateFlow()

    private val _generationProgress = MutableStateFlow(GenerationProgress())
    val generationProgress: StateFlow<GenerationProgress> = _generationProgress.asStateFlow()

    // ==================================================================
    // Generation Mode 推荐器 (供 UI 的「生成模式推荐卡片」订阅)
    // ==================================================================

    /** 推荐结果（自动 + 手动 override 合并）；UI collect 它更新卡片 */
    private val _recommendation = MutableStateFlow<GenerationRecommendation?>(null)
    val generationRecommendation: StateFlow<GenerationRecommendation?> = _recommendation.asStateFlow()

    /** 用户手动 override 的模式（非 null 时，不再被自动 recompute 覆盖） */
    private val _manualModeOverride = MutableStateFlow<GenerationMode?>(null)

    /** UI 上「确认使用当前推荐模式」按钮置 true 后，卡片会加 checked 标记 */
    private val _confirmed = MutableStateFlow(false)
    val isGenerationModeConfirmed: StateFlow<Boolean> = _confirmed.asStateFlow()

    /**
     * 根据 Project + Characters + Scenes 自动调 GenerationConfig.recommendFor 出推荐
     * 规则：
     * - 如果有 _manualModeOverride，直接用 override 的 mode 生成 config + report
     * - 否则按 heuristics 自动推荐
     */
    private fun computeRecommendation(project: VideoProject, chars: List<VideoCharacter>, scenes: List<VideoScene>):
            GenerationRecommendation {
        val hasDialogue = scenes.any { it.dialogue.isNotBlank() }
        val qualityPreset = when {
            project.style.contains("电影", true) || project.style.contains("cinematic", true)
            -> io.legado.app.video.config.QualityPreset.CINEMATIC
            project.style.contains("漫画", true) || project.style.contains("动漫", true)
            -> io.legado.app.video.config.QualityPreset.ANIME
            project.style.contains("商业", true)
            -> io.legado.app.video.config.QualityPreset.COMMERCIAL
            else -> io.legado.app.video.config.QualityPreset.STANDARD
        }
        val base = GenerationConfig.recommendFor(
            totalSegments = scenes.size.coerceAtLeast(1),
            distinctCharacters = chars.size.coerceAtLeast(1),
            hasDialogue = hasDialogue,
            budgetTier = BudgetTier.BALANCED
        )
        // 用户手动覆盖 → 不改 heuristics 其他字段，只把 mode 替换掉
        val effective = _manualModeOverride.value?.let { base.copy(mode = it) } ?: base
        val router = GenerationModeRouter()
        val warnings = ModeCapabilityCatalog.validate(effective, scenes.size.coerceAtLeast(1))
        val autoOrManual = if (_manualModeOverride.value == null) "AUTO" else "MANUAL"
        return GenerationRecommendation(
            mode = effective.mode,
            config = effective,
            dryRun = null,
            warnings = warnings,
            source = autoOrManual,
            heuristicInputs = HeuristicInputs(
                qualityPreset = qualityPreset.name,
                distinctCharacters = chars.size,
                hasDialogue = hasDialogue,
                totalSegments = scenes.size,
                budgetTier = BudgetTier.BALANCED.name
            ),
            confirmed = _confirmed.value
        )
    }

    init {
        loadProjects()

        // 响应式：currentProject / currentCharacters / currentScenes / manualOverride 任一变化
        //         → 重新出推荐卡片 (非空 project 才会出)
        viewModelScope.launch {
            combine(
                _currentProject,
                _currentCharacters,
                _currentScenes,
                _manualModeOverride,
                _confirmed
            ) { p, c, s, _, _ -> Triple(p, c, s) }
                .collect { (p, c, s) ->
                    _recommendation.value = if (p == null) null else computeRecommendation(p, c, s)
                }
        }
    }

    /** 用户在卡片上改选其他模式：设为手动 override 并立刻重算 */
    fun overrideGenerationMode(mode: GenerationMode) {
        _confirmed.value = false
        _manualModeOverride.value = mode
    }

    /** 用户点「恢复自动推荐」 → 清 override 重走 heuristics */
    fun resetGenerationModeToAuto() {
        _confirmed.value = false
        _manualModeOverride.value = null
    }

    /** 用户确认推荐，UI 上卡片变绿勾；返回当前推荐对象（空的话返回 null） */
    fun confirmGenerationMode(): GenerationRecommendation? {
        val rec = _recommendation.value ?: return null
        _confirmed.value = true
        return rec.copy(confirmed = true).also { _recommendation.value = it }
    }

    /** 推荐确认后直接一键走 executeFullPipeline(projectId)，带卡片 step 信息 */
    fun confirmRecommendationAndStartPipeline(projectId: String) {
        val rec = confirmGenerationMode() ?: run {
            _error.value = "请先确认生成模式推荐"
            return
        }
        _generationProgress.value = GenerationProgress(
            isRunning = true,
            step = "已确认模式：${rec.config.mode} · ${rec.dryRun?.profile?.displayName}，启动管线…",
            progress = 2
        )
        executeFullPipeline(projectId)
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                appDb.videoProjectDao().observeAll().collect { projectList ->
                    _projects.value = projectList
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                appDb.videoProjectDao().observeById(projectId).collect { project ->
                    _currentProject.value = project
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }

        viewModelScope.launch {
            appDb.videoSceneDao().observeByProject(projectId).collect { scenes ->
                _currentScenes.value = scenes.sortedBy { it.order }
            }
        }

        viewModelScope.launch {
            appDb.videoCharacterDao().observeByProject(projectId).collect { chars ->
                _currentCharacters.value = chars.sortedBy { it.order }
            }
        }
    }

    fun createProject(
        name: String,
        sourceType: String,
        content: String,
        genre: String = "",
        style: String = "",
        aspectRatio: String = "16:9",
        targetResolution: String = "1080p"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val project = VideoProject(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    sourceType = sourceType,
                    sourceContent = content,
                    genre = genre,
                    style = style,
                    targetAspectRatio = aspectRatio,
                    targetResolution = targetResolution,
                    status = VideoProject.STATUS_DRAFT
                )
                appDb.videoProjectDao().insert(project)
                _currentProject.value = project
                loadProject(project.id)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createProjectFromWizard(result: io.legado.app.video.ui.pipeline.WizardResult) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val project = VideoProject(
                    id = UUID.randomUUID().toString(),
                    name = result.projectName,
                    sourceType = VideoProject.SOURCE_IDEA,
                    genre = result.projectType.name,
                    style = result.qualityPreset.name,
                    targetAspectRatio = result.aspectRatio,
                    status = VideoProject.STATUS_DRAFT
                )
                appDb.videoProjectDao().insert(project)
                _currentProject.value = project
                loadProject(project.id)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createProjectFromTemplate(result: TemplateApplyResult) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val project = VideoProject(
                    id = UUID.randomUUID().toString(),
                    name = result.projectName,
                    sourceType = result.sourceType.name,
                    genre = "",
                    style = result.visualStyle.styleName,
                    targetAspectRatio = result.aspectRatio,
                    targetResolution = result.resolution,
                    status = VideoProject.STATUS_DRAFT
                )
                appDb.videoProjectDao().insert(project)
                _currentProject.value = project
                loadProject(project.id)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteProject(project: VideoProject) {
        viewModelScope.launch {
            appDb.videoProjectDao().deleteById(project.id)
            if (_currentProject.value?.id == project.id) {
                _currentProject.value = null
            }
        }
    }

    fun executeFullPipeline(projectId: String) {
        viewModelScope.launch {
            _isPipelineRunning.value = true
            _generationProgress.value = GenerationProgress(isRunning = true, step = "开始执行管线...")

            try {
                val result = orchestrator.runFullPipeline(projectId)

                if (result.success) {
                    _generationProgress.value = GenerationProgress(
                        isRunning = false,
                        step = "管线完成",
                        progress = 100
                    )
                    loadProject(projectId)
                } else {
                    _error.value = result.error ?: "管线执行失败"
                    _generationProgress.value = GenerationProgress(
                        isRunning = false,
                        step = "失败: ${result.error}"
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
                _generationProgress.value = GenerationProgress(
                    isRunning = false,
                    step = "异常: ${e.message}"
                )
            } finally {
                _isPipelineRunning.value = false
            }
        }
    }

    fun generateSceneImage(scene: VideoScene) {
        viewModelScope.launch {
            try {
                val result = BackendRouter.generateImage(
                    ImageGenerationRequest(
                        prompt = scene.visualPrompt,
                        width = 1280,
                        height = 720,
                        count = 1
                    )
                )
                result.getOrNull()?.images?.firstOrNull()?.let { img ->
                    appDb.videoSceneDao().update(
                        scene.copy(
                            generatedStoryboardPath = img.url.orEmpty(),
                            videoStatus = VideoScene.STATUS_STORYBOARD_READY
                        )
                    )
                }
            } catch (e: Exception) {
                _error.value = "图像生成失败: ${e.message}"
            }
        }
    }

    fun generateSceneVideo(scene: VideoScene) {
        viewModelScope.launch {
            try {
                val result = BackendRouter.generateVideo(
                    VideoGenerationRequest(
                        prompt = scene.videoPrompt.ifBlank { scene.visualPrompt },
                        duration = scene.durationSeconds,
                        aspectRatio = "16:9"
                    )
                )
                result.getOrNull()?.videoUrl?.let { url ->
                    appDb.videoSceneDao().update(
                        scene.copy(
                            generatedVideoPath = url,
                            videoStatus = VideoScene.STATUS_COMPLETED
                        )
                    )
                }
            } catch (e: Exception) {
                _error.value = "视频生成失败: ${e.message}"
                appDb.videoSceneDao().update(
                    scene.copy(videoStatus = VideoScene.STATUS_FAILED)
                )
            }
        }
    }

    fun saveScene(scene: VideoScene) {
        viewModelScope.launch {
            appDb.videoSceneDao().update(scene)
        }
    }

    fun updateScenePrompt(scene: VideoScene, newPrompt: String) {
        viewModelScope.launch {
            appDb.videoSceneDao().update(scene.copy(visualPrompt = newPrompt))
        }
    }

    fun deleteScene(scene: VideoScene) {
        viewModelScope.launch {
            appDb.videoSceneDao().delete(scene)
        }
    }

    fun addScene(projectId: String) {
        viewModelScope.launch {
            val maxOrder = _currentScenes.value.maxOfOrNull { it.order } ?: 0
            val newScene = VideoScene(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "分镜 ${maxOrder + 1}",
                order = maxOrder + 1,
                shotType = VideoScene.SHOT_MEDIUM,
                cameraMovement = VideoScene.CAMERA_STATIC,
                durationSeconds = 5
            )
            appDb.videoSceneDao().insert(newScene)
        }
    }

    fun saveCharacter(character: VideoCharacter) {
        viewModelScope.launch {
            appDb.videoCharacterDao().insert(character)
        }
    }

    fun deleteCharacter(character: VideoCharacter) {
        viewModelScope.launch {
            appDb.videoCharacterDao().delete(character)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun pausePipeline() {
        viewModelScope.launch {
            _currentProject.value?.id?.let { orchestrator.pauseProject(it) }
        }
    }

    fun cancelPipeline() {
        viewModelScope.launch {
            _currentProject.value?.id?.let { orchestrator.cancelProject(it) }
            _isPipelineRunning.value = false
        }
    }
}

data class GenerationProgress(
    val isRunning: Boolean = false,
    val step: String = "",
    val progress: Int = 0
)

/**
 * GenerationRecommendation：UI「生成模式推荐卡片」的单一数据来源。
 *
 * - 由 ViewModel 的 combine(project, chars, scenes, manualOverride, confirmed) 派生
 * - dryRun 可以为空（比如 scenes.size == 0 的初始状态），UI 要处理
 * - source="AUTO" 时显示「推荐」标签；source="MANUAL" 显示「手动」标签
 */
data class GenerationRecommendation(
    val mode: GenerationMode,
    val config: GenerationConfig,
    val dryRun: GenerationDryRunReport?,
    val warnings: List<String>,
    /** AUTO = 启发式自动; MANUAL = 用户手动 override */
    val source: String,
    val heuristicInputs: HeuristicInputs,
    val confirmed: Boolean
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
    val profileName: String get() = dryRun?.profile?.displayName ?: mode.name
    val estimatedCost: Float get() = dryRun?.estimatedTotalCost ?: 0f
    val frameCount: Int get() = dryRun?.segmentCount ?: 0
    val consistencyPct: Int get() = ((dryRun?.profile?.consistencyScore ?: 0f) * 100).toInt()
    val throughputMul: String get() = "x${dryRun?.profile?.throughputMultiplier ?: 1f}"
}

/** recommendFor 的 5 个 heuristics 输入（卡片底部「为什么推荐这个？」折叠区显示） */
data class HeuristicInputs(
    val qualityPreset: String,
    val distinctCharacters: Int,
    val hasDialogue: Boolean,
    val totalSegments: Int,
    val budgetTier: String
)
