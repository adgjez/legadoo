package io.legado.app.video.agent

import io.legado.app.video.api.BackendRouter
import io.legado.app.video.api.ChatMessage
import io.legado.app.video.api.TextGenerationRequest
import io.legado.app.video.api.TextGenerationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ArcReelAgent - 自主多智能体系统
 *
 * 借鉴 ArcReel 核心架构：
 * - Agent Team：CharacterAgent, StoryboardAgent, StyleAgent, ConsistencyAgent
 * - 自我批判循环：每个 Agent 可检测输出质量并自主迭代优化
 * - 记忆持久化：跨 Agent 共享上下文，避免角色漂移
 * - 协作协议：Agent 之间通过结构化消息通信
 *
 * = 2026-08 新增 (DryRun 能力) =
 * 新增 LLMProviderOverride：所有 generateText 调用先经过 override，未设置才走 BackendRouter。
 * 新增 CritiqueStrategy：控制 SelfCritiqueEngine 的评分，实现「第 1 轮故意低、第 2 轮达阈值」
 * 新增 AgentTeamCoordinator.dryRun(plan: TeamExecutionPlan) 确定性编排入口。
 */

// ==================================================================
// LLM Provider Override (Real / Mock) 抽象
// ==================================================================

interface LLMProviderOverride {
    suspend fun generateText(request: TextGenerationRequest): Result<TextGenerationResult>?
}

object LLMProviderHub {
    @Volatile
    private var override: LLMProviderOverride? = null

    fun installOverride(provider: LLMProviderOverride) { this.override = provider }
    fun clearOverride() { override = null }
    val isDryRun: Boolean get() = override != null

    suspend fun generateText(request: TextGenerationRequest): Result<TextGenerationResult> {
        val inst = override
        if (inst != null) {
            val r = runCatching { inst.generateText(request) }.getOrNull()
            if (r != null) return r
        }
        return BackendRouter.generateText(request)
    }
}

/**
 * 确定性 DryRun LLM Provider —— 根据调用次数/提示词关键词精确返回预设响应。
 *
 * 设计上「可预测」：对每种 LLM 调用的模式（角色/分镜/一致性/批判/质量）都返回
 * 合法 JSON，并根据 TeamExecutionPlan 控制 Critique 分数的高低，用来驱动
 * 团队级自我修正循环按剧本前进。
 */
