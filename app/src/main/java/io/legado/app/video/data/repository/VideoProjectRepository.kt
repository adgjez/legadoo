package io.legado.app.video.data.repository

import io.legado.app.video.data.dao.appDb
import io.legado.app.video.data.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * VideoProjectRepository - 数据仓库层
 *
 * 统一的数据访问层，隔离 Room 数据库操作
 */

class VideoProjectRepository {

    private val projectDao get() = appDb.videoProjectDao()
    private val sceneDao get() = appDb.videoSceneDao()
    private val characterDao get() = appDb.videoCharacterDao()
    private val taskDao get() = appDb.videoTaskDao()
    private val settingsDao get() = appDb.videoProjectSettingsDao()

    // ========== Project Operations ==========

    fun observeProjects(): Flow<List<VideoProject>> = projectDao.observeAll()

    suspend fun getProject(projectId: String): VideoProject? = withContext(Dispatchers.IO) {
        projectDao.getById(projectId)
    }

    fun observeProject(projectId: String): Flow<VideoProject?> = projectDao.observeById(projectId)

    suspend fun insertProject(project: VideoProject): String = withContext(Dispatchers.IO) {
        projectDao.insert(project)
        project.id
    }

    suspend fun updateProject(project: VideoProject) = withContext(Dispatchers.IO) {
        projectDao.update(project)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.deleteById(projectId)
    }

    suspend fun updateProjectStatus(projectId: String, status: String, progress: Int) = withContext(Dispatchers.IO) {
        projectDao.updateStatusAndProgress(projectId, status, progress)
    }

    suspend fun getProjectsByStatus(status: String): List<VideoProject> = withContext(Dispatchers.IO) {
        projectDao.getByStatus(status)
    }

    suspend fun getRecentProjects(limit: Int = 20): List<VideoProject> = withContext(Dispatchers.IO) {
        projectDao.getRecent(limit)
    }

    // ========== Scene Operations ==========

    fun observeScenes(projectId: String): Flow<List<VideoScene>> = sceneDao.observeByProject(projectId)

    suspend fun getScenes(projectId: String): List<VideoScene> = withContext(Dispatchers.IO) {
        sceneDao.getByProject(projectId).sortedBy { it.order }
    }

    suspend fun getScene(sceneId: String): VideoScene? = withContext(Dispatchers.IO) {
        sceneDao.getById(sceneId)
    }

    suspend fun insertScene(scene: VideoScene) = withContext(Dispatchers.IO) {
        sceneDao.insert(scene)
    }

    suspend fun insertScenes(scenes: List<VideoScene>) = withContext(Dispatchers.IO) {
        sceneDao.insertAll(scenes)
    }

    suspend fun updateScene(scene: VideoScene) = withContext(Dispatchers.IO) {
        sceneDao.update(scene)
    }

    suspend fun deleteScene(scene: VideoScene) = withContext(Dispatchers.IO) {
        sceneDao.delete(scene)
    }

    suspend fun updateSceneStatus(sceneId: String, status: String) = withContext(Dispatchers.IO) {
        sceneDao.updateStatus(sceneId, status)
    }

    suspend fun getSceneCount(projectId: String): Int = withContext(Dispatchers.IO) {
        sceneDao.countByProject(projectId)
    }

    suspend fun getScenesByStatus(projectId: String, status: String): List<VideoScene> = withContext(Dispatchers.IO) {
        sceneDao.getByStatus(projectId, status)
    }

    // ========== Character Operations ==========

    fun observeCharacters(projectId: String): Flow<List<VideoCharacter>> = characterDao.observeByProject(projectId)

    suspend fun getCharacters(projectId: String): List<VideoCharacter> = withContext(Dispatchers.IO) {
        characterDao.getByProject(projectId).sortedBy { it.order }
    }

    suspend fun getCharacter(characterId: String): VideoCharacter? = withContext(Dispatchers.IO) {
        characterDao.getById(characterId)
    }

