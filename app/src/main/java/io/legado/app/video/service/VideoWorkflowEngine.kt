package io.legado.app.video.service

import android.content.Context
import android.util.Log
import io.legado.app.data.appDb
import io.legado.app.video.api.*
import io.legado.app.video.agent.*
import io.legado.app.video.data.dao.*
import io.legado.app.video.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class VideoWorkflowEngine(private val context: Context) {
    
    private val apiClient by lazy { ApiProviderFactory.getClientOrDefault() as AgnesApiClient }
    private val projectDao by lazy { appDb.videoProjectDao }
    private val characterDao by lazy { appDb.videoCharacterDao }
    private val sceneDao by lazy { appDb.videoSceneDao }
    private val taskDao by lazy { appDb.videoTaskDao }
    private val traceDao by lazy { appDb.videoAgentTraceDao }
    
    private val _workflowState = MutableStateFlow(WorkflowState())
    val workflowState: StateFlow<WorkflowState> = _workflowState
    
    data class WorkflowState(
        val projectId: String = "",
        val currentStep: WorkflowStep = WorkflowStep.IDLE,
        val progress: Int = 0,
        val message: String = "",
        val error: String = "",
        val completedSteps: List<WorkflowStep> = emptyList()
    )
    
    enum class WorkflowStep {
        IDLE, ANALYZING, CHARACTER_DESIGN, STORYBOARD_PLANNING,
        STORYBOARD_GENERATION, VIDEO_GENERATION, EXPORT, COMPLETED, FAILED
    }
    
    suspend fun createIdeaProject(
        name: String,
        idea: String,
        genre: String = "",
        style: String = ""
    ): VideoProject = withContext(Dispatchers.IO) {
        val project = VideoProject(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceType = VideoProject.SOURCE_IDEA,
            sourceContent = idea,
            genre = genre,
            style = style,
            status = VideoProject.STATUS_DRAFT
        )
        projectDao.insert(project)
        project
    }
    
    suspend fun createNovelProject(
        name: String,
        novelText: String,
        sourceBookKey: String = "",
        chapterRange: String = ""
    ): VideoProject = withContext(Dispatchers.IO) {
        val project = VideoProject(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceType = VideoProject.SOURCE_NOVEL,
            sourceContent = novelText,
            sourceBookKey = sourceBookKey,
            sourceChapterRange = chapterRange,
            status = VideoProject.STATUS_DRAFT
        )
        projectDao.insert(project)
        project
    }
    
    suspend fun createScriptProject(
        name: String,
        script: String
    ): VideoProject = withContext(Dispatchers.IO) {
        val project = VideoProject(
            id = UUID.randomUUID().toString(),
            name = name,
            sourceType = VideoProject.SOURCE_SCRIPT,
            sourceContent = script,
            status = VideoProject.STATUS_DRAFT
        )
        projectDao.insert(project)
        project
    }
    
    suspend fun executeNovelWorkflow(projectId: String): Result<VideoProject> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getById(projectId) 
                ?: return@withContext Result.failure(Exception("Project not found"))
            
            _workflowState.value = WorkflowState(projectId, WorkflowStep.ANALYZING, 0, "开始解析小说")
            
            // Step 1: Parse novel
            val parserAgent = NovelParserAgent(apiClient)
            val parseResult = parserAgent.parseNovel(
                AgentContext(projectId, project.sourceContent)
            )
            
            if (!parseResult.success) throw Exception("解析失败: ${parseResult.error}")
            
            saveTrace(projectId, "novel_parser", parseResult)
            
            val analysis = parseResult.structuredData as NovelAnalysisResult
            
            // Step 2: Extract and save characters
            _workflowState.value = WorkflowState(projectId, WorkflowStep.ANALYZING, 25, "提取角色")
            val characters = analysis.characters.map { char ->
                VideoCharacter(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = char.name,
                    description = char.description,
                    appearance = char.appearance,
                    personality = char.personality,
                    order = 0
                )
            }
            characterDao.insertAll(characters)
            
            // Step 3: Design characters
            _workflowState.value = WorkflowState(projectId, WorkflowStep.CHARACTER_DESIGN, 0, "设计角色形象")
            val designAgent = CharacterDesignAgent(apiClient)
            val characterInfos = characters.map { 
                CharacterInfo(it.name, description = it.description, appearance = it.appearance, personality = it.personality)
            }
            val prompts = designAgent.designCharacters(characterInfos, project.style)
            
            characters.forEach { char ->
                characterDao.update(
                    char.copy(identityPrompt = prompts[char.name] ?: "", order = characters.indexOf(char))
                )
            }
            
            // Step 4: Plan storyboard
            _workflowState.value = WorkflowState(projectId, WorkflowStep.STORYBOARD_PLANNING, 0, "规划分镜")
            val plannerAgent = StoryboardPlannerAgent(apiClient)
            val planResult = plannerAgent.planStoryboard(
                AgentContext(projectId, project.sourceContent),
                analysis
            )
            
            if (!planResult.success) throw Exception("分镜规划失败: ${planResult.error}")
            
            saveTrace(projectId, "storyboard_planner", planResult)
            
            val plan = planResult.structuredData as StoryboardPlan
            
            // Step 5: Save scenes
            val charMap = characters.associateBy { it.name }
            val scenes = plan.scenes.mapIndexed { idx, scene ->
                VideoScene(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = scene.title,
                    summary = scene.summary,
                    novelText = scene.novelText,
                    order = idx + 1,
                    shotType = scene.shotType,
                    cameraMovement = scene.cameraMovement,
                    durationSeconds = scene.durationSeconds,
                    location = scene.location,
                    timeOfDay = scene.timeOfDay,
                    mood = scene.mood,
                    visualPrompt = scene.visualPrompt,
                    videoPrompt = scene.videoPrompt,
                    characterIds = scene.characters.mapNotNull { charMap[it]?.id }
                )
            }
            sceneDao.insertAll(scenes)
            projectDao.updateSceneCount(projectId, 0, scenes.size)
            
            _workflowState.value = WorkflowState(projectId, WorkflowStep.STORYBOARD_PLANNING, 100, "分镜规划完成")
            Result.success(projectDao.getById(projectId)!!)
            
        } catch (e: Exception) {
            Log.e("VideoWorkflow", "Workflow failed", e)
            _workflowState.value = WorkflowState(error = e.message ?: "Unknown error", currentStep = WorkflowStep.FAILED)
            Result.failure(e)
        }
    }
    
    private suspend fun saveTrace(projectId: String, agentName: String, result: AgentResult) {
        traceDao.insert(VideoAgentTrace(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            agentName = agentName,
            step = "execute",
            status = if (result.success) VideoAgentTrace.STATUS_COMPLETED else VideoAgentTrace.STATUS_FAILED,
            output = result.output.take(2000),
            durationMs = result.durationMs,
            tokensUsed = result.tokensUsed,
            error = result.error
        ))
    }
}