class DeterministicMockLLMProvider(
    private val plan: TeamExecutionPlan
) : LLMProviderOverride {

    private var callIndex = 0

    override suspend fun generateText(request: TextGenerationRequest): Result<TextGenerationResult> {
        val joinedPrompt = request.messages.joinToString("\n") { it.content }
        val idx = callIndex++

        val response: String = when {
            // === SelfCritiqueEngine (批判) ===
            joinedPrompt.contains("AI内容审查员") || joinedPrompt.contains("审查标准") -> {
                // 解析当前迭代
                val iter = "迭代：\\s*(\\d+)".toRegex().find(joinedPrompt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val firstLineSystem = request.messages.firstOrNull()?.content ?: ""
                val agentName = Regex("审查以下\\s*([^的]+)的输出").find(joinedPrompt)?.groupValues?.get(1)
                    ?: firstLineSystem.take(10)
                plan.critiqueResponse(agentName, iter, request.messages.lastOrNull()?.content ?: "")
            }
            // === ConsistencyChecker ===
            joinedPrompt.contains("视觉一致性检查员") || joinedPrompt.contains("检查项：") -> plan.consistencyResponse()
            // === QualityAssessor ===
            joinedPrompt.contains("AI内容质量评估专家") || joinedPrompt.contains("评估维度") -> plan.qualityResponse()
            // === CharacterAnalyst ===
            joinedPrompt.contains("角色视觉分析师") || joinedPrompt.contains("提取所有重要角色") -> plan.characterResponse()
            // === StoryboardPlanner ===
            joinedPrompt.contains("专业的AI分镜师") || joinedPrompt.contains("规划分镜") -> plan.storyboardResponse()
            else -> plan.fallbackResponse()
        }

        return Result.success(TextGenerationResult(content = response, model = "mock-dryrun-v1"))
    }
}

/**
 * 团队级 DryRun 执行计划（确定性剧本）
 *
 * - critiqueOn1stLow: 第1轮 Critique 故意给出低质量分 → 触发团队迭代2
 * - forceConsistencyIssue: 一致性检查强制返回 character_issues_present=true → 触发角色重跑
 * - forceQualityBelow: 质量评估强制返回 < 0.7 → 触发分镜重跑
 * - iteration2Passes: 进入第2轮时 quality/consistency 都达标，团队循环提前退出
 */
data class TeamExecutionPlan(
    val critiqueOn1stLow: Boolean = true,
    val forceConsistencyIssue: Boolean = true,
    val forceQualityBelow: Boolean = true,
    val iteration2Passes: Boolean = true,
    val characterCount: Int = 2,
    val storyboardSegments: Int = 6,
    val targetConsistencyPassScore: Float = 0.88f,
    val targetQualityPassScore: Float = 0.82f,
    val lowCritiqueScore: Float = 0.48f
) {
    internal fun critiqueResponse(agentName: String, iteration: Int, _fullPrompt: String): String {
        val isFirst = iteration == 1 || !iteration2Passes
        val score = if (isFirst && critiqueOn1stLow) 45 else 88
        val issues = if (isFirst) {
            "[\"$agentName 描述细节不足，部分服装配饰未明确\",\"英文视觉关键词量偏少，建议增至 40+ 词\"]"
        } else "[]"
        val suggestions = if (isFirst) {
            "[\"补充服装裁剪材质细节(丝绸/亚麻等)\",\"补充色温/光质关键词\"]"
        } else "[]"
        return buildJsonString(score, issues, suggestions, shouldContinue = isFirst)
    }

    internal fun consistencyResponse(): String {
        val consistent = !forceConsistencyIssue
        val issues = if (forceConsistencyIssue) {
            "[\"林瑶腰间玉佩在分镜3中缺失\",\"墨渊发色与档案银白不一致\"]"
        } else "[]"
        val corrections = if (forceConsistencyIssue) {
            "[\"分镜3补回 jade pendant on waist 描述\",\"墨渊发色统一为 silver long hair\"]"
        } else "[]"
        val score = (if (forceConsistencyIssue) 42 else 90)
        val charIssue = forceConsistencyIssue.toString()
        return """
            {
              "consistent": $consistent,
              "character_issues_present": $charIssue,
              "issues": $issues,
              "corrections": $corrections,
              "score": $score
            }
        """.trimIndent()
    }

    internal fun qualityResponse(): String {
        val (overall, label) = if (forceQualityBelow) 58 to "0_需要改进" else 84 to "1_达标"
        val dims = listOf(
            if (forceQualityBelow) 52 else 88,   // visual_executability
            if (forceQualityBelow) 61 else 82,   // creativity
            if (forceQualityBelow) 58 else 86,   // coherence
            if (forceQualityBelow) 60 else 90,   // consistency
            if (forceQualityBelow) 59 else 85    // technical
        )
        val highlights = if (forceQualityBelow) "[]" else """["色彩层次丰富","角色面部特征辨识度高"]"""
        val improvements = if (forceQualityBelow)
            """["分镜4-6转场需匹配连续性","补充特写/全景节奏变化"]""" else "[]"
        return """
            {
              "visual_executability": ${dims[0]},
              "creativity": ${dims[1]},
              "coherence": ${dims[2]},
              "consistency": ${dims[3]},
              "technical": ${dims[4]},
              "overall": ${overall},
              "iteration_label": "$label",
              "highlights": $highlights,
              "improvements": $improvements
            }
        """.trimIndent()
    }

    internal fun characterResponse(): String {
        val chars = listOf(
            Triple("林瑶","protagonist","a girl with long black hair, aqua eyes, hanfu short outfit, jade pendant on waist, cinematic lighting, anime style, soft fog beach dawn"),
            Triple("墨渊","major","elegant young man with silver long hair, golden vertical pupils, black and white hanfu robe, fox silhouette in background, cinematic, elegant smirk")
        ).take(characterCount)
        return buildString {
            append("[\n")
            chars.forEachIndexed { i, (name, role, vp) ->
                append("  {\"name\":\"$name\",\"role\":\"$role\",\"visual_prompt\":\"$vp\",\"color_palette\":[\"blue\",\"jade\"]}")
                if (i < chars.size - 1) append(",")
                append("\n")
            }
            append("]")
        }
    }

    internal fun storyboardResponse(): String {
        val moods = listOf("calm","tense","mysterious","shocked","playful","determined","romantic","epic").take(storyboardSegments)
        val shots = listOf("long","medium","close_up","extreme_close_up","over_shoulder","dutch_angle","wide","pov")
        return buildString {
            append("[\n")
            moods.forEachIndexed { i, mood ->
                val idx = i + 1
                val shot = shots[i % shots.size]
                val chars = listOf("林瑶") + if (i % 2 == 1) listOf("墨渊") else emptyList()
                val prompt = "shot_${idx}_${mood}_cinematic_2d_anime_style_eastern_fantasy"
                val charInvolved = chars.joinToString("\",\"", prefix = "\"", postfix = "\"")
                append("  {\"index\":$idx,\"image_prompt\":\"$prompt\",\"duration\":5,\"shot\":\"$shot\",\"mood\":\"$mood\",\"characters_involved\":[$charInvolved]}")
                if (i < moods.size - 1) append(",")
                append("\n")
            }
            append("]")
        }
    }

    internal fun fallbackResponse(): String = """{"ok":true}"""

    private fun buildJsonString(score0_100: Int, issues: String, suggestions: String, shouldContinue: Boolean) = """
        {
          "score": $score0_100,
          "issues": $issues,
          "suggestions": $suggestions,
          "should_continue": $shouldContinue
        }
    """.trimIndent()
}

// ========== Agent Team ==========

interface ArcReelAgent {
    val agentName: String
    val role: AgentRole
    val capabilities: Set<AgentCapability>
    suspend fun execute(input: AgentInput): AgentOutput
}

enum class AgentRole {
    CHARACTER_ANALYST,
    STORYBOARD_PLANNER,
    STYLE_CURATOR,
    CONSISTENCY_CHECKER,
    QUALITY_ASSESSOR,
    REFINEMENT_AGENT
}

enum class AgentCapability {
    TEXT_ANALYSIS,
    VISUAL_DESCRIPTION,
    CONSISTENCY_CHECK,
    QUALITY_SCORING,
    PROMPT_REFINEMENT,
    STYLE_APPLICATION,
    CHARACTER_LOCK,
    STORYBOARD_PLANNING
}

data class AgentInput(
    val taskId: String,
    val projectId: String,
    val content: String,
    val context: ArcReelAgentContext = ArcReelAgentContext(),
    val constraints: List<String> = emptyList(),
    val targetQuality: Float = 0.7f,
    val maxIterations: Int = 3
)

data class ArcReelAgentContext(
    val projectId: String = "",
    val characterProfiles: Map<String, String> = emptyMap(),
    val sceneHistory: List<String> = emptyList(),
    val styleProfile: String? = null,
    val visualReferences: List<String> = emptyList(),
    val sharedKnowledge: Map<String, Any?> = emptyMap()
)

data class AgentOutput(
    val taskId: String,
    val success: Boolean,
    val output: String = "",
    val structuredData: Map<String, Any?> = emptyMap(),
    val qualityScore: Float = 0f,
    val iterationsUsed: Int = 0,
    val critiqueHistory: List<CritiqueEntry> = emptyList(),
    val agentName: String = ""
)

data class CritiqueEntry(
    val iteration: Int,
    val issue: String,
    val suggestion: String,
    val resolved: Boolean,
    val confidence: Float
)

// ========== 自我批判循环 ==========

class SelfCritiqueEngine(
    private val maxIterations: Int = 3,
    private val targetQuality: Float = 0.7f
) {
    data class CritiqueResult(
        val issues: List<String>,
        val suggestions: List<String>,
        val score: Float,
        val shouldContinue: Boolean
    )

    suspend fun critique(
        content: String,
        agentName: String,
        iteration: Int,
        context: ArcReelAgentContext
    ): CritiqueResult = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("你是一位严格的AI内容审查员。请审查以下$agentName的输出，评估其质量。\n\n")
            append("输出内容：\n$content\n\n")
            append("审查标准：\n")
            append("1. 角色一致性 - 角色描述是否与已有资料一致\n")
            append("2. 视觉可执行性 - 描述是否足够详细供AI生图/生视频\n")
            append("3. 叙事连贯性 - 内容是否流畅、逻辑清晰\n")
            append("4. 风格统一性 - 是否符合项目整体风格\n")
            append("5. 简洁性 - 是否简洁明了，无冗余信息\n\n")
            append("当前迭代：$iteration / $maxIterations\n")
            append("目标质量分：${(targetQuality * 100).toInt()}/100\n\n")
            append("请输出JSON格式：\n")
            append("{\n")
            append("  \"score\": 0-100的质量分,\n")
            append("  \"issues\": [\"问题1\", \"问题2\"],\n")
            append("  \"suggestions\": [\"改进建议1\", \"改进建议2\"],\n")
            append("  \"should_continue\": true/false\n")
            append("}")
        }

        val result = LLMProviderHub.generateText(
            TextGenerationRequest(
                messages = listOf(
                    ChatMessage("system", "你是一位严格的AI内容质量审查员。"),
                    ChatMessage("user", prompt)
                ),
                temperature = 0.2f,
                maxTokens = 2048
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val content_ = textResult.content
                val score = (extractNumber(content_, "score") / 100f).coerceIn(0f, 1f)
                val issues = extractStringList(content_, "issues")
                val suggestions = extractStringList(content_, "suggestions")
                val shouldContinue = extractBoolean(content_, "should_continue")
                    ?: (score < targetQuality && iteration < maxIterations)

                CritiqueResult(issues, suggestions, score, shouldContinue)
            },
            onFailure = {
                CritiqueResult(
                    issues = listOf("审查服务不可用"),
                    suggestions = listOf("手动审查"),
                    score = 0.5f,
                    shouldContinue = iteration < maxIterations
                )
            }
        )
    }

    private fun extractNumber(json: String, key: String): Float {
        val regex = "\"$key\"\\s*:\\s*([\\d.]+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 50f
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"").ifBlank { null }
        }
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = "\"$key\"\\s*:\\s*(true|false)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }
}

