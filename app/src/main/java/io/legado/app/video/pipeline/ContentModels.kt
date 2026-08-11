package io.legado.app.video.pipeline

enum class ContentMode {
    NARRATION,
    DRAMA
}

data class NarrationSegment(
    val segmentId: String,
    val index: Int,
    val novelText: String,
    val readingDuration: Int,
    val imagePrompt: String? = null,
    val videoPrompt: String? = null,
    val referencedCharacters: List<String> = emptyList(),
    val referencedClues: List<String> = emptyList(),
    val status: SegmentStatus = SegmentStatus.PENDING
)

data class DramaUtterance(
    val utteranceId: String,
    val index: Int,
    val speaker: String?,
    val dialogue: String?,
    val action: String?,
    val sceneDescription: String?,
    val imagePrompt: String? = null,
    val videoPrompt: String? = null,
    val referencedCharacters: List<String> = emptyList(),
    val referencedClues: List<String> = emptyList(),
    val status: SegmentStatus = SegmentStatus.PENDING
)

enum class SegmentStatus {
    PENDING,
    CONTENT_READY,
    VISUAL_READY,
    APPROVED,
    GENERATING,
    COMPLETED,
    FAILED
}

data class NarrationScript(
    val scriptId: String,
    val episodeId: String,
    val mode: ContentMode = ContentMode.NARRATION,
    val segments: List<NarrationSegment>,
    val stage: ScriptStage,
    val totalSegments: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withVisualPrompts(prompts: Map<String, Pair<String?, String?>>): NarrationScript {
        return copy(
            segments = segments.map { seg ->
                val (img, vid) = prompts[seg.segmentId] ?: (null to null)
                seg.copy(imagePrompt = img, videoPrompt = vid, status = SegmentStatus.VISUAL_READY)
            },
            stage = ScriptStage.VISUAL_STAGE_COMPLETE,
            updatedAt = System.currentTimeMillis()
        )
    }
}

data class DramaScript(
    val scriptId: String,
    val episodeId: String,
    val mode: ContentMode = ContentMode.DRAMA,
    val utterances: List<DramaUtterance>,
    val stage: ScriptStage,
    val totalScenes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withVisualPrompts(prompts: Map<String, Pair<String?, String?>>): DramaScript {
        return copy(
            utterances = utterances.map { utt ->
                val (img, vid) = prompts[utt.utteranceId] ?: (null to null)
                utt.copy(imagePrompt = img, videoPrompt = vid, status = SegmentStatus.VISUAL_READY)
            },
            stage = ScriptStage.VISUAL_STAGE_COMPLETE,
            updatedAt = System.currentTimeMillis()
        )
    }
}

enum class ScriptStage {
    CHARACTER_ANALYSIS,
    CHARACTER_DESIGN,
    CHARACTER_LOCKED,
    CLUE_DESIGN,
    CLUE_LOCKED,
    SCRIPT_CONTENT_STAGE,
    SCRIPT_CONTENT_READY,
    SCRIPT_VISUAL_STAGE,
    VISUAL_STAGE_COMPLETE,
    REVIEW_GATE,
    STORYBOARD_GENERATION,
    VIDEO_GENERATION,
    ASSEMBLY,
    COMPLETE
}

data class ScriptState(
    val projectId: String,
    val currentStage: ScriptStage,
    val history: List<ScriptStage> = emptyList(),
    val approvedByUser: Boolean = false,
    val checkpoints: Map<ScriptStage, Long> = emptyMap()
) {
    fun advanceTo(newStage: ScriptStage, approved: Boolean = false): ScriptState {
        return copy(
            currentStage = newStage,
            history = history + currentStage,
            approvedByUser = approved,
            checkpoints = checkpoints + (newStage to System.currentTimeMillis())
        )
    }

    fun canAdvance(): Boolean = when (currentStage) {
        ScriptStage.CHARACTER_ANALYSIS -> true
        ScriptStage.CHARACTER_DESIGN -> AssetLibraryManager.isReady(projectId)
        ScriptStage.CHARACTER_LOCKED -> true
        ScriptStage.CLUE_DESIGN -> AssetLibraryManager.isReady(projectId)
        ScriptStage.CLUE_LOCKED -> true
        ScriptStage.SCRIPT_CONTENT_STAGE -> approvedByUser
        ScriptStage.SCRIPT_CONTENT_READY -> true
        ScriptStage.SCRIPT_VISUAL_STAGE -> approvedByUser
        ScriptStage.VISUAL_STAGE_COMPLETE -> true
        ScriptStage.REVIEW_GATE -> approvedByUser
        ScriptStage.STORYBOARD_GENERATION -> true
        ScriptStage.VIDEO_GENERATION -> true
        ScriptStage.ASSEMBLY -> true
        ScriptStage.COMPLETE -> false
    }

    fun isAtReviewGate(): Boolean = currentStage == ScriptStage.REVIEW_GATE
}
