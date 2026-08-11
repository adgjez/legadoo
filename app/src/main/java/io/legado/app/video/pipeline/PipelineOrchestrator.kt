package io.legado.app.video.pipeline

import android.content.Context
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.ImageGenerationRequest
import io.legado.app.video.api.VideoGenerationRequest
import io.legado.app.video.api.TextGenerationRequest
import io.legado.app.video.api.TextBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PipelineOrchestrator(private val context: Context) {

    private val _state = MutableStateFlow<ScriptState?>(null)
    val state: StateFlow<ScriptState?> = _state.asStateFlow()

    private val _narrationScript = MutableStateFlow<NarrationScript?>(null)
    val narrationScript: StateFlow<NarrationScript?> = _narrationScript.asStateFlow()

    private val _dramaScript = MutableStateFlow<DramaScript?>(null)
    val dramaScript: StateFlow<DramaScript?> = _dramaScript.asStateFlow()

    private val _assetLibrary = MutableStateFlow<AssetLibrary?>(null)
    val assetLibrary: StateFlow<AssetLibrary?> = _assetLibrary.asStateFlow()

    private val pipelineEngine = TwoStagePipelineEngine()
    private val characterAnalyzer = CharacterAnalyzer(context)

    suspend fun initialize(projectId: String) {
        _state.value = ScriptState(
            projectId = projectId,
            currentStage = ScriptStage.CHARACTER_ANALYSIS
        )
        AssetLibraryManager.clear(projectId)
        VersionManager.init(context)
    }

    suspend fun analyzeCharacters(projectId: String, novelText: String) {
        _state.value = ScriptState(projectId, ScriptStage.CHARACTER_ANALYSIS)

        val result = characterAnalyzer.analyze(novelText)

        result.getOrNull()?.let { characters ->
            characters.forEach { character ->
                AssetLibraryManager.addCharacter(projectId, character)
            }
            val library = AssetLibraryManager.getLibrary(projectId)
            _assetLibrary.value = library

            _state.value = _state.value?.advanceTo(ScriptStage.CHARACTER_DESIGN)
        }
    }

    suspend fun generateCharacterDesigns(projectId: String) {
        val library = AssetLibraryManager.getLibrary(projectId) ?: return
        val characters = library.characters.filter { !it.locked }

        for (character in characters) {
            val result = characterAnalyzer.generateDesignSheet(character)
            result.getOrNull()?.let { updated ->
                AssetLibraryManager.addCharacter(projectId, updated)
            }
        }

        _state.value = _state.value?.advanceTo(ScriptStage.CHARACTER_LOCKED)
        _assetLibrary.value = AssetLibraryManager.getLibrary(projectId)
    }

    suspend fun lockCharacter(projectId: String, characterId: String) {
        AssetLibraryManager.lockCharacter(projectId, characterId)
        _assetLibrary.value = AssetLibraryManager.getLibrary(projectId)
    }

    suspend fun generateScriptContent(
        projectId: String,
        novelText: String,
        mode: ContentMode,
        segmentCount: Int
    ) {
        _state.value = _state.value?.advanceTo(ScriptStage.SCRIPT_CONTENT_STAGE)

        when (mode) {
            ContentMode.NARRATION -> {
                val result = pipelineEngine.generateContentStage(projectId, novelText, mode, segmentCount)
                result.getOrNull()?.let { script ->
                    _narrationScript.value = script as NarrationScript
                    _state.value = _state.value?.advanceTo(ScriptStage.SCRIPT_CONTENT_READY, approved = false)
                }
            }
            ContentMode.DRAMA -> {
                val result = pipelineEngine.generateContentStage(projectId, novelText, mode, segmentCount)
                result.getOrNull()?.let { script ->
                    _dramaScript.value = script as DramaScript
                    _state.value = _state.value?.advanceTo(ScriptStage.SCRIPT_CONTENT_READY, approved = false)
                }
            }
        }
    }

    fun approveContentStage() {
        _state.value = _state.value?.advanceTo(ScriptStage.SCRIPT_VISUAL_STAGE, approved = true)
    }

    suspend fun generateScriptVisuals(projectId: String) {
        _state.value = _state.value?.advanceTo(ScriptStage.SCRIPT_VISUAL_STAGE)

        val library = AssetLibraryManager.getLibrary(projectId)

        _narrationScript.value?.let { script ->
            val result = pipelineEngine.generateVisualStage(projectId, script, library)
            result.getOrNull()?.let { prompts ->
                val updated = script.withVisualPrompts(prompts)
                _narrationScript.value = updated
                _state.value = _state.value?.advanceTo(ScriptStage.VISUAL_STAGE_COMPLETE, approved = false)
            }
        }

        _dramaScript.value?.let { script ->
            val result = pipelineEngine.generateVisualStage(projectId, script, library)
            result.getOrNull()?.let { prompts ->
                val updated = script.withVisualPrompts(prompts)
                _dramaScript.value = updated
                _state.value = _state.value?.advanceTo(ScriptStage.VISUAL_STAGE_COMPLETE, approved = false)
            }
        }
    }

    fun approveVisualStage() {
        _state.value = _state.value?.advanceTo(ScriptStage.REVIEW_GATE, approved = true)
    }

    suspend fun generateStoryboards(projectId: String) {
        _state.value = _state.value?.advanceTo(ScriptStage.STORYBOARD_GENERATION)

        val library = AssetLibraryManager.getLibrary(projectId)

        _narrationScript.value?.let { script ->
            for (segment in script.segments.filter { it.status == SegmentStatus.VISUAL_READY }) {
                val refs = AssetLibraryManager.getReferenceImagesForScene(
                    projectId,
                    segment.referencedCharacters,
                    segment.referencedClues
                )

                val prompt = buildString {
                    append(segment.imagePrompt ?: segment.novelText.take(200))
                    if (refs.isNotEmpty()) append(" (参考图: ${refs.joinToString(", ")})")
                }

                BackendRouter.generateImage(
                    ImageGenerationRequest(
                        prompt = prompt,
                        width = 1280,
                        height = 720,
                        count = 1
                    )
                )
            }
        }
    }

    suspend fun generateVideos(projectId: String) {
        _state.value = _state.value?.advanceTo(ScriptStage.VIDEO_GENERATION)

        _narrationScript.value?.let { script ->
            for (segment in script.segments.filter { it.status == SegmentStatus.APPROVED }) {
                val prompt = segment.videoPrompt ?: segment.imagePrompt ?: segment.novelText.take(100)

                BackendRouter.generateVideo(
                    VideoGenerationRequest(
                        prompt = prompt,
                        duration = segment.readingDuration.coerceIn(3, 30),
                        aspectRatio = "16:9"
                    )
                )
            }
        }
    }

    suspend fun completeAssembly(projectId: String) {
        _state.value = _state.value?.advanceTo(ScriptStage.ASSEMBLY)
        _state.value = _state.value?.advanceTo(ScriptStage.COMPLETE)
    }

    fun rollbackToVersion(projectId: String, versionId: String) {
        val success = VersionManager.rollback(projectId, versionId)
        if (success) {
            val restored = VersionManager.restoreNarrationScript(projectId, versionId)
            restored?.let { _narrationScript.value = it }
            _state.value = _state.value?.copy(currentStage = ScriptStage.VISUAL_STAGE_COMPLETE)
        }
    }

    fun canProceed(): Boolean = _state.value?.canAdvance() ?: false

    fun isAtReviewGate(): Boolean = _state.value?.isAtReviewGate() ?: false

    fun getCurrentStage(): ScriptStage = _state.value?.currentStage ?: ScriptStage.CHARACTER_ANALYSIS
}