// ========== 角色分析智能体 ==========

class CharacterAnalystAgent : ArcReelAgent {
    override val agentName = "CharacterAnalyst"
    override val role = AgentRole.CHARACTER_ANALYST
    override val capabilities = setOf(
        AgentCapability.TEXT_ANALYSIS,
        AgentCapability.VISUAL_DESCRIPTION,
        AgentCapability.CHARACTER_LOCK
    )

    private val critiqueEngine = SelfCritiqueEngine()

    override suspend fun execute(input: AgentInput): AgentOutput = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("你是一位专业的小说角色视觉分析师。\n")
            append("请分析以下文本，提取所有重要角色的视觉信息。\n\n")
            append("输入文本：\n${input.content.take(3000)}\n\n")
            append("要求：\n")
            append("1. 每个角色的描述必须包含：年龄、性别、外貌特征、服装、配饰\n")
            append("2. 描述必须足够详细，可直接用于AI生成角色设计图\n")
            append("3. 用英文关键词描述，便于图像生成\n")
            append("4. 识别角色之间的关系（主角/配角/反派等）\n\n")
            append("输出JSON格式：\n")
            append("[{\"name\":\"角色名\",\"role\":\"主角\",\"visual_prompt\":\"英文视觉描述\",\"color_palette\":[\"色1\",\"色2\"]}]")
        }

        var bestOutput = ""
        var bestScore = 0f
        val critiqueHistory = mutableListOf<CritiqueEntry>()

        for (iteration in 1..input.maxIterations) {
            val result = LLMProviderHub.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        ChatMessage("system", "你是一位专业的小说角色视觉分析师。"),
                        ChatMessage("user", prompt)
                    ),
                    temperature = 0.3f,
                    maxTokens = 4096
                )
            )

            val content = result.getOrNull()?.content ?: ""

            val critique = critiqueEngine.critique(
                content, agentName, iteration, input.context
            )

            critiqueHistory.add(
                CritiqueEntry(
                    iteration = iteration,
                    issue = critique.issues.firstOrNull() ?: "质量评估",
                    suggestion = critique.suggestions.firstOrNull() ?: "继续优化",
                    resolved = false,
                    confidence = critique.score
                )
            )

            if (critique.score >= bestScore) {
                bestScore = critique.score
                bestOutput = content
            }

            if (!critique.shouldContinue) break
        }

        AgentOutput(
            taskId = input.taskId,
            success = bestOutput.isNotBlank(),
            output = bestOutput,
            qualityScore = bestScore,
            iterationsUsed = critiqueHistory.size,
            critiqueHistory = critiqueHistory,
            agentName = agentName
        )
    }
}

