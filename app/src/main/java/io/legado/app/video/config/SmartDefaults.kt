package io.legado.app.video.config

import io.legado.app.video.api.ProviderCapability
import io.legado.app.video.api.ProviderRegistry
import io.legado.app.video.states.ConfigStatusState
import io.legado.app.video.states.ConfigStatusStore

/**
 * 智能默认配置系统
 *
 * 借鉴 ArcReel 的项目配置：
 * - 根据项目类型自动配置最佳参数
 * - 配置向导：一步步引导用户完成配置
 * - 配置模板：常见场景的预设配置
 * - 能力检测：自动检测已配置的能力
 */

// ========== 项目类型配置 ==========

enum class ProjectType(val displayName: String, val description: String) {
    NOVEL_ADAPTATION("小说改编", "将小说改编为动画剧集"),
    ORIGINAL_ANIMATION("原创动画", "制作原创动画内容"),
    COMIC_ADAPTATION("漫画改编", "将漫画改编为动画"),
    EDUCATIONAL("教育内容", "制作教育类动画"),
    MARKETING("营销宣传", "制作营销动画视频"),
    MUSIC_VIDEO("MV制作", "制作音乐视频"),
    SHORT_FILM("短片制作", "制作短篇动画"),
    SERIES("连续剧", "制作连载动画剧集")
}

data class ProjectDefaults(
    val projectType: ProjectType,
    val recommendedProviders: List<String>,
    val recommendedResolution: String,
    val recommendedDurationPerFrame: Int,
    val recommendedFramesPerEpisode: Int,
    val recommendedStylePreset: String,
    val recommendedTransitionPreset: String,
    val qualityPreset: QualityPreset,
    val costSensitivity: CostSensitivity,
    val autoGenerationEnabled: Boolean,
    val parallelGenerationEnabled: Boolean,
    val maxConcurrentGenerations: Int,
    val defaultAspectRatio: String,
    val watermarkEnabled: Boolean
)

enum class QualityPreset {
    QUICK_PROTOTYPE,
    STANDARD,
    HIGH_QUALITY,
    PRODUCTION,
    CINEMATIC
}

enum class CostSensitivity {
    LOW,
    MEDIUM,
    HIGH
}

// ========== 配置预设 ==========

object ProjectConfigPresets {

