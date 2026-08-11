package io.legado.app.data.entities

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
    val description: String = "",
    val appearance: String = "",
    val personality: String = "",
    val referenceImagePath: String = "",
    val referenceImageUrl: String = "",
    val generatedImagePath: String = "",
    val generatedImageUrl: String = "",
    val identityPrompt: String = "",
    val voice: String = "",
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
