@file:Suppress("unused")

package io.legado.app.video.docs

/**
 * ArcReel-Style 视频生成系统 - 架构总览与生产就绪评估
 *
 * 本文件描述整个视频生成系统的架构设计哲学、模块划分、
 * 数据流走向、以及各模块的生产就绪状态。
 *
 * 设计原则：
 *   1. 单一职责 - 每个模块只做一件事，并做好
 *   2. 松耦合高内聚 - 模块间通过明确接口交互
 *   3. 容错优先 - 任何外部调用都有降级和恢复策略
 *   4. 可观测 - 每个阶段都有状态追踪与质量评分
 *   5. 渐进增强 - 基础功能可用，高级功能逐步启用
 */

// ==================================================================
// PART 1: 架构总览图 (Mermaid ASCII 图)
// ==================================================================

/*
                                   ┌──────────────────────────────────┐
                                   │         UI Layer (Compose)       │
                                   │  ┌─────────────┐  ┌──────────┐  │
                                   │  │ Workbench   │  │ Wizards  │  │
                                   │  └──────┬──────┘  └────┬─────┘  │
                                   │  ┌──────┴──────┐  ┌────┴─────┐  │
                                   │  │ Screens     │  │ Components│ │
                                   │  └──────┬──────┘  └────┬─────┘  │
                                   └─────────┼──────────────┼────────┘
                                             │              │
                                   ┌─────────▼──────────────▼────────┐
                                   │    State Layer (MVVM + Store)    │
                                   │  ┌──────────────────────────┐    │
                                   │  │ VideoProjectViewModel    │    │
                                   │  │  - StateFlow (projects)  │    │
                                   │  │  - StateFlow (scenes)    │    │
                                   │  │  - StateFlow (chars)     │    │
                                   │  │  - Events (SharedFlow)   │    │
                                   │  └────────────┬─────────────┘    │
                                   │  ┌────────────▼─────────────┐    │
                                   │  │ ConfigStatusStore        │    │
                                   │  │ CostStore                │    │
                                   │  │ StageStateStore          │    │
                                   │  └────────────┬─────────────┘    │
                                   └───────────────┼──────────────────┘
                                                   │
                                   ┌───────────────▼──────────────────┐
                                   │    Orchestration Layer           │
                                   │  ┌────────────────────────────┐  │
                                   │  │ VideoPipelineOrchestrator  │  │  ← 核心协调器
                                   │  │  · 桥接 Room ↔ Pipeline    │  │
                                   │  │  · 驱动 11 阶段流转        │  │
                                   │  │  · 调度 Agent + Quality    │  │
                                   │  └──────────┬─────────────────┘  │
                                   │  ┌──────────▼─────────────────┐  │
                                   │  │ StageManager               │  │  ← 阶段管理
                                   │  │  · Pause/Resume/Cancel     │  │
                                   │  │  · Progress Tracking       │  │
                                   │  │  · Auto Retry              │  │
                                   │  └──────────┬─────────────────┘  │
                                   │  ┌──────────▼─────────────────┐  │
                                   │  │ ErrorRecoveryManager       │  │  ← 错误恢复
                                   │  │  · 错误分类                 │  │
                                   │  │  · 指数退避重试             │  │
                                   │  │  · 降级策略                 │  │
                                   │  └──────────┬─────────────────┘  │
                                   └─────────────┼────────────────────┘
                                                 │
                    ┌────────────────────────────┴─────────────────────────────┐
                    │                      Pipeline Core                        │
                    │                                                           │
                    │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
                    │  │   Agents     │  │   Engines    │  │   Quality    │   │
                    │  │  (自主协作)  │  │  (专门处理)  │  │  (质量保障)  │   │
                    │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
                    │         │                  │                  │           │
                    │  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐   │
                    │  │ CharAnalyst  │  │ PromptEvolution│ │ QualityScorer│   │
                    │  │ Storyboard   │  │ SceneTransition│ │(6维评分 A+~F)│   │
                    │  │ Consistency  │  │ StyleTransfer  │ │ ReviewGate   │   │
                    │  │ QualityAssess│  │ TTSPipeline    │ │ VersionMgr   │   │
                    │  └──────┬───────┘  └──────┬───────┘  └──────────────┘   │
                    │         │                  │                              │
                    └─────────┼──────────────────┼──────────────────────────────┘
                              │                  │
                   ┌──────────▼──────────────────▼───────────┐
                   │           Multi-Episode / Templates     │
                   │  ┌─────────────────────┐ ┌───────────┐ │
                   │  │ MultiEpisodeOrch.   │ │ Template  │ │
                   │  │  · WorldBuilding    │ │ Engine    │ │
                   │  │  · Char Continuity  │ │ 8 types   │ │
                   │  │  · Inter-Ep. Flow   │ │ presets   │ │
                   │  └─────────────────────┘ └───────────┘ │
                   └────────────────────────┬────────────────┘
                                            │
                          ┌─────────────────▼──────────────────┐
                          │        API / Provider Layer        │
                          │  ┌──────────────────────────────┐  │
                          │  │ BackendRouter (统一路由)      │  │
                          │  │  · CapabilityRouteTable      │  │
                          │  │  · FailoverRouter            │  │
                          │  │  · HealthChecker (熔断)       │  │
                          │  └──────────────┬───────────────┘  │
                          │  ┌──────────────▼───────────────┐  │
                          │  │ ProviderRegistry + Factories │  │
                          │  │  · Agnes / Seedance / Kling  │  │
                          │  │  · Doubao / Grok / New API   │  │
                          │  │  · DALL·E / Stability / Pika│  │
                          │  └──────────────┬───────────────┘  │
                          └─────────────────┼──────────────────┘
                                            │
                          ┌─────────────────▼──────────────────┐
                          │       Data Layer (Room DB)          │
                          │  ┌──────────────────────────────┐  │
                          │  │  VideoProjectRepository       │  │
                          │  └──────────┬───────────────────┘  │
                          │  ┌──────────▼───────────────────┐  │
                          │  │  DAOs: Project / Scene / Char│  │
                          │  │       Task / Settings / Trace │  │
                          │  └──────────┬───────────────────┘  │
                          │  ┌──────────▼───────────────────┐  │
                          │  │  Entities (TypeConvertered)  │  │
                          │  └──────────────────────────────┘  │
                          └────────────────────────────────────┘
*/