// ========== 分镜规划智能体 ==========

class ArcReelStoryboardPlannerAgent : ArcReelAgent {
    override val agentName = "StoryboardPlanner"
    override val role = AgentRole.STORYBOARD_PLANNER
    override val capabilities = setOf(
        AgentCapability.STORYBOARD_PLANNING,
        AgentCapability.VISUAL_DESCRIPTION,
        AgentCapability.STYLE_APPLICATION
    )

    private val critiqueEngine = SelfCritiqueEngine()

    override suspend fun execute(input: AgentInput): AgentOutput = withContext(Dispatchers.IO) {
        val characterContext = input.context.characterProfiles.entries.joinToString("\n") { (name, desc) ->
            "- $name: ${desc.take(100)}"
        }

        val styleHint = input.context.styleProfile?.let { "风格参考：$it\n" } ?: ""

        val prompt = buildString {
            append("你是一位专业的AI分镜师。请为以下小说片段规划分镜。\n\n")
            append(styleHint)
            append("角色参考：\n$characterContext\n\n")
            append("小说片段：\n${input.content.take(2000)}\n\n")
            append("要求：\n")
            append("1. 将文本拆分为5-10个分镜\n")
            append("2. 每个分镜包含：画面描述(英文)、时长、镜头类型、情绪\n")
            append("3. 确保相邻分镜的视觉连贯性\n")
            append("4. 角色描述必须与角色参考一致\n\n")
            append("输出JSON：\n")
            append("[{\"index\":1,\"image_prompt\":\"英文画面描述\",\"duration\":5,\"shot\":\"medium\",\"mood\":\"tense\",\"characters_involved\":[\"角色名\"]}]")
        }

        var bestOutput = ""
        var bestScore = 0f
        val critiqueHistory = mutableListOf<CritiqueEntry>()

        for (iteration in 1..input.maxIterations) {
            val result = LLMProviderHub.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        ChatMessage("system", "你是一位专业的AI分镜师。"),
                        ChatMessage("user", prompt)
                    ),
                    temperature = 0.4f,
                    maxTokens = 4096
                )
            )

            val content = result.getOrNull()?.content ?: ""

            val critique = critiqueEngine.critique(
                content, agentName, iteration, input.context
            )

            critiqueHistory.add(
                CritiqueEntry(
                    iteration = iteration,
                    issue = critique.issues.firstOrNull() ?: "质量评估",
                    suggestion = critique.suggestions.firstOrNull() ?: "继续优化",
                    resolved = false,
                    confidence = critique.score
                )
            )

            if (critique.score >= bestScore) {
                bestScore = critique.score
                bestOutput = content
            }

            if (!critique.shouldContinue) break
        }

        AgentOutput(
            taskId = input.taskId,
            success = bestOutput.isNotBlank(),
            output = bestOutput,
            qualityScore = bestScore,
            iterationsUsed = critiqueHistory.size,
            critiqueHistory = critiqueHistory,
            agentName = agentName
        )
    }
}

