package io.legado.app.video.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_scenes",
    indices = [
        Index("projectId"),
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
    val novelText: String = "",
    val dialogue: String = "",
    val order: Int = 0,
    val sceneType: String = TYPE_NORMAL,
    val shotType: String = SHOT_MEDIUM,
    val cameraMovement: String = CAMERA_STATIC,
    val location: String = "",
    val timeOfDay: String = "",
    val weather: String = "",
    val mood: String = "",
    val style: String = "",
    val visualPrompt: String = "",
    val negativePrompt: String = "",
    val videoPrompt: String = "",
    val durationSeconds: Int = 5,
    val characterIds: List<String> = emptyList(),
    val referenceImagePaths: List<String> = emptyList(),
    val generatedStoryboardPath: String = "",
    val generatedVideoPath: String = "",
    val videoTaskId: String = "",
    val videoStatus: String = STATUS_PENDING,
    val seed: Long = 0L,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_NORMAL = "normal"
        const val TYPE_KEYFRAME = "keyframe"
        const val TYPE_TRANSITION = "transition"
        const val TYPE_CLIMAX = "climax"
        const val TYPE_ENDING = "ending"

        const val SHOT_EXTREME_LONG = "extreme_long"
        const val SHOT_LONG = "long"
        const val SHOT_MEDIUM = "medium"
        const val SHOT_CLOSE_UP = "close_up"
        const val SHOT_EXTREME_CLOSE_UP = "extreme_close_up"
        const val SHOT_BIRD_EYE = "bird_eye"
        const val SHOT_WORM_EYE = "worm_eye"
        const val SHOT_OVER_SHOULDER = "over_shoulder"
        const val SHOT_POINT_OF_VIEW = "point_of_view"

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
        const val CAMERA_HANDHELD = "handheld"
        const val CAMERA_Crane = "crane"
        const val CAMERA_Steadicam = "steadicam"

        const val STATUS_PENDING = "pending"
        const val STATUS_GENERATING_STORYBOARD = "generating_storyboard"
        const val STATUS_STORYBOARD_READY = "storyboard_ready"
        const val STATUS_GENERATING_VIDEO = "generating_video"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_SKIPPED = "skipped"
    }
}