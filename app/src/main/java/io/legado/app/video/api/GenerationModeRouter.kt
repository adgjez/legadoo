package io.legado.app.video.api

import io.legado.app.video.pipeline.StoryboardFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 参考图/宫格图（Grid Images）API 规范
 *
 * 借鉴 ArcReel 的 reference-to-video 和 sheet-to-video 工作流：
 * - 单图参考：将一张或多张参考图传入视频生成
 * - 宫格图（Grid）：将多个分镜合并为一张大图，然后自动拆分逐帧生成
 * - 支持角色/场景/道具/风格参考
 */

data class GridImage(
    val gridId: String,
    val rows: Int,
    val cols: Int,
    val cellCount: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val imageUrl: String? = null,
    val segments: List<GridSegment> = emptyList()
)

data class GridSegment(
    val segmentId: String,
    val index: Int,
    val imageUrl: String? = null,
    val cellPosition: CellPosition
)

data class CellPosition(
    val row: Int,
    val col: Int,
    val cellIndex: Int
)

class GridImageGenerator {

    /**
     * 从多个分镜生成宫格图
     *
     * 流程：
     * 1. 计算最优 rows/cols
     * 2. 批量生成分镜图
     * 3. 拼接为宫格图
     * 4. 拆分回单帧（供 sheet-to-video 工作流使用）
     */
    suspend fun generateGrid(
        prompts: List<String>,
        characterRefs: Map<String, String> = emptyMap(),
        styleRef: String? = null
    ): Result<GridImage> = withContext(Dispatchers.IO) {
        if (prompts.isEmpty()) return@withContext Result.failure(IllegalArgumentException("prompts 不能为空"))

        val cellCount = prompts.size
        val cols = optimalCols(cellCount)
        val rows = (cellCount + cols - 1) / cols

        val cellWidth = 1024
        val cellHeight = 576

        val segments = mutableListOf<GridSegment>()

        prompts.forEachIndexed { index, prompt ->
            val row = index / cols
            val col = index % cols

            val fullPrompt = buildString {
                append(prompt)
                if (styleRef != null) append(" (style ref)")
                val charRefs = characterRefs.entries.take(3).joinToString(", ") { "${it.key}(ref)" }
                if (charRefs.isNotBlank()) append(". Character refs: $charRefs")
            }

            try {
                val result = BackendRouter.generateImage(
                    ImageGenerationRequest(
                        prompt = fullPrompt,
                        width = cellWidth,
                        height = cellHeight,
                        count = 1
                    )
                )

                val imageUrl = result.getOrNull()?.images?.firstOrNull()?.url

                segments.add(
                    GridSegment(
                        segmentId = "grid_seg_$index",
                        index = index,
                        imageUrl = imageUrl,
                        cellPosition = CellPosition(row, col, index)
                    )
                )
            } catch (_: Exception) {
                segments.add(
                    GridSegment(
                        segmentId = "grid_seg_$index",
                        index = index,
                        imageUrl = null,
                        cellPosition = CellPosition(row, col, index)
                    )
                )
            }
        }

        Result.success(
            GridImage(
                gridId = "grid_${System.currentTimeMillis()}",
                rows = rows,
                cols = cols,
                cellCount = cellCount,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                segments = segments
            )
        )
    }

    private fun optimalCols(count: Int): Int {
        return when {
            count <= 1 -> 1
            count <= 2 -> 2
            count <= 4 -> 2
            count <= 6 -> 3
            count <= 9 -> 3
            count <= 12 -> 4
            else -> 4
        }
    }

    suspend fun generateGridToVideo(
        grid: GridImage,
        durationPerCell: Int = 5,
        onCellComplete: (GridSegment, String?) -> Unit
    ): List<Pair<GridSegment, String?>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<GridSegment, String?>>()

        grid.segments.forEach { segment ->
            val prompt = "Grid segment ${segment.index} from storyboard"

            try {
                val videoResult = BackendRouter.generateVideo(
                    VideoGenerationRequest(
                        prompt = prompt,
                        imageUrl = segment.imageUrl,
                        duration = durationPerCell,
                        aspectRatio = "16:9"
                    )
                )

                val videoUrl = videoResult.getOrNull()?.videoUrl
                results.add(segment to videoUrl)
                onCellComplete(segment, videoUrl)
            } catch (e: Exception) {
                results.add(segment to null)
                onCellComplete(segment, null)
            }
        }

