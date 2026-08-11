package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_tasks",
    indices = [
        Index("projectId"),
        Index("sceneId"),
        Index("taskType"),
        Index("status"),
        Index("createdAt")
    ]
)
data class VideoTask(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val sceneId: String = "",
    val taskType: String = TYPE_VIDEO,
    val providerTaskId: String = "",
    val status: String = STATUS_PENDING,
    val progress: Int = 0,
    val prompt: String = "",
    val params: String = "",
    val resultUrl: String = "",
    val resultPath: String = "",
    val errorMessage: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val nextRetryAt: Long = 0,
    val completedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_CHARACTER = "character"
        const val TYPE_SCENE = "scene"
        const val TYPE_PROP = "prop"
        const val TYPE_TTS = "tts"

        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}