// ==================================================================
// PART 2: 11 生产阶段管道定义
// ==================================================================

/*
  Phase 01: INITIALIZATION
    · 从模板创建项目 (TemplateEngine + SmartDefaults)
    · 配置向导 (ConfigurationWizard: 5 steps)
    · 能力检测 (CapabilityAutoDetector)
    → 输出: 初始化的 VideoProject + Settings

  Phase 02: SOURCE_PARSING
    · 输入: 小说/漫画/原文
    · NovelParserAgent → 章节解析 + 情节归纳
    · SmartDefaults.inferProjectType() 自动识别项目类型
    → 输出: StructuredSource (结构化原始文本)

  Phase 03: CHARACTER_DESIGN
    · CharacterAnalyzer + CharacterDesignAgent
    · 自动提取人物关系、外貌、性格、服装
    · 生成视觉参考图 (Character Design Sheets)
    · ConsistencyChecker 跨场景一致性初检
    → 输出: VideoCharacter 列表 + 参考图

  Phase 04: WORLD_BUILDING
    · 世界观设定 (风格/色调/美术规则)
    · StyleAnalyzer 分析文本风格
    · 多集编排: MultiEpisodeOrchestrator
    → 输出: WorldBuilding + VisualStyleProfile

  Phase 05: SCRIPT_ADAPTATION
    · ArcReelAgent (剧本改编)
    · 分段/分集/分镜切分
    · PromptEvolutionEngine 文本增强
    → 输出: ScriptSegment 列表

  Phase 06: STORYBOARD
    · StoryboardPlannerAgent 分镜设计
    · 构图/景别/机位/运镜 (ShotList)
    · SceneTransitionEngine 转场推荐
    → 输出: VideoScene 列表 (含分镜提示词)

  Phase 07: PROMPT_OPTIMIZATION
    · PromptEvolutionEngine - 6 种进化技术
      1. VISUAL_ENRICHMENT 视觉增强
      2. STYLE_TRANSFER 风格迁移
      3. COMPOSITION_BALANCE 构图平衡
      4. EMOTION_AMPLIFICATION 情绪放大
      5. SUBJECT_CLARITY 主体清晰
      6. CINEMATIC_ENHANCEMENT 电影化增强
    · 3轮自我批判循环
    → 输出: EvolvePrompt (可直接送 ImageAPI)

  Phase 08: ASSET_GENERATION
    · 图片生成 (BackendRouter → ImageBackendProvider)
    · 视频生成 (BackendRouter → VideoBackendProvider)
    · GenerationQueue 并行生成 + 限流
    · ProviderHealthChecker 记录成功/失败
    · FailoverRouter 自动切换备用 Provider
    → 输出: 每场景 Image + Video

  Phase 09: POST_PROCESSING
    · QualityScorer - 6 维度评分 (0~1)
      - 视觉一致性 visualConsistency
      - 提示词质量 promptQuality
      - 角色一致性 characterConsistency
      - 叙事连贯性 narrativeFlow
      - 风格统一性 styleUnity
      - 技术质量 technicalQuality
    · 等级: A+(≥0.92) / A(≥0.85) / B(≥0.70) / C(≥0.60) / D(≥0.50) / F(<0.5)
    · ReviewGateManager:
      - F/D 自动重生成 (最多3次)
      - C/B 标记可选优化
      - A/A+ 直接通过
    · TTSPipeline: 角色配音 + BGM
    · PromptOptimizer: 优化下一轮提示词
    → 输出: QualityReport + 合格素材

  Phase 10: ASSEMBLY
    · SceneTransitionEngine 应用转场
    · VideoAssembly 视频拼接 (音轨+字幕)
    · VersionManager 版本归档
    · 成本统计: CostStore.record()
    → 输出: 单集成品 (MP4 + 字幕 + 信息)

  Phase 11: COMPLETE
    · ProjectExporter 多格式导出
    · Dashboard 统计汇总
    · Pipeline 资源清理
    → 输出: 项目交付包
*/