        results
    }
}

/**
 * Generation Mode 路由 —— 三模式设计
 *
 * 借鉴 ArcReel 的 generation_modes，但做了 Android 生产侧的适配：
 *
 * ① SINGLE（逐镜头独立生成）
 *   · 适用：独立的 1~3 分钟微短剧 / 分镜互不相连的 MV
 *   · 成本基线：N 次独立 Image + N 次独立 Video 调用
 *   · 优点：最稳健、失败时不影响其它镜头
 *   · 缺点：角色/场景一致性最弱
 *
 * ② GRID（宫格批量生成 → sheet-to-video）
 *   · 适用：16~32 格的分镜图一次性出图（漫画/四格/长图叙事）
 *   · 成本基线：1 次 GridImage + N/4 次独立 Video 调用（近似）
 *   · 优点：画风统一，批量出图快；Agnes/Runway 支持 sheet → 批量视频
 *   · 缺点：单格失败会整体重来；宫格图像素被摊薄
 *
 * ③ REFERENCE_VIDEO（多参考图链式驱动）
 *   · 适用：多角色叙事 + 连续长镜头（默认模式）
 *   · 成本基线：每段附加 1~N 张角色/风格/前一帧参考
 *   · 优点：一致性最强；跨镜头 face/prop/style 都可参考
 *   · 缺点：推理速度略慢，单帧错误会沿链式扩散
 */

enum class GenerationMode {
    SINGLE,
    GRID,
    REFERENCE_VIDEO
}

data class GenerationConfig(
    val mode: GenerationMode = GenerationMode.REFERENCE_VIDEO,
    val gridRows: Int = 2,
    val gridCols: Int = 2,
    val usePreviousFrameAsRef: Boolean = true,
    val maxConcurrentReferences: Int = 4,
    val crossShotReference: Boolean = true
) {
    companion object {
        /** 启发式推荐：根据脚本特征自动推荐模式 */
        fun recommendFor(
            totalSegments: Int,
            distinctCharacters: Int,
            hasDialogue: Boolean,
            budgetTier: BudgetTier = BudgetTier.BALANCED
        ): GenerationConfig {
            return when {
                // 无角色的纯氛围/MV → SINGLE 最快最便宜
                distinctCharacters == 0 && !hasDialogue ->
                    copy(mode = GenerationMode.SINGLE)
                // 镜头数很多的四格漫画/章节式 → GRID
                totalSegments >= 8 && budgetTier != BudgetTier.QUALITY ->
                    copy(mode = GenerationMode.GRID, gridRows = 2, gridCols = 4)
                // 默认：多角色叙事 → REFERENCE_VIDEO
                else -> copy(mode = GenerationMode.REFERENCE_VIDEO)
            }
        }
    }
}

enum class BudgetTier { SPEED, BALANCED, QUALITY }

/**
 * 三种模式的能力画像，用于 UI 提示 / 预算估算 / 路由决策
 */
data class ModeCapabilityProfile(
    val mode: GenerationMode,
    val displayName: String,
    val costPerSegment: Float,     // 相对成本 (1.0 = SINGLE baseline)
    val consistencyScore: Float,   // 0~1 一致性
    val throughputMultiplier: Float,   // 速度倍率，越高越快
    val bestFor: String,
    val minSegments: Int,
    val maxSegments: Int,
    val requiredRefTypes: Set<ReferenceRole>
)

