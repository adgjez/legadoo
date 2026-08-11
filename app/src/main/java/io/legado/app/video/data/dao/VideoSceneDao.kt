package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoScene
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoSceneDao {
    @Query("SELECT * FROM video_scenes WHERE projectId = :projectId ORDER BY `order` ASC")
    fun observeByProject(projectId: String): Flow<List<VideoScene>>

    @Query("SELECT * FROM video_scenes WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getByProject(projectId: String): List<VideoScene>

    @Query("SELECT * FROM video_scenes WHERE id = :id")
    suspend fun getById(id: String): VideoScene?

    @Query("SELECT * FROM video_scenes WHERE projectId = :projectId AND videoStatus = :status ORDER BY `order` ASC")
    suspend fun getByStatus(projectId: String, status: String): List<VideoScene>

    @Query("UPDATE video_scenes SET visualPrompt = :prompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVisualPrompt(id: String, prompt: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_scenes SET videoPrompt = :prompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVideoPrompt(id: String, prompt: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_scenes SET generatedStoryboardPath = :path, videoStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStoryboard(id: String, path: String, status: String = VideoScene.STATUS_STORYBOARD_READY, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_scenes SET generatedVideoPath = :path, videoStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVideo(id: String, path: String, status: String = VideoScene.STATUS_COMPLETED, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_scenes SET videoStatus = :status, errorMessage = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String = "", updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scene: VideoScene)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scenes: List<VideoScene>)

    @Update
    suspend fun update(scene: VideoScene)

    @Delete
    suspend fun delete(scene: VideoScene)

    @Query("DELETE FROM video_scenes WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT COUNT(*) FROM video_scenes WHERE projectId = :projectId")
    suspend fun countByProject(projectId: String): Int

    @Query("SELECT COUNT(*) FROM video_scenes WHERE projectId = :projectId AND videoStatus = :status")
    suspend fun countByStatus(projectId: String, status: String): Int
}