    suspend fun insertCharacter(character: VideoCharacter) = withContext(Dispatchers.IO) {
        characterDao.insert(character)
    }

    suspend fun insertCharacters(characters: List<VideoCharacter>) = withContext(Dispatchers.IO) {
        characterDao.insertAll(characters)
    }

    suspend fun updateCharacter(character: VideoCharacter) = withContext(Dispatchers.IO) {
        characterDao.update(character)
    }

    suspend fun deleteCharacter(character: VideoCharacter) = withContext(Dispatchers.IO) {
        characterDao.delete(character)
    }

    suspend fun getCharacterCount(projectId: String): Int = withContext(Dispatchers.IO) {
        characterDao.countByProject(projectId)
    }

    suspend fun getMainCharacters(projectId: String): List<VideoCharacter> = withContext(Dispatchers.IO) {
        characterDao.getByRole(projectId, VideoCharacter.ROLE_PROTAGONIST)
    }

    // ========== Task Operations ==========

    suspend fun insertTask(task: VideoTask) = withContext(Dispatchers.IO) {
        taskDao.insert(task)
    }

    suspend fun updateTask(task: VideoTask) = withContext(Dispatchers.IO) {
        taskDao.update(task)
    }

    suspend fun getTasksByProject(projectId: String): List<VideoTask> = withContext(Dispatchers.IO) {
        taskDao.getByProject(projectId)
    }

    suspend fun getPendingTasks(projectId: String): List<VideoTask> = withContext(Dispatchers.IO) {
        taskDao.getPending(projectId)
    }

    // ========== Settings Operations ==========

    suspend fun getSettings(projectId: String): VideoProjectSettings? = withContext(Dispatchers.IO) {
        settingsDao.getByProject(projectId)
    }

    suspend fun insertOrUpdateSettings(settings: VideoProjectSettings) = withContext(Dispatchers.IO) {
        val existing = settingsDao.getByProject(settings.projectId)
        if (existing != null) {
            settingsDao.update(settings)
        } else {
            settingsDao.insert(settings)
        }
    }

    // ========== Aggregate Queries ==========

    suspend fun getProjectSummary(projectId: String): ProjectSummary = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId)
        val scenes = sceneDao.getByProject(projectId)
        val characters = characterDao.getByProject(projectId)

        ProjectSummary(
            project = project,
            sceneCount = scenes.size,
            completedSceneCount = scenes.count { it.videoStatus == VideoScene.STATUS_COMPLETED },
            characterCount = characters.size,
            mainCharacterCount = characters.count { it.role == VideoCharacter.ROLE_PROTAGONIST }
        )
    }

    suspend fun getProjectsNeedingAttention(): List<VideoProject> = withContext(Dispatchers.IO) {
        projectDao.getByStatus(VideoProject.STATUS_FAILED) +
                projectDao.getByStatus(VideoProject.STATUS_STORYBOARD)
    }

    suspend fun getTotalStats(): GlobalStats = withContext(Dispatchers.IO) {
        val allProjects = projectDao.getAll()
        GlobalStats(
            totalProjects = allProjects.size,
            completedProjects = allProjects.count { it.status == VideoProject.STATUS_COMPLETED },
            failedProjects = allProjects.count { it.status == VideoProject.STATUS_FAILED },
            totalScenes = allProjects.sumOf { it.totalScenes },
            totalCharacters = allProjects.sumOf { characterDao.countByProject(it.id) }
        )
    }
}

data class ProjectSummary(
    val project: VideoProject?,
    val sceneCount: Int,
    val completedSceneCount: Int,
    val characterCount: Int,
    val mainCharacterCount: Int
)

data class GlobalStats(
    val totalProjects: Int,
    val completedProjects: Int,
    val failedProjects: Int,
    val totalScenes: Int,
    val totalCharacters: Int
)
