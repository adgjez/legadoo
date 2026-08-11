package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoProjectSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProjectSettingsDao {
    @Query("SELECT * FROM video_project_settings WHERE projectId = :projectId")
    suspend fun getByProject(projectId: String): VideoProjectSettings?

    @Query("SELECT * FROM video_project_settings WHERE projectId = :projectId")
    fun observeByProject(projectId: String): Flow<VideoProjectSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: VideoProjectSettings)

    @Update
    suspend fun update(settings: VideoProjectSettings)

    @Query("DELETE FROM video_project_settings WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}