    val NOVEL_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.NOVEL_ADAPTATION,
        recommendedProviders = listOf("agnes", "newapi", "doubao"),
        recommendedResolution = "1080p",
        recommendedDurationPerFrame = 5,
        recommendedFramesPerEpisode = 12,
        recommendedStylePreset = "cinematic_drama",
        recommendedTransitionPreset = "cinematic_drama",
        qualityPreset = QualityPreset.HIGH_QUALITY,
        costSensitivity = CostSensitivity.MEDIUM,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = true,
        maxConcurrentGenerations = 4,
        defaultAspectRatio = "16:9",
        watermarkEnabled = false
    )

    val ANIME_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.ORIGINAL_ANIMATION,
        recommendedProviders = listOf("agnes", "kling", "seedance"),
        recommendedResolution = "1080p",
        recommendedDurationPerFrame = 5,
        recommendedFramesPerEpisode = 16,
        recommendedStylePreset = "anime",
        recommendedTransitionPreset = "action_thriller",
        qualityPreset = QualityPreset.PRODUCTION,
        costSensitivity = CostSensitivity.HIGH,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = true,
        maxConcurrentGenerations = 6,
        defaultAspectRatio = "16:9",
        watermarkEnabled = false
    )

    val MARKETING_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.MARKETING,
        recommendedProviders = listOf("agnes", "newapi", "grok"),
        recommendedResolution = "4K",
        recommendedDurationPerFrame = 3,
        recommendedFramesPerEpisode = 8,
        recommendedStylePreset = "cinematic_drama",
        recommendedTransitionPreset = "commercial",
        qualityPreset = QualityPreset.CINEMATIC,
        costSensitivity = CostSensitivity.LOW,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = true,
        maxConcurrentGenerations = 8,
        defaultAspectRatio = "9:16",
        watermarkEnabled = true
    )

    val EDUCATIONAL_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.EDUCATIONAL,
        recommendedProviders = listOf("agnes", "newapi"),
        recommendedResolution = "1080p",
        recommendedDurationPerFrame = 4,
        recommendedFramesPerEpisode = 10,
        recommendedStylePreset = "documentary",
        recommendedTransitionPreset = "documentary",
        qualityPreset = QualityPreset.STANDARD,
        costSensitivity = CostSensitivity.MEDIUM,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = false,
        maxConcurrentGenerations = 2,
        defaultAspectRatio = "16:9",
        watermarkEnabled = true
    )

    val MUSIC_VIDEO_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.MUSIC_VIDEO,
        recommendedProviders = listOf("seedance", "agnes", "kling"),
        recommendedResolution = "4K",
        recommendedDurationPerFrame = 5,
        recommendedFramesPerEpisode = 20,
        recommendedStylePreset = "music_video",
        recommendedTransitionPreset = "music_video",
        qualityPreset = QualityPreset.CINEMATIC,
        costSensitivity = CostSensitivity.LOW,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = true,
        maxConcurrentGenerations = 6,
        defaultAspectRatio = "16:9",
        watermarkEnabled = false
    )

    val SERIES_DEFAULTS = ProjectDefaults(
        projectType = ProjectType.SERIES,
        recommendedProviders = listOf("agnes", "newapi", "doubao", "seedance"),
        recommendedResolution = "1080p",
        recommendedDurationPerFrame = 5,
        recommendedFramesPerEpisode = 14,
        recommendedStylePreset = "cinematic_drama",
        recommendedTransitionPreset = "cinematic_drama",
        qualityPreset = QualityPreset.PRODUCTION,
        costSensitivity = CostSensitivity.MEDIUM,
        autoGenerationEnabled = true,
        parallelGenerationEnabled = true,
        maxConcurrentGenerations = 6,
        defaultAspectRatio = "16:9",
        watermarkEnabled = false
    )

    private val presets = mapOf(
        ProjectType.NOVEL_ADAPTATION to NOVEL_DEFAULTS,
        ProjectType.ORIGINAL_ANIMATION to ANIME_DEFAULTS,
        ProjectType.COMIC_ADAPTATION to NOVEL_DEFAULTS,
        ProjectType.EDUCATIONAL to EDUCATIONAL_DEFAULTS,
        ProjectType.MARKETING to MARKETING_DEFAULTS,
        ProjectType.MUSIC_VIDEO to MUSIC_VIDEO_DEFAULTS,
        ProjectType.SHORT_FILM to NOVEL_DEFAULTS,
        ProjectType.SERIES to SERIES_DEFAULTS
    )

    fun getDefaults(type: ProjectType): ProjectDefaults = presets[type] ?: NOVEL_DEFAULTS

    fun listAll(): List<ProjectDefaults> = presets.values.distinct()
}

// ========== 配置向导 ==========

class ConfigurationWizard(
    private val configStore: ConfigStatusStore = ConfigStatusStore.instance
) {

    data class WizardStep(
        val stepId: String,
        val title: String,
        val description: String,
        val type: StepType,
        val options: List<String> = emptyList(),
        val required: Boolean = true
    )

    enum class StepType {
        SINGLE_SELECT,
        MULTI_SELECT,
        NUMBER_INPUT,
        TOGGLE,
        TEXT_INPUT
    }

    private val steps = listOf(
        WizardStep(
            stepId = "project_type",
            title = "选择项目类型",
            description = "不同类型的项目有不同的最佳配置",
            type = StepType.SINGLE_SELECT,
            options = ProjectType.entries.map { it.displayName }
        ),
        WizardStep(
            stepId = "quality_level",
            title = "选择质量等级",
            description = "质量越高，生成时间越长，成本越高",
            type = StepType.SINGLE_SELECT,
            options = QualityPreset.entries.map { it.name }
        ),
        WizardStep(
            stepId = "providers",
            title = "选择 AI 服务商",
            description = "选择一个或多个用于生成的 AI 服务商",
            type = StepType.MULTI_SELECT,
            options = listOf("Agnes", "NewAPI", "Doubao", "Kling", "Seedance", "Grok")
        ),
        WizardStep(
            stepId = "parallel",
            title = "启用并行生成",
            description = "同时生成多个分镜，速度更快但消耗更多资源",
            type = StepType.TOGGLE
        ),
        WizardStep(
            stepId = "aspect_ratio",
            title = "选择画面比例",
            description = "根据发布平台选择合适的画面比例",
            type = StepType.SINGLE_SELECT,
            options = listOf("16:9 (横屏)", "9:16 (竖屏)", "1:1 (方形)", "4:3 (传统)", "21:9 (宽屏)")
        )
    )

    fun getSteps(): List<WizardStep> = steps.toList()

    suspend fun validateAndComplete(answers: Map<String, Any?>): ConfigStatusState {
        val textConfigured = answers["providers"]?.toString()?.isNotBlank() == true
        val imageConfigured = answers["providers"]?.toString()?.isNotBlank() == true
        val videoConfigured = answers["providers"]?.toString()?.isNotBlank() == true
        val agentConfigured = textConfigured && imageConfigured && videoConfigured

        configStore.setTextProviderConfigured(textConfigured)
        configStore.setImageProviderConfigured(imageConfigured)
        configStore.setVideoProviderConfigured(videoConfigured)
        configStore.setAgentConfigured(agentConfigured)

        return configStore.getCurrent()
    }

    fun getRecommendedDefaults(projectType: ProjectType): ProjectDefaults {
        return ProjectConfigPresets.getDefaults(projectType)
    }
}

