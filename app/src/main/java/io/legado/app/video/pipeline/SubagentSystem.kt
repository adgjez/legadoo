package io.legado.app.video.pipeline

import android.content.Context
import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.TextGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 多 Agent 子智能体系统
 *
 * 借鉴 ArcReel 的 Subagent 架构：
 * - 主 Agent 不直接处理所有任务，而是委派给聚焦的 Subagent
 * - 每个 Subagent 只完成一项任务，返回精炼摘要
 * - 主 Agent 只看到蒸馏后的输出，避免上下文膨胀
 *
 * 命名 Subagent：
 * - analyze-characters-clues：全局角色/线索提取
 * - split-narration-segments：说书模式片段拆分
 * - normalize-drama-script：剧集模式剧本规范化
 * - create-episode-script：创建分集剧本
 * - asset-generation：资产生成（角色设计图、线索参考图）
 */

interface Subagent {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any?>): SubagentResult
}

data class SubagentResult(
    val success: Boolean,
    val summary: String,
    val data: Map<String, Any?> = emptyMap(),
    val errors: List<String> = emptyList()
)

class AnalyzeCharactersCluesSubagent(private val context: Context) : Subagent {

    override val name = "analyze-characters-clues"
    override val description = "全局角色/线索提取。从小说中提取可用于AI视频生成的角色和线索信息。"

    override suspend fun execute(params: Map<String, Any?>): SubagentResult = withContext(Dispatchers.IO) {
        val projectId = params["projectId"] as? String ?: return@withContext SubagentResult(false, "缺少 projectId")
        val novelText = params["novelText"] as? String ?: return@withContext SubagentResult(false, "缺少 novelText")
        val existingCharacterNames = (params["existingCharacters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val prompt = buildString {
            append("""你是一位专业的小说角色与世界观分析师。请从以下小说文本中提取可用于 AI 视频生成的角色和线索信息。

## 核心原则
1. 只提取视觉信息：外貌、服装、标志物、色彩关键词——不包含性格、关系、剧情
2. 增量追加：已存在的角色/线索（${existingCharacterNames.joinToString(", ")}）跳过
3. 输出结构化 JSON

## 角色提取规则
- 识别在小说中有实质出场的角色
- description 只包含视觉描述：
  - 外貌要点（五官、身材、标志性特征）
  - 服装（款式、颜色、材质）
  - 标志物（配饰、武器、道具）
  - 色彩关键词（主色调、辅助色）
  - 风格标签

## 线索提取规则
- 提取重复出现或具有视觉特征的场景和道具
- type: location（环境/场景）或 prop（道具/物品）
- importance: major（反复出现）或 minor（次要）

""")
            append("小说文本（前4000字）：\n")
            append(novelText.take(4000))
        }

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位专业的小说角色与世界观分析师，只提取视觉相关信息。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.2f,
                maxTokens = 8192
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val content = textResult.content
                SubagentResult(
                    success = true,
                    summary = "完成角色/线索分析，共提取角色和线索",
                    data = mapOf("rawResponse" to content)
                )
            },
            onFailure = { error ->
                SubagentResult(false, "角色分析失败: ${error.message}", errors = listOf(error.message ?: "未知错误"))
            }
        )
    }
}

class SplitNarrationSegmentsSubagent(private val context: Context) : Subagent {

    override val name = "split-narration-segments"
    override val description = "说书模式片段拆分。按阅读节奏将小说文本拆分成指定数量的片段。"

    override suspend fun execute(params: Map<String, Any?>): SubagentResult = withContext(Dispatchers.IO) {
        val novelText = params["novelText"] as? String ?: return@withContext SubagentResult(false, "缺少 novelText")
        val segmentCount = (params["segmentCount"] as? Number)?.toInt() ?: 10
        val referencedCharacters = (params["characters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val referencedClues = (params["clues"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val prompt = buildString {
            append("""你是一位说书音频编辑。请将小说按阅读节奏拆分成 $segmentCount 个片段。

## 要求
1. 每个片段是完整的阅读单元，不要在句子中间断开
2. 标注预估阅读时长（正常语速 300字/分钟）
3. 识别每个片段中出场的角色和线索
4. 角色列表：${referencedCharacters.joinToString("、")}
5. 线索列表：${referencedClues.joinToString("、")}

## 输出格式
返回 JSON 数组，每项包含：
- index: 序号
- novel_text: 片段原文
- reading_duration: 预估时长（秒）
- referenced_characters: 出场角色
- referenced_clues: 出现的线索

""")
            append("小说文本：\n")
            append(novelText.take(6000))
        }

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位说书音频编辑，擅长按阅读节奏拆分文本。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.2f,
                maxTokens = 8192
            )
        )

        result.fold(
            onSuccess = { textResult ->
                SubagentResult(
                    success = true,
                    summary = "说书片段拆分完成",
                    data = mapOf("segments" to textResult.content)
                )
            },
            onFailure = { error ->
                SubagentResult(false, "拆分失败: ${error.message}", errors = listOf(error.message ?: ""))
            }
        )
    }
}

class NormalizeDramaScriptSubagent(private val context: Context) : Subagent {

    override val name = "normalize-drama-script"
    override val description = "剧集模式剧本规范化。将小说改编成结构化的剧本格式，包含场景、对白、动作。"

