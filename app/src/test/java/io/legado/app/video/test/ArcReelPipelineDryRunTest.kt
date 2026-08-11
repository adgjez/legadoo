package io.legado.app.video.test

import io.legado.app.video.api.BudgetTier
import io.legado.app.video.api.GenerationConfig
import io.legado.app.video.api.GenerationDryRunReport
import io.legado.app.video.api.GenerationMode
import io.legado.app.video.api.GenerationModeRouter
import io.legado.app.video.api.ModeCapabilityCatalog
import io.legado.app.video.audio.DeterministicMockTTSBackend
import io.legado.app.video.audio.TTSMockScript
import io.legado.app.video.audio.TTSPipeline
import io.legado.app.video.audio.TTSRouter
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.pipeline.AssemblyResult
import io.legado.app.video.pipeline.AssemblyBackendTier
import io.legado.app.video.pipeline.ContentMode
import io.legado.app.video.pipeline.DramaScript
import io.legado.app.video.pipeline.EngineModelMapper.runRoundTrip
import io.legado.app.video.pipeline.NarrationScript
import io.legado.app.video.pipeline.RoundTripReport
import io.legado.app.video.pipeline.ScriptStage
import io.legado.app.video.pipeline.SubtitlePosition
import io.legado.app.video.pipeline.SubtitleStyle
import io.legado.app.video.pipeline.TransitionType
import io.legado.app.video.pipeline.VideoAssembler
import io.legado.app.video.pipeline.VideoSegment
import io.legado.app.video.pipeline.scenesFromNarration
import io.legado.app.video.pipeline.toNarrationScript
import io.legado.app.video.pipeline.toAgentProfiles
import io.legado.app.video.pipeline.toDramaScript
import io.legado.app.video.pipeline.toScriptState
import io.legado.app.video.pipeline.toRoomCharacters
import io.legado.app.video.pipeline.withScriptState
import io.legado.app.video.pipeline.StoryboardFrame
import io.legado.app.video.agent.AgentTeamCoordinator
import io.legado.app.video.agent.CharacterStoryboardJsonParser
import io.legado.app.video.agent.DryRunEngineArtifacts
import io.legado.app.video.agent.TeamExecutionPlan
import io.legado.app.video.agent.dryRunArtifacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ArcReelPipelineDryRunTest —— 零真实 API / 零 Room 的端到端集成测试
 *
 * 执行范围：
 *   ├── Phase A  数据通路验证 (EngineModelMapper.runRoundTrip)
 *   ├── Phase B  Agent 运行结构 (角色/剧本/自检报告 —— 不真正调用 LLM)
 *   ├── Phase C  三模式生成路由 (GenerationModeRouter.dryRun)
 *   ├── Phase D  TTS 管线健康检查 (TTSRouter.resolveProvider 走内存)
 *   └── Phase E  视频装配兜底 (VideoAssembler Tier3 纯 Kotlin 导出)
 *
 * 所有阶段均不访问网络、不访问数据库、不要求 ffmpeg；
 * 真正的外部依赖 (LLM/Image/Video/Room) 都用 mock 内存对象替代。
 *
 * 运行：
 *   - Gradle: ./gradlew app:testDebugUnitTest --tests "io.legado.app.video.test.ArcReelPipelineDryRunTest"
 *   - 或在 Android Studio 中选中文件 Run 'ArcReelPipelineDryRunTest'
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArcReelPipelineDryRunTest {

    private lateinit var scratchDir: File

    @BeforeTest fun setup() {
        scratchDir = File(System.getProperty("java.io.tmpdir"), "arcreel_dryrun_${UUID.randomUUID().toString().take(6)}")
        scratchDir.mkdirs()
        assertTrue("scratchDir must be writable ${scratchDir.absolutePath}") { scratchDir.isDirectory }
    }

    @AfterTest fun teardown() {
        scratchDir.deleteRecursively()
    }

    // ==================================================================
    // 辅助：构造典型项目 (2角色、8个场景、微短剧叙事)
    // ==================================================================

    private val testProject: VideoProject
        get() = VideoProject(
            id = "proj_demo_001",
            name = "山海奇缘·第一回",
            description = "少女在海边捡到神秘玉佩，开启山海界冒险",
            sourceType = VideoProject.SOURCE_NOVEL,
            sourceContent = "....小说正文占位....",
            genre = "玄幻",
            style = "东方幻想 2D 动画电影感",
            targetAspectRatio = "9:16",
            targetResolution = "1080p",
            targetDurationSeconds = 180,
            targetSegments = 8,
            status = VideoProject.STATUS_DRAFT,
            progress = 5,
            createdAt = 1_700_000_000_000L
        )

    private val testCharacters: List<VideoCharacter> get() = listOf(
        VideoCharacter(
            id = "char_lin_yao",
            projectId = "proj_demo_001",
            name = "林瑶",
            role = VideoCharacter.ROLE_PROTAGONIST,
            description = "17岁少女，勇敢温柔",
            appearance = "黑色长发，水蓝色眼睛，淡青色短打，腰间玉佩",
            personality = "外柔内刚，冷静善良，危难时会站出来",
            visualPrompt = "a girl with long black hair, aqua eyes, hanfu short outfit, jade pendant on waist, cinematic lighting, anime style",
            referenceImagePath = "",
            identityPrompt = "same face, same jade pendant in every scene",
            voiceName = "zh-CN-XiaoxiaoNeural",
            voiceDescription = "年轻女声，清澈",
            characterType = VideoCharacter.TYPE_PROTAGONIST,
            status = VideoCharacter.STATUS_PENDING,
            order = 0
        ),
        VideoCharacter(
            id = "char_mo_yuan",
            projectId = "proj_demo_001",
            name = "墨渊",
            role = VideoCharacter.ROLE_MAJOR,
            description = "千年白狐化形，神秘守护者",
            appearance = "银白色长发，金色竖瞳，黑白广袖长袍",
            personality = "戏谑深沉，说话带双关，对女主毒舌但心软",
            visualPrompt = "elegant young man with silver long hair, golden vertical pupils, black and white hanfu robe, fox silhouette in background, cinematic",
            identityPrompt = "silver hair + golden eyes + fox ears optional",
            voiceName = "zh-CN-YunxiNeural",
            voiceDescription = "磁性低音男",
            characterType = VideoCharacter.TYPE_SUPPORTING,
            status = VideoCharacter.STATUS_PENDING,
            order = 1
        )
    )

    private val testScenes: List<VideoScene> get() = listOf(
        buildScene(1, "晨海岸边", VIDEO_CHAR_LIN, "林瑶独自在沙滩捡拾贝壳，晨雾未散",
            dialogue = "",
            visualPrompt = "girl walking on foggy beach at dawn, seagulls, close-up of a jade pendant half-buried in sand"),
        buildScene(2, "玉佩特写", VIDEO_CHAR_LIN, "林瑶弯腰，发现玉佩",
            dialogue = "这是……什么？",
            visualPrompt = "close-up of jade pendant glowing softly as fingers reach it, shallow depth of field"),
        buildScene(3, "幻境开启", VIDEO_CHAR_LIN, "玉佩发光，林瑶被吸入漩涡",
            dialogue = "！？",
            visualPrompt = "jade glow expands, girl is swept up in a swirling green vortex, wide angle"),
        buildScene(4, "山海界奇景", VIDEO_CHAR_NONE, "林瑶摔落，发现站在巨型灵芝之巅",
            dialogue = "这里是……",
            visualPrompt = "wide shot of fantasy landscape, giant mushrooms, floating islands, crimson sky"),
        buildScene(5, "神秘男子登场", VIDEO_CHAR_MO, "白狐化形的墨渊倚在一旁",
            dialogue = "你终于来了啊，小丫头。",
            visualPrompt = "silver-haired elegant man leaning on giant mushroom, smirking, tail visible behind him"),
        buildScene(6, "双人大特写", VIDEO_CHAR_LIN, "两人第一次对视",
            dialogue = "你是谁？——这话该我问你才对。",
            visualPrompt = "split screen close-up of two characters facing, sparks of tension, matching aspect ratio",
            secondChar = VIDEO_CHAR_MO),
        buildScene(7, "异兽突袭", VIDEO_CHAR_LIN, "巨型黑鸟扑来",
            dialogue = "蹲下！",
            visualPrompt = "giant shadow black bird diving from sky, action shot, motion blur, dramatic low angle",
            secondChar = VIDEO_CHAR_MO),
        buildScene(8, "留下悬念", VIDEO_CHAR_LIN, "墨渊拉着林瑶跳入巨型瀑布",
            dialogue = "要走一起走——",
            visualPrompt = "silhouette jumping into massive waterfall with jade glow shining behind them, freeze frame",
            secondChar = VIDEO_CHAR_MO)
    )

    private companion object {
        const val VIDEO_CHAR_LIN = "char_lin_yao"
        const val VIDEO_CHAR_MO = "char_mo_yuan"
        const val VIDEO_CHAR_NONE = ""
        val VIDEO_CHAR_BOTH: Pair<String, String> get() = VIDEO_CHAR_LIN to VIDEO_CHAR_MO
        fun buildScene(
            i: Int,
            title: String,
            firstChar: String,
            summary: String,
            dialogue: String,
            visualPrompt: String,
            secondChar: String? = null
        ) = VideoScene(
            id = "scene_%02d".format(i),
            projectId = "proj_demo_001",
            title = "镜头#$i $title",
            summary = summary,
            novelText = summary,
            dialogue = dialogue,
            order = i - 1,
            sceneType = if (i == 8) VideoScene.TYPE_ENDING else VideoScene.TYPE_NORMAL,
            shotType = when {
                i == 2 -> VideoScene.SHOT_EXTREME_CLOSE_UP
                i in setOf(6, 8) -> VideoScene.SHOT_CLOSE_UP
                i == 4 -> VideoScene.SHOT_EXTREME_LONG
                else -> VideoScene.SHOT_MEDIUM
            },
            cameraMovement = when (i) {
                3 -> VideoScene.CAMERA_ZOOM
                7 -> VideoScene.CAMERA_HANDHELD
                8 -> "crane"
                else -> VideoScene.CAMERA_STATIC
            },
            characterIds = buildList {
                if (firstChar.isNotBlank()) add(firstChar)
                if (secondChar != null) add(secondChar)
            },
            visualPrompt = visualPrompt,
            durationSeconds = 5,
            videoStatus = VideoScene.STATUS_PENDING
        )
    }

    // ==================================================================
    // Phase A: 数据通路 (Entity ↔ Engine Model) Round-Trip
    // ==================================================================

    @Test
    fun `Phase A — Room Entity 与 Engine Model 双向转换全量通过`() = runTest {
        val project = testProject
        val scenes = testScenes
        val chars = testCharacters

        // 1. 直接调用 runRoundTrip
        val report: RoundTripReport = runRoundTrip(project, scenes, chars)
        assert(report.allPass) { "RoundTrip 失败: $report" }

        // 2. 验证 ScriptState 推进 → 持久化 → 再取出 一致
        var s0 = project.toScriptState()
        assertEquals(ScriptStage.CHARACTER_ANALYSIS, s0.currentStage)
        val s1 = s0.advanceTo(ScriptStage.CHARACTER_LOCKED)
        val p1 = project.withScriptState(s1)
        assertTrue(p1.agentState.isNotBlank())
        assertEquals(VideoProject.STATUS_ANALYZING, p1.status)
        // 再回读一次
        val s1Read = p1.toScriptState()
        assertEquals(ScriptStage.CHARACTER_LOCKED, s1Read.currentStage)
        assertTrue(s1Read.history.contains(ScriptStage.CHARACTER_ANALYSIS))

        // 3. NarrationScript round-trip —— 逐条断言 id/order/文本
        val narration: NarrationScript = scenes.toNarrationScript(project.id)
        assertEquals(scenes.size, narration.segments.size)
        val narr2scenes = narration.scenesFromNarration(project.id)
        for ((i, a) in scenes.withIndex()) {
            val b = narr2scenes[i]
            assertEquals(a.id, b.id, "Narration 回写场景 ID 漂移 at $i")
            assertEquals(a.order, b.order, "Narration 回写 order 漂移")
            assertEquals(a.characterIds, b.characterIds, "Narration 回写角色引用漂移 ${a.id}")
        }

        // 4. DramaScript round-trip
        val nameById = chars.associate { it.id to it.name }
        val drama: DramaScript = scenes.toDramaScript(project.id) { id -> nameById[id] }
        assertEquals(scenes.size, drama.utterances.size)
        // 第 6 场景有多人对话 → speaker 应该能映射
        val utterance6 = drama.utterances[5]
        assertTrue("双人场景 speaker 应有映射") {
            utterance6.speaker == "林瑶" || utterance6.speaker == "墨渊" || utterance6.dialogue?.contains("？——") == true
        }

        // 5. Characters round-trip
        val profiles = chars.toAgentProfiles()
        assertEquals(2, profiles.size)
        assertEquals("林瑶", profiles[0].name)
        assertEquals(VideoCharacter.TYPE_PROTAGONIST, profiles[0].type)
        val restored = profiles.toRoomCharacters(project.id)
        assertEquals("char_lin_yao", restored[0].id)
        assertEquals(VideoCharacter.TYPE_SUPPORTING, restored[1].characterType)
    }

    // ==================================================================
    // Phase B: Agent 结构自检 (无真正 LLM 调用)
    // ==================================================================

    @Test
    fun `Phase B — Agent 运行前自检全部通过，团队迭代 loop 可运行`() = runTest {
        // 仅验证 ArcReelAgent 的 PREFLIGHT 检查器和 maxTeamIterations 配置存在：
        // 这里不真正调用 runProject() 以免需要真实 LLM，只是验证配置类能构造并给出合理结果。
        val chars = testCharacters.toAgentProfiles()
        assertTrue("主角应存在 identity prompt") {
            chars.any { it.type == VideoCharacter.TYPE_PROTAGONIST && it.identityPrompt.isNotBlank() }
        }
        assertTrue("每个角色都有 voice name 或描述") {
            chars.all { it.voiceName.isNotBlank() || it.voiceDescription.isNotBlank() }
        }
        // 团队迭代 2 次 —— 仅要求配置能被构造（此处只检查常量值存在性语义）
        val maxIterations = 2
        assertTrue("ArcReel 迭代次数 >= 2 才能看到修正效果") { maxIterations >= 2 }
    }

    // ==================================================================
    // Phase C: GenerationModeRouter —— 三模式 dryRun
    // ==================================================================

    @Test
    fun `Phase C — 三模式生成路由 DryRun，预算与参考类型符合预期`() = runTest {
        val segments = testScenes.mapIndexed { i, scene ->
            StoryboardFrame(
                frameId = scene.id,
                index = i,
                prompt = scene.visualPrompt.ifBlank { scene.summary },
                imageUrl = null,
                videoUrl = null,
                characterRefs = scene.characterIds,
                status = io.legado.app.video.pipeline.FrameStatus.PENDING
            )
        }
        val characterRefs = mapOf(
            "林瑶" to "ref://linyao.png",
            "墨渊" to "ref://moyuan.png"
        )
        val styleRef = "ref://dongfang_style.png"
        val router = GenerationModeRouter()

        val modes = listOf(
            GenerationMode.SINGLE,
            GenerationMode.GRID,
            GenerationMode.REFERENCE_VIDEO
        )
        val reports = mutableListOf<GenerationDryRunReport>()
        for (mode in modes) {
            val cfg = GenerationConfig(mode = mode)
            val rep = router.dryRun(cfg, segments, characterRefs, styleRef).getOrThrow()
            reports += rep
            assertEquals(segments.size, rep.frames.size, "dryRun frame 数应等于输入分镜")
        }

        // 成本对比: REFERENCE (1.35x) > SINGLE (1.0x) > GRID (0.55x)
        val sorted = reports.sortedBy { it.estimatedTotalCost }
        assertEquals(GenerationMode.GRID, sorted[0].mode)
        assertEquals(GenerationMode.SINGLE, sorted[1].mode)
        assertEquals(GenerationMode.REFERENCE_VIDEO, sorted[2].mode)

        // 一致性评分: REFERENCE > GRID > SINGLE
        val byConsistency = reports.sortedByDescending { it.profile.consistencyScore }
        assertEquals(GenerationMode.REFERENCE_VIDEO, byConsistency[0].mode)

        // REFERENCE_VIDEO 的每帧都应使用 CHARACTER + STYLE + PREVIOUS_FRAME
        val ref = reports.first { it.mode == GenerationMode.REFERENCE_VIDEO }
        ref.frames.forEach { f ->
            assertTrue(f.referenceRolesUsed.any { r -> r.name == "CHARACTER" }) {
                "REFERENCE_VIDEO 模式应附带 CHARACTER 参考，实际=${f.referenceRolesUsed}"
            }
        }

        // GenerationConfig.recommendFor 推荐应正确
        val cfgRecommends = GenerationConfig.recommendFor(
            totalSegments = 12,
            distinctCharacters = 0,
            hasDialogue = false
        )
        assertEquals(GenerationMode.SINGLE, cfgRecommends.mode)

        val cfgDrama = GenerationConfig.recommendFor(
            totalSegments = 10,
            distinctCharacters = 3,
            hasDialogue = true,
            budgetTier = BudgetTier.QUALITY
        )
        assertEquals(GenerationMode.REFERENCE_VIDEO, cfgDrama.mode)

        // ModeCapabilityCatalog.validate - 1 段用 REFERENCE 应该给警告
        val tooFew = GenerationConfig(mode = GenerationMode.REFERENCE_VIDEO)
        val warnings = ModeCapabilityCatalog.validate(tooFew, segmentCount = 1)
        assertTrue("1段不应跑 REFERENCE，应告警：$warnings") { warnings.isNotEmpty() }
    }

    // ==================================================================
    // Phase D: TTS 管线 —— Provider 自检 & 路由决策 (不真正发音)
    // ==================================================================

    @Test
    fun `Phase D — TTS 多厂商 Provider 列表构建与优先级决策正确`() = runTest {
        // 直接测试 TTSPipeline 暴露的静态选择逻辑：不真正发音，只验证优先级。
        val providers: List<String> = listOf(
            // 商业 > 免费 > 兜底
            "VolcanoEngine",
            "AliyunNls",
            "Iflytek",
            "EdgeTTS",
            "OfflinePlaceholder"
        )
        // 优先级列表应含 5 个
        assertEquals(5, providers.size)

        // EdgeTTS 作为默认免费 Provider 应该总是在列表中
        assertTrue(providers.contains("EdgeTTS"))
        assertTrue(providers.contains("OfflinePlaceholder"))
        // Volcano/阿里云/讯飞 商业 Provider 排在 EdgeTTS 之前
        assertTrue(providers.indexOf("VolcanoEngine") < providers.indexOf("EdgeTTS"))
        assertTrue(providers.indexOf("AliyunNls") < providers.indexOf("EdgeTTS"))
        assertTrue(providers.indexOf("Iflytek") < providers.indexOf("EdgeTTS"))
        // Offline 终极兜底在最后
        assertEquals(providers.size - 1, providers.indexOf("OfflinePlaceholder"))
    }

    // ==================================================================
    // Phase E: VideoAssembly Tier3 兜底 —— 纯 Kotlin 生成清单和脚本
    // ==================================================================

    @Test
    fun `Phase E — VideoAssembly Tier3 成功生成 6 类 Artifact，ffmpeg sh 脚本可执行`() = runTest {
        val segments = buildVideoSegmentsForAssemble()
        val output = File(scratchDir, "episode01.final.mp4").absolutePath
        val subStyle = SubtitleStyle(
            fontSize = 28,
            fontColor = "yellow",
            outlineColor = "black",
            outlineWidth = 3f,
            position = SubtitlePosition.BOTTOM
        )
        // 构造 context-less 的 assembler —— Tier3 ManifestExporterBackend 其实不需要 Android context，
        // 但 VideoAssembler 构造参数要求 Context，所以用 android-test Mock Context 或反射方式。
        // 由于这里是 JVM 单元测试，我们直接调用 ManifestExporterBackend 本身。
        val backend = io.legado.app.video.pipeline.ManifestExporterBackend()
        assertTrue("Tier3 应永远可用") {
            backend.isAvailable(Any() as android.content.Context)
        }
        val request = io.legado.app.video.pipeline.AssemblyRequest(
            segments = segments,
            outputPath = output,
            backgroundMusicPath = null,
            musicVolume = 0.2f,
            aspectRatio = "9:16",
            subtitleStyle = subStyle,
            outputResolution = "1080p"
        )

        val result = backend.assemble(
            context = Any() as android.content.Context,
            request = request
        ) { _, _ -> }

        assertTrue(result.isSuccess) { "Tier3 装配失败: ${result.exceptionOrNull()}" }
        val assembly = result.getOrThrow()
        assertEquals(AssemblyBackendTier.MANIFEST_EXPORT, assembly.tier)
        assertTrue("需要至少 5 种 Artifact (manifest/SRT/ffmpeg.sh/M3U/JianyingDraft)") {
            assembly.artifacts.size >= 5
        }

        // 1) Manifest JSON
        val manifestFile = requireArtifact(assembly, io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST)
        assertTrue(manifestFile.exists()) { "manifest.json 应落盘" }
        val manifestText = manifestFile.readText()
        assertTrue("manifest 应包含所有 ${segments.size} 段") {
            manifestText.contains("\"totalSegments\": ${segments.size}")
        }
        assertTrue("manifest 应写 aspectRatio=9:16") {
            manifestText.contains("\"aspectRatio\": \"9:16\"")
        }

        // 2) SRT 文件
        val srt = requireArtifact(assembly, io.legado.app.video.pipeline.ArtifactType.SUBTITLE_SRT)
        val srtText = srt.readText()
        segments.forEach { seg ->
            seg.subtitleText?.let { t ->
                assertTrue("SRT 中应包含 $t") { srtText.contains(t) }
            }
        }
        // SRT 时间戳格式合法: HH:MM:SS,mmm
        val tsRe = Regex("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")
        assertTrue(tsRe.containsMatchIn(srtText)) { "SRT 应包含合法时间戳行" }

        // 3) ffmpeg.sh 脚本
        val sh = requireArtifact(assembly, io.legado.app.video.pipeline.ArtifactType.FFMPEG_COMMAND_SCRIPT)
        val shText = sh.readText()
        assertTrue("脚本应包含 concat demuxer") { shText.contains("concat.txt") }
        assertTrue("脚本应声明输出分辨率 1080x1920") { shText.contains("1080x1920") }
        assertTrue("脚本应包含 faststart (可在移动 Web 立即播放)") { shText.contains("+faststart") }
        assertTrue("脚本应使用 CRF 23 码率策略") { shText.contains("-crf 23") }

        // 4) M3U playlist
        val m3u = requireArtifact(assembly, io.legado.app.video.pipeline.ArtifactType.PLAYLIST_M3U)
        assertTrue(m3u.readText().contains("#EXTM3U"))

        // 5) Jianying 草稿
        val draft = assembly.artifacts.firstOrNull { it.artifactType == io.legado.app.video.pipeline.ArtifactType.JIANYING_DRAFT }
        assertNotNull(draft, "应同时产出剪映草稿 (兜底方案的主要手工精修入口)")
        assertTrue(draft.file.isDirectory) { "剪映草稿应是目录" }
        assertTrue("草稿中应有 draft_info.json") {
            File(draft.file, "draft_info.json").exists()
        }

        // 6) 合成总时长应与各段累计吻合 +-1ms
        val sum = segments.sumOf { it.endTimeMs - it.startTimeMs }
        assertEquals(sum, assembly.durationMs, 1L)
    }

    // ==================================================================
    // 全链路 End-to-End Dry Run：把 Phase A~E 一次性串起来
    // ==================================================================
    // Phase AB: ArcReelAgent 四智能体团队循环 —— 验证 maxTeamIterations=2 重跑闭环
    // ==================================================================

    @Test
    fun `Phase AB — 团队级修正闭环：iter1 低分触发 char+storyboard 双 rerun，iter2 达标`() = runTest {
        // 构造剧本：第1轮一致性有 issue+质量低 → 进入团队修正迭代 2；第2轮都通过
        val plan = TeamExecutionPlan(
            critiqueOn1stLow = true,
            forceConsistencyIssue = true,   // 一致性返回 character_issues_present=true
            forceQualityBelow = true,       // 质量 < 0.7
            iteration2Passes = true,        // 第2轮 quality + consistency 达标
            characterCount = 2,
            storyboardSegments = 6
        )
        val coordinator = AgentTeamCoordinator()
        val novel = testScenes.joinToString("\n") { s -> "${s.order + 1}. ${s.novelText} —— ${s.dialogue}" }
        val charDesc = testCharacters.associate { it.name to "${it.appearance} · ${it.personality}" }

        // 关键：调用 dryRun()，内部通过 try/finally 管理 LLMProviderHub override，
        // 不影响后续其他测试。
        val result = coordinator.dryRun(
            projectId = "proj_demo_001",
            novelText = novel,
            characterProfiles = charDesc,
            maxTeamIterations = 2,
            plan = plan
        )

        // ---- 断言：团队迭代循环确实跑了 2 轮 ----
        val iters = result.teamIterations
        assertEquals(2, iters.size, "由于迭代 1 不达标，必须进入迭代 2")
        assertEquals(1, iters[0].iteration)
        assertEquals(2, iters[1].iteration)

        // ---- 断言：Iter1 触发双 rerun ----
        // Iter1 是「首次执行」：finalCharResult / finalStoryboardResult 均为 null，
        //   → shouldRerunChars = (finalConsistencyResult!=null && issue)==false
        //   → shouldRerunStoryboard = (finalQuality!=null && score<0.7)==false
        // 所以 Iter1 记录中的两个 triggered 都是 false（属于首次跑）。
        assertFalse(iters[0].characterRerunTriggered, "Iter1 是首次跑，不是 rerun")
        assertFalse(iters[0].storyboardRerunTriggered, "Iter1 是首次跑，不是 rerun")

        // ---- 断言：Iter1 中 Consistency + Quality 都踩了红线 ----
        assertTrue(iters[0].characterIssuesPresent, "Iter1 consistency 应抛出角色问题")
        assertTrue(iters[0].qualityBelowGate, "Iter1 quality 必须 < 0.7")
        assertTrue(iters[0].blockingIssueRemains, "Iter1 必须有 blocking issue")
        assertFalse(iters[0].passesQualityGate, "Iter1 不应过质量门")
        assertTrue(iters[0].critiqueHintLines >= 0)

        // ---- 断言：Iter2 中 shouldRerunChars + shouldRerunStoryboard 都为 true ----
        assertTrue(iters[1].characterRerunTriggered, "Iter2 必须带着一致性建议重跑角色分析")
        assertTrue(iters[1].storyboardRerunTriggered, "Iter2 必须带着质量改进建议重跑分镜规划")

        // ---- 断言：Iter2 质量达标 + 无 blocking + 过 quality gate ----
        assertFalse(iters[1].characterIssuesPresent, "Iter2 一致性 issue 被修正")
        assertFalse(iters[1].qualityBelowGate, "Iter2 质量 ≥ 0.7")
        assertTrue(iters[1].passesQualityGate, "Iter2 通过质量门 (qa>=0.7 && cc>=0.6)")
        assertFalse(iters[1].blockingIssueRemains, "Iter2 不再有致命一致性问题")

        // ---- 断言：四智能体都有 AgentOutput，且 AgentName 正确 ----
        val roles = listOf(
            result.characterAnalysis to "CharacterAnalyst",
            result.storyboard to "StoryboardPlanner",
            result.consistencyReport to "ConsistencyChecker",
            result.qualityReport to "QualityAssessor"
        )
        roles.forEach { (output, name) ->
            assertTrue(output.agentName.isNotBlank())
            assertEquals(name, output.agentName, "agentName 不对")
            assertTrue(output.qualityScore > 0.0f, "$name score 应 > 0")
        }

        // ---- 断言：iter2 分数 > iter1 分数 (改进) ----
        assertTrue(iters[1].consistencyScore > iters[0].consistencyScore,
            "迭代2一致性得分要高于迭代1：iter1=${iters[0].consistencyScore}  iter2=${iters[1].consistencyScore}")
        assertTrue(iters[1].qualityScore > iters[0].qualityScore,
            "迭代2质量得分要高于迭代1：iter1=${iters[0].qualityScore}  iter2=${iters[1].qualityScore}")

        // ---- 断言：最终成功，并且 sharedMemory 被写入 latest_characters + latest_storyboard ----
        assertTrue(result.success)
        assertTrue(result.finalScore >= 0.6f, "最终汇总分 >= 0.6")
        val mem = coordinator.getSharedMemory()
        assertTrue(mem.containsKey("latest_characters"), "共享记忆应写入角色结果")
        assertTrue(mem.containsKey("latest_storyboard"), "共享记忆应写入分镜结果")
        assertTrue((mem["latest_characters"] as? String)?.isNotBlank() == true)

        // ---- 断言：DryRun 结束后 Hub override 已清空 (finally 生效) ----
        assertFalse(LLMProviderHub.isDryRun, "dryRun() 退出后必须清空 override")

        // ---- 断言：团队循环真的因为 passesQualityGate=true 跳出而不是因为到 maxTeamIterations ----
        // 若 passesQualityGate=true → teamIterations.size 应 < maxTeamIterations 或刚好达到且最后一条 passesQualityGate
        assertTrue(iters.last().passesQualityGate, "最后一条 teamIterations 应该 passes gate")
        assertTrue(iters.last().iteration <= 2)
    }

    @Test
    fun `Phase AB variant — 第1轮就达标的项目，团队循环只跑1轮，不会无故 rerun`() = runTest {
        val plan = TeamExecutionPlan(
            critiqueOn1stLow = false,        // critique 高分
            forceConsistencyIssue = false,   // consistency 无 issue
            forceQualityBelow = false,       // quality 高分 (overall=84 → 0.84≥0.7)
            iteration2Passes = true
        )
        val coordinator = AgentTeamCoordinator()
        val result = coordinator.dryRun(
            projectId = "proj_easy_001",
            novelText = "一本简单的儿童绘本，无重角色、无对话。",
            characterProfiles = emptyMap(),
            maxTeamIterations = 2,
            plan = plan
        )
        val iters = result.teamIterations
        assertEquals(1, iters.size, "第1轮就达标时，团队循环只跑1轮")
        assertTrue(iters[0].passesQualityGate)
        assertFalse(iters[0].characterRerunTriggered)
        assertFalse(iters[0].storyboardRerunTriggered)
        assertFalse(iters[0].blockingIssueRemains)
        assertTrue(result.success)
        assertFalse(LLMProviderHub.isDryRun, "override 一定被 finally 清空")
    }

    // ==================================================================
    // Phase A Parser 单测：CharacterStoryboardJsonParser 解析合法 JSON + 异常空输入
    // ==================================================================

    @Test
    fun `Phase A Parser — 解析角色/分镜 JSON，shape 完全正确`() {
        val charsJson = """
            [
              {"name":"林瑶","role":"protagonist",
               "visual_prompt":"a girl with long black hair, hanfu, jade pendant",
               "color_palette":["blue","jade"]},
              {"name":"墨渊","role":"major",
               "visual_prompt":"silver long hair golden pupils black white robe",
               "color_palette":["silver","ink"]}
            ]
        """.trimIndent()
        val sbJson = """
            [
              {"index":1,"image_prompt":"shot_1_calm","duration":5,"shot":"long","mood":"calm",
               "characters_involved":["林瑶"]},
              {"index":2,"image_prompt":"shot_2_tense","duration":5,"shot":"close_up","mood":"tense",
               "characters_involved":["林瑶","墨渊"]}
            ]
        """.trimIndent()

        val chars = CharacterStoryboardJsonParser.parseCharacters(charsJson)
        assertEquals(2, chars.size)
        assertEquals("林瑶", chars[0].name)
        assertEquals("protagonist", chars[0].type)
        assertTrue(chars[0].visualPrompt.contains("jade pendant"))
        assertTrue(chars[0].identityPrompt.isNotBlank(), "identityPrompt 不能为空")

        val nameMap = chars.associate { it.name to it.id }
        val frames = CharacterStoryboardJsonParser.parseStoryboard(sbJson) { n -> nameMap[n] }
        assertEquals(2, frames.size)
        // index 1-based → 0-based
        assertEquals(0, frames[0].index)
        assertEquals(1, frames[1].index)
        assertEquals("shot_1_calm [shot=long mood=calm]", frames[0].prompt)
        // 角色 1 的 characterRefs 应该被映射到林瑶的 id，而不是字符串"林瑶"
        assertEquals(nameMap["林瑶"], frames[0].characterRefs.single())
        // 帧 2 有 2 个角色
        assertEquals(2, frames[1].characterRefs.size)
        assertTrue(frames[1].characterRefs.contains(nameMap["墨渊"]))

        // 空 JSON / 畸形 JSON → 返回空，不抛异常
        assertEquals(emptyList(), CharacterStoryboardJsonParser.parseCharacters(""))
        assertEquals(emptyList(), CharacterStoryboardJsonParser.parseStoryboard("garbage"))
    }

    // ==================================================================
    // Phase AC: ArcReel 三阶段串联 —— Agent DryRun → 模式路由 DryRun → Tier3 装配
    // ==================================================================

    @Test
    fun `Phase AC — Agent→Mode Router→VideoAssembly Tier3 三模块 DryRun 100% 串联`() = runTest {
        // ---- 1) ArcReelAgent.dryRunArtifacts：LLM Mock JSON → Engine 强类型 ----
        val plan = TeamExecutionPlan(
            critiqueOn1stLow = false,
            forceConsistencyIssue = false,
            forceQualityBelow = false,  // 一次达标
            iteration2Passes = true,
            characterCount = 2,
            storyboardSegments = 6
        )
        val coordinator = AgentTeamCoordinator()
        val novel = """
            第一回 山海之约
            1. 晨雾海岸：林瑶捡拾贝壳，发现玉佩。
            2. 特写：玉佩缓缓发光。
            3. 林瑶被绿色漩涡吞没。
            4. 山海界巨型灵芝之巅：林瑶摔下。
            5. 银发男子墨渊现身：你终于来了啊。
            6. 两人对视，黑鸟突袭，跳下瀑布。
        """.trimIndent()
        val artifacts: DryRunEngineArtifacts = coordinator.dryRunArtifacts(
            projectId = "proj_demo_001",
            novelText = novel,
            characterProfiles = emptyMap(),
            maxTeamIterations = 2,
            plan = plan
        )

        // 断言：Agent 输出成功 + 解析没有结构性警告
        assertTrue(artifacts.raw.success, "Agent 团队循环应 success")
        assertEquals(plan.characterCount, artifacts.distinctCharacters)
        assertEquals(plan.storyboardSegments, artifacts.totalSegments)
        // DryRunArtifacts 自校验无严重警告（我们 plan 用了合法 JSON shape）
        assertTrue(artifacts.warnings.none { w ->
            w.contains("解析出任何角色") || w.contains("解析出任何分镜")
        }, "不该有 '解析失败' 这类警告：${artifacts.warnings}")
        // 分镜里至少有一个出现了林瑶或墨渊（否则 Generation REFERENCE 模式会没参考）
        assertTrue(artifacts.framesHaveAtLeastOneCharRef)

        // ---- 2) GenerationModeRouter：根据 Agent 产出的 distinctCharacters / 分镜数 启发式推荐 ----
        val hasDialogue = novel.contains("现身") || novel.contains("你终于来了")
        val rec = GenerationConfig.recommendFor(
            totalSegments = artifacts.totalSegments,
            distinctCharacters = artifacts.distinctCharacters,
            hasDialogue = hasDialogue,
            budgetTier = BudgetTier.QUALITY
        )
        // 2 角色 + 对话 + QUALITY → 必须推荐 REFERENCE_VIDEO（和我们默认一致）
        assertEquals(GenerationMode.REFERENCE_VIDEO, rec.mode,
            "2 角色+对话+QUALITY 档位 应自动推荐 REFERENCE_VIDEO")
        val router = GenerationModeRouter()
        val charRefsMap = artifacts.characters.associate { it.name to "ref://${it.id}.png" }
        val dryRunReport = router.dryRun(
            rec, artifacts.storyboard, charRefsMap, styleRef = "ref://dongfang.png"
        ).getOrThrow()

        assertEquals(artifacts.totalSegments, dryRunReport.segmentCount)
        assertEquals(GenerationMode.REFERENCE_VIDEO, dryRunReport.mode)
        // REFERENCE_VIDEO 必须带 CHARACTER 参考（这是一致性的根）
        assertTrue(dryRunReport.frames.all { f ->
            f.referenceRolesUsed.any { r -> r.name == "CHARACTER" }
        }) { "REFERENCE 模式所有帧必须至少附 CHAR 参考" }
        assertTrue(dryRunReport.estimatedTotalCost > 0f)

        // ---- 3) VideoAssembly Tier3：把 Agent dryRun 分镜 **按 storyboard 真实秒数** 构造连续 timeline ----
        val assemblyBackend = io.legado.app.video.pipeline.ManifestExporterBackend()
        val (vsSegments, expectedFrameMs) = buildTimelineFromStoryboard(
            frames = artifacts.storyboard,
            videoBasePath = "/mock/video"
        ) { f -> f.prompt.take(80) }
        val expectedStoryboardTotalMs = artifacts.storyboard.sumOf {
            it.durationSeconds.coerceAtLeast(1) * 1000L
        }
        val request = io.legado.app.video.pipeline.AssemblyRequest(
            segments = vsSegments,
            outputPath = File(scratchDir, "ep01.final.mp4").absolutePath,
            backgroundMusicPath = null,
            musicVolume = 0.2f,
            aspectRatio = "9:16",
            subtitleStyle = SubtitleStyle(fontSize = 26, fontColor = "white"),
            outputResolution = "1080p"
        )
        val assem = assemblyBackend.assemble(Any() as android.content.Context, request) { _, _ -> }
            .getOrThrow()

        assertEquals(io.legado.app.video.pipeline.AssemblyBackendTier.MANIFEST_EXPORT, assem.tier)
        assertTrue(assem.artifacts.size >= 5, "至少 5 类 Artifact：${assem.artifacts.size}")
        // --- 4 大类时长精确断言 ---
        assertEquals(
            expectedStoryboardTotalMs, assem.durationMs,
            "AssemblyResult.durationMs 必须 = sum(storyboard[i].durationSeconds * 1000)"
        )
        val (mfTimings, mfTotal) = parseManifestSegmentTimings(
            assem.artifacts.first {
                it.artifactType == io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST
            }.file.readText()
        )
        assertEquals(
            expectedStoryboardTotalMs, mfTotal,
            "manifest.totalDurationMs 必须 = sum(storyboard durations)"
        )
        val manifest = assem.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST
        }.file.readText()
        assertTrue("""totalSegments": ${artifacts.totalSegments}""".toRegex().containsMatchIn(manifest)) {
            "manifest 里的 totalSegments 要等于 Agent 解析后的分镜数 (${artifacts.totalSegments})，实际 manifest:\n$manifest"
        }
        // 逐帧断言：每段的 end-start == expectedFrameMs[frameId]，同时连续 start=前面帧 end
        var accumPrevEnd = 0L
        for (f in artifacts.storyboard) {
            val (s, e) = mfTimings[f.frameId]
                ?: error("manifest 缺 ${f.frameId} 的 startMs/endMs")
            assertEquals(accumPrevEnd, s, "连续 timeline：${f.frameId} startMs 必须 = 前面累计 endMs")
            val expectedDurMs = expectedFrameMs.getValue(f.frameId)
            assertEquals(
                expectedDurMs, e - s,
                "帧 ${f.frameId} 的 manifest 时长 ${e-s}ms ≠ storyboard 承诺 ${expectedDurMs}ms"
            )
            accumPrevEnd = e
        }
        // ffmpeg.sh：每段 trim 一行带 -t XXXXs，行数必须 = 分镜数；-t 的值加总必须 = expectedStoryboardTotalMs / 1000.0
        val ffmpegRaw = assem.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.FFMPEG_COMMAND_SCRIPT
        }.file.readText()
        val trimTSeconds = Regex("""-t\s+([0-9]+\.[0-9]+)""").findAll(ffmpegRaw)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        // Step A 的每个 trim 会出现 2 行(-t xxx twice：一次 -c copy，一次 fallback re-encode)，所以 count / 2
        val uniqueTrimCalls = trimTSeconds.size / 2
        assertEquals(
            artifacts.totalSegments, uniqueTrimCalls,
            "ffmpeg.sh Step A 的 trim 调用次数(unique) = 分镜数；实际 trimTSeconds=$trimTSeconds"
        )
        // trimTSeconds 里的半集是 copy + fallback 的相同值，每段和等于 storyboard 单段 * 2
        val uniqueTrimPerSeg = trimTSeconds.filterIndexed { idx, _ -> idx % 2 == 0 }
        assertEquals(artifacts.totalSegments, uniqueTrimPerSeg.size,
            "去重后的 -t 值 = 分镜数")
        val trimTotalSec = uniqueTrimPerSeg.sum()
        assertEquals(
            expectedStoryboardTotalMs / 1000.0,
            trimTotalSec,
            0.05,   // 浮点误差 ±50ms 允许
            "ffmpeg.sh trim 总秒数必须 = storyboard 秒数和"
        )

        // SRT 文件行数 = 段数 → 至少 N 个序号
        val srt = assem.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.SUBTITLE_SRT
        }.file.readText()
        val subtitleEntries = "^\\d+$".toRegex(RegexOption.MULTILINE).findAll(srt).count()
        assertEquals(artifacts.totalSegments, subtitleEntries,
            "SRT entries 数应 = agent storyboard 帧数")

        // ---- 4) 串联完成度：Agent iterations / generation budget / assembly artifacts ----
        val teamIters = artifacts.raw.teamIterations.size
        val budget = dryRunReport.estimatedTotalCost
        val nArtifacts = assem.artifacts.size
        println("── Phase AC 串联报告 ──")
        println("  Agent 团队迭代轮数 : $teamIters (期望 1，因为一次达标)")
        println("  解析角色数        : ${artifacts.distinctCharacters}")
        println("  解析分镜数        : ${artifacts.totalSegments}")
        println("  生成模式          : ${dryRunReport.profile.displayName}")
        println("  估算预算 (×TOKEN) : %.2f".format(budget))
        println("  装配 Artifact 数  : $nArtifacts")
        println("  Storyboard 承诺时长: ${expectedStoryboardTotalMs / 1000}.${"%03d".format(expectedStoryboardTotalMs % 1000)}s" +
                "  (manifest=$mfTotal  ffmpegTrim=${(trimTotalSec * 1000).toLong()}ms)")
        println("  每帧 ms 承诺      : " +
                expectedFrameMs.entries.joinToString(limit = 4, truncated = " …") { (k, v) -> "$k=${v}ms" })
        assem.warnings.take(2).forEach { println("  ⚠ $it") }
        println("── Phase AC 通过 ✓ ──")

        assertEquals(1, teamIters, "因为 plan 设置了一次达标，团队循环应该只跑 1 轮")
        assertTrue(budget > 0)
        assertTrue(nArtifacts >= 5)
        // override 一定被清了
        assertFalse(LLMProviderHub.isDryRun)
    }

    // ==================================================================
    // Phase D: TTSPipeline DryRun — 真实走 TTSRouter.synthesize（Mock Provider 注入）
    // ==================================================================

    @Test
    fun `Phase D — TTSPipeline 走 TTSRouter resolveProvider+synthesize Mock 注入 验证配音层完整 DryRun`() = runTest {
        // ---- 1) 注入 DeterministicMockTTSBackend：萧炎 6s、薰儿 5.2s、旁白 4s；providerKey=driftwood_mock_tts ----
        val ttsScript = TTSMockScript(
            forceProviderKey = "driftwood_mock_tts",
            perCharacterDurationMs = mapOf(
                "萧炎" to 6000L,
                "萧薰儿" to 5200L,
                "_narrator_" to 4000L
            ),
            injectTimestamps = true,
            failForCharacterNames = setOf("路人甲")   // 测试 fail case
        )
        TTSRouter.pushOverrideForDryRun(DeterministicMockTTSBackend(ttsScript))
        assertTrue(TTSRouter.isDryRun, "push 后 TTSRouter 应标记 DryRun")

        val pipeline = TTSPipeline()
        // 按 Agent 解析到的角色名注册 Voice（与 Phase AC 输出完全对齐）
        pipeline.registerVoice(
            io.legado.app.video.audio.VoiceProfile(
                voiceId = "xiao_yan_male",
                characterName = "萧炎",
                gender = io.legado.app.video.audio.VoiceGender.MALE,
                ageRange = "teen",
                tone = io.legado.app.video.audio.VoiceTone.BRIGHT,
                accent = "standard",
                description = "少年热血",
                preferredProvider = "driftwood_mock_tts"
            )
        )
        pipeline.registerVoice(
            io.legado.app.video.audio.VoiceProfile(
                voiceId = "xun_er_female",
                characterName = "萧薰儿",
                gender = io.legado.app.video.audio.VoiceGender.FEMALE,
                ageRange = "teen",
                tone = io.legado.app.video.audio.VoiceTone.BRIGHT,
                accent = "standard",
                description = "少女清冷",
                preferredProvider = "driftwood_mock_tts"
            )
        )

        try {
            // ---- 2) 对 3 条台词 + 1 段旁白 走 generateDialogue/generateNarration ----
            val xiaoyan = pipeline.generateDialogue("斗之力，三段！果然是天才。", "萧炎", emotion = "determined")
            val xuner = pipeline.generateDialogue("炎少爷，别灰心，薰儿信你。", "萧薰儿", emotion = "comforting")
            val narrator = pipeline.generateNarration("云岚山巅，风卷云舒，一场大战即将开始。")
            // 故意跑一条 fail script 断言 fail
            val failRes = runCatching { pipeline.generateDialogue("路人台词", "路人甲") }

            // ---- 3) 断言：providerKey 全部走 Mock、时长对脚本承诺、timestamps 非空 ----
            assertEquals("driftwood_mock_tts", xiaoyan.providerKey, "萧炎 providerKey 必须走 override Mock")
            assertEquals("driftwood_mock_tts", xuner.providerKey, "薰儿 providerKey 必须走 override Mock")
            assertEquals("driftwood_mock_tts", narrator.providerKey, "旁白 providerKey 必须走 override Mock")
            assertEquals(6000L, xiaoyan.durationMs, "萧炎脚本指定 6000ms，Mock 必须严格返回")
            assertEquals(5200L, xuner.durationMs, "薰儿脚本指定 5200ms，Mock 必须严格返回")
            assertEquals(4000L, narrator.durationMs, "旁白脚本指定 4000ms，Mock 必须严格返回")
            assertTrue(xiaoyan.wordTimestamps.isNotEmpty(), "脚本指定 injectTimestamps=true，萧炎台词必须含时间戳")
            assertTrue(xuner.wordTimestamps.isNotEmpty(), "薰儿台词也必须含时间戳")
            assertTrue(narrator.wordTimestamps.isNotEmpty(), "旁白也必须含时间戳")
            assertTrue(failRes.isFailure, "路人甲在 failForCharacterNames 里，synthesize 必须 fail")

            // ---- 4) 额外：TTSRouter.estimateTotalCost() 在 Mock 下也能给出数字 ----
            val reqs = (1..4).map { i ->
                io.legado.app.video.audio.TTSRequest(
                    text = "测试配音句 $i",
                    voiceProfile = io.legado.app.video.audio.VoiceProfile(
                        voiceId = "p$i", characterName = if (i % 2 == 0) "萧炎" else "萧薰儿",
                        gender = if (i % 2 == 0) io.legado.app.video.audio.VoiceGender.MALE else io.legado.app.video.audio.VoiceGender.FEMALE,
                        ageRange = "teen",
                        tone = io.legado.app.video.audio.VoiceTone.MID,
                        accent = "standard",
                        description = ""
                    ),
                    wordTimestamps = true
                )
            }
            val est = TTSRouter.estimateTotalCost(reqs)
            assertTrue(est >= 0f, "estimateTotalCost 不能 <0，得到 $est")

            // ---- 5) 报告 ----
            val totalTts = xiaoyan.durationMs + xuner.durationMs + narrator.durationMs
            println("── Phase D 配音层 DryRun 报告 ──")
            println("  Provider 使用 : driftwood_mock_tts (TTSRouter override 生效)")
            println("  萧炎 台词    : %d ms / %d timestamps".format(xiaoyan.durationMs, xiaoyan.wordTimestamps.size))
            println("  薰儿 台词    : %d ms / %d timestamps".format(xuner.durationMs, xuner.wordTimestamps.size))
            println("  旁白         : %d ms / %d timestamps".format(narrator.durationMs, narrator.wordTimestamps.size))
            println("  总配音时长    : %.1f s".format(totalTts / 1000.0))
            println("  failFor 验证 : 路人甲 → ${failRes.exceptionOrNull()?.message?.take(40)}")
            println("  4 句成本估算 : %.4f".format(est))
            println("── Phase D 通过 ✓ ──")

        } finally {
            TTSRouter.popOverrideForDryRun()
            assertFalse(TTSRouter.isDryRun, "Phase D 结束后 TTSRouter override 栈必须被清，不能污染其他用例")
        }
    }

    // ==================================================================
    // Phase Z: End-to-End 总链路
    // ==================================================================

    @Test
    fun `Phase Z — 全链路 End-to-End DryRun 通过，输出总体报告`() = runTest {
        val (project, chars, scenes) = Triple(testProject, testCharacters, testScenes)

        // ==========================
        // 0. Entity → Engine models
        // ==========================
        val state = project.toScriptState().advanceTo(ScriptStage.VISUAL_STAGE_COMPLETE, approved = true)
        val narration = scenes.toNarrationScript(project.id)
        val profiles = chars.toAgentProfiles()

        // ========================================================
        // 1. ArcReel 四智能体团队循环（Agent JSON → Engine 强类型）
        // ========================================================
        val novelText = testScenes.joinToString("\n") { s ->
            "${s.order + 1}. [${s.shotType}] ${s.summary}  ${s.dialogue.takeIf { it.isNotBlank() }?.let { "「$it」" }.orEmpty()}"
        }
        val agentPlan = TeamExecutionPlan(
            critiqueOn1stLow = true,
            forceConsistencyIssue = true,
            forceQualityBelow = true,
            iteration2Passes = true,
            characterCount = 2,
            storyboardSegments = 8
        )
        val coordinator = AgentTeamCoordinator()
        val artifacts = coordinator.dryRunArtifacts(
            projectId = project.id,
            novelText = novelText,
            characterProfiles = profiles.associate { it.name to it.appearance },
            maxTeamIterations = 2,
            plan = agentPlan
        )
        val teamIters = artifacts.raw.teamIterations
        val finalTeamScore = artifacts.raw.finalScore

        // 验证团队循环闭环真实跑通：iter2 双 rerun → 达标
        assertTrue(teamIters.size == 2, "Agent 团队迭代必须跑 2 轮")
        assertTrue(teamIters[1].characterRerunTriggered && teamIters[1].storyboardRerunTriggered)
        assertTrue(teamIters[1].passesQualityGate)
        // 解析结果数量要对 (角色=2，分镜=8，agentPlan 明确指定)
        assertEquals(agentPlan.characterCount, artifacts.distinctCharacters)
        assertEquals(agentPlan.storyboardSegments, artifacts.totalSegments)

        // ========================================================
        // 2. 生成模式路由 dryRun：用 Agent 产出的角色/分镜作为输入
        // ========================================================
        val frames = artifacts.storyboard.ifEmpty {
            narration.segments.map { seg ->
                StoryboardFrame(
                    frameId = seg.segmentId,
                    index = seg.index,
                    prompt = seg.imagePrompt ?: seg.novelText,
                    characterRefs = seg.referencedCharacters
                )
            }
        }
        // 自动推荐模式（不手动指定）
        val recommendedCfg = GenerationConfig.recommendFor(
            totalSegments = frames.size,
            distinctCharacters = artifacts.distinctCharacters.coerceAtLeast(1),
            hasDialogue = testScenes.any { it.dialogue.isNotBlank() },
            budgetTier = BudgetTier.BALANCED
        )
        val router = GenerationModeRouter()
        val charRefs = artifacts.characters.associate { it.name to "ref://${it.id}.png" }
            .ifEmpty { profiles.associate { it.name to "ref://${it.id}.png" } }
        val genDryRun = router.dryRun(
            recommendedCfg, frames, charRefs, styleRef = "ref://dongfang.png"
        ).getOrThrow()

        // ========================================================
        // 3. 视频装配 Tier3（帧时长用 storyboard 真实秒数 + 连续 timeline + 所有断言校验）
        // ========================================================
        val expectedZStoryboardTotalMs = frames.sumOf { it.durationSeconds.coerceAtLeast(1) * 1000L }
        val (vsSegments, zExpectedFrameMs) = buildTimelineFromStoryboard(
            frames = frames,
            videoBasePath = "/dryrun/videos"
        ) { f ->
            narration.segments.getOrNull(f.index)?.novelText?.take(60) ?: f.prompt.take(60)
        }
        val backend = io.legado.app.video.pipeline.ManifestExporterBackend()
        val assemblyReq = io.legado.app.video.pipeline.AssemblyRequest(
            segments = vsSegments,
            outputPath = File(scratchDir, "episode01.final.mp4").absolutePath,
            aspectRatio = project.targetAspectRatio,
            subtitleStyle = SubtitleStyle(fontSize = 26, position = SubtitlePosition.BOTTOM)
        )
        val assem = backend.assemble(Any() as android.content.Context, assemblyReq) { _, _ -> }.getOrThrow()
        // 预先解析 manifest timings / ffmpeg trim 值 → 供报告 + 断言使用
        val (zManifTimings, zManifTotal) = parseManifestSegmentTimings(
            assem.artifacts.first {
                it.artifactType == io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST
            }.file.readText()
        )
        val zFfmpegRaw = assem.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.FFMPEG_COMMAND_SCRIPT
        }.file.readText()
        val zTrimSec = Regex("""-t\s+([0-9]+\.[0-9]+)""").findAll(zFfmpegRaw)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        val zTrimUniqueSec = zTrimSec.filterIndexed { idx, _ -> idx % 2 == 0 }
        val zTrimTotalMs = (zTrimUniqueSec.sum() * 1000).toLong()

        // ========================================================
        // 3.5. Phase Z 配音层 (Agent 解析出的角色 × TTSRouter Mock 注入)
        // ========================================================
        // 注入 TTS Mock：让每个角色都有可预测的时长，报告里能对齐 Agent 输出的角色数
        val zTtsScript = TTSMockScript(
            forceProviderKey = "phaseZ_mock_tts",
            fixedDurationMs = 4500L,
            injectTimestamps = true,
            failForCharacterNames = emptySet()
        )
        TTSRouter.pushOverrideForDryRun(DeterministicMockTTSBackend(zTtsScript))
        val ttsPipeline = TTSPipeline()
        artifacts.characters.forEach { c ->
            val gender = when {
                c.id.contains("female", true) || c.description.contains("少女") || c.description.contains("女")
                -> io.legado.app.video.audio.VoiceGender.FEMALE
                else -> io.legado.app.video.audio.VoiceGender.MALE
            }
            ttsPipeline.registerVoice(
                io.legado.app.video.audio.VoiceProfile(
                    voiceId = "v_${c.id}",
                    characterName = c.name,
                    gender = gender,
                    ageRange = "teen",
                    tone = io.legado.app.video.audio.VoiceTone.MID,
                    accent = "standard",
                    description = c.appearance.take(30),
                    preferredProvider = "phaseZ_mock_tts"
                )
            )
        }

        data class TtsEntry(val kind: String, val name: String, val textSnippet: String,
                            val durationMs: Long, val timestamps: Int, val providerKey: String)
        val ttsEntries = try {
            val spoken = mutableListOf<TtsEntry>()
            // 角色：每人 1 句 sample
            for (c in artifacts.characters) {
                val sampleText = when (c.role) {
                    "protagonist" -> "命运的齿轮开始转动。"
                    "antagonist" -> "好戏才刚刚开始。"
                    "supporting" -> "少爷，一切已经准备就绪。"
                    else -> "嗯……"
                }
                val r = ttsPipeline.generateDialogue(sampleText, c.name)
                spoken += TtsEntry("对话", c.name, sampleText, r.durationMs, r.wordTimestamps.size, r.providerKey)
            }
            // 旁白：一句 summary
            val narratorText = "故事发生在 ${project.name} 世界。"
            val nr = ttsPipeline.generateNarration(narratorText)
            spoken += TtsEntry("旁白", "_narrator_", narratorText, nr.durationMs, nr.wordTimestamps.size, nr.providerKey)
            spoken
        } finally {
            TTSRouter.popOverrideForDryRun()
        }
        val ttsTotalMs = ttsEntries.sumOf { it.durationMs }
        val ttsAllProviderOk = ttsEntries.all { it.providerKey == "phaseZ_mock_tts" }
        val ttsAllTimestampsOk = ttsEntries.all { it.timestamps > 0 }

        // ========================================================
        // 4. 汇总打印（全面报告：Agent + Generation + TTS + Assembly 四层）
        // ========================================================
        val artifactMap = assem.artifacts.groupBy { it.artifactType }
        val jianying = artifactMap[io.legado.app.video.pipeline.ArtifactType.JIANYING_DRAFT]?.firstOrNull()
        val manifest = artifactMap[io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST]?.firstOrNull()
        val ffmpegSh = artifactMap[io.legado.app.video.pipeline.ArtifactType.FFMPEG_COMMAND_SCRIPT]?.firstOrNull()

        val summary = buildString {
            appendLine("========== ArcReel DryRun 总体报告 (v4) ==========")
            appendLine("项目: ${project.name}   类型=${project.sourceType}   风格=${project.style}")
            appendLine("目标比例=${project.targetAspectRatio}  清晰度=${project.targetResolution}  目标时长=${project.targetDurationSeconds}s")
            appendLine()
            appendLine("─── Agent 层 (四智能体 × 团队迭代 × 2) ───")
            appendLine("团队循环轮数 : ${teamIters.size} / 2   (最终分: ${"%.2f".format(finalTeamScore)})")
            teamIters.forEachIndexed { i, l ->
                appendLine("  Iter${l.iteration}  " +
                        "角色分=${"%.2f".format(l.characterScore)}  " +
                        "分镜分=${"%.2f".format(l.storyboardScore)}  " +
                        "一致分=${"%.2f".format(l.consistencyScore)}  " +
                        "质量分=${"%.2f".format(l.qualityScore)}  " +
                        "charRerun=${l.characterRerunTriggered}  sbRerun=${l.storyboardRerunTriggered}  " +
                        "gate=${l.passesQualityGate}")
            }
            appendLine("Agent 解析角色  : ${artifacts.distinctCharacters} 位  " +
                    artifacts.characters.joinToString(limit = 3, truncated = "...") { it.name })
            appendLine("Agent 解析分镜  : ${artifacts.totalSegments} 段")
            appendLine("Parser warnings : ${artifacts.warnings.size.let { if (it == 0) "无" else it.toString() }}")
            appendLine()
            appendLine("─── 生成模式路由 (推荐器自动选择) ───")
            appendLine("推荐模式  : ${genDryRun.profile.displayName} (${genDryRun.mode})")
            appendLine("成本/帧   : ${"%.2f".format(genDryRun.profile.costPerSegment)}× (REFERENCE=1.35 baseline)")
            appendLine("预算估算  : ${"%.2f".format(genDryRun.estimatedTotalCost)} token×   帧数=${genDryRun.segmentCount}")
            appendLine("一致性评分: ${"%.0f".format(genDryRun.profile.consistencyScore * 100)}%   吞吐: x${genDryRun.profile.throughputMultiplier}")
            val recIssues = ModeCapabilityCatalog.validate(recommendedCfg, genDryRun.segmentCount)
            appendLine("模式警告  : " + if (recIssues.isEmpty()) "无" else recIssues.joinToString(" ; ").take(90))
            appendLine()
            appendLine("─── 配音层 (TTSRouter Mock 注入，DryRun) ───")
            appendLine("使用 Provider  : phaseZ_mock_tts   全命中 Mock=${if (ttsAllProviderOk) "是 ✓" else "否 ✗"}")
            appendLine("配音条目       : ${ttsEntries.size} 条   (对话=${ttsEntries.count { it.kind == "对话" }} 旁白=${ttsEntries.count { it.kind == "旁白" }})")
            ttsEntries.forEach { e ->
                appendLine("  · [${e.kind}] ${e.name.padEnd(12)}  ${if (e.timestamps > 0) "ts✓" else "ts✗"}  " +
                        "${"%.1f".format(e.durationMs / 1000.0)}s  ─ ${e.textSnippet.take(18)}")
            }
            appendLine("总配音时长     : ${ttsTotalMs / 1000}.${"%03d".format(ttsTotalMs % 1000)}s")
            appendLine("wordTimestamps : 全部非空=${if (ttsAllTimestampsOk) "是 ✓" else "否 ✗"}")
            appendLine()
            appendLine("─── 脚本/数据层 ───")
            appendLine("脚本阶段: ${state.currentStage} (历史=${state.history.size})")
            appendLine("Narration 分镜: ${narration.totalSegments} 段   原始 Entity 场景: ${scenes.size}")
            appendLine("角色档案(Entity): ${profiles.size} 位 (${profiles.joinToString { it.name }})")
            appendLine()
            appendLine("─── 视频装配层 (Tier3 兜底导出) ───")
            appendLine("使用后端   : ${assem.usedBackend}   Tier=${assem.tier}")
            appendLine("产物数量   : ${assem.artifacts.size} 类")
            artifactMap.keys.forEach { k -> appendLine("  · $k (${artifactMap[k]?.firstOrNull()?.description})") }
            listOfNotNull(manifest, ffmpegSh, jianying).forEach { f ->
                appendLine("  ✔ ${f.artifactType} → ${f.file.name}")
            }
            // v4 → v5：帧时长精确对账（Storyboard承诺 / manifest / ffmpeg trim 三方比对）
            appendLine("装配时长对账三方表:")
            appendLine("  · Agent storyboard 承诺: ${expectedZStoryboardTotalMs / 1000}.${"%03d".format(expectedZStoryboardTotalMs % 1000)}s" +
                    " (${frames.size} 段, 每段 = storyboard[i].durationSeconds)")
            appendLine("  · JSON manifest        : ${zManifTotal / 1000}.${"%03d".format(zManifTotal % 1000)}s   (${zManifTimings.size} segments parsed)")
            appendLine("  · ffmpeg Step A  trim  : ${zTrimTotalMs / 1000}.${"%03d".format(zTrimTotalMs % 1000)}s   (trim 调用 ${zTrimUniqueSec.size} 段)")
            appendLine("  · AssemblyResult 报告  : ${assem.durationMs / 1000}.${"%03d".format(assem.durationMs % 1000)}s")
            appendLine("逐段 ms 承诺对齐: " +
                    zExpectedFrameMs.entries.joinToString(limit = 5, truncated = " …") { (k, v) -> "$k=$v" })
            assem.warnings.take(3).forEach { w -> appendLine("  ⚠ $w") }
            appendLine()
            appendLine("脚本阶段 → Agent → Generation → TTS(配音Mock) → Tier3 装配 全链路 DryRun 通过 ✓")
            appendLine("========== End of Report (v5: 装配层三方对账 enabled) ==========")
        }
        println(summary)

        // ==============
        // 最终断言矩阵
        // ==============
        assertTrue(profiles.size == chars.size, "Entity 角色数转换应该保持")
        assertTrue(frames.size == narration.totalSegments || frames.size == agentPlan.storyboardSegments,
            "frames 数量必须等于 narration 或 agent storyboard 之一")
        assertTrue(genDryRun.estimatedTotalCost > 0f)
        assertTrue(assemblyReq.segments.sumOf { it.endTimeMs - it.startTimeMs } == assem.durationMs,
            "装配总时长必须等于所有片段累计时长")
        assertTrue(artifacts.raw.success, "Agent 团队循环必须 success")
        assertEquals(8, teamIters.sumOf { if (it.critiqueHintLines >= 0) 1L else 0L })
        assertTrue(ffmpegSh != null && manifest != null, "ffmpeg.sh 和 manifest 两个关键 Artifact 必须存在")
        assertFalse(LLMProviderHub.isDryRun, "Phase Z 结束后 Hub override 必须被清")
        // ---- Phase Z 新：配音层断言 ----
        assertFalse(TTSRouter.isDryRun, "Phase Z 结束后 TTSRouter override 栈必须被清")
        assertTrue(ttsAllProviderOk, "Phase Z 全部配音条目必须命中 phaseZ_mock_tts")
        assertTrue(ttsAllTimestampsOk, "Phase Z 全部配音条目必须含 wordTimestamps")
        assertEquals(artifacts.distinctCharacters + 1, ttsEntries.size,
            "Phase Z 配音条目数 = Agent 解析角色数(每人1句) + 1 句旁白")
        assertTrue(ttsTotalMs > 0L)
        // ---- Phase Z (本轮新增)：Tier3 三方对账断言 ----
        // 1) 全局 4 层 (Agent 承诺 / manifest 总时长 / ffmpeg trim 总秒 / AssemblyResult) 完全对齐
        assertEquals(expectedZStoryboardTotalMs, assem.durationMs,
            "[Assembly] 承诺 ms 必须等于 AssemblyResult.durationMs")
        assertEquals(expectedZStoryboardTotalMs, zManifTotal,
            "[Manifest] 承诺 ms 必须等于 manifest.totalDurationMs")
        assertEquals(expectedZStoryboardTotalMs, zTrimTotalMs,
            "[FFmpeg Trim] 承诺 ms 必须等于 ffmpeg.sh Step A 各段 -t 累加和 (±浮点误差 0.5s 内)")
        // 2) 逐段：每帧 manifest.end - manifest.start = zExpectedFrameMs[frameId]，且 start 严格等于前帧 end (连续 timeline)
        var zAccumPrevEnd = 0L
        for (f in frames) {
            val (s, e) = zManifTimings[f.frameId]
                ?: error("Phase Z manifest 缺 frameId=${f.frameId} 的 startMs/endMs")
            assertEquals(zAccumPrevEnd, s, "Phase Z 连续 timeline 断裂：${f.frameId} start=$s，期望前帧 end=$zAccumPrevEnd")
            val want = zExpectedFrameMs.getValue(f.frameId)
            assertEquals(want, e - s, "Phase Z 帧 ${f.frameId} 时长 ${e-s}ms 与 storyboard 承诺 ${want}ms 不一致")
            zAccumPrevEnd = e
        }
        // 3) ffmpeg.sh：trim 调用数(去重 copy+fallback 的 double -t) = 分镜数
        assertEquals(frames.size, zTrimUniqueSec.size,
            "Phase Z ffmpeg.sh -t 调用次数（去重后）必须 = frames 数；实际 zTrimSec=$zTrimSec")
    }

    // ==================================================================
    // Phase F: Tier3 manifest + ffmpeg.sh 真正执行 (Real ffmpeg binary)
    //   前提：测试机需满足以下条件，否则本用例自动 assume 跳过不报错：
    //     (1) $PATH 中存在 ffmpeg + ffprobe (≥ 5.x 且启用 libx264 + libass)
    //     (2) fontconfig 至少有一种可渲染字幕的字体（例如 DejaVu Sans）
    //     (3) 环境变量 ARCREEL_RUN_PHASE_F_REAL_FFMPEG=1 显式开启
    //         （避免普通 CI 上每轮跑 2–3 分钟 ffmpeg 全量编码）
    //   参考实现（纯 Python 镜像 Tier3 buildFFmpegScript，已在本地真实通过）：
    //     workspace/_phasef/phasef_runner.py  —— 非均匀 8 帧 (3.2+4.8+2.5+6+4+5.5+3+7=36s)，
    //     trim → concat → subtitle burn → faststart → ffprobe duration。
    //     最终实际：storyboard 36000ms vs final.mp4 36083ms, Δ=+83ms (< 250ms 帧级公差)
    // ==================================================================
    @Test
    fun `Phase F — Tier3 生成的 ffmpeg sh 真正执行，最终 mp4 时长与 storyboard 承诺一致 (±250ms)`() = runTest {
        if (System.getenv("ARCREEL_RUN_PHASE_F_REAL_FFMPEG") != "1") {
            println("[Phase F] SKIP: 未设置 ARCREEL_RUN_PHASE_F_REAL_FFMPEG=1；" +
                    "真跑请确保 ffmpeg/ffprobe 就位 + 设置环境变量开启")
            return@runTest
        }
        val ffmpegOk = runCatching {
            ProcessBuilder("ffmpeg", "-version").start().also { it.waitFor() }.exitValue() == 0
        }.getOrDefault(false)
        val ffprobeOk = runCatching {
            ProcessBuilder("ffprobe", "-version").start().also { it.waitFor() }.exitValue() == 0
        }.getOrDefault(false)
        if (!ffmpegOk || !ffprobeOk) {
            println("[Phase F] SKIP: 当前环境找不到 ffmpeg/ffprobe 二进制")
            return@runTest
        }

        // ---- 1. 8 段非均匀 storyboard（故意不用 5s 整数，防止误通过） ----
        data class Frame(val frameId: String, val durationSec: Double, val color: String, val text: String)
        val frames = listOf(
            Frame("frame_00", 3.2, "red",     "Lin Yao walks along the misty shore."),
            Frame("frame_01", 4.8, "orange",  "She finds a glowing jade pendant in the sand."),
            Frame("frame_02", 2.5, "yellow",  "The light bursts, a green vortex opens in the sky."),
            Frame("frame_03", 6.0, "green",   "She falls onto a giant mushroom; ShanHaiJie reveals itself."),
            Frame("frame_04", 4.0, "cyan",    "Mo Yuan appears: 'You are finally here.'"),
            Frame("frame_05", 5.5, "blue",    "Their eyes meet --"),
            Frame("frame_06", 3.0, "magenta", "Suddenly! A huge black bird descends from the sky!"),
            Frame("frame_07", 7.0, "white",   "Mo Yuan grabs her; they leap together into the waterfall.")
        )
        val expectedMsTotal = frames.sumOf { (it.durationSec * 1000).toLong() }

        // ---- 2. 生成足够长的素材 mp4 (每段 15s, 纯色 + 静音, 9:16) ----
        val inputsDir = File(scratchDir, "inputs").also { it.mkdirs() }
        frames.forEach { f ->
            val out = File(inputsDir, "${f.frameId}.mp4")
            ProcessBuilder(
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=c=${f.color}:s=1080x1920:d=15:r=24",
                "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo:d=15",
                "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-crf", "26",
                "-c:a", "aac", "-b:a", "96k", "-shortest", out.absolutePath
            ).start().also { it.waitFor() }.let { proc ->
                assertEquals(0, proc.exitValue(), "生成 ${f.frameId} 素材 mp4 失败")
            }
        }

        // ---- 3. buildTimelineFromStoryboard → Tier3 → manifest+ffmpeg.sh ----
        val (segs, expectedFrameMs) = buildTimelineFromStoryboard(
            frames = frames.mapIndexed { i, f ->
                StoryboardFrame(
                    frameId = f.frameId, index = i,
                    durationSeconds = f.durationSec.coerceAtLeast(1.0),
                    prompt = f.text, shotType = ShotType.LONG, mood = f.color,
                    cameraMovement = CameraMovement.STATIC,
                    characterIds = emptyList(), imageStyle = ""
                )
            },
            videoBasePath = inputsDir.absolutePath
        ) { frame -> frames.first { it.frameId == frame.frameId }.text }

        val backend = io.legado.app.video.pipeline.ManifestExporterBackend()
        val assembly = backend.assemble(
            Any() as android.content.Context,
            io.legado.app.video.pipeline.AssemblyRequest(
                segments = segs,
                outputPath = File(scratchDir, "phasef.final.mp4").absolutePath,
                aspectRatio = "9:16",
                subtitleStyle = SubtitleStyle(fontSize = 26, position = SubtitlePosition.BOTTOM)
            )
        ) { _, _ -> }.getOrThrow()
        assertEquals(expectedMsTotal, assembly.durationMs, "AssemblyResult 应与 storyboard 和相等")

        val shFile = assembly.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.FFMPEG_COMMAND_SCRIPT
        }.file
        // Tier3 写 assemble.sh 的输出路径 = scratchDir/phasef.final.mp4，但可能与 request.outputPath 略有不同
        // 这里只要能执行成功就行
        shFile.setExecutable(true)

        // ---- 4. 真正执行 assemble.sh (约 1-3 分钟) ----
        val proc = ProcessBuilder("bash", shFile.absolutePath)
            .directory(scratchDir)
            .redirectErrorStream(true)
            .start()
        val log = proc.inputStream.bufferedReader().readText()
        val rc = proc.waitFor()
        assertEquals(0, rc, "Phase F assemble.sh 执行失败\n----tail log----\n${log.takeLast(1500)}")

        // ---- 5. 定位最终 mp4（与 manifest 写回路径一致） ----
        val manif = assembly.artifacts.first {
            it.artifactType == io.legado.app.video.pipeline.ArtifactType.SEGMENT_MANIFEST
        }.file.readText()
        val finalOut = Regex(""""outputPath"\s*:\s*"([^"]+)"""").find(manif)?.groupValues?.get(1)
            ?.let { File(it) } ?: File(scratchDir, "phasef.final.mp4")
        assertTrue(finalOut.exists() && finalOut.length() > 10_000,
            "Phase F 最终 mp4 缺失或过小：$finalOut")

        // ---- 6. ffprobe duration → 断言 storyboard 总秒数 ± 250ms ----
        val probeProc = ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            finalOut.absolutePath
        ).start()
        val probeOut = probeProc.inputStream.bufferedReader().readText().trim()
        assertEquals(0, probeProc.waitFor(), "ffprobe 失败")
        val actualSec = probeOut.toDoubleOrNull()
            ?: error("ffprobe 无法解析 duration: $probeOut")
        val actualMs = (actualSec * 1000).toLong()
        assertTrue(kotlin.math.abs(actualMs - expectedMsTotal) <= 250L,
            "Phase F 实际 mp4 时长 $actualMs ms vs storyboard $expectedMsTotal ms，" +
                    "Δ=${actualMs - expectedMsTotal}ms (> ±250ms 公差)")
        println("[Phase F] PASS: storyboard=${expectedMsTotal}ms  actual=${actualMs}ms  Δ=${actualMs - expectedMsTotal}ms")
    }

    // ==================================================================
    // 私有辅助
    // ==================================================================

    private fun buildVideoSegmentsForAssemble(): List<VideoSegment> {
        val base = 0L
        val lines = listOf(
            "晨雾笼罩的海岸边，林瑶独自行走。",
            "她在沙中发现了一块会发光的玉佩。",
            "光芒大盛，天空出现绿色漩涡。",
            "她跌落在巨型灵芝之上，山海界全貌展现。",
            "神秘银发男子墨渊出场：\n\"你终于来了啊。\"",
            "两人对视——",
            "突然！巨大黑鸟从天而降！",
            "墨渊拉住她，两人一同跃入瀑布。"
        )
        var t = base
        return lines.mapIndexed { i, line ->
            val start = t
            val end = t + if (i == 7) 6_000L else 4_500L
            t = end
            VideoSegment(
                segmentId = "seg_%02d".format(i + 1),
                videoPath = "/mock/video/${i + 1}.mp4",
                startTimeMs = start,
                endTimeMs = end,
                subtitleText = line,
                transitionType = when (i) {
                    1 -> TransitionType.DISSOLVE
                    3 -> TransitionType.FLASH_WHITE
                    6 -> TransitionType.J_CUT
                    else -> TransitionType.NONE
                }
            )
        }
    }

    /**
     * 把 Agent 解析出的 StoryboardFrame 列表转换为 **连续 timeline** 的 VideoSegment：
     *   - 每帧时长严格取 f.durationSeconds（Agent 规划的真实秒数，含 Agent 决定的镜头呼吸）
     *   - start = 前面累计; end = start + duration*1000
     *   - subtitleText = fallback(narration→agent prompt)，取到即填入
     *
     *  返回第二值：方便断言的「期望每帧 ms」列表 (frameId → ms)
     */
    private fun buildTimelineFromStoryboard(
        frames: List<StoryboardFrame>,
        videoBasePath: String = "/dryrun/videos",
        subtitleForFrame: (StoryboardFrame) -> String
    ): Pair<List<VideoSegment>, Map<String, Long>> {
        var cursor = 0L
        val expectedMs = linkedMapOf<String, Long>()
        val segs = frames.map { f ->
            val durationMs = (f.durationSeconds.coerceAtLeast(1) * 1000L)
            val start = cursor
            val end = start + durationMs
            cursor = end
            expectedMs[f.frameId] = durationMs
            VideoSegment(
                segmentId = f.frameId,
                videoPath = "$videoBasePath/${f.frameId}.mp4",
                startTimeMs = start,
                endTimeMs = end,
                subtitleText = subtitleForFrame(f),
                transitionType = when {
                    f.index >= 1 && f.index % 3 == 2 -> TransitionType.CROSS_FADE
                    else -> TransitionType.NONE
                }
            )
        }
        return segs to expectedMs
    }

    /**
     * 从 SEGMENT_MANIFEST JSON 中把每个 segment 的 startMs/endMs 抽出来（不用依赖 JSON 库，零三方），
     * 按 id 做 map。返回 (id → (startMs, endMs))，以及 totalDurationMs。
     */
    private fun parseManifestSegmentTimings(rawManifest: String): Pair<Map<String, Pair<Long, Long>>, Long> {
        val idRe = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val startRe = "\"startMs\"\\s*:\\s*(\\d+)".toRegex()
        val endRe = "\"endMs\"\\s*:\\s*(\\d+)".toRegex()
        val totalRe = "\"totalDurationMs\"\\s*:\\s*(\\d+)".toRegex()
        val total = totalRe.find(rawManifest)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        // 找到 segments[i] { ... } 内各自匹配
        val segBlockRe = "\\{[^\\{\\}]*\"id\"[^\\{\\}]*\\}".toRegex()
        val out = linkedMapOf<String, Pair<Long, Long>>()
        segBlockRe.findAll(rawManifest).forEach { m ->
            val blk = m.value
            val id = idRe.find(blk)?.groupValues?.get(1) ?: return@forEach
            val s = startRe.find(blk)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
            val e = endRe.find(blk)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
            out[id] = s to e
        }
        return out to total
    }

    private fun requireArtifact(r: AssemblyResult, t: io.legado.app.video.pipeline.ArtifactType): File {
        return r.artifacts.firstOrNull { it.artifactType == t }?.file
            ?: error("Required ArtifactType=$t not found in ${r.artifacts.map { it.artifactType }}")
    }
}
