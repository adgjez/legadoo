package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_characters",
    indices = [
        Index("projectId"),
        Index("name"),
        Index("order")
    ],
    foreignKeys = [
        ForeignKey(
            entity = VideoProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VideoCharacter(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val role: String = ROLE_SUPPORTING,
    val description: String = "",
    val appearance: String = "",
    val personality: String = "",
    val clothing: String = "",
    val visualPrompt: String = "",
    val referenceImagePath: String = "",
    val generatedImagePath: String = "",
    val generatedImageUrl: String = "",
    val identityPrompt: String = "",
    val voiceName: String = "",
    val voiceDescription: String = "",
    val characterType: String = TYPE_PROTAGONIST,
    val status: String = STATUS_PENDING,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_PROTAGONIST = "protagonist"
        const val TYPE_SUPPORTING = "supporting"
        const val TYPE_ANTAGONIST = "antagonist"
        const val TYPE_MINOR = "minor"

        const val ROLE_PROTAGONIST = "protagonist"
        const val ROLE_MAJOR = "major"
        const val ROLE_SUPPORTING = "supporting"
        const val ROLE_MINOR = "minor"

        const val STATUS_PENDING = "pending"
        const val STATUS_GENERATING = "generating"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}