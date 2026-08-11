package io.legado.app.video.pipeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SceneTransitionEngine - 场景转场引擎
 *
 * 借鉴 ArcReel 的视觉转场系统：
 * - 多种转场效果：淡入淡出、溶解、滑动、缩放、旋转、闪光
 * - 智能转场选择：根据场景内容自动推荐
 * - 转场参数化：时长、强度、方向、颜色
 * - 批量转场处理：一次处理多个转场
 */

// ========== 转场效果 ==========

enum class TransitionType(val displayName: String, val defaultDurationMs: Long) {
    NONE("无转场", 0L),
    FADE("淡入淡出", 500L),
    DISSOLVE("溶解", 800L),
    SLIDE_LEFT("左滑", 400L),
    SLIDE_RIGHT("右滑", 400L),
    SLIDE_UP("上滑", 400L),
    SLIDE_DOWN("下滑", 400L),
    ZOOM_IN("放大进入", 600L),
    ZOOM_OUT("缩小退出", 600L),
    ROTATE("旋转", 800L),
    WIPE("擦除", 500L),
    FLASH("闪光", 200L),
    FLASH_WHITE("白闪", 200L),
    FLASH_BLACK("黑闪", 200L),
    BLUR("模糊", 400L),
    GLITCH("故障效果", 300L),
    MORPH("变形", 1000L),
    CUT("硬切", 0L),
    CROSS_FADE("交叉淡入淡出", 500L),
    PUSH("推入", 400L),
    SPLIT("分裂", 600L),
    PAGE_TURN("翻页", 800L),
    ENERGY("能量波", 500L),
    MATCH_CUT("匹配剪辑", 0L),
    JUMP_CUT("跳切", 0L),
    L_CUT("L型剪辑", 0L),
    J_CUT("J型剪辑", 0L),
    IRIS_IN("圆形展开", 500L),
    IRIS_OUT("圆形收缩", 500L)
}

data class TransitionConfig(
    val type: TransitionType,
    val durationMs: Long,
    val intensity: Float = 1.0f,
    val direction: TransitionDirection = TransitionDirection.AUTO,
    val color: String? = null,
    val easing: EasingFunction = EasingFunction.EASE_IN_OUT,
    val audioSync: Boolean = true
)

enum class TransitionDirection {
    AUTO,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
    CENTER_OUT,
    EDGE_IN
}

enum class EasingFunction {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    SPRING,
    BOUNCE,
    CUBIC_BEZIER
}

// ========== 转场推荐引擎 ==========

class SceneTransitionEngine {

    /**
     * 根据场景内容自动推荐转场效果
     */
    suspend fun recommendTransition(
        fromScene: SceneContext,
        toScene: SceneContext,
        previousTransition: TransitionType? = null
    ): TransitionConfig = withContext(Dispatchers.Default) {
        val recommendation = analyzeSceneChange(fromScene, toScene)

        val type = when {
            recommendation.isMajorTimeSkip -> TransitionType.DISSOLVE
            recommendation.isLocationChange && !recommendation.isEmotionalShift -> TransitionType.SLIDE_LEFT
            recommendation.isEmotionalShift -> TransitionType.CROSS_FADE
            recommendation.isActionIntensityIncrease -> TransitionType.ZOOM_IN
            recommendation.isActionIntensityDecrease -> TransitionType.FADE
            recommendation.isMoodShift -> TransitionType.DISSOLVE
            recommendation.isCharacterChange -> TransitionType.WIPE
            recommendation.isSmoothContinuity -> TransitionType.CUT
            else -> TransitionType.CROSS_FADE
        }

        val duration = when (type) {
            TransitionType.CUT -> 0L
            TransitionType.FLASH, TransitionType.GLITCH -> 200L
            TransitionType.FADE, TransitionType.CROSS_FADE -> 500L
            TransitionType.DISSOLVE -> 800L
            TransitionType.MORPH, TransitionType.PAGE_TURN -> 1000L
            else -> type.defaultDurationMs
        }

        TransitionConfig(
            type = type,
            durationMs = duration,
            intensity = recommendation.suggestedIntensity,
            direction = suggestDirection(fromScene, toScene),
            easing = EasingFunction.EASE_IN_OUT
        )
    }

