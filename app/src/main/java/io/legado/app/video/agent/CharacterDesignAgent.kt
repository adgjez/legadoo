package io.legado.app.video.agent

import android.util.Log
import io.legado.app.video.api.AgnesApiClient
import io.legado.app.video.api.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CharacterDesignAgent(private val apiClient: AgnesApiClient) {
    
    companion object {
        private const val SYSTEM_PROMPT = """你是一个专业的角色设计专家。根据小说描述，为每个角色生成详细的视觉设计说明，用于AI图像生成。

角色设计需要包含：
1. **基本信息**：姓名、年龄、性别
2. **外貌特征**：身高、体型、脸型、发型发色、眼睛颜色、肤色
3. **服装**：详细的服装描述（颜色、款式、材质、配饰）
4. **标志性特征**：使角色具有辨识度的特征（疤痕、纹身、饰品等）
5. **气质神态**：整体气质、典型表情
6. **身份标识**：通过外观体现角色身份/地位的元素

请为每个角色生成一段完整的visualPrompt，用于AI图像生成。
使用以下格式：
角色名|visualPrompt内容
"""
    }
    
    suspend fun designCharacters(
        characters: List<CharacterInfo>,
        style: String = "realistic"
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val characterPrompts = mutableMapOf<String, String>()
        
        for (character in characters) {
            val result = designCharacter(character, style)
            characterPrompts[character.name] = result
        }
        
        characterPrompts
    }
    
    suspend fun designCharacter(
        character: CharacterInfo,
        style: String = "realistic"
    ): String = withContext(Dispatchers.IO) {
        try {
            val userPrompt = buildString {
                appendLine("请为以下角色设计详细的视觉描述：")
                appendLine()
                appendLine("姓名：${character.name}")
                appendLine("角色定位：${character.role}")
                appendLine("外貌描述：${character.appearance}")
                appendLine("性格特点：${character.personality}")
                appendLine("关键特征：${character.keyTraits.joinToString("、")}")
                appendLine()
                appendLine("风格：$style")
                appendLine()
                appendLine("请生成一段完整的visualPrompt，包含所有视觉元素，可以直接用于AI图像生成。")
            }
            
            val messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", userPrompt)
            )
            
            val response = apiClient.generateChat(
                messages = messages,
                model = "agnes-chat-v1",
                temperature = 0.3f,
                maxTokens = 1024
            )
            response.fold(
                onSuccess = { resp ->
                    resp.content.trim().ifBlank { character.appearance }
                },
                onFailure = {
                    Log.e("CharacterDesignAgent", "Design failed for ${character.name}", it)
                    character.appearance
                }
            )
        } catch (e: Exception) {
            Log.e("CharacterDesignAgent", "Design failed for ${character.name}", e)
            character.appearance
        }
    }
}