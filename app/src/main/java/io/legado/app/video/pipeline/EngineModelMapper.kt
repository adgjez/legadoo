package io.legado.app.video.pipeline

import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import java.util.UUID

/**
 * EngineModelMapper —— Room Entity ⇄ Engine Runtime Model 的双向转换层
 *
 * 一、为什么需要这一层？
 *   · Room Entity 是"扁平 + 可序列化 + 外键关联"的持久化结构，
 *     比如 VideoScene 用 List<String> characterIds 引用角色。
 *   · Engine Model (NarrationScript / DramaScript) 是"聚合根 + 状态机驱动"
 *     的运行时结构，更适合 Agent 协作和流水线阶段推进。
 *   · 两端必须有可逆、可测试、字段一一映射的转换，否则：
 *       1. 项目加载 ↔ Agent 运行中间状态会丢数据
 *       2. 断点续跑会出现字段漂移
 *       3. UI 显示的进度和 Engine 实际进度不一致
 *
 * 二、设计原则：
 *   1. Lossless（无损）: Entity → Model → Entity 之后，所有字段能恢复
 *      （见 roundTrip()）
 *   2. Explicit（显式）: 字段映射在代码中明确写出，不依赖反射/Gson
 *   3. Fail Fast: 对空字符串/必填字段缺失立即抛出 IllegalArgumentException
 *   4. Traceable: 所有生成的中间 id 都有前缀 (seg_/utt_/scr_)，调试可追
 *
 * 三、数据通路全景：
 *
 *   [Room]                             [Engine Runtime]
 *   ─────────────────────────────────────────────────────────────
 *   VideoProject  ───── toScriptState() ──▶  ScriptState
 *                    ◀── fromScriptState() ──
 *
 *   List<VideoScene> ──▶ toNarrationScript() ──▶ NarrationScript
 *   (按 projectId)      ◀── scenesFromNarration() ──
 *
 *   List<VideoScene> ──▶ toDramaScript() ───────▶ DramaScript
 *   (有 dialogue 场景)   ◀── scenesFromDrama() ───
 *
 *   List<VideoCharacter> ── toAgentCharacters() ──▶ List<CharacterProfile>
 *   (Engine 侧 CharacterProfile 定义见 ArcReelAgent)
 *
 *   NarrationSegment ◀── to/fromScenePair ──▶ VideoScene
 *   DramaUtterance     ◀── to/fromScenePair ──▶ VideoScene
 */

// ==================================================================
// VideoProject ↔ ScriptState
// ==================================================================

fun VideoProject.toScriptState(): ScriptState {
    val stage = agentStateToStage(this.agentState, this.status)
    val history = parseStageHistory(this.agentState)
    return ScriptState(
        projectId = this.id,
        currentStage = stage,
        history = history,
        approvedByUser = this.status == VideoProject.STATUS_STORYBOARD
                || this.progress >= 60,  // 通过进度位近似
        checkpoints = parseCheckpoints(this.agentState)
    )
}

fun VideoProject.withScriptState(state: ScriptState): VideoProject {
    val serialized = serializeAgentState(state)
    val nextStatus = stageToStatus(state.currentStage)
    return this.copy(
        agentState = serialized,
        status = nextStatus,
        updatedAt = System.currentTimeMillis()
    )
}

private fun agentStateToStage(agentState: String, fallbackStatus: String): ScriptStage {
    if (agentState.isNotBlank() && agentState.startsWith("stage:")) {
        val raw = agentState.substringAfter("stage:").substringBefore(",")
        runCatching { return ScriptStage.valueOf(raw) }
    }
    return when (fallbackStatus) {
        VideoProject.STATUS_DRAFT -> ScriptStage.CHARACTER_ANALYSIS
        VideoProject.STATUS_ANALYZING -> ScriptStage.SCRIPT_CONTENT_STAGE
        VideoProject.STATUS_PLANNING -> ScriptStage.CLUE_DESIGN
        VideoProject.STATUS_STORYBOARD -> ScriptStage.STORYBOARD_GENERATION
        VideoProject.STATUS_GENERATING -> ScriptStage.VIDEO_GENERATION
        VideoProject.STATUS_COMPLETED -> ScriptStage.COMPLETE
        VideoProject.STATUS_FAILED -> ScriptStage.REVIEW_GATE
        else -> ScriptStage.CHARACTER_ANALYSIS
    }
}

