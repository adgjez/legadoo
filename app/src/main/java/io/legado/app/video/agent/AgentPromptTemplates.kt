package io.legado.app.video.agent

object AgentPromptTemplates {

    val CHARACTER_DESIGN_PROMPT = """你是一位专业的角色视觉设计师。请根据以下角色信息，生成详细的AI生图提示词。

角色名称：{name}
角色定位：{role}
外貌描写：{appearance}
性格特征：{personality}
关键特征：{keyTraits}
视觉风格：{style}

请输出：
1. 一段详细的英文视觉提示词（用于AI生图），包含：
   - 角色外貌的具体描述（发型、发色、眼睛颜色、肤色、身高、体型）
   - 服装细节（颜色、款式、材质）
   - 标志性特征（伤疤、纹身、饰品等）
   - 姿态和表情
   - 光线和氛围

2. 一段简短的中文描述（用于参考）

格式：
{
  "english_prompt": "详细的英文提示词",
  "chinese_description": "中文描述",
  "key_visual_elements": ["元素1", "元素2", "元素3"]
}
"""

    val SCENE_PLANNING_PROMPT = """你是一位专业的分镜师。请根据以下小说文本，设计一个视频分镜。

小说文本：{novelText}
场景类型：{sceneType}
参考镜头：{shotType}
视觉风格：{style}
前情摘要：{previousSummary}

请输出：
1. 分镜标题
2. 镜头类型和运镜方式
3. 时长建议（秒）
4. 视觉提示词（用于图片生成）
5. 视频提示词（用于视频生成）
6. 情绪氛围
7. 关键动作描述
8. 包含角色

格式：
{
  "title": "分镜标题",
  "shot_type": "镜头类型",
  "camera_movement": "运镜方式",
  "duration": 5,
  "visual_prompt": "图片生成提示词（英文）",
  "video_prompt": "视频生成提示词（英文）",
  "mood": "氛围",
  "key_action": "关键动作",
  "characters": ["角色1", "角色2"]
}
"""

    val PROMPT_OPTIMIZATION_PROMPT = """你是一位专业的AI提示词优化专家。请对以下视频提示词进行增强和优化。

原始提示词：{originalPrompt}
视觉风格：{style}
角色一致性信息：{consistencyInfo}
场景连贯性信息：{continuityInfo}
自定义要求：{customInstructions}

请输出优化后的提示词，要求：
1. 增强视觉细节（环境、光影、材质、质感）
2. 融入指定的艺术风格
3. 保持角色一致性
4. 保持与前后场景的连贯性
5. 添加适当的镜头语言描述
6. 输出英文提示词（用于AI生成）

格式：
{
  "optimized_prompt": "优化后的英文提示词",
  "video_prompt": "视频生成专用提示词",
  "enhancements_made": ["增强点1", "增强点2"]
}
"""

    val DIALOGUE_EXTRACTION_PROMPT = """你是一位专业的对话提取专家。请从以下小说文本中提取关键对白。

小说文本：{novelText}

请：
1. 提取最重要的3-5句对白
2. 标注说话者和情绪
3. 描述对白的场景背景

格式：
{
  "dialogues": [
    {
      "speaker": "说话者",
      "text": "对白内容",
      "emotion": "情绪",
      "context": "场景背景"
    }
  ]
}
"""

    val CHARACTER_CONSISTENCY_PROMPT = """请检查以下角色描述是否一致，并给出优化建议。

原始描述：{originalDesc}
历史描述：{historyDesc}
场景上下文：{sceneContext}

请指出：
1. 哪些特征描述可能不一致
2. 建议采用哪种描述
3. 优化后的完整描述

格式：
{
  "consistent": true/false,
  "conflicts": ["冲突1", "冲突2"],
  "recommendation": "建议",
  "optimized_description": "优化后的描述"
}
"""

    val SCENE_EMOTION_PROMPT = """请分析以下小说文本的情绪基调，并给出视频化建议。

文本内容：{text}

请分析：
1. 主要情绪（多选）
2. 情绪强度（1-5）
3. 建议的视觉表现手法
4. 建议的音乐氛围
5. 建议的镜头节奏

格式：
{
  "primary_emotions": ["情绪1", "情绪2"],
  "intensity": 3,
  "visual_suggestions": ["建议1", "建议2"],
  "music_mood": "音乐氛围",
  "pace": "fast/medium/slow"
}
"""

    fun fillTemplate(template: String, params: Map<String, String>): String {
        var result = template
        params.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }
}
