package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoCharacter
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoCharacterDao {
    @Query("SELECT * FROM video_characters WHERE projectId = :projectId ORDER BY `order` ASC")
    fun observeByProject(projectId: String): Flow<List<VideoCharacter>>

    @Query("SELECT * FROM video_characters WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getByProject(projectId: String): List<VideoCharacter>

    @Query("SELECT * FROM video_characters WHERE id = :id")
    suspend fun getById(id: String): VideoCharacter?

    @Query("SELECT * FROM video_characters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<VideoCharacter>

    @Query("UPDATE video_characters SET generatedImagePath = :path, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGeneratedImage(id: String, path: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(character: VideoCharacter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<VideoCharacter>)

    @Update
    suspend fun update(character: VideoCharacter)

    @Delete
    suspend fun delete(character: VideoCharacter)

    @Query("DELETE FROM video_characters WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT COUNT(*) FROM video_characters WHERE projectId = :projectId")
    suspend fun countByProject(projectId: String): Int

    @Query("SELECT * FROM video_characters WHERE projectId = :projectId AND role = :role ORDER BY `order` ASC")
    suspend fun getByRole(projectId: String, role: String): List<VideoCharacter>
}