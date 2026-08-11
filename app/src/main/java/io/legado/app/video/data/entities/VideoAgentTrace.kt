package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_agent_traces",
    indices = [
        Index("projectId"),
        Index("agentName"),
        Index("step"),
        Index("status"),
        Index("createdAt")
    ]
)
data class VideoAgentTrace(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val agentName: String,
    val step: String,
    val status: String = STATUS_STARTED,
    val input: String = "",
    val output: String = "",
    val thinking: String = "",
    val error: String = "",
    val durationMs: Long = 0,
    val tokensUsed: Int = 0,
    val costAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_STARTED = "started"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_SKIPPED = "skipped"
    }
}