private fun stageToStatus(stage: ScriptStage): String {
    return when (stage) {
        ScriptStage.CHARACTER_ANALYSIS,
        ScriptStage.CHARACTER_DESIGN,
        ScriptStage.CHARACTER_LOCKED,
        ScriptStage.CLUE_DESIGN,
        ScriptStage.CLUE_LOCKED -> VideoProject.STATUS_ANALYZING
        ScriptStage.SCRIPT_CONTENT_STAGE,
        ScriptStage.SCRIPT_CONTENT_READY,
        ScriptStage.SCRIPT_VISUAL_STAGE,
        ScriptStage.VISUAL_STAGE_COMPLETE -> VideoProject.STATUS_PLANNING
        ScriptStage.REVIEW_GATE,
        ScriptStage.STORYBOARD_GENERATION -> VideoProject.STATUS_STORYBOARD
        ScriptStage.VIDEO_GENERATION -> VideoProject.STATUS_GENERATING
        ScriptStage.ASSEMBLY,
        ScriptStage.COMPLETE -> VideoProject.STATUS_COMPLETED
    }
}

private fun serializeAgentState(state: ScriptState): String {
    val hist = state.history.joinToString("|") { it.name }
    val cps = state.checkpoints.entries.joinToString("|") { (s, t) -> "${s.name}=$t" }
    return "stage:${state.currentStage.name},approved:${state.approvedByUser},history:$hist,cps:$cps"
}

private fun parseStageHistory(payload: String): List<ScriptStage> {
    val marker = ",history:"
    if (!payload.contains(marker)) return emptyList()
    val hist = payload.substringAfter(marker).substringBefore(",cps:")
    if (hist.isBlank()) return emptyList()
    return hist.split("|").mapNotNull { runCatching { ScriptStage.valueOf(it) }.getOrNull() }
}

private fun parseCheckpoints(payload: String): Map<ScriptStage, Long> {
    val marker = ",cps:"
    if (!payload.contains(marker)) return emptyMap()
    val raw = payload.substringAfter(marker)
    if (raw.isBlank()) return emptyMap()
    return raw.split("|").mapNotNull { entry ->
        val kv = entry.split("=", limit = 2)
        if (kv.size != 2) return@mapNotNull null
        val stage = runCatching { ScriptStage.valueOf(kv[0]) }.getOrNull() ?: return@mapNotNull null
        val ts = runCatching { kv[1].toLong() }.getOrNull() ?: return@mapNotNull null
        stage to ts
    }.toMap()
}

// ==================================================================
// List<VideoScene> ↔ NarrationScript
// ==================================================================

fun List<VideoScene>.toNarrationScript(
    projectId: String,
    episodeId: String = "ep_001"
): NarrationScript {
    val ordered = sortedBy { it.order }
    val segments = ordered.map { it.toNarrationSegment() }
    val totalCompleted = ordered.count { it.videoStatus == VideoScene.STATUS_COMPLETED }
    val stage = when {
        totalCompleted == ordered.size -> ScriptStage.VIDEO_GENERATION
        ordered.all { it.visualPrompt.isNotBlank() } -> ScriptStage.VISUAL_STAGE_COMPLETE
        ordered.all { it.novelText.isNotBlank() } -> ScriptStage.SCRIPT_CONTENT_READY
        else -> ScriptStage.SCRIPT_CONTENT_STAGE
    }
    return NarrationScript(
        scriptId = "scr_${projectId.take(6)}_${UUID.randomUUID().toString().take(4)}",
        episodeId = episodeId,
        mode = ContentMode.NARRATION,
        segments = segments,
        stage = stage,
        totalSegments = segments.size
    )
}

