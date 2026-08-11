package io.legado.app.video.data.dao

import androidx.room.*
import io.legado.app.video.data.entities.VideoAgentTrace
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoAgentTraceDao {
    @Query("SELECT * FROM video_agent_traces WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun observeByProject(projectId: String): Flow<List<VideoAgentTrace>>

    @Query("SELECT * FROM video_agent_traces WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getByProject(projectId: String): List<VideoAgentTrace>

    @Query("SELECT * FROM video_agent_traces WHERE projectId = :projectId AND agentName = :agentName ORDER BY createdAt ASC")
    suspend fun getByAgent(projectId: String, agentName: String): List<VideoAgentTrace>

    @Query("SELECT * FROM video_agent_traces WHERE projectId = :projectId AND step = :step ORDER BY createdAt ASC")
    suspend fun getByStep(projectId: String, step: String): List<VideoAgentTrace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trace: VideoAgentTrace)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traces: List<VideoAgentTrace>)

    @Update
    suspend fun update(trace: VideoAgentTrace)

    @Query("UPDATE video_agent_traces SET status = :status, output = :output, durationMs = :durationMs, tokensUsed = :tokens, costAmount = :cost, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: String, status: String, output: String, durationMs: Long, tokens: Int = 0, cost: Double = 0.0, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_agent_traces SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFailed(id: String, status: String, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM video_agent_traces WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}