// ========== 能力自动检测 ==========

class CapabilityAutoDetector {

    data class DetectedCapabilities(
        val availableProviders: List<String>,
        val availableCapabilities: Set<ProviderCapability>,
        val missingCapabilities: Set<ProviderCapability>,
        val recommendedAction: String
    )

    fun detect(): DetectedCapabilities {
        val activeProviders = ProviderRegistry.getActiveProviders()
        val availableKeys = activeProviders.map { it.providerKey }.distinct()

        val capabilities = mutableSetOf<ProviderCapability>()

        activeProviders.forEach { provider ->
            if (provider is io.legado.app.video.api.TextBackend) {
                capabilities.add(ProviderCapability.TEXT_GENERATION)
            }
            if (provider is io.legado.app.video.api.ImageBackend) {
                capabilities.add(ProviderCapability.IMAGE_GENERATION)
            }
            if (provider is io.legado.app.video.api.VideoBackend) {
                capabilities.add(ProviderCapability.VIDEO_GENERATION)
            }
        }

        val missing = ProviderCapability.entries.filter { cap ->
            cap !in capabilities
        }.toSet()

        val action = when {
            capabilities.isEmpty() -> "请至少配置一个 AI 服务商"
            missing.isNotEmpty() && capabilities.size < 3 -> "建议配置更多服务商以获得完整功能"
            else -> "配置已完成，可以开始使用"
        }

        return DetectedCapabilities(
            availableProviders = availableKeys,
            availableCapabilities = capabilities,
            missingCapabilities = missing,
            recommendedAction = action
        )
    }

    fun getStatusDescription(): String {
        val detection = detect()
        return buildString {
            append("已配置 ${detection.availableProviders.size} 个服务商，")
            append("支持 ${detection.availableCapabilities.size} 种能力。")
            if (detection.missingCapabilities.isNotEmpty()) {
                append(" 缺少：${detection.missingCapabilities.joinToString(", ")}。")
            }
        }
    }
}

// ========== 智能默认推荐总入口 ==========

object SmartDefaults {

    private val keywordHints = mapOf(
        "小说" to ProjectType.NOVEL_ADAPTATION,
        "玄幻" to ProjectType.NOVEL_ADAPTATION,
        "仙侠" to ProjectType.NOVEL_ADAPTATION,
        "武侠" to ProjectType.NOVEL_ADAPTATION,
        "同人" to ProjectType.NOVEL_ADAPTATION,
        "漫画" to ProjectType.COMIC_ADAPTATION,
        "动漫" to ProjectType.COMIC_ADAPTATION,
        "MV" to ProjectType.MUSIC_VIDEO,
        "音乐" to ProjectType.MUSIC_VIDEO,
        "教育" to ProjectType.EDUCATIONAL,
        "教学" to ProjectType.EDUCATIONAL,
        "知识" to ProjectType.EDUCATIONAL,
        "宣传" to ProjectType.MARKETING,
        "广告" to ProjectType.MARKETING,
        "营销" to ProjectType.MARKETING,
        "短片" to ProjectType.SHORT_FILM,
        "系列" to ProjectType.SERIES,
        "连载" to ProjectType.SERIES,
        "原创" to ProjectType.ORIGINAL_ANIMATION
    )