object ModeCapabilityCatalog {
    val PROFILES = mapOf(
        GenerationMode.SINGLE to ModeCapabilityProfile(
            mode = GenerationMode.SINGLE,
            displayName = "单镜头独立",
            costPerSegment = 1.0f,
            consistencyScore = 0.35f,
            throughputMultiplier = 1.4f,
            bestFor = "MV/氛围/广告片（单镜头风格差异大）",
            minSegments = 1,
            maxSegments = 6,
            requiredRefTypes = setOf(ReferenceRole.GENERAL)
        ),
        GenerationMode.GRID to ModeCapabilityProfile(
            mode = GenerationMode.GRID,
            displayName = "宫格批量出图",
            costPerSegment = 0.55f,
            consistencyScore = 0.72f,
            throughputMultiplier = 2.2f,
            bestFor = "漫画分镜/四格/长图叙事 (>8 格)",
            minSegments = 4,
            maxSegments = 32,
            requiredRefTypes = setOf(ReferenceRole.STYLE)
        ),
        GenerationMode.REFERENCE_VIDEO to ModeCapabilityProfile(
            mode = GenerationMode.REFERENCE_VIDEO,
            displayName = "多参考链式生成",
            costPerSegment = 1.35f,
            consistencyScore = 0.92f,
            throughputMultiplier = 0.85f,
            bestFor = "多角色叙事 / 连续分镜 / 连续剧（默认）",
            minSegments = 2,
            maxSegments = 64,
            requiredRefTypes = setOf(
                ReferenceRole.CHARACTER,
                ReferenceRole.STYLE,
                ReferenceRole.PREVIOUS_FRAME
            )
        )
    )

    fun estimateBudget(config: GenerationConfig, segmentCount: Int): Float {
        val p = PROFILES[config.mode] ?: return segmentCount * 1.0f
        return segmentCount * p.costPerSegment
    }

    fun validate(config: GenerationConfig, segmentCount: Int): List<String> {
        val issues = mutableListOf<String>()
        val p = PROFILES[config.mode] ?: return listOf("未知模式")
        if (segmentCount < p.minSegments) {
            issues += "分段数=$segmentCount < 建议最小值=${p.minSegments}，建议改用 SINGLE"
        }
        if (segmentCount > p.maxSegments) {
            issues += "分段数=$segmentCount > 建议最大值=${p.maxSegments}，建议分批"
        }
        return issues
    }
}

class GenerationModeRouter {

    suspend fun run(
        config: GenerationConfig,
        segments: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        styleRef: String?
    ): Result<List<StoryboardFrame>> = withContext(Dispatchers.IO) {
        val violations = ModeCapabilityCatalog.validate(config, segments.size)
        if (violations.isNotEmpty() && config.mode != GenerationMode.SINGLE) {
            // 不阻断，只降低层级
            return@withContext runSingleMode(segments, characterRefs, styleRef, config)
        }
        when (config.mode) {
            GenerationMode.SINGLE -> runSingleMode(segments, characterRefs, styleRef, config)
            GenerationMode.GRID -> runGridMode(segments, characterRefs, styleRef, config)
            GenerationMode.REFERENCE_VIDEO -> runReferenceVideoMode(segments, characterRefs, styleRef, config)
        }
    }

    /**
     * Dry Run 模式：不调用任何真实 API，只模拟三种模式的执行路径，
     * 返回包含模拟结果的 StoryboardFrame 列表 + 每张帧模拟耗时/消耗的元数据。
     * 用于集成测试 & 用户预览"如果选此模式，大致需要多少步骤"。
     */
    suspend fun dryRun(
        config: GenerationConfig,
        segments: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        styleRef: String?
    ): Result<GenerationDryRunReport> = withContext(Dispatchers.IO) {
        val profile = ModeCapabilityCatalog.PROFILES[config.mode]
            ?: return@withContext Result.failure(Exception("未知模式 ${config.mode}"))
        val warnings = ModeCapabilityCatalog.validate(config, segments.size)
        val perFrame = segments.map { seg ->
            val refs = mutableListOf<ReferenceRole>()
            when (config.mode) {
                GenerationMode.REFERENCE_VIDEO -> {
                    refs += ReferenceRole.CHARACTER
                    styleRef?.let { refs += ReferenceRole.STYLE }
                    if (config.usePreviousFrameAsRef) refs += ReferenceRole.PREVIOUS_FRAME
                }
                GenerationMode.GRID -> refs += ReferenceRole.STYLE
                else -> refs += ReferenceRole.GENERAL
            }
            DryRunFrame(
                frameId = seg.frameId,
                simulatedImageUrl = "dryrun://img/${seg.frameId}",
                simulatedVideoUrl = "dryrun://vid/${seg.frameId}",
                referenceRolesUsed = refs,
                estimatedCost = profile.costPerSegment
            )
        }
        Result.success(
            GenerationDryRunReport(
                mode = config.mode,
                segmentCount = segments.size,
                estimatedTotalCost = ModeCapabilityCatalog.estimateBudget(config, segments.size),
                profile = profile,
                warnings = warnings,
                frames = perFrame
            )
        )
    }

