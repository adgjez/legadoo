package io.legado.app.video.templates

import io.legado.app.video.agent.AgentStylePresets
import io.legado.app.video.converter.BookConversionConfig
import io.legado.app.video.export.ExportConfig
import io.legado.app.video.export.ExportFormat
import io.legado.app.video.export.ExportQuality

data class WorkflowTemplate(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val category: TemplateCategory,
    val difficulty: Difficulty,
    val estimatedDuration: String,
    val novelConfig: BookConversionConfig,
    val agentConfig: Map<String, String>,
    val exportConfig: ExportConfig,
    val stylePreset: String,
    val tips: List<String>,
    val communitySource: String = "built-in",
    val isPremium: Boolean = false
)

enum class TemplateCategory {
    NOVEL_ADAPTATION,
    SHORT_FILM,
    MUSIC_VIDEO,
    TRAILER,
    TUTORIAL,
    SOCIAL_MEDIA,
    CINEMATIC
}

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

object WorkflowTemplateManager {

    private val builtInTemplates = listOf(
        WorkflowTemplate(
            id = "classic-novel",
            name = "经典小说改编",
            description = "将经典小说改编为高质量视频，适合长篇小说",
            emoji = "📖",
            category = TemplateCategory.NOVEL_ADAPTATION,
            difficulty = Difficulty.INTERMEDIATE,
            estimatedDuration = "2-4小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 2,
                maxSceneLength = 400,
                style = "cinematic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "3",
                "parallelism" to "2"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 8_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4,
                includeTransitions = true
            ),
            stylePreset = "cinematic",
            tips = listOf(
                "建议选择经典名著",
                "适合改编10-50章",
                "角色较多，建议启用角色一致性"
            )
        ),
        WorkflowTemplate(
            id = "anime-adaptation",
            name = "动漫改编",
            description = "日本动漫风格改编，色彩鲜艳，动态镜头",
            emoji = "🎌",
            category = TemplateCategory.NOVEL_ADAPTATION,
            difficulty = Difficulty.INTERMEDIATE,
            estimatedDuration = "2-3小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 1,
                maxSceneLength = 300,
                style = "anime",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "3",
                "parallelism" to "3"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 6_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4
            ),
            stylePreset = "anime",
            tips = listOf(
                "适合轻小说、漫画改编",
                "每章一个分镜效果最佳",
                "建议使用动漫风格的角色设计"
            )
        ),
        WorkflowTemplate(
            id = "short-drama",
            name = "短剧制作",
            description = "竖屏短剧风格，适合抖音/快手等短视频平台",
            emoji = "🎭",
            category = TemplateCategory.SHORT_FILM,
            difficulty = Difficulty.BEGINNER,
            estimatedDuration = "1-2小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 1,
                maxSceneLength = 200,
                style = "realistic",
                aspectRatio = "9:16",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "2",
                "parallelism" to "4"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1080,
                resolutionHeight = 1920,
                bitrate = 4_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "竖屏9:16比例",
                "适合霸总、甜宠等题材",
                "每集3-5分钟"
            )
        ),
        WorkflowTemplate(
            id = "trailer-maker",
            name = "预告片制作",
            description = "高燃预告片风格，快速节奏，精彩片段",
            emoji = "🎬",
            category = TemplateCategory.TRAILER,
            difficulty = Difficulty.ADVANCED,
            estimatedDuration = "30-60分钟",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 5,
                maxSceneLength = 1000,
                style = "cinematic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "false",
                "qualityLevel" to "4",
                "parallelism" to "2"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 10_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "cinematic",
            tips = listOf(
                "选择最精彩的5-10章",
                "快节奏剪辑",
                "建议使用片头片尾"
            )
        ),
        WorkflowTemplate(
            id = "cyberpunk-sci-fi",
            name = "赛博朋克科幻",
            description = "未来科幻题材，霓虹都市，反乌托邦氛围",
            emoji = "🌆",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.EXPERT,
            estimatedDuration = "4-6小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 500,
                style = "cyberpunk",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4",
                "parallelism" to "2"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 12_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "cyberpunk",
            tips = listOf(
                "适合赛博朋克、硬科幻小说",
                "视觉效果要求高",
                "建议GPU性能好的设备"
            ),
            isPremium = true
        ),
        WorkflowTemplate(
            id = "wuxia-epic",
            name = "武侠史诗",
            description = "中国传统武侠风格，水墨意境，飘逸武打",
            emoji = "⚔️",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.ADVANCED,
            estimatedDuration = "3-5小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 2,
                maxSceneLength = 400,
                style = "wuxia",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 8_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4
            ),
            stylePreset = "wuxia",
            tips = listOf(
                "适合金庸、古龙等武侠小说",
                "注重动作场面",
                "建议分镜精细"
            )
        ),
        WorkflowTemplate(
            id = "fantasy-epic",
            name = "奇幻史诗",
            description = "奇幻魔幻题材，史诗规模，神秘氛围",
            emoji = "🐉",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.EXPERT,
            estimatedDuration = "4-6小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 600,
                style = "fantasy",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4",
                "parallelism" to "2"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 12_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "fantasy",
            tips = listOf(
                "适合玄幻、奇幻小说",
                "世界观构建很重要",
                "建议启用风格迁移"
            ),
            isPremium = true
        ),
        WorkflowTemplate(
            id = "social-reel",
            name = "短视频竖屏",
            description = "9:16竖屏格式，适合社交媒体发布",
            emoji = "📱",
            category = TemplateCategory.SOCIAL_MEDIA,
            difficulty = Difficulty.BEGINNER,
            estimatedDuration = "30-60分钟",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 1,
                maxSceneLength = 150,
                style = "realistic",
                aspectRatio = "9:16",
                targetResolution = "1080p",
                includeCharacters = false
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "false",
                "enableSceneContinuity" to "false",
                "qualityLevel" to "1",
                "parallelism" to "6"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1080,
                resolutionHeight = 1920,
                bitrate = 3_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "竖屏格式",
                "单镜头时长15-30秒",
                "适合快节奏内容"
            )
        ),
        WorkflowTemplate(
            id = "mvsong-drama",
            name = "MV剧情短片",
            description = "音乐视频风格，配合歌曲，富有诗意",
            emoji = "🎵",
            category = TemplateCategory.MUSIC_VIDEO,
            difficulty = Difficulty.INTERMEDIATE,
            estimatedDuration = "1-2小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 400,
                style = "cinematic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "3"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 6_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4
            ),
            stylePreset = "cinematic",
            tips = listOf(
                "适合配合音乐",
                "注重情绪表达",
                "建议1-3分钟时长"
            )
        ),
        WorkflowTemplate(
            id = "tutorial-video",
            name = "教程知识视频",
            description = "教学/知识类视频，清晰明了",
            emoji = "📚",
            category = TemplateCategory.TUTORIAL,
            difficulty = Difficulty.BEGINNER,
            estimatedDuration = "30分钟",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 5,
                maxSceneLength = 500,
                style = "documentary",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = false
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "false",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "2",
                "parallelism" to "4"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 4_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "documentary",
            tips = listOf(
                "适合知识类内容",
                "讲解清晰",
                "配合字幕效果好"
            )
        ),
        WorkflowTemplate(
            id = "romantic-comedy",
            name = "浪漫爱情喜剧",
            description = "都市浪漫爱情题材，轻松甜蜜氛围",
            emoji = "💕",
            category = TemplateCategory.NOVEL_ADAPTATION,
            difficulty = Difficulty.INTERMEDIATE,
            estimatedDuration = "2-3小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 2,
                maxSceneLength = 300,
                style = "realistic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "3"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 6_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "适合甜宠、都市爱情小说",
                "注重男女主角色一致性",
                "光影柔美温馨"
            )
        ),
        WorkflowTemplate(
            id = "historical-drama",
            name = "历史正剧",
            description = "历史题材正剧风格，庄重沉稳氛围",
            emoji = "🏛️",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.EXPERT,
            estimatedDuration = "4-6小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 500,
                style = "realistic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 10_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "适合历史穿越、架空历史小说",
                "注重时代还原",
                "建议详细分镜脚本"
            ),
            isPremium = true
        ),
        WorkflowTemplate(
            id = "horror-thriller",
            name = "恐怖悬疑",
            description = "恐怖/悬疑/惊悚风格，阴森紧张氛围",
            emoji = "👻",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.ADVANCED,
            estimatedDuration = "3-4小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 2,
                maxSceneLength = 300,
                style = "realistic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "4",
                "customInstructions" to "营造阴森、紧张、悬疑的氛围"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 8_000_000,
                quality = ExportQuality.HIGH,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "适合恐怖、悬疑、推理小说",
                "使用暗色调和阴影",
                "音效和音乐配合很重要"
            )
        ),
        WorkflowTemplate(
            id = "ancient-chinese",
            name = "古装仙侠",
            description = "中国古代/仙侠题材，飘逸唯美",
            emoji = "🏯",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.EXPERT,
            estimatedDuration = "4-6小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 500,
                style = "fantasy",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 12_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "fantasy",
            tips = listOf(
                "适合仙侠、玄幻、修真小说",
                "飘逸的服装和特效",
                "建议启用风格迁移"
            ),
            isPremium = true
        ),
        WorkflowTemplate(
            id = "urban-realistic",
            name = "都市写实",
            description = "现代都市题材，真实自然风格",
            emoji = "🏙️",
            category = TemplateCategory.NOVEL_ADAPTATION,
            difficulty = Difficulty.BEGINNER,
            estimatedDuration = "1-2小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 2,
                maxSceneLength = 250,
                style = "realistic",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "2",
                "parallelism" to "3"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 4_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "realistic",
            tips = listOf(
                "适合都市、职场小说",
                "真实自然的表演",
                "可用快速并行处理"
            )
        ),
        WorkflowTemplate(
            id = "fantasy-sci-fi",
            name = "科幻奇幻混合",
            description = "科幻与奇幻融合，未来与魔法交织",
            emoji = "🚀",
            category = TemplateCategory.CINEMATIC,
            difficulty = Difficulty.EXPERT,
            estimatedDuration = "5-8小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 5,
                maxSceneLength = 800,
                style = "cyberpunk",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "enableStyleTransfer" to "true",
                "qualityLevel" to "4",
                "parallelism" to "1"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 15_000_000,
                quality = ExportQuality.ULTRA,
                format = ExportFormat.MP4
            ),
            stylePreset = "cyberpunk",
            tips = listOf(
                "适合硬科幻、机甲小说",
                "视觉效果要求极高",
                "建议单线程确保质量"
            ),
            isPremium = true
        ),
        WorkflowTemplate(
            id = "slice-of-life",
            name = "日常治愈",
            description = "温馨日常题材，治愈系风格",
            emoji = "🌸",
            category = TemplateCategory.NOVEL_ADAPTATION,
            difficulty = Difficulty.BEGINNER,
            estimatedDuration = "30-60分钟",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 3,
                maxSceneLength = 200,
                style = "anime",
                aspectRatio = "16:9",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "true",
                "qualityLevel" to "2",
                "parallelism" to "5"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                bitrate = 4_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "anime",
            tips = listOf(
                "适合日常、治愈、美食小说",
                "温暖柔和的色调",
                "快速并行处理效率高"
            )
        ),
        WorkflowTemplate(
            id = "comedy-skit",
            name = "喜剧小品",
            description = "喜剧风格，搞笑桥段密集",
            emoji = "😂",
            category = TemplateCategory.SHORT_FILM,
            difficulty = Difficulty.INTERMEDIATE,
            estimatedDuration = "1小时",
            novelConfig = BookConversionConfig(
                chaptersPerScene = 1,
                maxSceneLength = 150,
                style = "comic",
                aspectRatio = "9:16",
                targetResolution = "1080p",
                includeCharacters = true
            ),
            agentConfig = mapOf(
                "enableCharacterConsistency" to "true",
                "enableSceneContinuity" to "false",
                "qualityLevel" to "2",
                "parallelism" to "6"
            ),
            exportConfig = ExportConfig(
                resolutionWidth = 1080,
                resolutionHeight = 1920,
                bitrate = 3_000_000,
                quality = ExportQuality.MEDIUM,
                format = ExportFormat.MP4
            ),
            stylePreset = "comic",
            tips = listOf(
                "适合搞笑、吐槽、喜剧小说",
                "竖屏适合短视频平台",
                "镜头切换快速"
            )
        )
    )

    fun getBuiltInTemplates(): List<WorkflowTemplate> = builtInTemplates

    fun getTemplate(id: String): WorkflowTemplate? = builtInTemplates.find { it.id == id }

    fun getTemplatesByCategory(category: TemplateCategory): List<WorkflowTemplate> {
        return builtInTemplates.filter { it.category == category }
    }

    fun getTemplatesByDifficulty(difficulty: Difficulty): List<WorkflowTemplate> {
        return builtInTemplates.filter { it.difficulty == difficulty }
    }

    fun getCategories(): List<TemplateCategory> = TemplateCategory.values().toList()

    fun searchTemplates(query: String): List<WorkflowTemplate> {
        val lowerQuery = query.lowercase()
        return builtInTemplates.filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery) ||
                it.stylePreset.lowercase().contains(lowerQuery)
        }
    }

    fun getTemplateCategories(): Map<TemplateCategory, List<WorkflowTemplate>> {
        return builtInTemplates.groupBy { it.category }
    }

    fun applyTemplateToConfig(
        templateId: String,
        baseConfig: BookConversionConfig = BookConversionConfig()
    ): BookConversionConfig {
        val template = getTemplate(templateId) ?: return baseConfig
        return template.novelConfig
    }
}