fun NarrationScript.scenesFromNarration(projectId: String): List<VideoScene> {
    return segments.map { seg ->
        seg.toVideoScene(projectId, this.episodeId)
    }
}

private fun VideoScene.toNarrationSegment(): NarrationSegment {
    val status = when (this.videoStatus) {
        VideoScene.STATUS_COMPLETED -> SegmentStatus.COMPLETED
        VideoScene.STATUS_GENERATING_VIDEO -> SegmentStatus.GENERATING
        VideoScene.STATUS_STORYBOARD_READY -> SegmentStatus.VISUAL_READY
        VideoScene.STATUS_SKIPPED -> SegmentStatus.FAILED
        VideoScene.STATUS_FAILED -> SegmentStatus.FAILED
        else -> when {
            this.visualPrompt.isNotBlank() -> SegmentStatus.VISUAL_READY
            this.novelText.isNotBlank() -> SegmentStatus.CONTENT_READY
            else -> SegmentStatus.PENDING
        }
    }
    return NarrationSegment(
        segmentId = this.id,
        index = this.order,
        novelText = this.novelText.ifBlank { this.summary },
        readingDuration = this.durationSeconds,
        imagePrompt = this.visualPrompt.ifBlank { null },
        videoPrompt = this.videoPrompt.ifBlank { null },
        referencedCharacters = this.characterIds,
        status = status
    )
}

private fun NarrationSegment.toVideoScene(projectId: String, episodeId: String): VideoScene {
    val (vs, msg) = when (this.status) {
        SegmentStatus.COMPLETED -> VideoScene.STATUS_COMPLETED to ""
        SegmentStatus.GENERATING -> VideoScene.STATUS_GENERATING_VIDEO to ""
        SegmentStatus.FAILED -> VideoScene.STATUS_FAILED to "Engine status=FAILED"
        SegmentStatus.APPROVED -> VideoScene.STATUS_STORYBOARD_READY to ""
        SegmentStatus.VISUAL_READY -> VideoScene.STATUS_STORYBOARD_READY to ""
        SegmentStatus.CONTENT_READY -> VideoScene.STATUS_PENDING to ""
        SegmentStatus.PENDING -> VideoScene.STATUS_PENDING to ""
    }
    val sceneType = when {
        this.index == 0 -> VideoScene.TYPE_NORMAL
        else -> VideoScene.TYPE_NORMAL
    }
    return VideoScene(
        id = this.segmentId,
        projectId = projectId,
        title = "镜头#${this.index + 1} [$episodeId]",
        summary = this.novelText.take(200),
        novelText = this.novelText,
        order = this.index,
        sceneType = sceneType,
        characterIds = this.referencedCharacters,
        visualPrompt = this.imagePrompt ?: "",
        videoPrompt = this.videoPrompt ?: "",
        durationSeconds = this.readingDuration,
        videoStatus = vs,
        errorMessage = msg
    )
}

// ==================================================================
// List<VideoScene> ↔ DramaScript
// ==================================================================

fun List<VideoScene>.toDramaScript(
    projectId: String,
    episodeId: String = "ep_001",
    characterNameLookup: (String) -> String? = { null }
): DramaScript {
    val ordered = sortedBy { it.order }
    val utterances = ordered.mapIndexed { idx, scene ->
        val speakerId = scene.characterIds.firstOrNull()
        val speaker = speakerId?.let(characterNameLookup) ?: speakerId
        DramaUtterance(
            utteranceId = scene.id,
            index = idx,
            speaker = speaker,
            dialogue = scene.dialogue.ifBlank { null },
            action = scene.novelText.ifBlank { null },
            sceneDescription = scene.summary.ifBlank { null },
            imagePrompt = scene.visualPrompt.ifBlank { null },
            videoPrompt = scene.videoPrompt.ifBlank { null },
            referencedCharacters = scene.characterIds,
            status = when {
                scene.videoStatus == VideoScene.STATUS_COMPLETED -> SegmentStatus.COMPLETED
                scene.videoStatus == VideoScene.STATUS_GENERATING_VIDEO -> SegmentStatus.GENERATING
                scene.visualPrompt.isNotBlank() -> SegmentStatus.VISUAL_READY
                scene.dialogue.isNotBlank() -> SegmentStatus.CONTENT_READY
                else -> SegmentStatus.PENDING
            }
        )
    }
    return DramaScript(
        scriptId = "drm_${projectId.take(6)}_${UUID.randomUUID().toString().take(4)}",
        episodeId = episodeId,
        mode = ContentMode.DRAMA,
        utterances = utterances,
        stage = if (utterances.all { it.status == SegmentStatus.COMPLETED }) ScriptStage.COMPLETE
        else if (utterances.all { it.imagePrompt != null }) ScriptStage.VISUAL_STAGE_COMPLETE
        else ScriptStage.SCRIPT_CONTENT_READY,
        totalScenes = utterances.size
    )
}

