package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_scenes",
    indices = [
        Index("projectId"),
        Index("characterId"),
        Index("sceneType"),
        Index("status"),
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
data class VideoScene(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val title: String = "",
    val summary: String = "",
    val order: Int = 0,
    val sceneType: String = TYPE_NORMAL,
    val shotType: String = SHOT_MEDIUM,
    val cameraMovement: String = CAMERA_STATIC,
    val dialogue: String = "",
    val narration: String = "",
    val visualPrompt: String = "",
    val negativePrompt: String = "",
    val durationSeconds: Int = 5,
    val startFramePath: String = "",
    val endFramePath: String = "",
    val referenceImagePaths: List<String> = emptyList(),
    val generatedImagePath: String = "",
    val generatedVideoPath: String = "",
    val videoTaskId: String = "",
    val videoStatus: String = VIDEO_PENDING,
    val seed: Long = 0L,
    val characterIds: List<String> = emptyList(),
    val location: String = "",
    val timeOfDay: String = "",
    val mood: String = "",
    val style: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_NORMAL = "normal"
        const val TYPE_KEYFRAME = "keyframe"
        const val TYPE_TRANSITION = "transition"

        const val SHOT_EXTREME_LONG = "extreme_long"
        const val SHOT_LONG = "long"
        const val SHOT_MEDIUM = "medium"
        const val SHOT_CLOSE_UP = "close_up"
        const val SHOT_EXTREME_CLOSE_UP = "extreme_close_up"
        const val SHOT_BIRD_EYE = "bird_eye"
        const val SHOT_WORM_EYE = "worm_eye"
        const val SHOT_OVER_SHOULDER = "over_shoulder"

        const val CAMERA_STATIC = "static"
        const val CAMERA_PAN = "pan"
        const val CAMERA_TILT = "tilt"
        const val CAMERA_DOLLY = "dolly"
        const val CAMERA_TRUCK = "truck"
        const val CAMERA_PEDESTAL = "pedestal"
        const val CAMERA_ROTATE = "rotate"
        const val CAMERA_ZOOM = "zoom"
        const val CAMERA_TRACKING = "tracking"
        const val CAMERA_AERIAL = "aerial"

        const val VIDEO_PENDING = "pending"
        const val VIDEO_GENERATING = "generating"
        const val VIDEO_COMPLETED = "completed"
        const val VIDEO_FAILED = "failed"
    }
}
