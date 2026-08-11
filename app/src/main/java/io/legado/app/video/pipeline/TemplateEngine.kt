package io.legado.app.video.pipeline

import io.legado.app.video.api.ProviderCapability
import io.legado.app.video.styles.VisualStyleProfile
import io.legado.app.video.tts.VoiceProfile

/**
 * TemplateEngine - 智能模板引擎
 *
 * 借鉴 ArcReel 的预设系统设计哲学：
 * - 降低创作门槛：一键选择模板即可开始创作
 * - 风格一致性：模板确保风格统一性
 * - 复用效率：成功配置可保存为模板
 * - 社区共享：支持模板导入导出
 *
 * 模板层次结构：
 * 1. 项目模板（ProjectTemplate）- 定义完整项目配置
 * 2. 风格预设（StylePreset）- 定义视觉/听觉风格
 * 3. 转场预设（TransitionPreset）- 定义场景转场序列
 */

// ========== 项目类型模板 ==========

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val sourceType: SourceType,
    val visualStyle: VisualStyleProfile,
    val narrationStyle: NarrationStyle,
    val targetResolution: String = "1080p",
    val targetAspectRatio: String = "16:9",
    val recommendedProviders: List<ProviderCapability> = emptyList(),
    val defaultSceneCount: Int = 10,
    val sceneDurationSeconds: Int = 5,
    val transitionPreset: String? = null,
    val bgmStyle: BgmStyle = BgmStyle.CINEMATIC,
    val voiceProfile: VoiceProfile? = null,
    val isBuiltIn: Boolean = false,
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList()
)

enum class TemplateCategory(val displayName: String) {
    NOVEL_ADAPTATION("小说改编"),
    COMIC_ADAPTATION("漫画改编"),
    ORIGINAL_STORY("原创故事"),
    DOCUMENTARY("纪录片"),
    EDUCATIONAL("教育科普"),
    MARKETING("营销宣传"),
    SOCIAL_MEDIA("社交媒体"),
    ENTERTAINMENT("娱乐短片")
}

enum class SourceType(val displayName: String) {
    NOVEL("小说文本"),
    COMIC_SCRIPT("漫画脚本"),
    IDEA("创意灵感"),
    ARTICLE("文章"),
    SCRIPT("剧本"),
    BLOG("博文")
}

enum class NarrationStyle(val displayName: String) {
    CINEMATIC("电影旁白"),
    DOCUMENTARY("纪录片解说"),
    VLOG("Vlog风格"),
    STORYBOOK("故事书风格"),
    NEWS("新闻播报"),
    POETIC("诗意旁白"),
    HUMOROUS("幽默解说"),
    ACADEMIC("学术讲解")
}

enum class BgmStyle(val displayName: String) {
    CINEMATIC("电影配乐"),
    AMBIENT("氛围音乐"),
    UPBEAT("活力节奏"),
    EMOTIONAL("情感丰富"),
    MINIMALIST("极简风格"),
    ELECTRONIC("电子音乐"),
    CLASSICAL("古典音乐"),
    FOLK("民谣风格"),
    NO_BGM("无背景音乐")
}

// ========== 风格预设 ==========

data class StylePreset(
    val id: String,
    val name: String,
    val visualStyle: VisualStyleProfile,
    val colorPalette: ColorPalette,
    val lightingStyle: LightingStyle,
    val compositionGuide: CompositionGuide,
    val recommendedProviders: List<ProviderCapability>,
    val description: String
)

data class ColorPalette(
    val primary: String,
    val secondary: String,
    val accent: String,
    val background: String,
    val mood: ColorMood
)

enum class ColorMood {
    WARM,
    COOL,
    NEUTRAL,
    HIGH_CONTRAST,
    PASTEL,
    MONOCHROME,
    VIBRANT,
    DESATURATED
}

enum class LightingStyle(val displayName: String) {
    NATURAL("自然光"),
    STUDIO("工作室灯光"),
    CINEMATIC("电影光效"),
    DRAMATIC("戏剧光"),
    SOFT("柔和光线"),
    HIGH_KEY("高调光"),
    LOW_KEY("低调光"),
    NEON("霓虹光效")
}