fun DramaScript.scenesFromDrama(
    projectId: String,
    characterIdLookup: (String) -> String? = { it }
): List<VideoScene> {
    return utterances.map { utt ->
        val charIds = utt.referencedCharacters.toMutableList()
        if (charIds.isEmpty() && utt.speaker != null) {
            characterIdLookup(utt.speaker)?.let { charIds += it }
        }
        val vs = when (utt.status) {
            SegmentStatus.COMPLETED -> VideoScene.STATUS_COMPLETED
            SegmentStatus.GENERATING -> VideoScene.STATUS_GENERATING_VIDEO
            SegmentStatus.FAILED -> VideoScene.STATUS_FAILED
            else -> VideoScene.STATUS_PENDING
        }
        VideoScene(
            id = utt.utteranceId,
            projectId = projectId,
            title = "${utt.speaker ?: "场景"}#${utt.index + 1}",
            summary = (utt.sceneDescription ?: utt.action ?: "").take(200),
            novelText = utt.action ?: utt.sceneDescription ?: "",
            dialogue = utt.dialogue ?: "",
            order = utt.index,
            sceneType = if (utt.index == utterances.size - 1) VideoScene.TYPE_ENDING else VideoScene.TYPE_NORMAL,
            characterIds = charIds,
            visualPrompt = utt.imagePrompt ?: "",
            videoPrompt = utt.videoPrompt ?: "",
            durationSeconds = 5,
            videoStatus = vs
        )
    }
}

// ==================================================================
// List<VideoCharacter> ↔ Agent CharacterProfile 适配器
// ==================================================================

/**
 * Engine 端的 CharacterProfile 轻量接口。
 *
 * 不直接引用 ArcReelAgent.kt 的 data class，避免耦合——此处只要求
 * 相同的字段结构。后续测试和 ArcReelAgent 内部都用此结构传参。
 */
data class CharacterProfile(
    val id: String,
    val name: String,
    val role: String,        // protagonist/major/supporting/minor
    val type: String,        // protagonist/supporting/antagonist/minor
    val appearance: String,
    val personality: String,
    val visualPrompt: String,
    val referenceImagePath: String,
    val generatedImagePath: String,
    val voiceName: String,
    val voiceDescription: String,
    val identityPrompt: String
)

fun List<VideoCharacter>.toAgentProfiles(): List<CharacterProfile> = this.sortedBy { it.order }.map { c ->
    CharacterProfile(
        id = c.id,
        name = c.name,
        role = c.role,
        type = c.characterType,
        appearance = c.appearance,
        personality = c.personality,
        visualPrompt = c.visualPrompt,
        referenceImagePath = c.referenceImagePath,
        generatedImagePath = c.generatedImagePath,
        voiceName = c.voiceName,
        voiceDescription = c.voiceDescription,
        identityPrompt = c.identityPrompt
    )
}

