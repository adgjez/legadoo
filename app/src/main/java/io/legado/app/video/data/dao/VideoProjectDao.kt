package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoProject
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VideoProject>>

    @Query("SELECT * FROM video_projects WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: String): Flow<List<VideoProject>>

    @Query("SELECT * FROM video_projects WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getByStatus(status: String): List<VideoProject>

    @Query("SELECT * FROM video_projects WHERE id = :id")
    suspend fun getById(id: String): VideoProject?

    @Query("SELECT * FROM video_projects WHERE id = :id")
    fun observeById(id: String): Flow<VideoProject?>

    @Query("SELECT * FROM video_projects ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<VideoProject>

    @Query("SELECT * FROM video_projects")
    suspend fun getAll(): List<VideoProject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: VideoProject)

    @Update
    suspend fun update(project: VideoProject)

    @Query("UPDATE video_projects SET status = :status, progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_projects SET status = :status, progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusAndProgress(id: String, status: String, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_projects SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_projects SET completedScenes = :completedScenes, totalScenes = :totalScenes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSceneCount(id: String, completedScenes: Int, totalScenes: Int, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(project: VideoProject)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM video_projects WHERE status != 'archived'")
    suspend fun countActive(): Int
}