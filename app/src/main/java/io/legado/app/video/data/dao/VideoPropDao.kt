package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoProp
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoPropDao {
    @Query("SELECT * FROM video_props WHERE projectId = :projectId ORDER BY `order` ASC")
    fun observeByProject(projectId: String): Flow<List<VideoProp>>

    @Query("SELECT * FROM video_props WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getByProject(projectId: String): List<VideoProp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prop: VideoProp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(props: List<VideoProp>)

    @Update
    suspend fun update(prop: VideoProp)

    @Query("UPDATE video_props SET generatedImagePath = :path, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGeneratedImage(id: String, path: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(prop: VideoProp)

    @Query("DELETE FROM video_props WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}