package io.legado.app.video.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.legado.app.data.appDb
import io.legado.app.video.api.*
import io.legado.app.video.agent.*
import io.legado.app.video.data.dao.*
import io.legado.app.video.data.entities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class VideoGenerationService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "video_generation"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "io.legado.app.video.START"
        const val ACTION_CANCEL = "io.legado.app.video.CANCEL"
        
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_CONTENT = "content"
        
        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState
        
        fun startNovelToVideo(context: Context, projectId: String, novelText: String) {
            val intent = Intent(context, VideoGenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_SOURCE, VideoProject.SOURCE_NOVEL)
                putExtra(EXTRA_CONTENT, novelText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun cancelGeneration(context: Context, projectId: String) {
            val intent = Intent(context, VideoGenerationService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startService(intent)
        }
    }
    
    data class ServiceState(
        val isRunning: Boolean = false,
        val projectId: String = "",
        val currentStep: String = "",
        val progress: Int = 0,
        val error: String = ""
    )
    
    private val binder = VideoBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var apiClient: AgnesApiClient
    private lateinit var projectDao: VideoProjectDao
    private lateinit var characterDao: VideoCharacterDao
    private lateinit var sceneDao: VideoSceneDao
    private lateinit var taskDao: VideoTaskDao
    private lateinit var traceDao: VideoAgentTraceDao
    private var cancelled = false
    
    inner class VideoBinder : Binder() {
        fun getService(): VideoGenerationService = this@VideoGenerationService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("视频生成服务启动"))
        
        val db = getDatabase()
        projectDao = db.videoProjectDao()
        characterDao = db.videoCharacterDao()
        sceneDao = db.videoSceneDao()
        taskDao = db.videoTaskDao()
        traceDao = db.videoAgentTraceDao()
        apiClient = AgnesApiClient()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
                cancelled = false
                
                serviceScope.launch {
                    when (source) {
                        VideoProject.SOURCE_NOVEL -> processNovelToVideo(projectId, content)
                        VideoProject.SOURCE_SCRIPT -> processScriptToVideo(projectId, content)
                        VideoProject.SOURCE_IDEA -> processIdeaToVideo(projectId, content)
                    }
                }
            }
            ACTION_CANCEL -> {
                cancelled = true
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: ""
                serviceScope.launch {
                    projectDao.updateStatus(projectId, VideoProject.STATUS_FAILED, 0)
                    _serviceState.value = ServiceState(error = "已取消")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private suspend fun processNovelToVideo(projectId: String, novelText: String) {
        _serviceState.value = ServiceState(isRunning = true, projectId = projectId, currentStep = "开始解析小说", progress = 0)
        updateProjectStatus(projectId, VideoProject.STATUS_ANALYZING, 5)
        
        try {
            // Step 1: Parse novel
            updateNotification("解析小说内容...")
            val parserAgent = NovelParserAgent(apiClient)
            val parseResult = parserAgent.parseNovel(
                AgentContext(projectId = projectId, input = novelText)
            )
            
            if (!parseResult.success) {
                throw Exception("小说解析失败: ${parseResult.error}")
            }
            
            val analysis = parseResult.structuredData as? NovelAnalysisResult
                ?: throw Exception("小说解析结果为空")
            
            saveAgentTrace(projectId, "novel_parser", "parse", parseResult)
            
            // Step 2: Save characters
            updateNotification("保存角色设定...")
            val characters = analysis.characters.map { char ->
                VideoCharacter(
                    id = generateId(),
                    projectId = projectId,
                    name = char.name,
                    description = char.description,
                    appearance = char.appearance,
                    personality = char.personality,
                    order = 0
                )
            }
            characterDao.insertAll(characters)
            updateProjectStatus(projectId, VideoProject.STATUS_ANALYZING, 20)
            
            // Step 3: Design characters
            updateNotification("设计角色形象...")
            val designAgent = CharacterDesignAgent(apiClient)
            val characterPrompts = designAgent.designCharacters(characters.map { 
                CharacterInfo(it.name, description = it.description, appearance = it.appearance, personality = it.personality) 
            })
            
            characters.forEach { char ->
                val prompt = characterPrompts[char.name] ?: ""
                characterDao.update(
                    char.copy(identityPrompt = prompt, order = characters.indexOf(char))
                )
            }
            updateProjectStatus(projectId, VideoProject.STATUS_ANALYZING, 30)
            
            // Step 4: Plan storyboard
            updateNotification("规划分镜...")
            _serviceState.value = ServiceState(isRunning = true, projectId = projectId, currentStep = "规划分镜", progress = 30)
            updateProjectStatus(projectId, VideoProject.STATUS_PLANNING, 35)
            
            val plannerAgent = StoryboardPlannerAgent(apiClient)
            val planResult = plannerAgent.planStoryboard(
                AgentContext(projectId = projectId, input = novelText),
                analysis,
                targetDuration = 60,
                targetScenes = 12
            )
            
            if (!planResult.success) {
                throw Exception("分镜规划失败: ${planResult.error}")
            }
            
            val plan = planResult.structuredData as? StoryboardPlan
                ?: throw Exception("分镜规划结果为空")
            
            saveAgentTrace(projectId, "storyboard_planner", "plan", planResult)
            
            // Step 5: Save scenes
            updateNotification("保存分镜...")
            val characterMap = characters.associateBy { it.name }
            val scenes = plan.scenes.map { scene ->
                VideoScene(
                    id = generateId(),
                    projectId = projectId,
                    title = scene.title,
                    summary = scene.summary,
                    novelText = scene.novelText,
                    order = scene.order,
                    sceneType = if (scene.isKeyframe) VideoScene.TYPE_KEYFRAME else VideoScene.TYPE_NORMAL,
                    shotType = scene.shotType,
                    cameraMovement = scene.cameraMovement,
                    location = scene.location,
                    timeOfDay = scene.timeOfDay,
                    mood = scene.mood,
                    visualPrompt = scene.visualPrompt,
                    videoPrompt = scene.videoPrompt,
                    durationSeconds = scene.durationSeconds,
                    characterIds = scene.characters.mapNotNull { characterMap[it]?.id }
                )
            }
            sceneDao.insertAll(scenes)
            projectDao.updateSceneCount(projectId, 0, scenes.size)
            updateProjectStatus(projectId, VideoProject.STATUS_STORYBOARD, 50)
            
            // Step 6: Generate storyboard images
            updateNotification("生成分镜图...")
            updateProjectStatus(projectId, VideoProject.STATUS_GENERATING, 55)
            
            val promptAgent = PromptOptimizerAgent(apiClient)
            awaitAll(scenes.map { scene ->
                async(serviceScope.coroutineContext) {
                    generateSceneStoryboard(projectId, scene, characters, promptAgent)
                }
            })
            
            val completedStoryboards = scenes.count { 
                sceneDao.getById(it.id)?.generatedStoryboardPath?.isNotEmpty() == true 
            }
            projectDao.updateSceneCount(projectId, completedStoryboards, scenes.size)
            updateProjectStatus(projectId, VideoProject.STATUS_GENERATING, 70)
            
            // Step 7: Generate videos
            updateNotification("生成视频...")
            _serviceState.value = ServiceState(isRunning = true, projectId = projectId, currentStep = "生成视频", progress = 70)
            
            awaitAll(scenes.map { scene ->
                async(serviceScope.coroutineContext) {
                    generateSceneVideo(projectId, scene)
                }
            })
            
            val completedVideos = scenes.count {
                sceneDao.getById(it.id)?.generatedVideoPath?.isNotEmpty() == true
            }
            projectDao.updateSceneCount(projectId, completedVideos, scenes.size)
            updateProjectStatus(projectId, VideoProject.STATUS_COMPLETED, 100)
            
            // Step 8: Export final video
            updateNotification("合成成片...")
            exportFinalVideo(projectId, scenes)
            
            _serviceState.value = ServiceState(isRunning = false, projectId = projectId, progress = 100)
            stopSelf()
            
        } catch (e: CancellationException) {
            Log.d("VideoGenService", "Generation cancelled")
        } catch (e: Exception) {
            Log.e("VideoGenService", "Generation failed", e)
            projectDao.updateStatus(projectId, VideoProject.STATUS_FAILED, 0)
            _serviceState.value = ServiceState(isRunning = false, projectId = projectId, error = e.message ?: "Unknown error")
            stopSelf()
        }
    }
    
    private suspend fun processScriptToVideo(projectId: String, scriptContent: String) {
        _serviceState.value = ServiceState(isRunning = true, projectId = projectId, currentStep = "解析剧本", progress = 0)
        updateProjectStatus(projectId, VideoProject.STATUS_ANALYZING, 5)
        // Similar to novel pipeline but with script-specific parsing
        processNovelToVideo(projectId, scriptContent)
    }
    
    private suspend fun processIdeaToVideo(projectId: String, idea: String) {
        _serviceState.value = ServiceState(isRunning = true, projectId = projectId, currentStep = "创意扩展", progress = 0)
        updateProjectStatus(projectId, VideoProject.STATUS_ANALYZING, 5)
        
        try {
            // Expand idea into a full script using AI
            val request = AgnesChatRequest(
                messages = listOf(
                    AgnesChatMessage("system", "你是一个创意编剧。请将用户的创意扩展成一个完整的短视频剧本，包含角色、场景、对白和动作描述。用markdown格式输出。"),
                    AgnesChatMessage("user", idea)
                ),
                temperature = 0.8,
                maxTokens = 4096
            )
            val response = apiClient.chatCompletion(request)
            val expandedScript = response.getOrNull()?.choices?.firstOrNull()?.message?.content ?: idea
            
            // Process as script
            processNovelToVideo(projectId, expandedScript)
        } catch (e: Exception) {
            throw e
        }
    }
    
    private suspend fun generateSceneStoryboard(
        projectId: String,
        scene: VideoScene,
        characters: List<VideoCharacter>,
        promptAgent: PromptOptimizerAgent
    ) {
        if (cancelled) return
        
        updateNotification("生成第${scene.order}个分镜图...")
        
        try {
            val characterPrompts = characters.map { it.identityPrompt }.filter { it.isNotBlank() }
            val optimizedPrompt = promptAgent.optimizeVisualPrompt(
                originalPrompt = scene.visualPrompt,
                characterDescriptions = characterPrompts,
                sceneDescription = "${scene.location} ${scene.timeOfDay} ${scene.mood}",
                shotType = scene.shotType
            ).output
            
            val request = AgnesImageRequest(
                model = AgnesConfig.imageModel,
                prompt = optimizedPrompt,
                negativePrompt = "blurry, distorted, low quality, deformed",
                size = "1280x720",
                n = 1
            )
            
            val result = apiClient.generateImage(request)
            
            result.onSuccess { response ->
                val imageUrl = response.data?.firstOrNull()?.url
                if (imageUrl != null) {
                    val localFile = downloadImage(imageUrl, projectId, scene.id)
                    sceneDao.updateStoryboard(scene.id, localFile.absolutePath)
                }
            }.onFailure { error ->
                Log.e("VideoGenService", "Storyboard generation failed for scene ${scene.order}", error)
                sceneDao.updateStatus(scene.id, VideoScene.STATUS_FAILED, error.message ?: "")
            }
        } catch (e: Exception) {
            Log.e("VideoGenService", "Storyboard generation failed for scene ${scene.order}", e)
            sceneDao.updateStatus(scene.id, VideoScene.STATUS_FAILED, e.message ?: "")
        }
    }
    
    private suspend fun generateSceneVideo(projectId: String, scene: VideoScene) {
        if (cancelled) return
        
        val currentScene = sceneDao.getById(scene.id) ?: return
        val storyboardPath = currentScene.generatedStoryboardPath
        if (storyboardPath.isBlank()) return
        
        updateNotification("生成第${scene.order}个视频...")
        sceneDao.updateStatus(scene.id, VideoScene.STATUS_GENERATING_VIDEO)
        
        try {
            val request = AgnesVideoRequest(
                model = AgnesConfig.videoModel,
                prompt = currentScene.videoPrompt.ifBlank { currentScene.visualPrompt },
                duration = currentScene.durationSeconds,
                aspectRatio = "16:9",
                resolution = "720p"
            )
            
            // Add start image if available
            val storyboardFile = File(storyboardPath)
            if (storyboardFile.exists()) {
                val imageUrl = uploadImageOrGetUrl(storyboardFile)
                // Note: Agnes API may support base64 or URL reference
                // For now, we pass the storyboard as context
            }
            
            val result = apiClient.generateVideo(request)
            
            result.onSuccess { response ->
                val videoId = response.videoId ?: response.id
                if (!videoId.isNullOrBlank()) {
                    taskDao.insert(VideoTask(
                        id = generateId(),
                        projectId = projectId,
                        sceneId = scene.id,
                        taskType = VideoTask.TYPE_VIDEO,
                        providerTaskId = videoId,
                        status = VideoTask.STATUS_PROCESSING
                    ))
                    
                    val pollResult = apiClient.pollVideoCompletion(videoId) { progress, status ->
                        Log.d("VideoGenService", "Video ${scene.order}: $progress% - $status")
                    }
                    
                    pollResult.onSuccess { videoData ->
                        val localFile = downloadVideo(videoData.url ?: "", projectId, scene.id)
                        sceneDao.updateVideo(scene.id, localFile.absolutePath)
                        taskDao.updateStatus(
                            taskDao.getByProviderTaskId(videoId)?.id ?: "",
                            VideoTask.STATUS_COMPLETED
                        )
                    }.onFailure { error ->
                        sceneDao.updateStatus(scene.id, VideoScene.STATUS_FAILED, error.message ?: "")
                    }
                }
            }.onFailure { error ->
                sceneDao.updateStatus(scene.id, VideoScene.STATUS_FAILED, error.message ?: "")
            }
        } catch (e: Exception) {
            sceneDao.updateStatus(scene.id, VideoScene.STATUS_FAILED, e.message ?: "")
        }
    }
    
    private suspend fun exportFinalVideo(projectId: String, scenes: List<VideoScene>) {
        val completedVideos = scenes.mapNotNull { scene ->
            val path = sceneDao.getById(it.id)?.generatedVideoPath
            if (!path.isNullOrBlank() && File(path).exists()) path else null
        }
        
        if (completedVideos.isEmpty()) {
            Log.w("VideoGenService", "No videos to export for project $projectId")
            return
        }
        
        // Use FFmpeg to concatenate videos
        val project = projectDao.getById(projectId) ?: return
        val outputDir = File(getExternalFilesDir(null), "video/$projectId")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "${project.name}_final.mp4")
        
        val concatCommand = buildString {
            append("ffmpeg -y ")
            completedVideos.forEach { path ->
                append("-i \"$path\" ")
            }
            append("-filter_complex \"")
            completedVideos.indices.forEach { i ->
                append("[$i:v][$i:a]")
                if (i < completedVideos.size - 1) append("concat=n=${completedVideos.size}:v=1:a=1[v][a]")
            }
            append("\" -map \"[v]\" -map \"[a]\" -c:v libx264 -crf 23 -c:a aac \"${outputFile.absolutePath}\"")
        }
        
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", concatCommand))
            process.waitFor()
            
            if (process.exitValue() == 0) {
                projectDao.update(
                    project.copy(exportedPath = outputFile.absolutePath)
                )
            }
        } catch (e: Exception) {
            Log.e("VideoGenService", "Export failed", e)
        }
    }
    
    private suspend fun downloadImage(url: String, projectId: String, sceneId: String): File {
        val dir = File(getExternalFilesDir(null), "video/$projectId/images")
        dir.mkdirs()
        val file = File(dir, "$sceneId.png")
        apiClient.downloadFile(url, file)
        return file
    }
    
    private suspend fun downloadVideo(url: String, projectId: String, sceneId: String): File {
        val dir = File(getExternalFilesDir(null), "video/$projectId/videos")
        dir.mkdirs()
        val file = File(dir, "$sceneId.mp4")
        apiClient.downloadFile(url, file)
        return file
    }
    
    private suspend fun uploadImageOrGetUrl(file: File): String {
        // For now, return the file path. In production, upload to a hosting service.
        return file.toURI().toString()
    }
    
    private suspend fun updateProjectStatus(projectId: String, status: String, progress: Int) {
        projectDao.updateStatus(projectId, status, progress)
    }
    
    private suspend fun saveAgentTrace(
        projectId: String,
        agentName: String,
        step: String,
        result: AgentResult
    ) {
        traceDao.insert(VideoAgentTrace(
            id = generateId(),
            projectId = projectId,
            agentName = agentName,
            step = step,
            status = if (result.success) VideoAgentTrace.STATUS_COMPLETED else VideoAgentTrace.STATUS_FAILED,
            output = result.output.take(2000),
            durationMs = result.durationMs,
            tokensUsed = result.tokensUsed,
            error = result.error
        ))
    }
    
    private fun getDatabase() = appDb
    
    private fun generateId(): String = java.util.UUID.randomUUID().toString()
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频生成",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI视频生成进度通知"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI视频生成")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}