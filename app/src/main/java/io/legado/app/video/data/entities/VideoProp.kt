package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_props",
    indices = [
        Index("projectId"),
        Index("name")
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
data class VideoProp(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val description: String = "",
    val referenceImagePath: String = "",
    val generatedImagePath: String = "",
    val generatedImageUrl: String = "",
    val propType: String = TYPE_GENERAL,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_GENERAL = "general"
        const val TYPE_WEAPON = "weapon"
        const val TYPE_VEHICLE = "vehicle"
        const val TYPE_BUILDING = "building"
        const val TYPE_ITEM = "item"
        const val TYPE_MAGIC = "magic"
    }
}