enum class CompositionGuide(val displayName: String) {
    RULE_OF_THIRDS("三分法则"),
    CENTERED("居中构图"),
    LEADING_LINES("引导线"),
    SYMMETRICAL("对称构图"),
    DIAGONAL("对角线构图"),
    CLOSE_UP("特写构图"),
    WIDE_SHOT("广角构图"),
    OVER_THE_SHOULDER("过肩构图")
}

// ========== 模板引擎 ==========

class TemplateEngine {

    companion object {
        private val builtInTemplates = listOf(
            // 小说改编 - 古装玄幻
            ProjectTemplate(
                id = "novel_fantasy_cn",
                name = "古装玄幻改编",
                description = "适合中国古代背景的玄幻、仙侠、武侠小说改编",
                category = TemplateCategory.NOVEL_ADAPTATION,
                sourceType = SourceType.NOVEL,
                visualStyle = VisualStyleProfile(
                    styleName = "Chinese Fantasy",
                    artStyle = "guofeng_wuxia",
                    colorTone = "warm_gold",
                    detailLevel = 0.9f,
                    cinematicLighting = true
                ),
                narrationStyle = NarrationStyle.CINEMATIC,
                targetResolution = "1080p",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 12,
                sceneDurationSeconds = 5,
                transitionPreset = "epic_fantasy",
                bgmStyle = BgmStyle.CINEMATIC,
                isBuiltIn = true,
                tags = listOf("古装", "玄幻", "武侠", "中国风")
            ),

            // 小说改编 - 都市现代
            ProjectTemplate(
                id = "novel_modern_urban",
                name = "都市现代改编",
                description = "适合现代都市背景的言情、职场、悬疑小说改编",
                category = TemplateCategory.NOVEL_ADAPTATION,
                sourceType = SourceType.NOVEL,
                visualStyle = VisualStyleProfile(
                    styleName = "Modern Urban",
                    artStyle = "cinematic_realism",
                    colorTone = "cool_blue",
                    detailLevel = 0.85f,
                    cinematicLighting = true
                ),
                narrationStyle = NarrationStyle.CINEMATIC,
                targetResolution = "1080p",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 10,
                sceneDurationSeconds = 5,
                transitionPreset = "urban_sleek",
                bgmStyle = BgmStyle.EMOTIONAL,
                isBuiltIn = true,
                tags = listOf("都市", "现代", "言情")
            ),

            // 小说改编 - 悬疑推理
            ProjectTemplate(
                id = "novel_mystery",
                name = "悬疑推理改编",
                description = "适合侦探推理、犯罪悬疑小说改编",
                category = TemplateCategory.NOVEL_ADAPTATION,
                sourceType = SourceType.NOVEL,
                visualStyle = VisualStyleProfile(
                    styleName = "Noir Mystery",
                    artStyle = "noir_cinema",
                    colorTone = "desaturated",
                    detailLevel = 0.9f,
                    cinematicLighting = true
                ),
                narrationStyle = NarrationStyle.DOCUMENTARY,
                targetResolution = "1080p",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 8,
                sceneDurationSeconds = 5,
                transitionPreset = "mystery_intense",
                bgmStyle = BgmStyle.MINIMALIST,
                isBuiltIn = true,
                tags = listOf("悬疑", "推理", "犯罪")
            ),

            // 原创故事 - 温馨治愈
            ProjectTemplate(
                id = "original_healing",
                name = "温馨治愈原创",
                description = "适合温馨日常、治愈系原创故事",
                category = TemplateCategory.ORIGINAL_STORY,
                sourceType = SourceType.IDEA,
                visualStyle = VisualStyleProfile(
                    styleName = "Healing Warm",
                    artStyle = "anime_soft",
                    colorTone = "warm_pastel",
                    detailLevel = 0.75f,
                    cinematicLighting = false
                ),
                narrationStyle = NarrationStyle.STORYBOOK,
                targetResolution = "1080p",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION
                ),
                defaultSceneCount = 8,
                sceneDurationSeconds = 4,
                transitionPreset = "gentle_smooth",
                bgmStyle = BgmStyle.AMBIENT,
                isBuiltIn = true,
                tags = listOf("温馨", "治愈", "日常")
            ),

            // 社交媒体 - 知识科普
            ProjectTemplate(
                id = "edu_knowledge",
                name = "知识科普短片",
                description = "适合科普知识、教育内容的短视频制作",
                category = TemplateCategory.EDUCATIONAL,
                sourceType = SourceType.ARTICLE,
                visualStyle = VisualStyleProfile(
                    styleName = "Educational Clean",
                    artStyle = "clean_infographic",
                    colorTone = "bright_clear",
                    detailLevel = 0.7f,
                    cinematicLighting = false
                ),
                narrationStyle = NarrationStyle.ACADEMIC,
                targetResolution = "1080p",
                targetAspectRatio = "9:16",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 6,
                sceneDurationSeconds = 6,
                transitionPreset = "quick_cut",
                bgmStyle = BgmStyle.UPBEAT,
                isBuiltIn = true,
                tags = listOf("科普", "教育", "知识")
            ),

            // 营销宣传 - 产品展示
            ProjectTemplate(
                id = "marketing_product",
                name = "产品营销宣传",
                description = "适合产品展示、品牌宣传的营销视频",
                category = TemplateCategory.MARKETING,
                sourceType = SourceType.IDEA,
                visualStyle = VisualStyleProfile(
                    styleName = "Product Commercial",
                    artStyle = "commercial_polished",
                    colorTone = "brand_bright",
                    detailLevel = 0.95f,
                    cinematicLighting = true
                ),
                narrationStyle = NarrationStyle.CINEMATIC,
                targetResolution = "1080p",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 6,
                sceneDurationSeconds = 5,
                transitionPreset = "brand_sleek",
                bgmStyle = BgmStyle.ELECTRONIC,
                isBuiltIn = true,
                tags = listOf("营销", "产品", "品牌")
            ),

            // 社交媒体 - 短视频竖屏
            ProjectTemplate(
                id = "social_vertical",
                name = "竖屏短视频",
                description = "适合抖音、快手等竖屏短视频平台",
                category = TemplateCategory.SOCIAL_MEDIA,
                sourceType = SourceType.IDEA,
                visualStyle = VisualStyleProfile(
                    styleName = "Social Vertical",
                    artStyle = "vibrant_dynamic",
                    colorTone = "saturated_vibrant",
                    detailLevel = 0.8f,
                    cinematicLighting = false
                ),
                narrationStyle = NarrationStyle.HUMOROUS,
                targetResolution = "1080p",
                targetAspectRatio = "9:16",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 4,
                sceneDurationSeconds = 5,
                transitionPreset = "dynamic_cut",
                bgmStyle = BgmStyle.UPBEAT,
                isBuiltIn = true,
                tags = listOf("短视频", "竖屏", "社交")
            ),

            // 纪录片 - 自然人文
            ProjectTemplate(
                id = "doc_nature",
                name = "自然人文纪录片",
                description = "适合自然风光、人文历史纪录片制作",
                category = TemplateCategory.DOCUMENTARY,
                sourceType = SourceType.ARTICLE,
                visualStyle = VisualStyleProfile(
                    styleName = "Nature Documentary",
                    artStyle = "photorealistic",
                    colorTone = "natural_real",
                    detailLevel = 1.0f,
                    cinematicLighting = true
                ),
                narrationStyle = NarrationStyle.DOCUMENTARY,
                targetResolution = "4K",
                targetAspectRatio = "16:9",
                recommendedProviders = listOf(
                    ProviderCapability.IMAGE_GENERATION,
                    ProviderCapability.VIDEO_GENERATION
                ),
                defaultSceneCount = 15,
                sceneDurationSeconds = 8,
                transitionPreset = "nature_flow",
                bgmStyle = BgmStyle.AMBIENT,
                isBuiltIn = true,
                tags = listOf("纪录片", "自然", "人文")
            )
        )