    fun inferProjectType(sourceContent: String, projectNameHint: String = ""): ProjectType {
        val combined = "$projectNameHint $sourceContent"

        for ((keyword, type) in keywordHints) {
            if (combined.contains(keyword, ignoreCase = true)) {
                return type
            }
        }

        val length = sourceContent.length
        return when {
            length > 5000 -> ProjectType.SERIES
            length > 1000 -> ProjectType.NOVEL_ADAPTATION
            else -> ProjectType.SHORT_FILM
        }
    }

    fun recommendDefaults(
        projectType: ProjectType,
        capabilityDetector: CapabilityAutoDetector = CapabilityAutoDetector()
    ): ProjectDefaults {
        val baseDefaults = ProjectConfigPresets.getDefaults(projectType)
        val detected = capabilityDetector.detect()

        val availableProviderKeys = detected.availableProviders.toSet()
        val filteredProviders = baseDefaults.recommendedProviders.filter { it in availableProviderKeys }

        val actualProviders = filteredProviders.ifEmpty {
            if (availableProviderKeys.isNotEmpty()) availableProviderKeys.take(3).toList()
            else baseDefaults.recommendedProviders
        }

        val parallelEnabled = if (actualProviders.size < 2) false else baseDefaults.parallelGenerationEnabled
        val maxConcurrent = if (parallelEnabled) {
            minOf(baseDefaults.maxConcurrentGenerations, actualProviders.size * 2)
        } else 1

        return baseDefaults.copy(
            recommendedProviders = actualProviders,
            parallelGenerationEnabled = parallelEnabled,
            maxConcurrentGenerations = maxConcurrent
        )
    }

    fun recommendFromContent(
        sourceContent: String,
        projectName: String = ""
    ): Pair<ProjectType, ProjectDefaults> {
        val inferredType = inferProjectType(sourceContent, projectName)
        val defaults = recommendDefaults(inferredType)
        return inferredType to defaults
    }

    fun recommendAspectRatio(platformHint: String = ""): String {
        return when {
            platformHint.contains("抖音", ignoreCase = true) ||
            platformHint.contains("tiktok", ignoreCase = true) ||
            platformHint.contains("短视频", ignoreCase = true) -> "9:16"
            platformHint.contains("B站", ignoreCase = true) ||
            platformHint.contains("YouTube", ignoreCase = true) ||
            platformHint.contains("油管", ignoreCase = true) -> "16:9"
            platformHint.contains("小红书", ignoreCase = true) ||
            platformHint.contains("Instagram", ignoreCase = true) -> "3:4"
            else -> "16:9"
        }
    }

    fun estimateCost(
        defaults: ProjectDefaults,
        numScenes: Int = defaults.recommendedFramesPerEpisode,
        numEpisodes: Int = 1
    ): Map<String, Float> {
        val perSceneImageCost = 0.05f
        val perSceneVideoCost = 0.2f
        val perSceneTextCost = 0.01f
        val perSceneTTSCost = 0.02f

        val totalScenes = numScenes * numEpisodes

        return mapOf(
            "image" to totalScenes * perSceneImageCost,
            "video" to totalScenes * perSceneVideoCost,
            "text" to totalScenes * perSceneTextCost,
            "tts" to totalScenes * perSceneTTSCost,
            "total" to totalScenes * (perSceneImageCost + perSceneVideoCost + perSceneTextCost + perSceneTTSCost)
        )
    }

    fun estimateDuration(
        defaults: ProjectDefaults,
        numScenes: Int = defaults.recommendedFramesPerEpisode,
        includeTransition: Boolean = true
    ): Int {
        val sceneDuration = numScenes * defaults.recommendedDurationPerFrame
        val transitionDuration = if (includeTransition) (numScenes - 1) * 1 else 0
        return sceneDuration + transitionDuration
    }
}
