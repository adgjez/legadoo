package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_projects",
    indices = [
        Index("status"),
        Index("sourceType"),
        Index("createdAt"),
        Index("updatedAt")
    ]
)
data class VideoProject(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val sourceType: String = SOURCE_IDEA,
    val sourceContent: String = "",
    val sourceBookKey: String = "",
    val sourceChapterRange: String = "",
    val genre: String = "",
    val style: String = "",
    val styleDescription: String = "",
    val targetAspectRatio: String = "16:9",
    val targetResolution: String = "720p",
    val targetDurationSeconds: Int = 30,
    val targetSegments: Int = 0,
    val status: String = STATUS_DRAFT,
    val progress: Int = 0,
    val coverPath: String = "",
    val totalScenes: Int = 0,
    val completedScenes: Int = 0,
    val totalCharacters: Int = 0,
    val exportedPath: String = "",
    val agentState: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_IDEA = "idea"
        const val SOURCE_NOVEL = "novel"
        const val SOURCE_SCRIPT = "script"

        const val STATUS_DRAFT = "draft"
        const val STATUS_ANALYZING = "analyzing"
        const val STATUS_PLANNING = "planning"
        const val STATUS_STORYBOARD = "storyboard"
        const val STATUS_GENERATING = "generating"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ARCHIVED = "archived"
        const val STATUS_FAILED = "failed"
    }
}