    override suspend fun execute(params: Map<String, Any?>): SubagentResult = withContext(Dispatchers.IO) {
        val novelText = params["novelText"] as? String ?: return@withContext SubagentResult(false, "缺少 novelText")
        val characters = (params["characters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val prompt = buildString {
            append("""你是一位剧集动画编剧。请将小说改编成剧本格式。

## 要求
1. 按场景分割，每个场景包含：场景描述、角色对话、动作指示
2. 格式标注：
   - [场景] 地点、时间、环境
   - [角色] 角色名称 + 动作/表情
   - [对白] 角色说的话
3. 角色列表：${characters.joinToString("、")}
4. 识别每个场景的出场角色和线索

## 输出格式
返回 JSON 数组，每项包含：
- index: 序号
- speaker: 说话角色
- dialogue: 对白内容
- action: 动作/表情
- scene_description: 场景描述
- referenced_characters: 涉及角色
- referenced_clues: 涉及线索

""")
            append("小说文本：\n")
            append(novelText.take(6000))
        }

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位剧集动画编剧。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.3f,
                maxTokens = 8192
            )
        )

        result.fold(
            onSuccess = { textResult ->
                SubagentResult(
                    success = true,
                    summary = "剧集剧本规范化完成",
                    data = mapOf("utterances" to textResult.content)
                )
            },
            onFailure = { error ->
                SubagentResult(false, "规范化失败: ${error.message}", errors = listOf(error.message ?: ""))
            }
        )
    }
}

class CreateEpisodeScriptSubagent(private val context: Context) : Subagent {

    override val name = "create-episode-script"
    override val description = "创建分集剧本。将长小说按章节切分，生成每集的结构化剧本。"

    override suspend fun execute(params: Map<String, Any?>): SubagentResult = withContext(Dispatchers.IO) {
        val novelText = params["novelText"] as? String ?: return@withContext SubagentResult(false, "缺少 novelText")
        val episodeCount = (params["episodeCount"] as? Number)?.toInt() ?: 3
        val mode = params["mode"] as? String ?: "narration"

        val prompt = """你是一位资深的剧集策划。请将小说切分成 $episodeCount 集。

## 要求
1. 每集应该有明确的主题和节奏（起承转合）
2. 每集的结尾应该有钩子吸引继续观看
3. 标注每集的标题、关键事件、情绪基调
4. 模式：$mode（narration=说书 / drama=剧集）

## 输出格式
返回 JSON 数组，每项包含：
- episode_index: 集序号
- title: 集标题
- summary: 剧情摘要
- key_events: 关键事件列表
- emotional_arc: 情绪走向
- start_chapter: 起始位置
- end_chapter: 结束位置"""

        val result = BackendRouter.generateText(
            TextGenerationRequest(
                messages = listOf(
                    mapOf("role" to "system", "content" to "你是一位资深的剧集策划。"),
                    mapOf("role" to "user", "content" to prompt)
                ),
                temperature = 0.3f,
                maxTokens = 4096
            )
        )

        result.fold(
            onSuccess = { textResult ->
                SubagentResult(
                    success = true,
                    summary = "分集规划完成",
                    data = mapOf("episodes" to textResult.content)
                )
            },
            onFailure = { error ->
                SubagentResult(false, "分集失败: ${error.message}")
            }
        )
    }
}

class AssetGenerationSubagent(private val context: Context) : Subagent {

    override val name = "asset-generation"
    override val description = "资产生成。为角色和线索生成设计参考图。"

    override suspend fun execute(params: Map<String, Any?>): SubagentResult = withContext(Dispatchers.IO) {
        val projectId = params["projectId"] as? String ?: return@withContext SubagentResult(false, "缺少 projectId")
        val assetType = params["assetType"] as? String ?: "character"
        val assetName = params["assetName"] as? String ?: ""
        val visualDesc = params["visualDescription"] as? String ?: ""

        val prompt = buildString {
            append("Character design sheet. ")
            append(visualDesc.ifBlank { assetName })
            append(". Full body shot, character sheet, white background, front view, high detail")
        }

        try {
            val result = BackendRouter.generateImage(
                io.legado.app.video.api.ImageGenerationRequest(
                    prompt = prompt,
                    width = 1024,
                    height = 1024,
                    count = 1
                )
            )

            result.fold(
                onSuccess = { imageResult ->
                    val url = imageResult.images.firstOrNull()?.url
                    SubagentResult(
                        success = true,
                        summary = "${assetType}设计图生成完成",
                        data = mapOf("imageUrl" to (url ?: ""))
                    )
                },
                onFailure = { error ->
                    SubagentResult(false, "设计图生成失败: ${error.message}")
                }
            )
        } catch (e: Exception) {
            SubagentResult(false, "生成异常: ${e.message}")
        }
    }
}

object SubagentRegistry {
    private val agents = mutableMapOf<String, Subagent>()

    fun register(agent: Subagent) {
        agents[agent.name] = agent
    }

    fun get(name: String): Subagent? = agents[name]

    fun getAll(): List<Subagent> = agents.values.toList()

    fun initDefaults(context: Context) {
        register(AnalyzeCharactersCluesSubagent(context))
        register(SplitNarrationSegmentsSubagent(context))
        register(NormalizeDramaScriptSubagent(context))
        register(CreateEpisodeScriptSubagent(context))
        register(AssetGenerationSubagent(context))
    }

    suspend fun execute(name: String, params: Map<String, Any?>): SubagentResult {
        val agent = agents[name] ?: return SubagentResult(false, "Subagent not found: $name")
        return agent.execute(params)
    }

    fun getAvailableAgentsSummary(): String {
        return agents.values.joinToString("\n") { agent ->
            "- **${agent.name}**: ${agent.description}"
        }
    }
}
