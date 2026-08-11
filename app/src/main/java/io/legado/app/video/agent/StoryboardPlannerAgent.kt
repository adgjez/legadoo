package io.legado.app.video.agent

import android.util.Log
import io.legado.app.video.api.AgentContext
import io.legado.app.video.api.AgentResult
import io.legado.app.video.api.AgnesApiClient
import io.legado.app.video.api.AgnesChatMessage
import io.legado.app.video.api.AgnesChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StoryboardPlannerAgent(private val apiClient: AgnesApiClient) {
    
    companion object {
        private const val SYSTEM_PROMPT = """你是一个专业的分镜脚本规划师。你需要将小说内容转化为详细的视频分镜。

每个分镜需要包含：
- **title**: 分镜标题（简洁有力）
- **summary**: 分镜摘要（描述这个镜头的内容）
- **novelText**: 对应的小说原文摘录
- **shotType**: 景别（extreme_long/long/medium/close_up/extreme_close_up/bird_eye/worm_eye/over_shoulder/point_of_view）
- **cameraMovement**: 运镜方式（static/pan/tilt/dolly/truck/pedestal/rotate/zoom/tracking/aerial/handheld/crane/steadicam）
- **durationSeconds**: 时长（3-10秒，关键镜头可以更长）
- **location**: 拍摄地点
- **timeOfDay**: 时间（清晨/白天/黄昏/夜晚/深夜）
- **mood**: 情绪氛围
- **characters**: 参与角色（用角色名）
- **keyAction**: 关键动作
- **dialogue**: 对白（如有）
- **visualPrompt**: 详细的视觉描述（用于图片生成，需要包含：角色外貌、服装、表情、姿态、场景环境、光影、色调等）
- **videoPrompt**: 视频运动描述（用于视频生成，需要包含：镜头运动、角色动作、场景变化等）
- **isKeyframe**: 是否关键帧（true/false）

分镜原则：
1. 每个分镜应有明确的视觉焦点
2. 运镜方式要服务于剧情表达
3. 分镜之间要有流畅的衔接
4. 关键剧情转折点使用特写或特殊运镜
5. 视觉描述要具体到可以直接用于AI生成
6. 尽量覆盖所有重要剧情

请以JSON数组输出分镜列表。
"""

        private const val VISUAL_PROMPT_TEMPLATE = """{character_descriptions}
{scene_description}
{action_description}
{emotion_description}
{style_tags}
{lighting_description}
{camera_description}"""
    }
    
    suspend fun planStoryboard(
        context: AgentContext,
        analysisResult: NovelAnalysisResult?,
        targetDuration: Int = 60,
        targetScenes: Int = 12
    ): AgentResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val characters = analysisResult?.characters ?: emptyList()
            val plots = analysisResult?.plotSegments ?: emptyList()
            val novelText = context.input
            
            // Build character reference section
            val characterSection = buildString {
                appendLine("## 角色参考")
                characters.forEach { char ->
                    appendLine("- ${char.name}（${char.role}）：${char.appearance}。性格：${char.personality}。关键特征：${char.keyTraits.joinToString("、")}")
                }
            }
            
            // Build plot summary
            val plotSection = buildString {
                appendLine("## 剧情概要")
                plots.take(20).forEach { plot ->
                    appendLine("- [${plot.importance}★] ${plot.title}：${plot.summary}")
                }
            }
            
            val userPrompt = buildString {
                appendLine("请将以下小说内容规划为${targetScenes}个视频分镜，目标总时长约${targetDuration}秒。")
                appendLine()
                appendLine(characterSection)
                appendLine()
                appendLine(plotSection)
                appendLine()
                appendLine("## 小说内容")
                appendLine(novelText.take(6000))
                appendLine()
                appendLine("## 要求")
                appendLine("- 输出${targetScenes}个分镜")
                appendLine("- 合理分配时长，关键剧情给更多时间")
                appendLine("- 每个分镜的visualPrompt要非常详细，包含角色外貌、服装、场景、光影等所有视觉元素")
                appendLine("- videoPrompt要详细描述镜头运动和角色动作")
                appendLine("- 优先选择importance高的剧情段落")
            }
            
            val messages = listOf(
                AgnesChatMessage("system", SYSTEM_PROMPT),
                AgnesChatMessage("user", userPrompt)
            )
            
            val request = AgnesChatRequest(
                model = "agnes-chat-v1",
                messages = messages,
                temperature = 0.5,
                maxTokens = 8192
            )
            
            val response = apiClient.chatCompletion(request)
            
            response.fold(
                onSuccess = { resp ->
                    val content = resp.choices?.firstOrNull()?.message?.content ?: ""
                    val json = extractJsonArray(content)
                    val scenes = parseStoryboardScenes(json)
                    
                    val totalDuration = scenes.sumOf { it.durationSeconds }
                    
                    val plan = StoryboardPlan(
                        scenes = scenes.sortedBy { it.order },
                        totalDurationSeconds = totalDuration,
                        estimatedCost = calculateEstimatedCost(scenes.size, totalDuration)
                    )
                    
                    AgentResult(
                        success = true,
                        output = "生成了${scenes.size}个分镜，总时长${totalDuration}秒",
                        structuredData = plan,
                        tokensUsed = resp.usage?.totalTokens ?: 0,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                },
                onFailure = { error ->
                    AgentResult(
                        success = false,
                        output = "",
                        error = "Storyboard planning failed: ${error.message}",
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("StoryboardPlannerAgent", "Plan failed", e)
            AgentResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    private fun extractJsonArray(text: String): String {
        val jsonStart = text.indexOf('[')
        val jsonEnd = text.lastIndexOf(']')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }
        return "[]"
    }
    
    private fun parseStoryboardScenes(json: String): List<StoryboardScene> {
        return try {
            val gson = com.google.gson.Gson()
            val list = gson.fromJsonArray(json, StoryboardScene::class.java)
            list.mapIndexed { index, scene ->
                if (scene.order == 0) scene.copy(order = index + 1) else scene
            }
        } catch (e: Exception) {
            Log.e("StoryboardPlannerAgent", "Parse scenes failed: $json", e)
            emptyList()
        }
    }
    
    private fun calculateEstimatedCost(sceneCount: Int, totalDuration: Int): Double {
        // Rough estimate: ~$0.05 per image + ~$0.10 per video second
        return sceneCount * 0.05 + totalDuration * 0.10
    }
}