        private val stylePresets = listOf(
            StylePreset(
                id = "style_cinematic",
                name = "电影感",
                visualStyle = VisualStyleProfile(
                    styleName = "Cinematic",
                    artStyle = "cinematic_realism",
                    colorTone = "teal_orange",
                    detailLevel = 0.9f,
                    cinematicLighting = true
                ),
                colorPalette = ColorPalette(
                    primary = "#0d4f6b",
                    secondary = "#e68c3c",
                    accent = "#ffd700",
                    background = "#1a1a2e",
                    mood = ColorMood.WARM
                ),
                lightingStyle = LightingStyle.CINEMATIC,
                compositionGuide = CompositionGuide.RULE_OF_THIRDS,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION),
                description = "好莱坞级电影感色调与构图"
            ),

            StylePreset(
                id = "style_anime",
                name = "动画风格",
                visualStyle = VisualStyleProfile(
                    styleName = "Anime",
                    artStyle = "anime_cel",
                    colorTone = "vibrant_clean",
                    detailLevel = 0.7f,
                    cinematicLighting = false
                ),
                colorPalette = ColorPalette(
                    primary = "#ff6b9d",
                    secondary = "#4ecdc4",
                    accent = "#ffe66d",
                    background = "#f7f7f7",
                    mood = ColorMood.VIBRANT
                ),
                lightingStyle = LightingStyle.SOFT,
                compositionGuide = CompositionGuide.CENTERED,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION),
                description = "日系动画赛璐璐风格"
            ),

            StylePreset(
                id = "style_noir",
                name = "黑白复古",
                visualStyle = VisualStyleProfile(
                    styleName = "Film Noir",
                    artStyle = "black_white_high_contrast",
                    colorTone = "monochrome",
                    detailLevel = 0.85f,
                    cinematicLighting = true
                ),
                colorPalette = ColorPalette(
                    primary = "#2c2c2c",
                    secondary = "#6b6b6b",
                    accent = "#c0c0c0",
                    background = "#0a0a0a",
                    mood = ColorMood.MONOCHROME
                ),
                lightingStyle = LightingStyle.DRAMATIC,
                compositionGuide = CompositionGuide.DIAGONAL,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION),
                description = "经典黑色电影风格"
            ),

            StylePreset(
                id = "style_watercolor",
                name = "水彩艺术",
                visualStyle = VisualStyleProfile(
                    styleName = "Watercolor Art",
                    artStyle = "watercolor_painting",
                    colorTone = "soft_pastel",
                    detailLevel = 0.6f,
                    cinematicLighting = false
                ),
                colorPalette = ColorPalette(
                    primary = "#a8d8ea",
                    secondary = "#aa96da",
                    accent = "#fcbad3",
                    background = "#ffffd2",
                    mood = ColorMood.PASTEL
                ),
                lightingStyle = LightingStyle.SOFT,
                compositionGuide = CompositionGuide.CENTERED,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION),
                description = "水彩画艺术风格"
            ),

            StylePreset(
                id = "style_3d_render",
                name = "3D渲染",
                visualStyle = VisualStyleProfile(
                    styleName = "3D Render",
                    artStyle = "3d_octane_render",
                    colorTone = "hyper_real",
                    detailLevel = 1.0f,
                    cinematicLighting = true
                ),
                colorPalette = ColorPalette(
                    primary = "#00d4ff",
                    secondary = "#ff00aa",
                    accent = "#ffee00",
                    background = "#0a0a1e",
                    mood = ColorMood.HIGH_CONTRAST
                ),
                lightingStyle = LightingStyle.STUDIO,
                compositionGuide = CompositionGuide.CENTERED,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION, ProviderCapability.VIDEO_GENERATION),
                description = "高质量3D渲染效果"
            ),

            StylePreset(
                id = "style_docs",
                name = "纪录片风",
                visualStyle = VisualStyleProfile(
                    styleName = "Documentary",
                    artStyle = "documentary_realism",
                    colorTone = "natural_real",
                    detailLevel = 0.95f,
                    cinematicLighting = false
                ),
                colorPalette = ColorPalette(
                    primary = "#556b2f",
                    secondary = "#8b7355",
                    accent = "#d2b48c",
                    background = "#2f2f2f",
                    mood = ColorMood.NEUTRAL
                ),
                lightingStyle = LightingStyle.NATURAL,
                compositionGuide = CompositionGuide.RULE_OF_THIRDS,
                recommendedProviders = listOf(ProviderCapability.IMAGE_GENERATION, ProviderCapability.VIDEO_GENERATION),
                description = "真实纪录片影像风格"
            )
        )

        fun getBuiltInTemplates(): List<ProjectTemplate> = builtInTemplates
        fun getStylePresets(): List<StylePreset> = stylePresets

        fun getTemplateById(id: String): ProjectTemplate? =
            builtInTemplates.find { it.id == id }

        fun getTemplatesByCategory(category: TemplateCategory): List<ProjectTemplate> =
            builtInTemplates.filter { it.category == category }

        fun getStylePresetById(id: String): StylePreset? =
            stylePresets.find { it.id == id }

        fun searchTemplates(query: String): List<ProjectTemplate> {
            val lower = query.lowercase()
            return builtInTemplates.filter {
                it.name.lowercase().contains(lower) ||
                it.description.lowercase().contains(lower) ||
                it.tags.any { tag -> tag.lowercase().contains(lower) }
            }
        }

        fun getCategories(): List<TemplateCategory> = TemplateCategory.values().toList()

        fun getTemplatesByProviderCapability(capability: ProviderCapability): List<ProjectTemplate> =
            builtInTemplates.filter { capability in it.recommendedProviders }
    }

    fun applyTemplate(
        template: ProjectTemplate,
        projectId: String,
        projectName: String? = null
    ): TemplateApplyResult {
        val finalName = projectName ?: "${template.name} 项目"

        return TemplateApplyResult(
            projectName = finalName,
            sourceType = template.sourceType,
            visualStyle = template.visualStyle,
            narrationStyle = template.narrationStyle,
            resolution = template.targetResolution,
            aspectRatio = template.targetAspectRatio,
            defaultSceneCount = template.defaultSceneCount,
            sceneDurationSeconds = template.sceneDurationSeconds,
            transitionPreset = template.transitionPreset,
            bgmStyle = template.bgmStyle,
            recommendedProviders = template.recommendedProviders,
            templateId = template.id
        )
    }

    fun applyStylePreset(
        preset: StylePreset,
        baseTemplate: ProjectTemplate
    ): ProjectTemplate {
        return baseTemplate.copy(
            visualStyle = preset.visualStyle
        )
    }
}

data class TemplateApplyResult(
    val projectName: String,
    val sourceType: SourceType,
    val visualStyle: VisualStyleProfile,
    val narrationStyle: NarrationStyle,
    val resolution: String,
    val aspectRatio: String,
    val defaultSceneCount: Int,
    val sceneDurationSeconds: Int,
    val transitionPreset: String?,
    val bgmStyle: BgmStyle,
    val recommendedProviders: List<ProviderCapability>,
    val templateId: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "templateId" to templateId,
        "projectName" to projectName,
        "sourceType" to sourceType.name,
        "visualStyle" to visualStyle.styleName,
        "narrationStyle" to narrationStyle.name,
        "resolution" to resolution,
        "aspectRatio" to aspectRatio,
        "defaultSceneCount" to defaultSceneCount,
        "sceneDurationSeconds" to sceneDurationSeconds,
        "transitionPreset" to transitionPreset,
        "bgmStyle" to bgmStyle.name,
        "recommendedProviders" to recommendedProviders.map { it.name }
    )
}
