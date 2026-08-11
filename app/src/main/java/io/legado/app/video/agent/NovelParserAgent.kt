package io.legado.app.video.agent

import android.util.Log
import io.legado.app.video.api.AgentContext
import io.legado.app.video.api.AgentResult
import io.legado.app.video.api.AgnesApiClient
import io.legado.app.video.api.AgnesChatMessage
import io.legado.app.video.api.AgnesChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NovelParserAgent(private val apiClient: AgnesApiClient) {
    
    companion object {
        private const val SYSTEM_PROMPT = """你是一个专业的小说分析专家。你的任务是仔细阅读小说文本，提取以下信息：

1. **角色分析**：识别所有重要角色，描述他们的外貌、性格、关键特征和人际关系。
2. **场景分析**：识别故事中出现的所有重要场景/地点。
3. **剧情分段**：将小说分成有意义的剧情段落（按重要性排序）。
4. **主题风格**：识别小说的题材、风格和主题。

请以结构化JSON格式输出，格式如下：
{
  "characters": [
    {
      "name": "角色名",
      "role": "主角/配角/反派",
      "description": "整体描述",
      "appearance": "外貌描写",
      "personality": "性格特征",
      "keyTraits": ["特征1", "特征2"],
      "relationships": {"角色名": "关系"}
    }
  ],
  "scenes": [
    {
      "name": "场景名",
      "location": "地点",
      "timeOfDay": "时间",
      "atmosphere": "氛围",
      "keyCharacters": ["角色1", "角色2"],
      "description": "场景描述"
    }
  ],
  "plotSegments": [
    {
      "title": "段落标题",
      "summary": "段落摘要",
      "purpose": "剧情目的",
      "characters": ["参与角色"],
      "wordCount": 0,
      "importance": 1-5
    }
  ],
  "genre": "题材类型",
  "style": "叙事风格",
  "theme": "主题",
  "summary": "小说整体摘要",
  "keyDialogues": ["经典对白1", "经典对白2"]
}

注意：
- 只输出JSON，不要有其他内容
- 确保JSON格式正确
- 角色、场景、剧情段都要尽可能详细
- importance从1到5，5最重要
- keyDialogues选最具代表性的对白
"""
    }
    
    suspend fun parseNovel(
        context: AgentContext,
        maxCharsPerChunk: Int = 4000
    ): AgentResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val novelText = context.input
            val chunks = splitIntoChunks(novelText, maxCharsPerChunk)
            
            Log.d("NovelParserAgent", "Parsing novel: ${novelText.length} chars, ${chunks.size} chunks")
            
            val allCharacters = mutableListOf<CharacterInfo>()
            val allScenes = mutableListOf<SceneInfo>()
            val allSegments = mutableListOf<PlotSegment>()
            var genre = ""
            var style = ""
            var theme = ""
            var summary = ""
            var keyDialogues = mutableListOf<String>()
            
            // Process first chunk to get overall analysis
            if (chunks.isNotEmpty()) {
                val firstResult = analyzeChunk(chunks.first(), isFullText = chunks.size == 1)
                firstResult.onSuccess { data ->
                    allCharacters.addAll(data.characters)
                    allScenes.addAll(data.scenes)
                    allSegments.addAll(data.plotSegments)
                    genre = data.genre
                    style = data.style
                    theme = data.theme
                    summary = data.summary
                    keyDialogues.addAll(data.keyDialogues)
                }.onFailure {
                    return@withContext AgentResult(
                        success = false,
                        output = "",
                        error = "First chunk analysis failed: ${it.message}",
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }
            
            // Process remaining chunks for additional details
            for (i in 1 until chunks.size) {
                analyzeChunk(chunks[i], isFullText = false, existingCharacters = allCharacters)
            }
            
            val result = NovelAnalysisResult(
                characters = deduplicateCharacters(allCharacters),
                scenes = deduplicateScenes(allScenes),
                plotSegments = allSegments.sortedByDescending { it.importance },
                genre = genre,
                style = style,
                theme = theme,
                summary = summary,
                keyDialogues = keyDialogues.distinct()
            )
            
            AgentResult(
                success = true,
                output = summary,
                structuredData = result,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e("NovelParserAgent", "Parse failed", e)
            AgentResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    private suspend fun analyzeChunk(
        chunk: String,
        isFullText: Boolean,
        existingCharacters: List<CharacterInfo> = emptyList()
    ): Result<NovelAnalysisResult> {
        val userPrompt = buildString {
            appendLine("请分析以下小说文本")
            if (!isFullText) {
                appendLine("（注意：这是小说的一个片段，可能缺少上下文）")
                if (existingCharacters.isNotEmpty()) {
                    appendLine("已识别的角色：${existingCharacters.map { it.name }.joinToString("、")}")
                    appendLine("如果出现新角色或补充信息，请补充。")
                }
            }
            appendLine()
            appendLine("小说文本：")
            appendLine(chunk.take(4000))
        }
        
        val messages = listOf(
            AgnesChatMessage("system", SYSTEM_PROMPT),
            AgnesChatMessage("user", userPrompt)
        )
        
        val request = AgnesChatRequest(
            model = "agnes-chat-v1",
            messages = messages,
            temperature = 0.3,
            maxTokens = 4096
        )
        
        val response = apiClient.chatCompletion(request)
        return response.map { resp ->
            val content = resp.choices?.firstOrNull()?.message?.content ?: ""
            val json = extractJson(content)
            parseAnalysisResult(json)
        }
    }
    
    private fun splitIntoChunks(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        
        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        var currentChunk = StringBuilder()
        
        for (para in paragraphs) {
            if (currentChunk.length + para.length > maxChars && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
            }
            if (para.length > maxChars) {
                // Split long paragraph by sentences
                val sentences = para.split("。")
                for (sent in sentences) {
                    if (currentChunk.length + sent.length + 1 > maxChars && currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }
                    currentChunk.append(sent).append("。")
                }
            } else {
                currentChunk.append(para).append("\n\n")
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return chunks
    }
    
    private fun extractJson(text: String): String {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }
        return "{}"
    }
    
    private fun parseAnalysisResult(json: String): NovelAnalysisResult {
        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(json, NovelAnalysisResult::class.java)
        } catch (e: Exception) {
            Log.e("NovelParserAgent", "Parse JSON failed: $json", e)
            NovelAnalysisResult()
        }
    }
    
    private fun deduplicateCharacters(characters: List<CharacterInfo>): List<CharacterInfo> {
        return characters.groupBy { it.name }.map { (name, group) ->
            group.first().copy(
                keyTraits = group.flatMap { it.keyTraits }.distinct(),
                relationships = group.fold(emptyMap()) { acc, info -> acc + info.relationships }
            )
        }
    }
    
    private fun deduplicateScenes(scenes: List<SceneInfo>): List<SceneInfo> {
        return scenes.groupBy { it.name }.map { (name, group) ->
            group.first().copy(
                keyCharacters = group.flatMap { it.keyCharacters }.distinct()
            )
        }
    }
}