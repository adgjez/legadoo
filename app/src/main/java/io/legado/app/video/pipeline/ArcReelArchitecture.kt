package io.legado.app.video.pipeline

object ArcReelArchitecture {

    const val VERSION = "2.0"
    const val PHILOSOPHY = """
ArcReel 核心设计哲学（不是照抄 API，而是吸收思想）：

1. CHARACTER/CLUE LOCK（角色/线索锁定）
   - 先生成角色设计图并锁定，所有后续生成都严格引用锁定的资产
   - 这是"设计先行"的工业级流程，不是在 prompt 里反复描述角色

2. TWO-STAGE PIPELINE（两阶段流水线）
   - Stage 1 (Content): 生成结构化内容（novel_text / utterances）
   - Stage 2 (Visual): 基于 Stage 1 内容生成视觉描述（image_prompt / video_prompt）
   - Stage 2 不重新提取内容，只专注视觉，避免"漂移"

3. DUAL CONTENT MODE（双内容模式）
   - Narration（说书）：按朗读节奏拆分，输出 novel_text
   - Drama（剧集）：按场景/对话组织，输出 utterances
   - 两种模式有不同的数据模型和处理逻辑

4. REVIEW GATE（结构化审核门）
   - 每个阶段产出结构化中间态供人工审阅
   - 支持 approve / reject / modify 三种操作
   - 只有审核通过才能进入下一阶段

5. VERSION HISTORY（版本历史）
   - 每次生成自动保存版本快照
   - 支持一键回滚到任意历史版本
   - 内容和视觉分离存储，可单独回滚
"""

    data class DesignPrinciple(
        val name: String,
        val description: String,
        val implementation: String
    )

    val principles = listOf(
        DesignPrinciple(
            "Character Lock",
            "锁定角色设计，后续所有生成都引用锁定资产",
            "AssetLibrary + CharacterSheet + CharacterAnalyzer"
        ),
        DesignPrinciple(
            "Two-Stage Pipeline",
            "内容Stage与视觉Stage分离，避免漂移",
            "TwoStagePipelineEngine + ContentModels"
        ),
        DesignPrinciple(
            "Dual Mode",
            "Narration/Drama 双模式数据模型",
            "ContentMode.NARRATION/DRAMa + NarrationScript/DramaScript"
        ),
        DesignPrinciple(
            "Review Gate",
            "关键节点人工审核，结构化中间态",
            "ReviewGateManager + PipelineState"
        ),
        DesignPrinciple(
            "Version History",
            "版本快照 + 一键回滚",
            "VersionManager + ScriptVersion"
        )
    )

    fun getPipelineFlow(): String = """
完整流水线（状态机模型）：

1. CHARACTER_ANALYSIS    → AI 分析小说角色，提取视觉信息
2. CHARACTER_DESIGN      → AI 生成角色设计图（参考图）
3. CHARACTER_LOCKED      → 角色设计锁定，后续不可随意更改
4. CLUE_DESIGN           → AI 分析线索（场景/道具），生成参考图
5. CLUE_LOCKED           → 线索锁定
6. SCRIPT_CONTENT_STAGE  → Stage 1: 生成结构化内容（novel_text / utterances）
   └─ REVIEW GATE 1: 人工审核内容结构
7. SCRIPT_CONTENT_READY  → 内容审核通过
8. SCRIPT_VISUAL_STAGE   → Stage 2: 生成视觉提示词（image_prompt / video_prompt）
   └─ REVIEW GATE 2: 人工审核视觉质量
9. VISUAL_STAGE_COMPLETE → 视觉审核通过
10. REVIEW_GATE          → 最终全链路审核
11. STORYBOARD_GENERATION → 生成分镜图（引用锁定的角色/线索参考图）
12. VIDEO_GENERATION     → 生成视频片段
13. ASSEMBLY             → FFmpeg 合成
14. COMPLETE             → 完成
"""
}