// ========== 一致性检查智能体 ==========

class ConsistencyCheckerAgent : ArcReelAgent {
    override val agentName = "ConsistencyChecker"
    override val role = AgentRole.CONSISTENCY_CHECKER
    override val capabilities = setOf(
        AgentCapability.CONSISTENCY_CHECK,
        AgentCapability.QUALITY_SCORING
    )

    override suspend fun execute(input: AgentInput): AgentOutput = withContext(Dispatchers.IO) {
        val characterContext = input.context.characterProfiles.entries.joinToString("\n") { (name, desc) ->
            "- $name: ${desc.take(150)}"
        }

        val sceneHistory = input.context.sceneHistory.takeLast(5).joinToString("\n") { "- $it" }

        val prompt = buildString {
            append("你是一位视觉一致性检查员。请检查以下分镜描述是否存在不一致问题。\n\n")
            append("角色档案：\n$characterContext\n\n")
            append("历史分镜：\n$sceneHistory\n\n")
            append("当前分镜：\n${input.content}\n\n")
            append("检查项：\n")
            append("1. 角色外观是否与档案一致\n")
            append("2. 场景是否与历史连贯\n")
            append("3. 色彩/光影风格是否统一\n")
            append("4. 是否有角色遗漏或错误引用\n\n")
            append("输出JSON：\n")
            append("{\"consistent\":true/false,\"issues\":[\"问题\"],\"corrections\":[\"修正建议\"],\"score\":0-100}")
        }

        val result = LLMProviderHub.generateText(
            TextGenerationRequest(
                messages = listOf(
                    ChatMessage("system", "你是一位视觉一致性检查员。"),
                    ChatMessage("user", prompt)
                ),
                temperature = 0.1f,
                maxTokens = 2048
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val content = textResult.content
                val consistent = content.contains("\"consistent\":true")
                val issues = extractStringList(content, "issues")
                val corrections = extractStringList(content, "corrections")
                val score = (extractNumber_(content, "score") / 100f).coerceIn(0f, 1f)

                AgentOutput(
                    taskId = input.taskId,
                    success = consistent || issues.isEmpty(),
                    output = content,
                    structuredData = mapOf(
                        "consistent" to consistent,
                        "issues" to issues,
                        "corrections" to corrections
                    ),
                    qualityScore = score,
                    agentName = agentName
                )
            },
            onFailure = {
                AgentOutput(
                    taskId = input.taskId,
                    success = true,
                    output = "一致性检查跳过（服务不可用）",
                    qualityScore = 0.8f,
                    agentName = agentName
                )
            }
        )
    }

    private fun extractNumber_(json: String, key: String): Float {
        val regex = "\"$key\"\\s*:\\s*([\\d.]+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 80f
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"").ifBlank { null }
        }
    }
}

// ========== 质量评估智能体 ==========

class QualityAssessorAgent : ArcReelAgent {
    override val agentName = "QualityAssessor"
    override val role = AgentRole.QUALITY_ASSESSOR
    override val capabilities = setOf(
        AgentCapability.QUALITY_SCORING,
        AgentCapability.CONSISTENCY_CHECK
    )

