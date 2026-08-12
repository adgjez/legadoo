package io.legado.app.video.pipeline

/**
 * 分集规划器
 *
 * 借鉴 ArcReel 的渐进式规划：
 * - Peek 检测：预览小说结构
 * - Agent 建议断点：基于节奏分析推荐分集点
 * - 用户确认：人工审核确认分集方案
 * - 物理切分：按确认的断点实际切分
 */

data class EpisodePlanRequest(
    val episodeCount: Int,
    val episodes: List<EpisodeBreakpoint>,
    val recommended: Boolean = true
)

data class EpisodeBreakpoint(
    val episodeIndex: Int,
    val title: String,
    val startPosition: Int,
    val endPosition: Int,
    val keyEvents: List<String>,
    val emotionalArc: EmotionalArc,
    val hookStrength: Float
)

enum class EmotionalArc {
    SLOW_BUILD,
    RISING_ACTION,
    CLIMAX,
    RESOLUTION,
    MIXED
}

class EpisodePlanner {

    /**
     * 渐进式规划：peek → 建议 → 确认 → 切分
     */
    suspend fun planEpisodes(
        novelText: String,
        targetEpisodes: Int,
        chapterMarkers: List<Int> = emptyList()
    ): EpisodePlanRequest {
        val textLength = novelText.length
        val baseChunkSize = textLength / targetEpisodes

        val episodes = mutableListOf<EpisodeBreakpoint>()

        for (i in 0 until targetEpisodes) {
            val start = i * baseChunkSize
            val end = if (i == targetEpisodes - 1) textLength else (i + 1) * baseChunkSize

            val segment = novelText.substring(start, end.coerceAtMost(textLength))
            val title = generateEpisodeTitle(segment, i + 1)
            val keyEvents = extractKeyEvents(segment)
            val arc = detectEmotionalArc(segment)
            val hookStrength = calculateHookStrength(segment, i, targetEpisodes)

            episodes.add(
                EpisodeBreakpoint(
                    episodeIndex = i + 1,
                    title = title,
                    startPosition = start,
                    endPosition = end,
                    keyEvents = keyEvents,
                    emotionalArc = arc,
                    hookStrength = hookStrength
                )
            )
        }

        return EpisodePlanRequest(
            episodeCount = targetEpisodes,
            episodes = episodes,
            recommended = true
        )
    }

    /**
     * 基于章节标记的更精确切分
     */
    suspend fun planByChapters(
        chapterTitles: List<String>,
        chapterContents: List<String>,
        targetEpisodes: Int
    ): EpisodePlanRequest {
        val totalChapters = chapterTitles.size
        val chaptersPerEpisode = (totalChapters + targetEpisodes - 1) / targetEpisodes

        val episodes = mutableListOf<EpisodeBreakpoint>()

        for (i in 0 until targetEpisodes) {
            val startChapter = i * chaptersPerEpisode
            val endChapter = (startChapter + chaptersPerEpisode).coerceAtMost(totalChapters) - 1

            if (startChapter >= totalChapters) break

            val episodeChapters = chapterTitles.subList(startChapter, endChapter + 1)
            val combinedContent = chapterContents.subList(startChapter, endChapter + 1).joinToString("\n")

            episodes.add(
                EpisodeBreakpoint(
                    episodeIndex = i + 1,
                    title = episodeChapters.firstOrNull() ?: "第${i + 1}集",
                    startPosition = startChapter,
                    endPosition = endChapter,
                    keyEvents = extractKeyEvents(combinedContent),
                    emotionalArc = detectEmotionalArc(combinedContent),
                    hookStrength = calculateHookStrength(combinedContent, i, targetEpisodes)
                )
            )
        }

        return EpisodePlanRequest(
            episodeCount = episodes.size,
            episodes = episodes,
            recommended = true
        )
    }

    private fun generateEpisodeTitle(content: String, index: Int): String {
        val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: ""
        val keyPhrases = content.filter { it.isChinese() || it.isLetterOrDigit() }
        return if (firstLine.isNotBlank()) {
            "第${index}集·${firstLine}"
        } else {
            "第${index}集"
        }
    }

    private fun extractKeyEvents(content: String): List<String> {
        val events = mutableListOf<String>()

        val sentences = content.split(Regex("[。！？\n"))
            .filter { it.length in 10..100 }
            .take(3)

        sentences.forEach { sentence ->
            if (sentence.contains("遇到") || sentence.contains("发现") || sentence.contains("决定") ||
                sentence.contains("终于") || sentence.contains("突然") || sentence.contains("但是")
            ) {
                events.add(sentence.take(50))
            }
        }

        if (events.isEmpty()) {
            events.addAll(sentences.take(2).map { it.take(40) })
        }

        return events
    }

    private fun detectEmotionalArc(content: String): EmotionalArc {
        val positiveWords = listOf("开心", "喜悦", "成功", "美好", "幸福", "温暖")
        val negativeWords = listOf("悲伤", "痛苦", "失败", "绝望", "愤怒", "恐惧")
        val climaxWords = listOf("决战", "最终", "最后", "终极", "生死", "关键")
        val resolutionWords = listOf("从此", "终于", "结局", "圆满", "和平", "新生活")

        val positiveScore = positiveWords.count { content.contains(it) }
        val negativeScore = negativeWords.count { content.contains(it) }
        val climaxScore = climaxWords.count { content.contains(it) }
        val resolutionScore = resolutionWords.count { content.contains(it) }

        return when {
            climaxScore > 0 -> EmotionalArc.CLIMAX
            resolutionScore > 0 -> EmotionalArc.RESOLUTION
            positiveScore > negativeScore * 2 -> EmotionalArc.SLOW_BUILD
            negativeScore > positiveScore -> EmotionalArc.RISING_ACTION
            else -> EmotionalArc.MIXED
        }
    }

    private fun calculateHookStrength(content: String, currentIndex: Int, total: Int): Float {
        if (currentIndex == total - 1) return 0.3f

        val lastSentence = content.lines().lastOrNull { it.isNotBlank() } ?: ""
        var score = 0.5f

        if (lastSentence.contains("但是") || lastSentence.contains("然而") || lastSentence.contains("不过")) {
            score += 0.2f
        }
        if (lastSentence.contains("？") || lastSentence.contains("...") || lastSentence.contains("……")) {
            score += 0.15f
        }
        if (lastSentence.contains("突然") || lastSentence.contains("就在这时") || lastSentence.contains("此时")) {
            score += 0.15f
        }

        return score.coerceIn(0f, 1f)
    }

    fun recalculateWithUserFeedback(
        original: EpisodePlanRequest,
        userAdjustments: Map<Int, EpisodeBreakpoint>
    ): EpisodePlanRequest {
        val updatedEpisodes = original.episodes.map { ep ->
            userAdjustments[ep.episodeIndex] ?: ep
        }
        return original.copy(episodes = updatedEpisodes, recommended = false)
    }
}

private fun Char.isChinese(): Boolean = this.code in 0x4E00..0x9FFF
