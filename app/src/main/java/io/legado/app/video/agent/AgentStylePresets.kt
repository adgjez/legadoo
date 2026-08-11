package io.legado.app.video.agent

data class StylePreset(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val visualStyle: String,
    val cameraStyle: String,
    val colorPalette: String,
    val lighting: String,
    val composition: String,
    val promptAdditions: List<String>,
    val negativePrompts: List<String>,
    val recommendedResolution: String = "1080p",
    val recommendedAspectRatio: String = "16:9"
)

object AgentStylePresets {

    val PRESETS = mapOf(
        "cinematic" to StylePreset(
            id = "cinematic",
            name = "电影风格",
            emoji = "🎬",
            description = "好莱坞电影质感，宽银幕构图，戏剧性光影",
            visualStyle = "cinematic, film look, movie quality",
            cameraStyle = "wide angle shots, dramatic angles, professional cinematography",
            colorPalette = "teal and orange color grading, film grade, rich colors",
            lighting = "dramatic lighting, chiaroscuro, rim lighting, golden hour",
            composition = "rule of thirds, leading lines, depth of field",
            promptAdditions = listOf(
                "shot on Arri Alexa",
                "35mm film look",
                "cinematic color grading",
                "anamorphic lens flare"
            ),
            negativePrompts = listOf("amateur", "phone camera", "flat lighting")
        ),
        "anime" to StylePreset(
            id = "anime",
            name = "动漫风格",
            emoji = "🎨",
            description = "日本动漫风格，手绘质感，鲜艳色彩",
            visualStyle = "anime style, cel shaded, hand-drawn, studio ghibli",
            cameraStyle = "dynamic angles, expressive close-ups, speed lines",
            colorPalette = "vibrant colors, saturated, bright highlights, pastel",
            lighting = "soft cel shading, rim light, dramatic shadows",
            composition = "dynamic framing, character-focused, action lines",
            promptAdditions = listOf(
                "anime key visual",
                "studio ghibli inspired",
                "makoto shinkai style",
                "ultra detailed anime"
            ),
            negativePrompts = listOf("realistic", "photographic", "3d render")
        ),
        "realistic" to StylePreset(
            id = "realistic",
            name = "写实风格",
            emoji = "📸",
            description = "照片级真实感，自然光照，细节丰富",
            visualStyle = "photorealistic, hyperrealistic, DSLR photography",
            cameraStyle = "natural perspective, realistic lens distortion",
            colorPalette = "natural colors, accurate skin tones, realistic",
            lighting = "natural lighting, ambient light, softbox",
            composition = "natural framing, documentary style",
            promptAdditions = listOf(
                "8k resolution",
                "photorealistic",
                "shot on Canon EOS R5",
                "ultra detailed skin texture"
            ),
            negativePrompts = listOf("cartoon", "anime", "painterly")
        ),
        "cyberpunk" to StylePreset(
            id = "cyberpunk",
            name = "赛博朋克",
            emoji = "🌆",
            description = "未来都市，霓虹灯光，反乌托邦氛围",
            visualStyle = "cyberpunk, neon noir, futuristic, sci-fi",
            cameraStyle = "low angle, dutch angle, wide shots of cityscapes",
            colorPalette = "neon pink, cyan, purple, high contrast",
            lighting = "neon lighting, holographic, rain reflections, backlighting",
            composition = "futuristic architecture, dense city, signage",
            promptAdditions = listOf(
                "cyberpunk 2077 style",
                "blade runner 2049 aesthetic",
                "neon lights, rain-soaked streets",
                "volumetric fog"
            ),
            negativePrompts = listOf("bright", "sunny day", "nature")
        ),
        "fantasy" to StylePreset(
            id = "fantasy",
            name = "奇幻风格",
            emoji = "✨",
            description = "魔幻奇幻，史诗氛围，神秘光影",
            visualStyle = "fantasy art, magical, ethereal, epic",
            cameraStyle = "wide landscape shots, aerial perspective, dramatic",
            colorPalette = "mystical blues, purples, golden glow, enchanted",
            lighting = "magical glow, god rays, ethereal, bioluminescent",
            composition = "epic landscapes, mystical architecture, floating elements",
            promptAdditions = listOf(
                "game of thrones aesthetic",
                "lord of the rings landscape",
                "enchanted forest",
                "magical realism"
            ),
            negativePrompts = listOf("modern", "urban", "technology")
        ),
        "wuxia" to StylePreset(
            id = "wuxia",
            name = "武侠风格",
            emoji = "⚔️",
            description = "中国传统武侠，水墨意境，飘逸动感",
            visualStyle = "wuxia, chinese ink painting, martial arts, elegant",
            cameraStyle = "flowing camera, wire work, dynamic movement",
            colorPalette = "ink black, white, red accent, muted tones",
            lighting = "dramatic natural light, moonlit, candle light",
            composition = "ink wash aesthetic, minimalist, dynamic poses",
            promptAdditions = listOf(
                "zhang yimou style",
                "hero (2002) aesthetic",
                "crouching tiger hidden dragon",
                "chinese ink painting style"
            ),
            negativePrompts = listOf("western", "modern", "cyberpunk")
        ),
        "comic" to StylePreset(
            id = "comic",
            name = "漫画风格",
            emoji = "💬",
            description = "美漫/日漫风格，分镜感，粗线条",
            visualStyle = "comic book, manga, graphic novel, bold lines",
            cameraStyle = "comic panel composition, dynamic angles, close-ups",
            colorPalette = "bold colors, halftone, saturated, ink outline",
            lighting = "dramatic shadows, bold outlines, screen tone",
            composition = "panel layout, speech bubbles, action lines",
            promptAdditions = listOf(
                "marvel comic style",
                "detailed comic illustration",
                "bold ink outlines",
                "dynamic action pose"
            ),
            negativePrompts = listOf("realistic", "photographic", "subtle")
        ),
        "documentary" to StylePreset(
            id = "documentary",
            name = "纪录片风格",
            emoji = "📹",
            description = "纪录片质感，手持摄影，真实氛围",
            visualStyle = "documentary, handheld, cinéma vérité, natural",
            cameraStyle = "handheld camera, documentary angles, candid",
            colorPalette = "natural, muted, documentary color grading",
            lighting = "natural light, available light, minimal equipment",
            composition = "documentary framing, realistic, observational",
            promptAdditions = listOf(
                "netflix documentary aesthetic",
                "POV documentary shot",
                "natural candid moment",
                "realistic handheld"
            ),
            negativePrompts = listOf("studio", "artificial lighting", "posed")
        )
    )

    fun getPreset(id: String): StylePreset = PRESETS[id] ?: PRESETS["cinematic"]!!

    fun getAllPresets(): List<StylePreset> = PRESETS.values.toList()

    fun getPresetPromptSuffix(styleId: String): String {
        val preset = getPreset(styleId)
        return buildString {
            appendLine()
            appendLine("【视觉风格】${preset.visualStyle}")
            appendLine("【镜头风格】${preset.cameraStyle}")
            appendLine("【色调】${preset.colorPalette}")
            appendLine("【光影】${preset.lighting}")
            appendLine("【构图】${preset.composition}")
            if (preset.promptAdditions.isNotEmpty()) {
                appendLine("【风格补充】${preset.promptAdditions.joinToString(", ")}")
            }
        }
    }

    fun getPresetNegativePrompt(styleId: String): String {
        val preset = getPreset(styleId)
        return preset.negativePrompts.joinToString(", ")
    }
}