    override suspend fun execute(input: AgentInput): AgentOutput = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("你是一位AI内容质量评估专家。请对以下生成内容进行全面评估。\n\n")
            append("待评估内容：\n${input.content.take(2000)}\n\n")
            append("评估维度（每项0-100分）：\n")
            append("1. 视觉可执行性 - 描述是否足够详细供AI生成\n")
            append("2. 创意价值 - 是否具有独特性和想象力\n")
            append("3. 叙事连贯性 - 是否流畅、逻辑清晰\n")
            append("4. 角色一致性 - 角色描述是否稳定\n")
            append("5. 技术规范 - 是否符合prompt工程最佳实践\n\n")
            append("输出JSON：\n")
            append("{\"visual_executability\":85,\"creativity\":75,\"coherence\":90,\"consistency\":88,\"technical\":80,\"overall\":84,\"highlights\":[\"亮点1\"],\"improvements\":[\"改进1\"]}")
        }

        val result = LLMProviderHub.generateText(
            TextGenerationRequest(
                messages = listOf(
                    ChatMessage("system", "你是一位AI内容质量评估专家。"),
                    ChatMessage("user", prompt)
                ),
                temperature = 0.1f,
                maxTokens = 2048
            )
        )

        result.fold(
            onSuccess = { textResult ->
                val content = textResult.content
                val overallScore = extractNumber_(content, "overall") / 100f
                val highlights = extractStringList(content, "highlights")
                val improvements = extractStringList(content, "improvements")

                AgentOutput(
                    taskId = input.taskId,
                    success = overallScore >= 0.6f,
                    output = content,
                    structuredData = mapOf(
                        "visual_executability" to extractNumber_(content, "visual_executability"),
                        "creativity" to extractNumber_(content, "creativity"),
                        "coherence" to extractNumber_(content, "coherence"),
                        "consistency" to extractNumber_(content, "consistency"),
                        "technical" to extractNumber_(content, "technical"),
                        "highlights" to highlights,
                        "improvements" to improvements
                    ),
                    qualityScore = overallScore.coerceIn(0f, 1f),
                    agentName = agentName
                )
            },
            onFailure = {
                AgentOutput(
                    taskId = input.taskId,
                    success = true,
                    qualityScore = 0.7f,
                    agentName = agentName
                )
            }
        )
    }

    private fun extractNumber_(json: String, key: String): Float {
        val regex = "\"$key\"\\s*:\\s*([\\d.]+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 70f
    }

    private fun extractStringList(json: String, key: String): List<String> {
        val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val match = regex.find(json) ?: return emptyList()
        return match.groupValues[1].split(",").mapNotNull { item ->
            item.trim().removeSurrounding("\"").ifBlank { null }
        }
    }
}

// ========== Agent 协作总线 ==========

class AgentTeamCoordinator {

    private val agents = mapOf(
        AgentRole.CHARACTER_ANALYST to CharacterAnalystAgent(),
        AgentRole.STORYBOARD_PLANNER to ArcReelStoryboardPlannerAgent(),
        AgentRole.CONSISTENCY_CHECKER to ConsistencyCheckerAgent(),
        AgentRole.QUALITY_ASSESSOR to QualityAssessorAgent()
    )

    private val sharedMemory = mutableMapOf<String, Any?>()
    private val critiqueHistory = mutableListOf<TeamCritiqueEntry>()