// ==================================================================
// PART 3: 核心设计哲学 (区别于简单串联式管线)
// ==================================================================

/*
  设计哲学 01: "智能体协作 + 自我批判" (Agent Collaboration with Self-Critique)
    ─ 不是一次生成就完事，而是 Agent → Critic → (修正) → Agent 循环
    ─ 最多 3 次迭代，质量不达标则标记失败
    ─ 四智能体分工:
      · CharacterAnalyst: 提取并构建角色档案
      · StoryboardPlanner: 将剧本切分为可拍摄的分镜
      · ConsistencyChecker: 跨场景检查角色风格连续性
      · QualityAssessor: 评估成品质量并建议重做

  设计哲学 02: "一切皆阶段" (Everything Is A Stage)
    ─ 不是 `generate() -> done`，而是 11 个阶段的状态机
    ─ 每个阶段可单独 Pause/Resume/Cancel/Retry
    ─ 阶段产出可独立持久化到 Room，崩溃可断点恢复
    ─ 阶段进度 0~100% 实时可见，真实而非模拟

  设计哲学 03: "多 API 架构 + 熔断降级" (Multi-Provider + Circuit Breaker)
    ─ 不绑定任何一家 API，用 ProviderCapability 抽象能力
    ─ 每种能力 (Image/Video/Text) 可多 Provider
    ─ ProviderHealthChecker 连续失败≥3次 → 熔断 5 分钟
    ─ FailoverRouter: 主 Provider 失败 → 自动切备用链
    ─ CapabilityRouteTable: 根据能力 + 健康状态动态路由

  设计哲学 04: "内建质量保障" (Built-In Quality Assurance)
    ─ 不是事后人工挑错，而是在 ASSET_GENERATION 之后
      自动运行 QualityScorer 6 维度评分
    ─ F/D 级自动触发重生成，最多 3 次
    ─ 每次重生成使用不同 Provider + 进化后的提示词
    ─ ReviewGate 允许人工介入或跳过

  设计哲学 05: "成本与进度可预测" (Predictable Cost & Schedule)
    ─ SmartDefaults.estimateCost() 在创建项目前估算
    ─ CostStore 按 projectId + 调用类型 实时记录
    ─ CostTrackerScreen: 分布饼图 + 预算预警 (80%/100%)
    ─ estimateDuration(): 基于帧数/秒数估算总时长

  设计哲学 06: "智能默认 + 可配置" (Smart Defaults, Overridable)
    ─ 零配置可用: inferProjectType() + recommendDefaults()
      自动识别小说/MV/教育等类型并应用最佳预设
    ─ 8 套预设项目类型:
      · 古装玄幻改编 / 都市言情改编 / 原创动漫 /
        漫画解说 / MV 制作 / 教育动画 /
        营销广告 / 连载短剧
    ─ 但每一个参数都允许用户 override (Wizard + 手动)

  设计哲学 07: "多集叙事的连续性" (Narrative Continuity Across Episodes)
    ─ 不是一集集孤立生成，而是 MultiEpisodeOrchestrator
    ─ WorldBuilding: 世界观、美术规则、色调、年代
    ─ Character Continuity Score(0~1): 跨集角色视觉一致性
    ─ EpisodePlan: 每集主题 + 悬念点 + 下集钩子
    ─ Recap Generation: 前情回顾/次回预告自动生成

  设计哲学 08: "可观测的黑盒" (Observable Black Box)
    ─ 对用户: PipelineDashboardScreen 总览
      · 阶段进度条 / 成本 / 质量得分 / 快速操作
    ─ 对开发者: VideoAgentTrace 数据库表
      · 每个 Agent 调用的输入/输出/耗时/Token 使用
    ─ 对运维: Provider Health Status
      · 每个 Provider 的成功率/响应时间/熔断状态
*/