    private fun analyzeSceneChange(from: SceneContext, to: SceneContext): SceneAnalysis {
        val timeGap = estimateTimeGap(from.timeMarker, to.timeMarker)
        val locationChanged = from.location != to.location
        val charactersChanged = from.mainCharacters != to.mainCharacters
        val moodShift = from.mood != to.mood && from.mood.isNotBlank() && to.mood.isNotBlank()
        val intensityChange = to.actionIntensity - from.actionIntensity

        return SceneAnalysis(
            isMajorTimeSkip = timeGap > 300,
            isLocationChange = locationChanged,
            isEmotionalShift = moodShift,
            isActionIntensityIncrease = intensityChange > 0.2f,
            isActionIntensityDecrease = intensityChange < -0.2f,
            isMoodShift = moodShift,
            isCharacterChange = charactersChanged,
            isSmoothContinuity = timeGap <= 5 && !locationChanged && !charactersChanged,
            suggestedIntensity = when {
                intensityChange > 0.3f -> 1.2f
                intensityChange < -0.3f -> 0.6f
                moodShift -> 0.8f
                locationChanged -> 0.9f
                else -> 0.5f
            }
        )
    }

    private fun estimateTimeGap(from: String?, to: String?): Long {
        if (from == null || to == null) return 0L
        return try {
            val fromMinutes = parseTimeMarker(from)
            val toMinutes = parseTimeMarker(to)
            (toMinutes - fromMinutes).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseTimeMarker(time: String): Long {
        val parts = time.split(":", " ", "点", "时", "分")
        return parts.filter { it.isNumeric() }.map { it.toLong() }.let { nums ->
            when {
                nums.size >= 2 -> nums[0] * 60 + nums[1]
                nums.size == 1 -> nums[0] * 60
                else -> 0L
            }
        }
    }

    private fun suggestDirection(from: SceneContext, to: SceneContext): TransitionDirection {
        return when {
            to.cameraMovement.contains("left") -> TransitionDirection.LEFT_TO_RIGHT
            to.cameraMovement.contains("right") -> TransitionDirection.RIGHT_TO_LEFT
            to.cameraMovement.contains("up") -> TransitionDirection.TOP_TO_BOTTOM
            to.cameraMovement.contains("down") -> TransitionDirection.BOTTOM_TO_TOP
            else -> TransitionDirection.AUTO
        }
    }

    /**
     * 批量生成转场计划
     */
    suspend fun planTransitions(
        scenes: List<SceneContext>,
        defaultConfig: TransitionConfig? = null
    ): List<TransitionPlan> = withContext(Dispatchers.Default) {
        if (scenes.size < 2) return@withContext emptyList()

        val plans = mutableListOf<TransitionPlan>()

        for (i in 0 until scenes.size - 1) {
            val from = scenes[i]
            val to = scenes[i + 1]

            val config = defaultConfig ?: recommendTransition(from, to)

            plans.add(
                TransitionPlan(
                    fromSceneIndex = i,
                    toSceneIndex = i + 1,
                    config = config,
                    reason = explainChoice(from, to, config.type),
                    estimatedDurationMs = config.durationMs + 300
                )
            )
        }

        plans
    }

    private fun explainChoice(from: SceneContext, to: SceneContext, type: TransitionType): String {
        return when (type) {
            TransitionType.DISSOLVE -> "时间/场景跨度较大，使用溶解过渡"
            TransitionType.SLIDE_LEFT -> "地点切换，使用滑动过渡"
            TransitionType.CROSS_FADE -> "情绪转换，使用交叉淡入淡出"
            TransitionType.ZOOM_IN -> "紧张感增强，使用放大进入"
            TransitionType.FADE -> "紧张感减弱，使用淡入淡出"
            TransitionType.WIPE -> "主要角色替换，使用擦除效果"
            TransitionType.CUT -> "场景连续，使用硬切"
            TransitionType.FLASH -> "强调冲击感，使用闪光效果"
            TransitionType.MORPH -> "奇幻/变形场景，使用变形转场"
            else -> "默认转场"
        }
    }
}

data class SceneContext(
    val sceneId: String,
    val index: Int,
    val location: String = "",
    val timeMarker: String? = null,
    val mood: String = "",
    val mainCharacters: List<String> = emptyList(),
    val actionIntensity: Float = 0.5f,
    val cameraMovement: String = "",
    val description: String = ""
)

data class SceneAnalysis(
    val isMajorTimeSkip: Boolean,
    val isLocationChange: Boolean,
    val isEmotionalShift: Boolean,
    val isActionIntensityIncrease: Boolean,
    val isActionIntensityDecrease: Boolean,
    val isMoodShift: Boolean,
    val isCharacterChange: Boolean,
    val isSmoothContinuity: Boolean,
    val suggestedIntensity: Float
)

data class TransitionPlan(
    val fromSceneIndex: Int,
    val toSceneIndex: Int,
    val config: TransitionConfig,
    val reason: String,
    val estimatedDurationMs: Long
)

/**
 * 转场预设 - 常用场景转场组合
 */

object TransitionPresets {

    val CINEMATIC_DRAMA = listOf(
        TransitionConfig(TransitionType.CROSS_FADE, durationMs = 600L, intensity = 0.8f),
        TransitionConfig(TransitionType.DISSOLVE, durationMs = 800L, intensity = 0.7f),
        TransitionConfig(TransitionType.FADE, durationMs = 500L, intensity = 0.6f)
    )

    val ACTION_THRILLER = listOf(
        TransitionConfig(TransitionType.CUT, durationMs = 0L, intensity = 1.0f),
        TransitionConfig(TransitionType.FLASH, durationMs = 200L, intensity = 1.0f),
        TransitionConfig(TransitionType.ZOOM_IN, durationMs = 400L, intensity = 1.2f)
    )

    val ROMANCE = listOf(
        TransitionConfig(TransitionType.DISSOLVE, durationMs = 1000L, intensity = 0.6f),
        TransitionConfig(TransitionType.CROSS_FADE, durationMs = 700L, intensity = 0.5f),
        TransitionConfig(TransitionType.FADE, durationMs = 800L, intensity = 0.4f)
    )

    val COMEDY = listOf(
        TransitionConfig(TransitionType.SLIDE_LEFT, durationMs = 300L, intensity = 0.9f),
        TransitionConfig(TransitionType.WIPE, durationMs = 400L, intensity = 1.0f),
        TransitionConfig(TransitionType.BLUR, durationMs = 200L, intensity = 0.7f)
    )

    val FANTASY = listOf(
        TransitionConfig(TransitionType.MORPH, durationMs = 1200L, intensity = 1.0f),
        TransitionConfig(TransitionType.ENERGY, durationMs = 600L, intensity = 1.2f),
        TransitionConfig(TransitionType.GLITCH, durationMs = 300L, intensity = 1.0f)
    )

    val DOCUMENTARY = listOf(
        TransitionConfig(TransitionType.CUT, durationMs = 0L, intensity = 1.0f),
        TransitionConfig(TransitionType.FADE, durationMs = 400L, intensity = 0.3f)
    )

    val MUSIC_VIDEO = listOf(
        TransitionConfig(TransitionType.CROSS_FADE, durationMs = 400L, intensity = 1.0f),
        TransitionConfig(TransitionType.ZOOM_IN, durationMs = 300L, intensity = 1.1f),
        TransitionConfig(TransitionType.FLASH, durationMs = 150L, intensity = 1.0f)
    )

    val presets = mapOf(
        "cinematic_drama" to CINEMATIC_DRAMA,
        "action_thriller" to ACTION_THRILLER,
        "romance" to ROMANCE,
        "comedy" to COMEDY,
        "fantasy" to FANTASY,
        "documentary" to DOCUMENTARY,
        "music_video" to MUSIC_VIDEO
    )

    fun getPreset(key: String): List<TransitionConfig>? = presets[key]

    fun listPresets(): List<String> = presets.keys.toList()

    fun randomFromPreset(key: String): TransitionConfig? {
        val preset = presets[key] ?: return null
        return preset.random()
    }
}
