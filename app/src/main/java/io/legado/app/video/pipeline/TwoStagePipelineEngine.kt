package io.legado.app.video.pipeline

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class TwoStagePipelineEngine {

    suspend fun generateContentStage(
        projectId: String,
        novelText: String,
        mode: ContentMode,
        segmentCount: Int
    ): Result<Any> = withContext(Dispatchers.IO) {
        when (mode) {
            ContentMode.NARRATION -> generateNarrationContent(projectId, novelText, segmentCount)
            ContentMode.DRAMA -> generateDramaContent(projectId, novelText)
        }
    }

    private suspend fun generateNarrationContent(
        projectId: String,
        novelText: String,
        segmentCount: Int
    ): Result<NarrationScript> {
        val prompt = """你是一位专业的小说口播内容编辑。请将以下小说文本按阅读节奏拆分成 ${segmentCount} 个片段。

要求：
1. 每个片段应该是一个完整的阅读单元（不要在句子中间断开）
2. 每个片段标注预估阅读时长（以正常语速 300字/分钟 估算）
3. 识别每个片段中出场的角色名称
4. 识别每个片段中出现的关键场景或道具（线索）
5. 只输出结构化的 JSON，不要额外解释

小说文本：
$novelText

输出格式：
```json
[
  {
    "index": 1,
    "novel_text": "片段原文...",
    "reading_duration": 15,
    "referenced_characters": ["角色A", "角色B"],
    "referenced_clues": ["场景X", "道具Y"]
  }
]
```"""

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位专业的小说口播内容编辑。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.3f,
                maxTokens = 8192
            )
        )

        return result.fold(
            onSuccess = { textResult ->
                val segments = parseNarrationSegments(textResult.content)
                val script = NarrationScript(
                    scriptId = "narration_${projectId}_${System.currentTimeMillis()}",
                    episodeId = projectId,
                    segments = segments,
                    stage = ScriptStage.SCRIPT_CONTENT_READY,
                    totalSegments = segments.size
                )
                Result.success(script)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private fun parseNarrationSegments(content: String): List<NarrationSegment> {
        val segments = mutableListOf<NarrationSegment>()
        try {
            val jsonContent = content.substringAfter("```json").substringBefore("```").trim()
            val array = jsonContent.trim().removeSurrounding("[").removeSurrounding("]")
            val segmentObjects = array.split("},{"").mapIndexed { index, part ->
                val adjusted = if (index == 0) part else "{$part"
                if (index == array.split("},{"").size - 1) adjusted.removeSuffix("}")
                else adjusted
            }

            segmentObjects.forEach { obj ->
                val index = extractInt(obj, "index") ?: (segments.size + 1)
                val novelText = extractString(obj, "novel_text") ?: ""
                val duration = extractInt(obj, "reading_duration") ?: 10
                val characters = extractStringList(obj, "referenced_characters")
                val clues = extractStringList(obj, "referenced_clues")

                segments.add(
                    NarrationSegment(
                        segmentId = "seg_$index",
                        index = index,
                        novelText = novelText,
                        readingDuration = duration,
                        referencedCharacters = characters,
                        referencedClues = clues,
                        status = SegmentStatus.CONTENT_READY
                    )
                )
            }
        } catch (e: Exception) {
            segments.addAll(content.split("\n\n").mapIndexed { index, block ->
                NarrationSegment(
                    segmentId = "seg_$index",
                    index = index + 1,
                    novelText = block.trim(),
                    readingDuration = (block.length / 5).coerceAtLeast(5),
                    status = SegmentStatus.CONTENT_READY
                )
            })
        }
        return segments
    }

    private suspend fun generateDramaContent(
        projectId: String,
        novelText: String
    ): Result<DramaScript> {
        val prompt = """你是一位专业的剧集动画编剧。请将以下小说文本改编成剧本格式。

要求：
1. 按场景分割，每个场景包含：场景描述、角色对话、动作指示
2. 用统一的格式标注：
   - [场景] 场景地点、时间、环境描述
   - [角色] 角色名称 + 动作/表情
   - [对白] 角色说的话
3. 识别每个场景中出场的角色和关键线索
4. 只输出结构化内容，不要额外解释

小说文本：
$novelText

输出格式（结构化 JSON）：
```json
[
  {
    "index": 1,
    "speaker": "角色名",
    "dialogue": "角色说的话",
    "action": "角色的动作/表情",
    "scene_description": "场景环境描述",
    "referenced_characters": ["角色A"],
    "referenced_clues": ["场景X"]
  }
]
```"""

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位专业的剧集动画编剧。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.3f,
                maxTokens = 8192
            )
        )

        return result.fold(
            onSuccess = { textResult ->
                val utterances = parseDramaUtterances(textResult.content)
                val script = DramaScript(
                    scriptId = "drama_${projectId}_${System.currentTimeMillis()}",
                    episodeId = projectId,
                    utterances = utterances,
                    stage = ScriptStage.SCRIPT_CONTENT_READY,
                    totalScenes = utterances.size
                )
                Result.success(script)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private fun parseDramaUtterances(content: String): List<DramaUtterance> {
        val utterances = mutableListOf<DramaUtterance>()
        try {
            val jsonContent = content.substringAfter("```json").substringBefore("```").trim()
            val lines = jsonContent.lines()
            var currentSpeaker: String? = null
            var currentDialogue: String? = null
            var currentAction: String? = null
            var currentScene: String? = null

            lines.forEach { line ->
                when {
                    line.contains("speaker") -> currentSpeaker = extractString(line, "speaker")
                    line.contains("dialogue") -> currentDialogue = extractString(line, "dialogue")
                    line.contains("action") -> currentAction = extractString(line, "action")
                    line.contains("scene_description") -> currentScene = extractString(line, "scene_description")
                    line.trim() == "}" || line.contains("},") -> {
                        if (currentSpeaker != null || currentDialogue != null) {
                            utterances.add(
                                DramaUtterance(
                                    utteranceId = "utt_${utterances.size + 1}",
                                    index = utterances.size + 1,
                                    speaker = currentSpeaker,
                                    dialogue = currentDialogue,
                                    action = currentAction,
                                    sceneDescription = currentScene,
                                    status = SegmentStatus.CONTENT_READY
                                )
                            )
                            currentSpeaker = null
                            currentDialogue = null
                            currentAction = null
                            currentScene = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            utterances.add(
                DramaUtterance(
                    utteranceId = "utt_1",
                    index = 1,
                    sceneDescription = content.take(200),
                    status = SegmentStatus.CONTENT_READY
                )
            )
        }
        return utterances
    }

    suspend fun generateVisualStage(
        projectId: String,
        script: Any,
        assetLibrary: AssetLibrary?
    ): Result<Map<String, Pair<String?, String?>>> = withContext(Dispatchers.IO) {
        when (script) {
            is NarrationScript -> generateNarrationVisuals(projectId, script, assetLibrary)
            is DramaScript -> generateDramaVisuals(projectId, script, assetLibrary)
            else -> Result.failure(IllegalArgumentException("Unknown script type"))
        }
    }

    private suspend fun generateNarrationVisuals(
        projectId: String,
        script: NarrationScript,
        assetLibrary: AssetLibrary?
    ): Result<Map<String, Pair<String?, String?>>> {
        val prompts = mutableMapOf<String, Pair<String?, String?>>()

        for (segment in script.segments) {
            val characterRefs = segment.referencedCharacters.mapNotNull { name ->
                assetLibrary?.getCharacter(name)?.toPromptReference()?.let { "$name: $it" }
            }.joinToString("; ")

            val clueRefs = segment.referencedClues.mapNotNull { name ->
                assetLibrary?.getClue(name)?.visualDescription?.let { "$name: $it" }
            }.joinToString("; ")

            val prompt = """为以下小说片段生成画面描述。只输出视觉描述，不重复原文内容。

原文片段：${segment.novelText.take(200)}

角色参考：$characterRefs
场景/道具参考：$clueRefs

请输出：
1. 画面主体描述（角色、动作、表情）
2. 场景环境（时间、地点、氛围）
3. 色彩和光影风格
4. 镜头角度建议

格式：
[IMAGE_PROMPT] 简洁的英文prompt，用于AI生图
[VIDEO_PROMPT] 简洁的英文prompt，用于AI生视频"""

            val result = BackendRouter.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        mapOf("role" to "system", "content" to "你是一位专业的AI视频分镜提示词工程师。"),
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    temperature = 0.5f,
                    maxTokens = 2048
                )
            )

            result.getOrNull()?.let { textResult ->
                val content = textResult.content
                val imagePrompt = content.substringAfter("[IMAGE_PROMPT]").substringBefore("[VIDEO_PROMPT]").trim()
                val videoPrompt = content.substringAfter("[VIDEO_PROMPT]").trim()
                prompts[segment.segmentId] = imagePrompt to videoPrompt
            } ?: run {
                prompts[segment.segmentId] = null to null
            }

            delay(500)
        }

        return Result.success(prompts)
    }

    private suspend fun generateDramaVisuals(
        projectId: String,
        script: DramaScript,
        assetLibrary: AssetLibrary?
    ): Result<Map<String, Pair<String?, String?>>> {
        val prompts = mutableMapOf<String, Pair<String?, String?>>()

        for (utterance in script.utterances) {
            val characterRefs = utterance.speaker?.let { name ->
                assetLibrary?.getCharacter(name)?.toPromptReference()?.let { "$name: $it" }
            } ?: ""

            val clueRefs = utterance.referencedClues.mapNotNull { name ->
                assetLibrary?.getClue(name)?.visualDescription?.let { "$name: $it" }
            }.joinToString("; ")

            val prompt = """为以下剧本对白生成画面描述。只输出视觉描述。

场景：${utterance.sceneDescription ?: ""}
角色：${utterance.speaker ?: ""}
动作：${utterance.action ?: ""}
对白：${utterance.dialogue ?: ""}

角色参考：$characterRefs
场景/道具参考：$clueRefs

请输出：
[IMAGE_PROMPT] 用于AI生图的英文prompt
[VIDEO_PROMPT] 用于AI生视频的英文prompt"""

            val result = BackendRouter.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        mapOf("role" to "system", "content" to "你是一位专业的AI视频分镜提示词工程师。"),
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    temperature = 0.5f,
                    maxTokens = 2048
                )
            )

            result.getOrNull()?.let { textResult ->
                val content = textResult.content
                val imagePrompt = content.substringAfter("[IMAGE_PROMPT]").substringBefore("[VIDEO_PROMPT]").trim()
                val videoPrompt = content.substringAfter("[VIDEO_PROMPT]").trim()
                prompts[utterance.utteranceId] = imagePrompt to videoPrompt
            } ?: run {
                prompts[utterance.utteranceId] = null to null
            }

            delay(500)
        }

        return Result.success(prompts)
    }

    private fun extractString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        val content = match.groupValues[1]
        return content.split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"")
        }
    }
}