// ==================================================================
// PART 4: 模块职责清单与依赖关系
// ==================================================================

/*
  模块: data (Data Layer)
  ├── entities: VideoProject, VideoScene, VideoCharacter,
  │             VideoTask, VideoProjectSettings, VideoAgentTrace
  ├── dao:      ProjectDao, SceneDao, CharacterDao,
  │             TaskDao, SettingsDao, AgentTraceDao
  ├── repository: VideoProjectRepository (聚合查询入口)
  └── converters: VideoConverters (JSON ↔ Kotlin)

  模块: api (Provider Layer)
  ├── BackendProtocols:       Request/Result data classes
  ├── ProviderCapabilities:   TEXT/IMAGE/VIDEO/AUDIO/TTS 枚举
  ├── ProviderRegistry:       active/inactive providers
  ├── BackendRouter:          统一入口 + Failover + Health
  ├── ProviderFailoverManager:
  │   ├── ProviderHealthChecker  (成功率/熔断)
  │   ├── FailoverRouter         (主→备用链执行)
  │   └── CapabilityRouteTable   (能力↔provider映射)
  ├── ProviderCredentialManager: 加密存储 API key
  ├── ProviderModelMapper:       modelId ↔ backend endpoint
  ├── GenerationModeRouter:      direct / two_stage / step_by_step
  └── backends: Agnes/Seedance/Kling/Doubao/Grok/NewAPI/
                DALL·E/Stability/Pika/Runway

  模块: config (默认配置 + 向导)
  ├── SmartDefaults (object 顶层推荐总入口)
  │   ├── inferProjectType(content, hint) → ProjectType
  │   ├── recommendDefaults(type) → ProjectDefaults
  │   ├── recommendFromContent(content) → (Type, Defaults)
  │   ├── recommendAspectRatio(platformHint) → "9:16" etc
  │   ├── estimateCost(defaults, scenes, episodes) → 分项费用
  │   └── estimateDuration(defaults, scenes) → 秒数
  ├── ProjectConfigPresets (8 套预设 NOVEL_DEFAULTS etc)
  ├── ProjectType enum (8 种项目类型)
  ├── QualityPreset enum (QUICK→CINEMATIC 5 档)
  ├── CostSensitivity enum (LOW/MEDIUM/HIGH)
  ├── ConfigurationWizard (5 steps 向导)
  │   Step 1: 选择项目类型
  │   Step 2: 选择配置服务商
  │   Step 3: 选择风格预设
  │   Step 4: 设置质量级别
  │   Step 5: 选择并行/比例
  └── CapabilityAutoDetector.detect() → 已配置能力摘要

  模块: agents (自主智能体层)
  ├── ArcReelAgent
  │   ├── analyzeCharacters()      → 提取角色档案
  │   ├── planStoryboard()         → 分镜规划
  │   ├── checkConsistency()       → 跨场景一致性
  │   ├── assessQuality()          → 质量评估
  │   └── critiqueLoop()           → 自我批判(最多3次)
  ├── NovelParserAgent             → 小说章节/情节解析
  ├── CharacterDesignAgent         → 角色视觉设计
  ├── StoryboardPlannerAgent       → 分镜详细设计
  ├── PromptOptimizerAgent         → 提示词优化
  ├── AgentTypes: AgentRole / TaskStatus / CritiqueResult
  ├── AgentMemory (短期 + 场景记忆)
  ├── AgentPromptTemplates (各任务 system prompt)
  ├── AgentStylePresets (cinematic_drama / documentary / music_video)
  └── AgentOrchestrator (调度 + 并发控制)

  模块: pipeline (生产管线核心)
  ├── StageManager
  │   · 11阶段枚举 PipelineStage
  │   · transitionTo(target) 阶段流转
  │   · pause() / resume() / cancel()
  │   · recordStageProgress(0~100)
  │   · retryFailedStages(maxRetries=3)
  ├── TemplateEngine (8 套项目模板)
  │   · 古装玄幻改编 / 都市言情 / 原创动漫 / 漫画解说 /
  │     MV / 教育 / 营销 / 连载短剧
  │   · 每套: VisualStyleProfile + aspectRatio + resolution +
  │           sceneDuration + 默认提示词
  ├── PromptEvolutionEngine
  │   · 6 EvolutionTechnique 枚举
  │   · 5 EvolutionTemplate (STANDARD/CINEMATIC/CHARACTER/MOOD/DETAIL)
  │   · evolve(prompt, techniques, iterations=3)
  │   · changeHistory + qualityScore 可视化
  ├── SceneTransitionEngine
  │   · 20 TransitionType 枚举 (FADE/wipe/DISSOLVE/MATCH_CUT 等)
  │   · recommendTransitions(sceneA, sceneB) → 建议类型+时长
  │   · 7 预设 (cinematic_drama / action / documentary /
  │              music_video / romantic / commercial / minimal)
  ├── StyleAnalyzer / StyleTransferEngine
  │   · analyzeStyleFromContent() → 风格配置
  │   · applyStyle(prompt, styleProfile) → 带风格提示词
  ├── CharacterAnalyzer (调用 LLM 做角色分析)
  ├── MultiEpisodeOrchestrator
  │   · WorldBuilding + Character continuity + 集间衔接
  │   · planEpisodes(totalEpisodes, content) → EpisodePlan[]
  │   · generateRecap(episode) → 前情回顾文本
  │   · continuityScore(projectId) → 角色/风格一致性
  ├── ProductionPipeline / PipelineOrchestrator / TwoStagePipeline
  │   · 不同复杂度的流水线实现
  ├── VideoWorkflows (常用工作流组合)
  ├── ReviewGateManager (质量评审门禁)
  ├── GenerationQueue (并发 + 优先级队列)
  ├── CostTracker (API 调用成本追踪)
  ├── EpisodePlanner (分集规划)
  ├── AssetLibrary (素材复用库)
  ├── VersionManager (版本归档)
  ├── ProjectExporter (交付导出)
  ├── VideoAssembly (音视频拼接)
  ├── SubagentSystem (子任务拆分)
  └── ArcReelArchitecture (架构入口文档类)

  模块: quality (质量层)
  └── QualityScorer
      · 6 Dimension 评分
      · qualityGrade(score) → A+~F 等级
      · generateReport() → QualityReport
      · suggestImprovements() → 改进建议 Prompt
      · assessStoryboardFrame() / assessCharacterDesign() /
        assessScriptSegment() / assessVideoClip() / assessFullEpisode()

  模块: service (服务层)
  ├── VideoPipelineOrchestrator (核心: Room ↔ Pipeline 桥接)
  │   · startPipeline(projectId) → 从当前阶段继续
  │   · createProjectFromTemplate() → 模板创建
  │   · recordCost() → API 调用记账
  │   · 驱动 11 阶段流转并持久化状态
  ├── ErrorRecoveryManager
  │   · 错误分类 NETWORK_TIMEOUT / RATE_LIMIT / CONTENT_POLICY /
  │          SERVER_ERROR / AUTH_FAILURE / INVALID_REQUEST / QUOTA_EXCEEDED
  │   · calculateBackoff(category) → 指数退避毫秒
  │   · fallbackStrategy(category) → FALLBACK_PROVIDER / SIMPLIFY / SKIP
  │   · executeWithRecovery<T>(block) → 自动重试
  ├── VideoWorkflowEngine (子流程引擎)
  └── VideoGenerationService (Android Service 前台通知)

  模块: audio (音频层)
  └── TTSPipeline
      · VoiceProfile: 角色音色档案 (pitch/speed/emotion)
      · Emotion → Voice 参数映射 (happy/sad/angry 等 8 种)
      · 9 类 BGM 推荐 (EPIC/ROMANTIC/SUSPENSE/TENSE/HAPPY/
                       SAD/HEROIC/MYSTERIOUS/RELAXING)
      · generateNarration(script) / generateDialogue(char, line)
      · generateBackgroundMusic(sceneList)

  模块: states (全局状态 Store)
  └── AppStores: ConfigStatusStore / CostStore / StageStateStore
       (都使用 MutableStateFlow + data class, 线程安全)

  模块: styles (视觉风格)
  └── VisualStyleProfile (美术风格配置)
       artStyle / colorTone / detailLevel / cinematicLighting /
       cameraMovement / aspectRatio / resolution

  模块: export (交付层)
  ├── VideoExportManager (FFmpeg 封装)
  │   · concatScenes() / addSubtitles() / mergeAudio() /
  │     addWatermark() / applyTransition()
  │   · export(projectId, format) → File
  └── ExportDialog / ProjectExportScreen (UI)

  模块: realtime (事件总线)
  └── ProjectEventService: emit(ProjectCreated/StageCompleted/
                                QualityReportGenerated/CostRecorded 等)

  模块: ui (所有 Compose Screen)
  ├── VideoWorkbenchActivity (NavHost + 12 Screen 路由)
  │   Screen 枚举:
  │   · ProjectList      项目列表 (快速操作卡)
  │   · Storyboard       分镜工作台
  │   · Pipeline         11阶段进度
  │   · Preview          单场景预览
  │   · Characters       角色列表 + 设计
  │   · Settings         全局 + Provider 配置
  │   · Templates        8 套模板浏览
  │   · MultiEpisode     多集编排 + 世界观
  │   · CostTracker      成本分布 + 预算
  │   · Export           多格式导出
  │   · PromptEvolution  提示词进化编辑器
  │   · SceneTransition  转场编辑
  │   · SceneEditor(id)  单场景编辑器
  │
  ├── components: VideoComponents (Chip/Progress/StageCard 等)
  ├── project: VideoProjectListScreen / NewProjectDialog
  ├── storyboard: StoryboardWorkbenchScreen
  ├── scene: SceneEditorScreen / PromptEvolutionScreen / SceneTransitionScreen
  ├── pipeline: PipelineDashboardScreen / PipelineStageScreen
  │            MultiEpisodeScreen / CostTrackerScreen
  │            TemplateEngineScreen / NewProjectWizard / QualityReportScreen
  ├── character: CharacterListScreen
  ├── preview: VideoPreviewScreen / ProjectExportScreen
  ├── settings: VideoSettingsScreen / ProviderSettingsScreen
  ├── theme: VideoTheme + VideoAnimations
  └── templates: TemplateBrowseScreen
*/