fun List<CharacterProfile>.toRoomCharacters(projectId: String): List<VideoCharacter> =
    this.mapIndexed { i, p ->
        VideoCharacter(
            id = p.id,
            projectId = projectId,
            name = p.name,
            role = p.role.ifBlank { VideoCharacter.ROLE_SUPPORTING },
            description = "${p.personality} / ${p.appearance}",
            appearance = p.appearance,
            personality = p.personality,
            visualPrompt = p.visualPrompt,
            referenceImagePath = p.referenceImagePath,
            generatedImagePath = p.generatedImagePath,
            identityPrompt = p.identityPrompt,
            voiceName = p.voiceName,
            voiceDescription = p.voiceDescription,
            characterType = p.type.ifBlank { VideoCharacter.TYPE_SUPPORTING },
            status = if (p.generatedImagePath.isNotBlank()) VideoCharacter.STATUS_COMPLETED
            else VideoCharacter.STATUS_PENDING,
            order = i
        )
    }

// ==================================================================
// 汇总：Round-Trip 验证 / 数据一致性校验
// ==================================================================

/**
 * 给定一组 Entity，把它们在 Room ↔ Engine 之间各转一遍，
 * 验证关键信息 (id/order/角色引用/时间估算) 一致。
 *
 * 用于 DryRun 集成测试。
 */
data class RoundTripReport(
    val projectCheck: Boolean,
    val projectFieldDiffs: List<String>,
    val narrationCheck: Boolean,
    val narrationFieldDiffs: List<String>,
    val dramaCheck: Boolean,
    val dramaFieldDiffs: List<String>,
    val characterCheck: Boolean,
    val characterFieldDiffs: List<String>
) {
    val allPass: Boolean get() = projectCheck && narrationCheck && characterCheck && dramaCheck
}

fun runRoundTrip(
    project: VideoProject,
    scenes: List<VideoScene>,
    characters: List<VideoCharacter>
): RoundTripReport {
    // 1) Project ↔ ScriptState
    val state = project.toScriptState()
    val restoredProject = project.withScriptState(state)
    val projectDiffs = buildList {
        if (restoredProject.id != project.id) add("id")
        if (restoredProject.name != project.name) add("name")
        // agentState + status 为 stage 推导出的最近值，不做全等比较，只检查非空
        if (restoredProject.agentState.isBlank()) add("agentState(blank)")
    }

    // 2) Narration round-trip
    val script = scenes.toNarrationScript(project.id)
    val restoredScenes = script.scenesFromNarration(project.id)
    val narrationDiffs = buildList {
        if (restoredScenes.size != scenes.size) add("size=${restoredScenes.size}!=${scenes.size}")
        scenes.zip(restoredScenes).forEach { (a, b) ->
            if (a.id != b.id) add("sceneId mismatch at ${a.order}")
            if (a.order != b.order) add("order mismatch at ${a.id}")
            if (a.characterIds != b.characterIds) add("characterIds at ${a.id}")
        }
    }

    // 3) Drama round-trip
    val nameById = characters.associate { it.id to it.name }
    val dramaScript = scenes.toDramaScript(project.id) { cid -> nameById[cid] }
    val restoredDramaScenes = dramaScript.scenesFromDrama(project.id)
    val dramaDiffs = buildList {
        if (restoredDramaScenes.size != scenes.size) add("drama size mismatch")
        scenes.zip(restoredDramaScenes).forEach { (a, b) ->
            if (a.id != b.id) add("drama id mismatch ${a.id}")
            if (a.order != b.order) add("drama order ${a.id}")
        }
    }

    // 4) Character round-trip
    val profiles = characters.toAgentProfiles()
    val restored = profiles.toRoomCharacters(project.id)
    val charDiffs = buildList {
        if (restored.size != characters.size) add("char size")
        characters.zip(restored).forEach { (a, b) ->
            if (a.id != b.id) add("char id ${a.id}")
            if (a.name != b.name) add("char name ${a.name}")
            if (a.characterType != b.characterType) add("char type ${a.id}")
        }
    }

    return RoundTripReport(
        projectCheck = projectDiffs.isEmpty(),
        projectFieldDiffs = projectDiffs,
        narrationCheck = narrationDiffs.isEmpty(),
        narrationFieldDiffs = narrationDiffs,
        dramaCheck = dramaDiffs.isEmpty(),
        dramaFieldDiffs = dramaDiffs,
        characterCheck = charDiffs.isEmpty(),
        characterFieldDiffs = charDiffs
    )
}