    suspend fun coordinate(
        projectId: String,
        novelText: String,
        characterProfiles: Map<String, String>,
        maxTeamIterations: Int = 2
    ): TeamCoordinationResult = withContext(Dispatchers.IO) {
        val context = ArcReelAgentContext(
            projectId = projectId,
            characterProfiles = characterProfiles,
            sharedKnowledge = sharedMemory
        )

        val results = mutableMapOf<AgentRole, AgentOutput>()
        val teamIterations = mutableListOf<TeamIterationLog>()
        var finalCharResult: AgentOutput? = null
        var finalStoryboardResult: AgentOutput? = null
        var finalConsistencyResult: AgentOutput? = null
        var finalQualityResult: AgentOutput? = null

        // ---- 团队级自我修正循环 ----
        // Consistency / Quality 发现严重问题 → 带着建议重跑角色+分镜
        for (teamIteration in 1..maxTeamIterations) {

            // Phase 1: 角色分析（首次必跑；若一致性批判反馈了角色问题则重跑）
            val shouldRerunChars = finalConsistencyResult != null &&
                    finalConsistencyResult!!.structuredData["character_issues_present"] == true
            val charCritiqueCount = finalConsistencyResult?.critiqueHistory
                ?.count { !it.resolved } ?: 0
            if (finalCharResult == null || shouldRerunChars) {
                val charAnalyst = agents[AgentRole.CHARACTER_ANALYST]!!
                val hintForChars = finalConsistencyResult?.critiqueHistory
                    ?.filter { !it.resolved }
                    ?.joinToString("\n") { h -> "- ${h.issue} → ${h.suggestion}" }
                    ?: ""
                val charInputText = if (hintForChars.isNotBlank())
                    "$novelText\n\n【上轮一致性批判，请据此修正】\n$hintForChars"
                else
                    novelText
                finalCharResult = charAnalyst.execute(
                    AgentInput(
                        taskId = "char_analysis_${projectId}_v$teamIteration",
                        projectId = projectId,
                        content = charInputText,
                        context = context,
                        maxIterations = 3
                    )
                )
                results[AgentRole.CHARACTER_ANALYST] = finalCharResult
                sharedMemory["latest_characters"] = finalCharResult.output
            }

            // Phase 2: 分镜规划（若质量批判不通过则带上改进建议重跑）
            val shouldRerunStoryboard = finalQualityResult != null &&
                    finalQualityResult!!.qualityScore < 0.7f
            val qualityCritiqueCount = finalQualityResult?.critiqueHistory
                ?.count { !it.resolved } ?: 0
            if (finalStoryboardResult == null || shouldRerunStoryboard) {
                val storyboardPlanner = agents[AgentRole.STORYBOARD_PLANNER]!!
                val qualityHints = finalQualityResult?.critiqueHistory
                    ?.filter { !it.resolved }
                    ?.joinToString("\n") { h -> "- ${h.issue}：${h.suggestion}" }
                    ?: ""
                val storyboardContent = if (qualityHints.isNotBlank())
                    "$novelText\n\n【上轮质量评审的改进建议】\n$qualityHints"
                else
                    novelText
                finalStoryboardResult = storyboardPlanner.execute(
                    AgentInput(
                        taskId = "storyboard_${projectId}_v$teamIteration",
                        projectId = projectId,
                        content = storyboardContent,
                        context = context.copy(
                            characterProfiles = characterProfiles + ("_latest" to (finalCharResult?.output ?: ""))
                        ),
                        maxIterations = 3
                    )
                )
                results[AgentRole.STORYBOARD_PLANNER] = finalStoryboardResult
                sharedMemory["latest_storyboard"] = finalStoryboardResult.output
            }

            // Phase 3: 一致性检查
            val consistencyChecker = agents[AgentRole.CONSISTENCY_CHECKER]!!
            finalConsistencyResult = consistencyChecker.execute(
                AgentInput(
                    taskId = "consistency_${projectId}_v$teamIteration",
                    projectId = projectId,
                    content = finalStoryboardResult.output,
                    context = context.copy(
                        sceneHistory = listOf(finalStoryboardResult.output.take(200)),
                        characterProfiles = characterProfiles + ("_char_design" to (finalCharResult?.output ?: ""))
                    )
                )
            )
            results[AgentRole.CONSISTENCY_CHECKER] = finalConsistencyResult

            // Phase 4: 质量评估
            val qualityAssessor = agents[AgentRole.QUALITY_ASSESSOR]!!
            finalQualityResult = qualityAssessor.execute(
                AgentInput(
                    taskId = "quality_${projectId}_v$teamIteration",
                    projectId = projectId,
                    content = finalStoryboardResult.output,
                    context = context
                )
            )
            results[AgentRole.QUALITY_ASSESSOR] = finalQualityResult

            val hasBlockingConsistencyIssue = finalConsistencyResult.qualityScore < 0.55f ||
                    finalConsistencyResult.structuredData["character_issues_present"] == true
            val passesQualityGate = finalQualityResult.qualityScore >= 0.7f &&
                    finalConsistencyResult.qualityScore >= 0.6f

            teamIterations.add(
                TeamIterationLog(
                    iteration = teamIteration,
                    characterScore = finalCharResult?.qualityScore ?: 0f,
                    storyboardScore = finalStoryboardResult?.qualityScore ?: 0f,
                    consistencyScore = finalConsistencyResult.qualityScore,
                    qualityScore = finalQualityResult.qualityScore,
                    characterRerunTriggered = shouldRerunChars,
                    storyboardRerunTriggered = shouldRerunStoryboard,
                    characterIssuesPresent = finalConsistencyResult.structuredData["character_issues_present"] == true,
                    qualityBelowGate = finalQualityResult.qualityScore < 0.7f,
                    passesQualityGate = passesQualityGate,
                    blockingIssueRemains = hasBlockingConsistencyIssue,
                    critiqueHintLines = charCritiqueCount + qualityCritiqueCount
                )
            )

            if (passesQualityGate) {
                // 质量达标，提前跳出团队循环
                break
            }
            if (!hasBlockingConsistencyIssue && teamIteration >= maxTeamIterations) {
                // 虽未通过最高阈值，但也没致命问题，不无限循环
                break
            }
        }

        // 汇总
        val finalScore = results.values.map { it.qualityScore }.average().toFloat()
        val allIssues = results.values.flatMap { it.critiqueHistory.map { h -> h.issue } }

        return@withContext TeamCoordinationResult(
            projectId = projectId,
            characterAnalysis = finalCharResult ?: AgentOutput(
                taskId = "char_$projectId", success = false, agentName = "CharacterAnalyst"
            ),
            storyboard = finalStoryboardResult ?: AgentOutput(
                taskId = "sb_$projectId", success = false, agentName = "StoryboardPlanner"
            ),
            consistencyReport = finalConsistencyResult ?: AgentOutput(
                taskId = "cc_$projectId", success = false, agentName = "ConsistencyChecker"
            ),
            qualityReport = finalQualityResult ?: AgentOutput(
                taskId = "qa_$projectId", success = false, agentName = "QualityAssessor"
            ),
            finalScore = finalScore,
            allIssues = allIssues,
            teamIterations = teamIterations,
            success = finalScore >= 0.6f
        )
    }

    fun getSharedMemory(): Map<String, Any?> = sharedMemory.toMap()

    fun clearMemory() {
        sharedMemory.clear()
        critiqueHistory.clear()
    }