// ==================================================================
// PART 5: 数据流一致性说明
// ==================================================================

/*
  数据流 (单向 + CQRS 风格)
  ─────────────────────────

  写入路径 (Command)
    UI Action
    → ViewModel Intent
    → VideoPipelineOrchestrator.command(projectId, action)
    → StageManager.transitionTo() + Engine.work()
    → VideoProjectRepository.insertXxx / updateXxx()
    → Room DAO
    → SQLite DB
    ↓
    发布 MutableStateFlow.value = newState
    ↓
    所有 UI collector 自动重绘

  读取路径 (Query)
    UI collectAsStateWithLifecycle
    ← ViewModel.state: StateFlow<UiState>
    ← VideoProjectRepository.observeXxx(): Flow<T>
    ← Room DAO @Query → Flow<T>
    ← 自动推送 DB 变化

  为什么这样设计 (不直接调用 DAO)?
  ─ Repository 隔离实现: 以后 Room → 别的 DB 只改 Repository
  ─ 业务查询 (getProjectSummary) 在 Repository 聚合，不用散在 UI
  ─ 所有 IO 强制走 Dispatchers.IO
  ─ 统一错误处理

  核心桥接: VideoPipelineOrchestrator
  ────────────────────────────────────
  它是"纯数据实体 (Room Entities)"和"纯管线对象 (Pipeline Engines/Agents)"的中间层:
    · 从 Room 加载 VideoProject → 转为 PipelineConfig
    · 从 VideoScenes 列表 → 转为 Agent 需要的 ScriptSegment + ShotDescriptor
    · CharacterAnalyzer 输出 → VideoCharacter 实体化入库
    · QualityReport → 写入 VideoAgentTrace
    · 每个阶段完成 → updateStatus + updateProgress → 自动 push 到 UI
    · API 调用 → recordCost() → CostStore

  没有这个桥接的后果是 UI 直接耦合各种 Engine，以后很难替换实现。
*/

