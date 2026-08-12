package io.legado.app.video.service

import android.content.Context
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.ImageGenerationRequest
import io.legado.app.video.api.VideoGenerationRequest
import io.legado.app.data.appDb
import io.legado.app.video.data.entities.VideoScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ErrorRecoveryManager - 智能错误恢复与自动重试策略
 *
 * 借鉴 ArcReel 的错误处理机制：
 * - 错误分类（可恢复 vs 不可恢复）
 * - 指数退避重试
 * - 替代路径执行（Failover）
 * - 错误聚合报告
 * - 降级策略（如使用低质量替代）
 */

class ErrorRecoveryManager(
    private val context: Context
) {
    private val sceneDao by lazy { appDb.videoSceneDao }

    data class RecoveryResult(
        val success: Boolean,
        val recovered: Boolean,
        val attempts: Int,
        val strategy: RecoveryStrategy,
        val errorMessage: String? = null
    )

    enum class RecoveryStrategy {
        RETRY,
        RETRY_WITH_BACKOFF,
        FAILOVER_PROVIDER,
        DEGRADE_QUALITY,
        SKIP_STAGE,
        ABORT
    }

    data class ErrorClassification(
        val isRecoverable: Boolean,
        val category: ErrorCategory,
        val suggestedStrategy: RecoveryStrategy,
        val maxRetries: Int
    )

    enum class ErrorCategory {
        NETWORK_TIMEOUT,
        RATE_LIMIT,
        PROVIDER_UNAVAILABLE,
        CONTENT_POLICY,
        INVALID_INPUT,
        SERVER_ERROR,
        CLIENT_ERROR,
        UNKNOWN
    }

    fun classifyError(error: Throwable): ErrorClassification {
        val message = error.message?.lowercase().orEmpty()

        return when {
            message.contains("timeout") || message.contains("timed out") -> ErrorClassification(
                isRecoverable = true,
                category = ErrorCategory.NETWORK_TIMEOUT,
                suggestedStrategy = RecoveryStrategy.RETRY_WITH_BACKOFF,
                maxRetries = 3
            )
            message.contains("rate limit") || message.contains("429") -> ErrorClassification(
                isRecoverable = true,
                category = ErrorCategory.RATE_LIMIT,
                suggestedStrategy = RecoveryStrategy.RETRY_WITH_BACKOFF,
                maxRetries = 5
            )
            message.contains("403") || message.contains("forbidden") || message.contains("policy") -> ErrorClassification(
                isRecoverable = false,
                category = ErrorCategory.CONTENT_POLICY,
                suggestedStrategy = RecoveryStrategy.DEGRADE_QUALITY,
                maxRetries = 0
            )
            message.contains("404") || message.contains("not found") -> ErrorClassification(
                isRecoverable = false,
                category = ErrorCategory.CLIENT_ERROR,
                suggestedStrategy = RecoveryStrategy.ABORT,
                maxRetries = 0
            )
            message.contains("500") || message.contains("502") || message.contains("503") -> ErrorClassification(
                isRecoverable = true,
                category = ErrorCategory.SERVER_ERROR,
                suggestedStrategy = RecoveryStrategy.RETRY_WITH_BACKOFF,
                maxRetries = 3
            )
            message.contains("invalid") || message.contains("malformed") -> ErrorClassification(
                isRecoverable = false,
                category = ErrorCategory.INVALID_INPUT,
                suggestedStrategy = RecoveryStrategy.DEGRADE_QUALITY,
                maxRetries = 0
            )
            else -> ErrorClassification(
                isRecoverable = true,
                category = ErrorCategory.UNKNOWN,
                suggestedStrategy = RecoveryStrategy.RETRY,
                maxRetries = 2
            )
        }
    }

    suspend fun executeWithRecovery(
        scene: VideoScene,
        operation: suspend (VideoScene) -> Unit
    ): RecoveryResult = withContext(Dispatchers.IO) {
        val classification = try {
            operation(scene)
            return@withContext RecoveryResult(
                success = true,
                recovered = false,
                attempts = 1,
                strategy = RecoveryStrategy.RETRY
            )
        } catch (e: Exception) {
            val result = classifyError(e)
            if (!result.isRecoverable) {
                sceneDao.update(
                    scene.copy(
                        videoStatus = VideoScene.STATUS_FAILED,
                        errorMessage = "不可恢复错误: ${result.category}"
                    )
                )
                return@withContext RecoveryResult(
                    success = false,
                    recovered = false,
                    attempts = 1,
                    strategy = result.suggestedStrategy,
                    errorMessage = e.message
                )
            }
            result
        }

        var lastError: Exception? = null
        repeat(classification.maxRetries) { attempt ->
            try {
                when (classification.suggestedStrategy) {
                    RecoveryStrategy.RETRY -> {
                        delay(1000)
                        operation(scene)
                    }
                    RecoveryStrategy.RETRY_WITH_BACKOFF -> {
                        val backoff = (1000L * (1 shl attempt.coerceAtMost(3))).coerceAtMost(30_000L)
                        delay(backoff)
                        operation(scene)
                    }
                    RecoveryStrategy.DEGRADE_QUALITY -> {
                        val degradedScene = scene.copy(
                            visualPrompt = scene.visualPrompt.take(200),
                            durationSeconds = scene.durationSeconds.coerceAtMost(5)
                        )
                        operation(degradedScene)
                    }
                    RecoveryStrategy.FAILOVER_PROVIDER -> {
                        operation(scene)
                    }
                    RecoveryStrategy.SKIP_STAGE -> {
                        sceneDao.update(
                            scene.copy(videoStatus = VideoScene.STATUS_STORYBOARD_READY)
                        )
                    }
                    RecoveryStrategy.ABORT -> {
                        sceneDao.update(
                            scene.copy(
                                videoStatus = VideoScene.STATUS_FAILED,
                                errorMessage = "操作已中止"
                            )
                        )
                    }
                }

                return@withContext RecoveryResult(
                    success = true,
                    recovered = true,
                    attempts = attempt + 2,
                    strategy = classification.suggestedStrategy,
                    errorMessage = lastError?.message
                )
            } catch (e: Exception) {
                lastError = e
            }
        }

        sceneDao.update(
            scene.copy(
                videoStatus = VideoScene.STATUS_FAILED,
                errorMessage = "重试耗尽: ${classification.category}"
            )
        )

        RecoveryResult(
            success = false,
            recovered = false,
            attempts = classification.maxRetries + 1,
            strategy = classification.suggestedStrategy,
            errorMessage = lastError?.message
        )
    }

    suspend fun retryImageGeneration(scene: VideoScene): RecoveryResult = withContext(Dispatchers.IO) {
        executeWithRecovery(scene) { s ->
            val result = BackendRouter.generateImage(
                ImageGenerationRequest(
                    prompt = s.visualPrompt,
                    width = 1280,
                    height = 720,
                    count = 1
                )
            )
            result.getOrNull()?.images?.firstOrNull()?.let { img ->
                sceneDao.update(
                    s.copy(
                        generatedStoryboardPath = img.url.orEmpty(),
                        videoStatus = VideoScene.STATUS_STORYBOARD_READY
                    )
                )
            } ?: throw Exception("图像生成返回空结果")
        }
    }

    suspend fun retryVideoGeneration(scene: VideoScene): RecoveryResult = withContext(Dispatchers.IO) {
        executeWithRecovery(scene) { s ->
            val result = BackendRouter.generateVideo(
                VideoGenerationRequest(
                    prompt = s.videoPrompt.ifBlank { s.visualPrompt },
                    duration = s.durationSeconds,
                    aspectRatio = "16:9"
                )
            )
            result.getOrNull()?.videoUrl?.let { url ->
                sceneDao.update(
                    s.copy(
                        generatedVideoPath = url,
                        videoStatus = VideoScene.STATUS_COMPLETED
                    )
                )
            } ?: throw Exception("视频生成返回空结果")
        }
    }

    suspend fun getErrorSummary(projectId: String): ErrorSummary = withContext(Dispatchers.IO) {
        val failedScenes = sceneDao.getByProject(projectId).filter {
            it.videoStatus == VideoScene.STATUS_FAILED
        }

        val categories = failedScenes.mapNotNull { scene ->
            val msg = scene.errorMessage
            if (msg.isNotBlank()) {
                classifyError(Exception(msg)).category
            } else {
                null
            }
        }.groupingBy { it }.eachCount()

        ErrorSummary(
            totalFailed = failedScenes.size,
            errorCategories = categories,
            scenesNeedingAttention = failedScenes.map { it.id }
        )
    }

    data class ErrorSummary(
        val totalFailed: Int,
        val errorCategories: Map<ErrorCategory, Int>,
        val scenesNeedingAttention: List<String>
    )
}
