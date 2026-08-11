package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_project_settings",
    indices = [Index("projectId")],
    foreignKeys = [
        ForeignKey(
            entity = VideoProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VideoProjectSettings(
    @PrimaryKey
    val projectId: String,
    val provider: String = PROVIDER_AGNES,
    val imageModel: String = "agnes-image-21-flash",
    val videoModel: String = "agnes-video-v20",
    val resolution: String = "720p",
    val aspectRatio: String = "16:9",
    val fps: Int = 24,
    val videoDurationSeconds: Int = 5,
    val imageStylePreset: String = "",
    val enableCharacterConsistency: Boolean = true,
    val enableSceneConsistency: Boolean = true,
    val enableAutoPromptOptimize: Boolean = true,
    val enableEndFrame: Boolean = false,
    val enableTTS: Boolean = false,
    val ttsVoice: String = "",
    val language: String = "zh-CN",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROVIDER_AGNES = "agnes"
        const val PROVIDER_KLING = "kling"
        const val PROVIDER_VEO = "veo"
        const val PROVIDER_GROK = "grok"
        const val PROVIDER_MINIMAX = "minimax"
    }
}