// ==================================================================
// PART 6: 生产就绪评估 (Production Readiness Checklist)
// ==================================================================

/*
  ╔════════════════════════════════════════════════════════════════╗
  ║                     生产就绪评估矩阵 (v2)                       ║
  ╠════════════════════════════╤═══════╤════════════╤═════════════════════════╣
  ║ 模块                       │ 就绪度│ 风险等级   │ 备注                    ║
  ╠════════════════════════════╪═══════╪════════════╪═════════════════════════╣
  ║ Room DB (Entities + DAOs) │ ████░ │ LOW        │ 字段、索引、类型转换齐全║
  ║ + Repository               │       │            │ (13 entities + 5 DAOs)  ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ VideoPipelineOrchestrator  │ ████░ │ MEDIUM     │ 11阶段驱动 OK；需       ║
  ║ + StageManager             │       │            │ Orchestrator 端到端联调 ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ BackendRouter              │ ████░ │ MEDIUM     │ Failover + HealthCheck  ║
  ║ + Failover + HealthCheck   │       │            │ 已注入 LLMProviderHub   ║
  ║ + LLMProviderOverride      │       │            │ (Real/Mock 双 Provider) ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ ArcReelAgent (4智能体)     │ ████░ │ MEDIUM     │ 团队级自我修正循环 ok   ║
  ║ + 3次批判循环 + Plan 注入  │       │            │ maxTeamIterations=2     ║
  ║ + SelfCritiqueEngine 策略  │       │            │ DryRun 可测             ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ AgentOutput → Engine 解析  │ ████░ │ LOW        │ CharacterStoryboard     ║
  ║  (CharacterStoryboard      │       │            │ JsonParser 零三方依赖   ║
  ║   JsonParser)              │       │            │ 5 种解析质量自校验      ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ DryRunEngineArtifacts      │ ████░ │ LOW        │ Agent 团队 JSON →       ║
  ║ (四智能体串联产物)         │       │            │ Character/Storyboard    ║
  ║                            │       │            │ + warnings + 聚合属性   ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ GenerationModeRouter       │ ████░ │ LOW        │ 三模式成本/一致性/吞吐   ║
  ║ + 3 Mode CapabilityProfile │       │            │ recommendFor 启发式 OK  ║
  ║ + recommendFor 启发式      │       │            │ + dryRun 预算估算       ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ QualityScorer 6维评分      │ ███░░ │ MEDIUM     │ LLM 评分稳定性          ║
  ║ + ReviewGate 自动重做      │       │            │ 需人工阈值校准          ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ PromptEvolutionEngine      │ ████░ │ LOW        │ 6技术 + 5模板 + UI编辑器║
  ║ 6技术 + 5模板 + UI编辑器   │       │            │ 纯文本变换稳定性高      ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ SceneTransitionEngine      │ ████░ │ LOW        │ 20种转场 + 7预设        ║
  ║ 20种转场 + 7预设           │       │            │ 纯数据计算              ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ TTSPipeline                │ ████░ │ MEDIUM     │ TTSBackend 抽象 +       ║
  ║ + 5 Provider Backend       │       │            │ 5 厂商(火山/阿里/讯飞/  ║
  ║   (火山/阿里/讯飞/Edge/离线)│       │            │ Edge-TTS/离线占位)      ║
  ║ + TTSRouter 熔断/健康检查  │       │            │ + 优先级调度 + 健康检   ║
  ║ + BGM 推荐                 │       │            │ DryRun 可注入           ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ MultiEpisodeOrchestrator   │ ███░░ │ MEDIUM     │ 数据结构完备            ║
  ║ WorldBuilding + 角色延续   │       │            │ 端到端需验证            ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ TemplateEngine             │ ████░ │ LOW        │ 8套项目模板 +            ║
  ║ 8套项目模板 + SmartDefaults│       │            │ SmartDefaults 关键字识别║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ ErrorRecoveryManager       │ ████░ │ LOW        │ 7类错误+指数退避+       ║
  ║                            │       │            │ 降级策略                ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ CostStore + CostTracker    │ ████░ │ LOW        │ 统计/分布/预警齐全      ║
  ║                            │       │            │ 加 GenerationMode 预算  ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ VideoWorkbench UI (12屏)   │ ███░░ │ MEDIUM     │ 全部 Screen 存在        ║
  ║ 组件 + 主题                │       │            │ 缺 GenerateMode 推荐卡  ║
  ╟────────────────────────────┼───────┼────────────┼─────────────────────────╢
  ║ VideoAssembly + Export     │ ████░ │ MEDIUM     │ 三级降级策略:           ║
  ║ 三级降级 (Tier1/2/3)       │       │            │ Tier1 MobileFFmpeg      ║
  ║                            │       │            │ Tier2 RuntimeFFmpeg     ║
  ║                            │       │            │ Tier3 ManifestExporter  ║
  ║                            │       │            │ + 纯 Kotlin fallback    ║
  ╚════════════════════════════╧═══════╧════════════╧═════════════════════════╝

  就绪度等级说明:
    █████ 100% - 已测并上线
    ████░ 80%  - 代码完成，需端到端联调
    ███░░ 60%  - 核心逻辑完成，缺业务验证
    ██░░░ 40%  - 框架搭好，缺外部依赖集成
    █░░░░ 20%  - 接口定义，缺核心实现

  [新增] v2 版本相较于 v1 的主要变化:
    1. Agent 协作 (80% / MEDIUM): 新增团队级循环 + TeamExecutionPlan 可复现测试
    2. JSON→Engine 解析 (80% / LOW): 新增零三方依赖 CharacterStoryboardJsonParser
    3. DryRunEngineArtifacts (80% / LOW): 四智能体串联后直接产出 Engine 强类型
    4. GenerationModeRouter (80% / LOW): 三模式完整 Catalog + recommendFor + dryRun 预算
    5. TTSPipeline (80% / MEDIUM): 5 Provider + TTSRouter 熔断/健康检查
    6. VideoAssembly (80% / MEDIUM): 三级降级 + 纯 Kotlin fallback
*/

