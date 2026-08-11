package io.legado.app.video.pipeline

/**
 * Storyboard 跨镜头引用机制
 *
 * 借鉴 ArcReel 的核心创新：自动将上一帧分镜图作为下一帧的参考图
 * 这大幅提升了相邻镜头之间的视觉一致性
 *
 * 三种视频工作流：
 * 1. Storyboard image-to-video：从单张分镜图逐一生成
 * 2. Storyboard sheet-to-video：多张分镜一起生成，然后逐帧处理
 * 3. Reference-to-video：直接从角色/场景参考图生成视频
 */

enum class VideoWorkflow {
    IMAGE_TO_VIDEO,
    SHEET_TO_VIDEO,
    REFERENCE_TO_VIDEO
}

data class StoryboardFrame(
    val frameId: String,
    val index: Int,
    val prompt: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val characterRefs: List<String> = emptyList(),
    val clueRefs: List<String> = emptyList(),
    val styleRefUrl: String? = null,
    val previousFrameUrl: String? = null,
    val status: FrameStatus = FrameStatus.PENDING
)

enum class FrameStatus {
    PENDING,
    IMAGE_GENERATING,
    IMAGE_READY,
    VIDEO_GENERATING,
    VIDEO_READY,
    COMPLETED,
    FAILED
}

class StoryboardSequencer {

    /**
     * 构建分镜序列：自动注入跨帧引用
     *
     * 关键逻辑：
     * - 第 N 帧引用第 N-1 帧的图像（视觉延续性）
     * - 同时引用锁定的角色/线索设计图
     * - 风格参考图全局共享
     */
    fun buildSequence(
        frames: List<StoryboardFrame>,
        characterImageUrls: Map<String, String>,
        clueImageUrls: Map<String, String>,
        styleImageUrl: String?
    ): List<StoryboardFrame> {
        if (frames.isEmpty()) return frames

        val sequenced = mutableListOf<StoryboardFrame>()

        frames.forEachIndexed { index, frame ->
            val previousFrame = sequenced.lastOrNull()

            val enrichedPrompt = buildString {
                append(frame.prompt)

                if (frame.characterRefs.isNotEmpty()) {
                    val charRefs = frame.characterRefs.mapNotNull { name ->
                        characterImageUrls[name]?.let { url -> "$name(ref: $url)" }
                    }
                    if (charRefs.isNotEmpty()) {
                        append(". Character references: ${charRefs.joinToString(", ")}")
                    }
                }

                if (frame.clueRefs.isNotEmpty()) {
                    val clueRefs = frame.clueRefs.mapNotNull { name ->
                        clueImageUrls[name]?.let { url -> "$name(ref: $url)" }
                    }
                    if (clueRefs.isNotEmpty()) {
                        append(". Scene/prop references: ${clueRefs.joinToString(", ")}")
                    }
                }

                if (previousFrame != null && previousFrame.imageUrl != null) {
                    append(". Visual continuity reference: previous frame ${previousFrame.frameId}")
                }
            }

            sequenced.add(
                frame.copy(
                    prompt = enrichedPrompt,
                    previousFrameUrl = previousFrame?.imageUrl,
                    styleRefUrl = styleImageUrl
                )
            )
        }

        return sequenced
    }

    /**
     * 计算帧间依赖关系
     */
    fun calculateDependencies(frames: List<StoryboardFrame>): Map<String, List<String>> {
        val dependencies = mutableMapOf<String, List<String>>()

        frames.forEachIndexed { index, frame ->
            val deps = mutableListOf<String>()

            if (index > 0) {
                deps.add(frames[index - 1].frameId)
            }

            dependencies[frame.frameId] = deps
        }

        return dependencies
    }

    /**
     * 生成下一批可并行的帧（所有依赖已完成）
     */
    fun getReadyFrames(
        frames: List<StoryboardFrame>,
        completedIds: Set<String>,
        maxBatchSize: Int = 3
    ): List<StoryboardFrame> {
        val dependencies = calculateDependencies(frames)

        return frames.filter { frame ->
            val deps = dependencies[frame.frameId] ?: emptyList()
            deps.all { completedIds.contains(it) }
        }.take(maxBatchSize)
    }
}

class VideoWorkflowEngine {

    private val sequencer = StoryboardSequencer()

    /**
     * Image-to-Video：逐帧从分镜图生成视频
     * 适合需要逐帧审查的场景
     */
    suspend fun runImageToVideo(
        frames: List<StoryboardFrame>,
        onFrameComplete: (StoryboardFrame) -> Unit
    ): List<StoryboardFrame> {
        val results = frames.toMutableList()

        for ((index, frame) in frames.withIndex()) {
            val previousImage = if (index > 0) results[index - 1].imageUrl else null

            val result = generateFrameWithContinuity(frame, previousImage)

            results[index] = result
            onFrameComplete(result)
        }

        return results
    }

    /**
     * Sheet-to-Video：批量生成分镜图，然后逐帧转视频
     * 适合需要保持跨帧一致性的长段落
     */
    suspend fun runSheetToVideo(
        frames: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        clueRefs: Map<String, String>,
        styleRef: String?,
        onProgress: (Int, Int) -> Unit
    ): List<StoryboardFrame> {
        val sequenced = sequencer.buildSequence(frames, characterRefs, clueRefs, styleRef)
        val results = sequenced.toMutableList()

        for ((index, frame) in sequenced.withIndex()) {
            results[index] = generateFrameWithContinuity(frame, frame.previousFrameUrl)
            onProgress(index + 1, sequenced.size)
        }

        return results
    }

    /**
     * Reference-to-Video：直接从角色/场景参考图生成视频
     * 适合广告/产品短片等需要强锚定的场景
     */
    suspend fun runReferenceToVideo(
        prompt: String,
        referenceImageUrls: List<String>,
        duration: Int = 5,
        aspectRatio: String = "16:9"
    ): Result<String> {
        return try {
            val result = io.legado.app.video.api.BackendRouter.generateVideo(
                io.legado.app.video.api.VideoGenerationRequest(
                    prompt = prompt,
                    imageUrl = referenceImageUrls.firstOrNull(),
                    duration = duration,
                    aspectRatio = aspectRatio,
                    gridImages = referenceImageUrls
                )
            )
            result.map { it.videoUrl ?: "" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun generateFrameWithContinuity(
        frame: StoryboardFrame,
        previousImageUrl: String?
    ): StoryboardFrame {
        var imageUrl = frame.imageUrl

        if (imageUrl == null) {
            try {
                val imageResult = io.legado.app.video.api.BackendRouter.generateImage(
                    io.legado.app.video.api.ImageGenerationRequest(
                        prompt = frame.prompt,
                        width = 1280,
                        height = 720,
                        count = 1
                    )
                )
                imageResult.getOrNull()?.let {
                    imageUrl = it.images.firstOrNull()?.url
                }
            } catch (_: Exception) { }
        }

        return frame.copy(
            imageUrl = imageUrl,
            status = FrameStatus.IMAGE_READY
        )
    }
}
