package io.legado.app.video.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReviewGateItem(
    val id: String,
    val title: String,
    val content: String,
    val type: ReviewItemType,
    val status: ReviewStatus,
    val suggestions: List<String> = emptyList()
)

enum class ReviewItemType {
    CHARACTER,
    CLUE,
    CONTENT_SEGMENT,
    VISUAL_PROMPT,
    SCRIPT_STRUCTURE
}

enum class ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    MODIFIED
}

class ReviewGateManager {

    private val _items = MutableStateFlow<List<ReviewGateItem>>(emptyList())
    val items: StateFlow<List<ReviewGateItem>> = _items.asStateFlow()

    private val _overallStatus = MutableStateFlow(ReviewStatus.PENDING)
    val overallStatus: StateFlow<ReviewStatus> = _overallStatus.asStateFlow()

    fun submitForReview(items: List<ReviewGateItem>) {
        _items.value = items
        _overallStatus.value = if (items.isEmpty()) ReviewStatus.APPROVED else ReviewStatus.PENDING
    }

    fun approveItem(id: String) {
        _items.value = _items.value.map { item ->
            if (item.id == id) item.copy(status = ReviewStatus.APPROVED)
            else item
        }
        checkOverallStatus()
    }

    fun rejectItem(id: String, reason: String? = null) {
        _items.value = _items.value.map { item ->
            if (item.id == id) item.copy(status = ReviewStatus.REJECTED)
            else item
        }
        checkOverallStatus()
    }

    fun modifyItem(id: String, newContent: String) {
        _items.value = _items.value.map { item ->
            if (item.id == id) item.copy(content = newContent, status = ReviewStatus.MODIFIED)
            else item
        }
        checkOverallStatus()
    }

    fun approveAll() {
        _items.value = _items.value.map { it.copy(status = ReviewStatus.APPROVED) }
        _overallStatus.value = ReviewStatus.APPROVED
    }

    fun getPendingItems(): List<ReviewGateItem> =
        _items.value.filter { it.status == ReviewStatus.PENDING }

    fun getRejectedItems(): List<ReviewGateItem> =
        _items.value.filter { it.status == ReviewStatus.REJECTED }

    private fun checkOverallStatus() {
        val items = _items.value
        _overallStatus.value = when {
            items.isEmpty() -> ReviewStatus.APPROVED
            items.all { it.status == ReviewStatus.APPROVED } -> ReviewStatus.APPROVED
            items.any { it.status == ReviewStatus.REJECTED } -> ReviewStatus.REJECTED
            else -> ReviewStatus.PENDING
        }
    }

    fun isApproved(): Boolean = _overallStatus.value == ReviewStatus.APPROVED

    fun buildCharacterReviewItems(library: AssetLibrary): List<ReviewGateItem> {
        return library.characters.map { character ->
            ReviewGateItem(
                id = "char_${character.characterId}",
                title = "角色：${character.name}",
                content = buildString {
                    append(character.visualDescription)
                    if (character.costumes.isNotEmpty()) {
                        append("\n服装：${character.costumes.joinToString(", ") { it.name }}")
                    }
                    if (character.referenceImagePath != null) {
                        append("\n[已生成设计图]")
                    }
                },
                type = ReviewItemType.CHARACTER,
                status = if (character.locked) ReviewStatus.APPROVED else ReviewStatus.PENDING,
                suggestions = listOf(
                    if (character.referenceImagePath == null) "建议先生成角色设计图" else "设计图已就绪"
                )
            )
        }
    }

    fun buildContentReviewItems(script: Any): List<ReviewGateItem> {
        return when (script) {
            is NarrationScript -> script.segments.map { segment ->
                ReviewGateItem(
                    id = segment.segmentId,
                    title = "片段 ${segment.index}",
                    content = segment.novelText.take(150),
                    type = ReviewItemType.CONTENT_SEGMENT,
                    status = if (segment.status == SegmentStatus.CONTENT_READY) ReviewStatus.PENDING else ReviewStatus.APPROVED,
                    suggestions = buildList {
                        if (segment.referencedCharacters.isEmpty()) add("未检测到角色引用")
                        if (segment.referencedClues.isEmpty()) add("未检测到场景/道具引用")
                    }
                )
            }
            is DramaScript -> script.utterances.map { utterance ->
                ReviewGateItem(
                    id = utterance.utteranceId,
                    title = "对白 ${utterance.index}：${utterance.speaker ?: "未知"}",
                    content = buildString {
                        append(utterance.dialogue ?: "")
                        if (utterance.action != null) append("\n动作：${utterance.action}")
                        if (utterance.sceneDescription != null) append("\n场景：${utterance.sceneDescription}")
                    },
                    type = ReviewItemType.CONTENT_SEGMENT,
                    status = if (utterance.status == SegmentStatus.CONTENT_READY) ReviewStatus.PENDING else ReviewStatus.APPROVED
                )
            }
            else -> emptyList()
        }
    }

    fun buildVisualReviewItems(script: Any): List<ReviewGateItem> {
        return when (script) {
            is NarrationScript -> script.segments.filter { it.imagePrompt != null || it.videoPrompt != null }.map { segment ->
                ReviewGateItem(
                    id = "vis_${segment.segmentId}",
                    title = "视觉：片段 ${segment.index}",
                    content = buildString {
                        append("画面：${segment.imagePrompt ?: "（未生成）"}")
                        append("\n视频：${segment.videoPrompt ?: "（未生成）"}")
                    },
                    type = ReviewItemType.VISUAL_PROMPT,
                    status = ReviewStatus.PENDING
                )
            }
            is DramaScript -> script.utterances.filter { it.imagePrompt != null || it.videoPrompt != null }.map { utterance ->
                ReviewGateItem(
                    id = "vis_${utterance.utteranceId}",
                    title = "视觉：${utterance.speaker ?: "对白"} ${utterance.index}",
                    content = buildString {
                        append("画面：${utterance.imagePrompt ?: "（未生成）"}")
                        append("\n视频：${utterance.videoPrompt ?: "（未生成）"}")
                    },
                    type = ReviewItemType.VISUAL_PROMPT,
                    status = ReviewStatus.PENDING
                )
            }
            else -> emptyList()
        }
    }

    fun clear() {
        _items.value = emptyList()
        _overallStatus.value = ReviewStatus.PENDING
    }
}