// ==================================================================
// PART 7: 生产部署前的检查项 (Go-Live Checklist)
// ==================================================================

/*
  Go-Live Checklist (v2)
  ═══════════════════════════════════════════════════════════════

  [P0 必须]
  □ 所有 Provider 的 API Key 已加密存储 (ProviderCredentialManager)
  □ 文本/图片/视频 Provider 至少各配一个并通过 testProvider()
  □ VideoGenerationService Android Foreground Service 权限已给
  □ Room DB Schema 测试: 升级/降级 Migration
  □ ErrorRecoveryManager: ContentPolicy 错误不会无限重试
  □ BackendRouter: initDefaultRoutes() 在启动时调用
  □ 项目磁盘配额: 每个项目限制素材目录大小 (例如 ≤ 2GB)
  □ [必须-新增] 一键 DryRun 全部通过:
      ./gradlew :app:testDebugUnitTest --tests "io.legado.app.video.test.ArcReelPipelineDryRunTest"
        Phase A Parser / Phase AC 三模块串联 / Phase Z 四层报告 必须 100% 绿色
  □ [必须-新增] VideoAssembly Tier3 可用: ManifestExporterBackend.assemble()
      在任一能启动 JVM 的机器上必须返回 100% 成功(无外部依赖)
  □ [必须-新增] TTSPipeline 至少 1 个 Provider testProvider() 通过:
      如果线上环境无法提供 API Key → 必须启用 OfflineTTSBackend 作为 fallback
  □ [必须-新增] LLMProviderHub 线上环境 unsetOverride() 被调用:
      (禁止 DeterministicMockLLMProvider 溜进正式环境)

  [P1 强烈推荐]
  □ Provider 并发限流: GenerationQueue 设置 maxConcurrent ≤ 提供商配额
  □ 成本预警: CostStore 在 80% 时弹 Dialog 提醒用户确认
  □ 崩溃恢复: VideoPipelineOrchestrator.onAppRestart(projectId)
    扫描处于 GENERATING 状态但无 VideoTask 运行中的项目 → 自动重试
  □ 质量评估阈值: A+ 通过 / C 需要用户确认 / F 自动重做3次后停下
  □ 用户可跳过任意阶段 (可在 Stage 菜单选择 "跳过此阶段")
  □ 日志聚合: 所有阶段状态变更打点 (可用 AgentTrace 表实现)
  □ [推荐-新增] GenerationConfig.recommendFor() 在创建项目时弹出卡片:
      让用户确认所选生成模式 (尤其 REFERENCE_VIDEO 模式会增加成本)
  □ [推荐-新增] TTSRouter 健康检查 + 熔断阈值调优:
      建议 healthy=false 后至少 60 秒才重新探查,避免消耗 Provider 免费额度
  □ [推荐-新增] Agent 团队 maxTeamIterations=2 保留默认:
      不要因为想省钱就改成 1,会导致角色/分镜一致性显著下降
  □ [推荐-新增] Agent→Parser 自校验 warnings UI 展示:
      有 warnings > 0 时,提示用户"有 X 帧缺少角色引用,请检查小说文本人名是否一致"

  [P2 优化]
  □ 磁盘缓存: 生成过的提示词 → 相同 prompt 命中后跳过生成
  □ 预生成: 用户创建项目时先预生成 Character 设计
  □ 低电量模式: 检测到电池 ≤20% 时自动暂停长耗时任务
  □ 省电 Doze 模式: 白名单 + Wifi Only 生成选项
  □ [优化-新增] DryRun 作为 CI 门卡: PR 必须通过 Phase Z 才能合入
  □ [优化-新增] ModeCapabilityCatalog 定期重新测 (每季度一次):
      因为提供商单价/延迟/配额都会变
*/

