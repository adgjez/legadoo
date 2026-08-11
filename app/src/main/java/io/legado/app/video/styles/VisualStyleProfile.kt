package io.legado.app.video.styles

/**
 * VisualStyleProfile - 视觉风格档案
 *
 * 定义视频项目的视觉风格参数：
 * - 艺术风格（artStyle）：整体画风
 * - 色调（colorTone）：主色调倾向
 * - 细节等级（detailLevel）：0.0 ~ 1.0
 * - 电影光效（cinematicLighting）：是否启用电影级光照
 *
 * 这些参数会传递给图像/视频生成 API，
 * 确保风格在整个项目中的一致性。
 */

data class VisualStyleProfile(
    val styleName: String,
    val artStyle: String,
    val colorTone: String,
    val detailLevel: Float = 0.8f,
    val cinematicLighting: Boolean = false,
    val aspectRatio: String = "16:9",
    val resolution: String = "1080p",
    val additionalPrompts: List<String> = emptyList(),
    val negativePrompts: List<String> = defaultNegativePrompts
) {
    fun toPromptModifier(): String = buildString {
        append("Style: $artStyle. ")
        append("Color tone: $colorTone. ")
        append("Detail level: ${"%.1f".format(detailLevel)}. ")
        if (cinematicLighting) {
            append("Cinematic lighting, dramatic atmosphere. ")
        }
        if (additionalPrompts.isNotEmpty()) {
            append(additionalPrompts.joinToString(". "))
        }
    }

    fun toParamMap(): Map<String, Any> = mapOf(
        "style_name" to styleName,
        "art_style" to artStyle,
        "color_tone" to colorTone,
        "detail_level" to detailLevel,
        "cinematic_lighting" to cinematicLighting,
        "aspect_ratio" to aspectRatio,
        "resolution" to resolution
    )

    companion object {
        val defaultNegativePrompts = listOf(
            "low quality",
            "blurry",
            "distorted",
            "ugly",
            "deformed",
            "bad anatomy",
            "bad hands",
            "missing fingers",
            "extra fingers",
            "fused fingers",
            "poorly drawn face",
            "watermark",
            "signature",
            "text"
        )

        val PRESET_CINEMATIC = VisualStyleProfile(
            styleName = "Cinematic",
            artStyle = "cinematic_realism",
            colorTone = "teal_orange",
            detailLevel = 0.9f,
            cinematicLighting = true
        )

        val PRESET_ANIME = VisualStyleProfile(
            styleName = "Anime",
            artStyle = "anime_cel",
            colorTone = "vibrant_clean",
            detailLevel = 0.7f,
            cinematicLighting = false
        )

        val PRESET_PHOTO = VisualStyleProfile(
            styleName = "Photorealistic",
            artStyle = "photorealistic",
            colorTone = "natural_real",
            detailLevel = 1.0f,
            cinematicLighting = true
        )

        val PRESET_WATERCOLOR = VisualStyleProfile(
            styleName = "Watercolor",
            artStyle = "watercolor_painting",
            colorTone = "soft_pastel",
            detailLevel = 0.6f,
            cinematicLighting = false
        )
    }
}