    private suspend fun runSingleMode(
        segments: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        styleRef: String?,
        config: GenerationConfig
    ): Result<List<StoryboardFrame>> {
        val results = segments.toMutableList()

        for ((index, segment) in segments.withIndex()) {
            val previousRef = if (config.usePreviousFrameAsRef && index > 0) {
                results[index - 1].imageUrl
            } else null

            val refs = mutableListOf<ReferenceImage>()
            previousRef?.let { refs.add(ReferenceImage(url = it, role = ReferenceRole.PREVIOUS_FRAME)) }

            val result = generateSegmentWithRefs(segment, refs, characterRefs, styleRef)
            results[index] = result
        }

        return Result.success(results)
    }

    private suspend fun runGridMode(
        segments: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        styleRef: String?,
        config: GenerationConfig
    ): Result<List<StoryboardFrame>> {
        val gridGenerator = GridImageGenerator()
        val prompts = segments.map { it.prompt }

        val gridResult = gridGenerator.generateGrid(prompts, characterRefs, styleRef)
        gridResult.getOrNull()?.let { grid ->
            val videoResults = gridGenerator.generateGridToVideo(
                grid,
                durationPerCell = 5
            ) { _, _ -> }

            val results = segments.toMutableList()
            videoResults.forEach { (segment, videoUrl) ->
                val index = segment.index
                if (index < results.size) {
                    results[index] = results[index].copy(
                        imageUrl = segment.imageUrl,
                        videoUrl = videoUrl
                    )
                }
            }
            return Result.success(results)
        }

        return Result.failure(gridResult.exceptionOrNull() ?: Exception("Grid generation failed"))
    }

    private suspend fun runReferenceVideoMode(
        segments: List<StoryboardFrame>,
        characterRefs: Map<String, String>,
        styleRef: String?,
        config: GenerationConfig
    ): Result<List<StoryboardFrame>> {
        val results = segments.toMutableList()

        for ((index, segment) in segments.withIndex()) {
            val refs = buildReferenceSet(segment, characterRefs, styleRef, config)
            val result = generateSegmentWithRefs(segment, refs, characterRefs, styleRef)
            results[index] = result
        }

        return Result.success(results)
    }

    private fun buildReferenceSet(
        segment: StoryboardFrame,
        characterRefs: Map<String, String>,
        styleRef: String?,
        config: GenerationConfig
    ): List<ReferenceImage> {
        val refs = mutableListOf<ReferenceImage>()

        segment.characterRefs.take(config.maxConcurrentReferences).forEach { name ->
            characterRefs[name]?.let { url ->
                refs.add(ReferenceImage(url = url, label = name, role = ReferenceRole.CHARACTER))
            }
        }

        styleRef?.let {
            refs.add(ReferenceImage(url = it, role = ReferenceRole.STYLE))
        }

        return refs
    }

    private suspend fun generateSegmentWithRefs(
        segment: StoryboardFrame,
        refs: List<ReferenceImage>,
        characterRefs: Map<String, String>,
        styleRef: String?
    ): StoryboardFrame {
        var imageUrl = segment.imageUrl

        if (imageUrl == null) {
            try {
                val prompt = buildString {
                    append(segment.prompt)
                    if (refs.isNotEmpty()) {
                        append(". References: ${refs.map { it.label ?: it.role.name }.joinToString(", ")}")
                    }
                }

                val result = BackendRouter.generateImage(
                    ImageGenerationRequest(
                        prompt = prompt,
                        width = 1280,
                        height = 720,
                        count = 1
                    )
                )
                imageUrl = result.getOrNull()?.images?.firstOrNull()?.url
            } catch (_: Exception) { }
        }

        return segment.copy(imageUrl = imageUrl)
    }
}

/**
 * Dry Run 报告 —— 集成测试使用
 */
data class GenerationDryRunReport(
    val mode: GenerationMode,
    val segmentCount: Int,
    val estimatedTotalCost: Float,
    val profile: ModeCapabilityProfile,
    val warnings: List<String>,
    val frames: List<DryRunFrame>
)

data class DryRunFrame(
    val frameId: String,
    val simulatedImageUrl: String,
    val simulatedVideoUrl: String,
    val referenceRolesUsed: List<ReferenceRole>,
    val estimatedCost: Float
)