// ==================================================================
// PART 8: 快速使用示例 (Hello World Flow)
// ==================================================================

/*
  ```kotlin
  // 1. 用 SmartDefaults 智能推荐
  val (projectType, defaults) = SmartDefaults.recommendFromContent(
      sourceContent = novelText,
      projectName = "斗破苍穹第一集"
  )
  // → (NOVEL_ADAPTATION, 古装玄幻 默认配置)

  // 2. 从模板创建项目
  val orchestrator = VideoPipelineOrchestrator.instance
  val project = orchestrator.createProjectFromTemplate(
      templateId = "novel_fantasy_cn",
      projectName = "斗破苍穹·云岚山之战",
      sourceContent = novelText
  ).getOrThrow()

  // 3. 启动管线 (11阶段自动流转)
  lifecycleScope.launch {
      orchestrator.pipelineState.collect { state ->
          println("Stage: ${state.currentStage} ${state.progress}%")
          println("Quality A+: ${state.qualityReport.filter { it.grade == "A+" }.size}")
          println("Cost so far: $${state.totalCostSpent}")
      }
  }
  orchestrator.startPipeline(project.id)

  // 4. 随时可暂停/恢复
  orchestrator.pausePipeline(project.id)
  delay(5_000)
  orchestrator.resumePipeline(project.id)

  // 5. 最终导出
  VideoExportManager.export(
      projectId = project.id,
      format = ExportFormat.MP4_1080P,
      includeSubtitles = true,
      watermark = "ArcReel Studio"
  )
  ```
*/
