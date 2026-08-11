package io.legado.app.video.pipeline

import android.content.Context
import io.legado.app.video.api.BackendRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipelineExample(private val context: Context) {

    /**
     * 完整流水线示例：从小说到视频
     *
     * 这是 ArcReel 设计哲学的具体实现：
     * 1. 先锁定角色/线索设计图
     * 2. 两阶段脚本生成（内容Stage → 视觉Stage）
     * 3. 人工审核门控制关键节点
     * 4. 版本历史支持回滚
     */
    suspend fun runFullPipeline(
        projectId: String,
        novelText: String,
        contentMode: ContentMode = ContentMode.NARRATION,
        segmentCount: Int = 10
    ): PipelineResult = withContext(Dispatchers.IO) {
        val orchestrator = PipelineOrchestrator(context)
        val reviewManager = ReviewGateManager()

        // Step 0: 初始化
        orchestrator.initialize(projectId)

        // Step 1: 角色分析（自动）
        orchestrator.analyzeCharacters(projectId, novelText)
        val library = AssetLibraryManager.getLibrary(projectId)

        // Step 2: 角色设计图生成
        library?.characters?.forEach { character ->
            if (character.referenceImagePath == null) {
                // 在实际使用中这会调用后端服务生成图片
                // characterAnalyzer.generateDesignSheet(character)
            }
        }

        // Step 3: 审核门 - 确认角色设计
        val characterReviewItems = library?.let {
            reviewManager.buildCharacterReviewItems(it)
        } ?: emptyList()
        reviewManager.submitForReview(characterReviewItems)

        // 用户审核通过角色设计
        reviewManager.approveAll()
        library?.characters?.forEach {
            orchestrator.lockCharacter(projectId, it.characterId)
        }

        // Step 4: Stage 1 - 内容生成（结构化）
        orchestrator.generateScriptContent(projectId, novelText, contentMode, segmentCount)

        // Step 5: 审核门 - 确认内容结构
        val script = when (contentMode) {
            ContentMode.NARRATION -> orchestrator.narrationScript.value
            ContentMode.DRAMA -> orchestrator.dramaScript.value
        }

        val contentReviewItems = script?.let {
            reviewManager.buildContentReviewItems(it)
        } ?: emptyList()
        reviewManager.submitForReview(contentReviewItems)
        reviewManager.approveAll()

        // 用户确认内容通过
        orchestrator.approveContentStage()

        // Step 6: Stage 2 - 视觉生成（不重提取内容）
        orchestrator.generateScriptVisuals(projectId)

        // Step 7: 审核门 - 确认视觉质量
        val visualReviewItems = script?.let {
            reviewManager.buildVisualReviewItems(it)
        } ?: emptyList()
        reviewManager.submitForReview(visualReviewItems)
        reviewManager.approveAll()

        // 用户确认视觉通过
        orchestrator.approveVisualStage()

        // Step 8: 生成分镜图
        orchestrator.generateStoryboards(projectId)

        // Step 9: 生成视频
        orchestrator.generateVideos(projectId)

        // Step 10: 合成
        orchestrator.completeAssembly(projectId)

        // 记录版本
        when (contentMode) {
            ContentMode.NARRATION -> {
                orchestrator.narrationScript.value?.let { script ->
                    VersionManager.snapshotNarrationScript(
                        projectId, script, "完整流水线执行完成"
                    )
                }
            }
            ContentMode.DRAMA -> {
                // Drama script snapshot
            }
        }

        PipelineResult(
            success = orchestrator.getCurrentStage() == ScriptStage.COMPLETE,
            finalStage = orchestrator.getCurrentStage(),
            characterCount = library?.characters?.size ?: 0,
            segmentCount = when (contentMode) {
                ContentMode.NARRATION -> orchestrator.narrationScript.value?.segments?.size ?: 0
                ContentMode.DRAMA -> orchestrator.dramaScript.value?.utterances?.size ?: 0
            },
            versionHistory = VersionManager.getVersionsList(projectId)
        )
    }

    /**
     * 回滚示例
     */
    suspend fun rollbackExample(projectId: String) {
        val versions = VersionManager.getVersionsList(projectId)
        if (versions.size >= 2) {
            val previousVersion = versions[1]
            PipelineOrchestrator(context).rollbackToVersion(projectId, previousVersion.versionId)
        }
    }

    /**
     * 从中间阶段恢复（断点续传）
     */
    suspend fun resumeFromCheckpoint(
        projectId: String,
        resumeStage: ScriptStage
    ) {
        val orchestrator = PipelineOrchestrator(context)
        orchestrator.initialize(projectId)

        val stageHandlers = mapOf(
            ScriptStage.CHARACTER_ANALYSIS to { /* 重新分析 */ },
            ScriptStage.CHARACTER_DESIGN to { /* 已有设计图可跳过 */ },
            ScriptStage.SCRIPT_CONTENT_STAGE to { /* 重新生成内容 */ },
            ScriptStage.SCRIPT_VISUAL_STAGE to { /* 重新生成视觉 */ },
            ScriptStage.STORYBOARD_GENERATION to { /* 已有脚本可直接生成分镜 */ },
            ScriptStage.VIDEO_GENERATION to { /* 已有分镜可直接生成视频 */ }
        )

        stageHandlers[resumeStage]?.invoke()
    }
}

data class PipelineResult(
    val success: Boolean,
    val finalStage: ScriptStage,
    val characterCount: Int,
    val segmentCount: Int,
    val versionHistory: List<ScriptVersion>
)
