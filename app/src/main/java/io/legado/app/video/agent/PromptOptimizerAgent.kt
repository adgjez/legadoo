package io.legado.app.video.agent

import android.util.Log
import io.legado.app.video.api.AgnesApiClient
import io.legado.app.video.api.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PromptOptimizerAgent(private val apiClient: AgnesApiClient) {
    
    companion object {
        private const val SYSTEM_PROMPT = """你是一个专业的AI图像/视频生成提示词优化专家。你需要将简短的描述转化为适合AI生成的详细提示词。

提示词优化原则：
1. **角色一致性**：包含角色的完整外貌描述（年龄、性别、发型、发色、眼睛、身高、体型、服装）
2. **场景细节**：具体的环境描述（地点、时间、天气、背景元素）
3. **光影效果**：光源方向、色温、氛围光
4. **艺术风格**：整体风格（写实/动漫/油画/水彩/赛博朋克等）
5. **镜头语言**：景别、角度、景深
6. **情绪氛围**：通过视觉元素传达情绪
7. **动作描述**：具体的姿态、表情、动作
8. **色彩基调**：主色调、配色方案

对于视频生成的videoPrompt，还需要：
- 镜头运动描述（推拉摇移跟等）
- 角色动作序列
- 场景变化过渡
- 时间流逝表现

请直接输出优化后的提示词，不要解释。
"""
    }
    
    suspend fun optimizeVisualPrompt(
        originalPrompt: String,
        characterDescriptions: List<String> = emptyList(),
        sceneDescription: String = "",
        style: String = "",
        mood: String = "",
        shotType: String = "",
        cameraMovement: String = ""
    ): AgentResult = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("请优化以下提示词用于AI图像生成：")
                appendLine()
                appendLine("原始提示词：$originalPrompt")
                if (characterDescriptions.isNotEmpty()) {
                    appendLine()
                    appendLine("角色参考：${characterDescriptions.joinToString("；")}")
                }
                if (sceneDescription.isNotBlank()) {
                    appendLine()
                    appendLine("场景描述：$sceneDescription")
                }
                if (style.isNotBlank()) appendLine("风格：$style")
                if (mood.isNotBlank()) appendLine("情绪：$mood")
                if (shotType.isNotBlank()) appendLine("景别：$shotType")
                if (cameraMovement.isNotBlank()) appendLine("运镜：$cameraMovement")
                appendLine()
                appendLine("请输出优化后的visualPrompt（用于图片生成）和videoPrompt（用于视频生成），用|||分隔。")
            }
            
            val messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", userPrompt)
            )
            
            val response = apiClient.generateChat(
                messages = messages,
                model = "agnes-chat-v1",
                temperature = 0.3f,
                maxTokens = 2048
            )
            response.fold(
                onSuccess = { resp ->
                    val content = resp.content.ifBlank { originalPrompt }
                    AgentResult(
                        success = true,
                        output = content.trim(),
                        tokensUsed = resp.usage?.totalTokens ?: 0
                    )
                },
                onFailure = {
                    AgentResult(success = false, output = originalPrompt, error = it.message ?: "")
                }
            )
        } catch (e: Exception) {
            AgentResult(success = false, output = originalPrompt, error = e.message ?: "")
        }
    }
    
    suspend fun optimizeVideoPrompt(
        scene: StoryboardScene,
        characterDescriptions: List<String> = emptyList()
    ): AgentResult = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("请优化以下分镜的视频生成提示词：")
                appendLine()
                appendLine("分镜标题：${scene.title}")
                appendLine("分镜描述：${scene.summary}")
                appendLine("原始视觉提示：${scene.visualPrompt}")
                appendLine()
                if (characterDescriptions.isNotEmpty()) {
                    appendLine("角色：${characterDescriptions.joinToString("、")}")
                }
                appendLine("景别：${scene.shotType}")
                appendLine("运镜：${scene.cameraMovement}")
                appendLine("时长：${scene.durationSeconds}秒")
                appendLine()
                appendLine("请输出优化后的videoPrompt，详细描述镜头运动、角色动作和场景变化。")
            }
            
            val messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", userPrompt)
            )
            
            val response = apiClient.generateChat(
                messages = messages,
                model = "agnes-chat-v1",
                temperature = 0.3f,
                maxTokens = 2048
            )
            response.fold(
                onSuccess = { resp ->
                    val content = resp.content.ifBlank { scene.videoPrompt }
                    AgentResult(success = true, output = content.trim())
                },
                onFailure = {
                    AgentResult(success = false, output = scene.videoPrompt, error = it.message ?: "")
                }
            )
        } catch (e: Exception) {
            AgentResult(success = false, output = scene.videoPrompt, error = e.message ?: "")
        }
    }
}