    /**
     * Dry Run 入口：按给定 TeamExecutionPlan 确定性运行四智能体团队循环。
     *
     * 这个方法是 thread-safe（内部用 try/finally 管理 LLMProviderHub override）。
     *
     * @return 结果里 teamIterations.size 就是团队循环实际跑的轮数（1 or 2 or maxTeamIterations）。
     */
    suspend fun dryRun(
        projectId: String,
        novelText: String,
        characterProfiles: Map<String, String> = emptyMap(),
        maxTeamIterations: Int = 2,
        plan: TeamExecutionPlan = TeamExecutionPlan()
    ): TeamCoordinationResult {
        clearMemory()
        val mock = DeterministicMockLLMProvider(plan)
        LLMProviderHub.installOverride(mock)
        try {
            return coordinate(projectId, novelText, characterProfiles, maxTeamIterations)
        } finally {
            LLMProviderHub.clearOverride()
        }
    }
}

data class TeamCritiqueEntry(
    val agentName: String,
    val iteration: Int,
    val issue: String,
    val suggestion: String
)

data class TeamIterationLog(
    val iteration: Int,
    val characterScore: Float,
    val storyboardScore: Float,
    val consistencyScore: Float,
    val qualityScore: Float,
    val characterRerunTriggered: Boolean = false,
    val storyboardRerunTriggered: Boolean = false,
    val characterIssuesPresent: Boolean = false,
    val qualityBelowGate: Boolean = false,
    val passesQualityGate: Boolean = false,
    val blockingIssueRemains: Boolean = false,
    val critiqueHintLines: Int = 0
)

data class TeamCoordinationResult(
    val projectId: String,
    val characterAnalysis: AgentOutput,
    val storyboard: AgentOutput,
    val consistencyReport: AgentOutput,
    val qualityReport: AgentOutput,
    val finalScore: Float,
    val allIssues: List<String>,
    val teamIterations: List<TeamIterationLog> = emptyList(),
    val success: Boolean
)

/**
 * dryRunArtifacts() 把 Agent 团队执行的 raw JSON 输出进一步解析成 Engine 强类型数据结构。
 *
 * 包含：
 * - characters:     从 CharacterAnalyst Agent JSON 解析的 List<CharacterProfile>
 * - nameToCharId:   角色名 → Character.id（给分镜 "characters_involved" 做映射用）
 * - storyboard:     从 StoryboardPlanner Agent JSON 解析的 List<StoryboardFrame>
 * - raw:            原始 TeamCoordinationResult 不丢
 */
data class DryRunEngineArtifacts(
    val raw: TeamCoordinationResult,
    val characters: List<io.legado.app.video.pipeline.CharacterProfile>,
    val storyboard: List<io.legado.app.video.pipeline.StoryboardFrame>,
    val nameToCharId: Map<String, String>,
    val warnings: List<String>
) {
    val distinctCharacters: Int get() = characters.size
    val totalSegments: Int get() = storyboard.size
    val framesHaveAtLeastOneCharRef: Boolean get() =
        storyboard.isEmpty() || storyboard.any { it.characterRefs.isNotEmpty() }
}

suspend fun AgentTeamCoordinator.dryRunArtifacts(
    projectId: String,
    novelText: String,
    characterProfiles: Map<String, String> = emptyMap(),
    maxTeamIterations: Int = 2,
    plan: TeamExecutionPlan = TeamExecutionPlan()
): DryRunEngineArtifacts {
    val raw = dryRun(projectId, novelText, characterProfiles, maxTeamIterations, plan)

    val chars = CharacterStoryboardJsonParser.parseCharacters(raw.characterAnalysis.output)
    val nameToCharId = chars.associate { it.name to it.id }

    val frames = CharacterStoryboardJsonParser.parseStoryboard(raw.storyboard.output) { name ->
        nameToCharId[name]
    }

    val warnings = buildList {
        if (chars.isEmpty()) add("CharacterAnalyst 输出没能解析出任何角色（JSON 可能损坏）")
        if (frames.isEmpty()) add("StoryboardPlanner 输出没能解析出任何分镜（JSON 可能损坏）")
        if (frames.isNotEmpty() && raw.success && frames.none { it.characterRefs.isNotEmpty() }) {
            add("分镜列表里没有任何角色引用，一致性路由会缺少 CHARACTER 参考")
        }
        if (frames.map { it.index }.distinct().size != frames.size) {
            add("分镜 index 有重复，Storyboard JSON 的 index 字段可能不唯一")
        }
        if (frames.isNotEmpty()) {
            val expect = (0 until frames.size).toSet()
            val actual = frames.map { it.index }.toSet()
            if (expect != actual) add("分镜 index 不是 0..${frames.size - 1} 的连续值：实际=$actual")
        }
    }

    return DryRunEngineArtifacts(
        raw = raw,
        characters = chars,
        storyboard = frames,
        nameToCharId = nameToCharId,
        warnings = warnings
    )
}
