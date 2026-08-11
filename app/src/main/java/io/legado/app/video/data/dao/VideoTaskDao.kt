package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoTask
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoTaskDao {
    @Query("SELECT * FROM video_tasks WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<VideoTask>>

    @Query("SELECT * FROM video_tasks WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getByProject(projectId: String): List<VideoTask>

    @Query("SELECT * FROM video_tasks WHERE id = :id")
    suspend fun getById(id: String): VideoTask?

    @Query("SELECT * FROM video_tasks WHERE providerTaskId = :providerTaskId")
    suspend fun getByProviderTaskId(providerTaskId: String): VideoTask?

    @Query("SELECT * FROM video_tasks WHERE status IN (:statuses) AND nextRetryAt > 0 AND nextRetryAt <= :now")
    suspend fun getPendingRetry(statuses: List<String>, now: Long): List<VideoTask>

    @Query("SELECT * FROM video_tasks WHERE projectId = :projectId AND status NOT IN ('completed', 'failed') ORDER BY createdAt DESC")
    suspend fun getPending(projectId: String): List<VideoTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: VideoTask)

    @Update
    suspend fun update(task: VideoTask)

    @Query("UPDATE video_tasks SET status = :status, progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, progress: Int = 0, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_tasks SET status = :status, resultUrl = :url, resultPath = :path, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: String, status: String, url: String, path: String, completedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_tasks SET status = :status, errorMessage = :error, retryCount = retryCount + 1, nextRetryAt = :nextRetryAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRetry(id: String, status: String, error: String, nextRetryAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(task: VideoTask)

    @Query("DELETE FROM video_tasks WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}