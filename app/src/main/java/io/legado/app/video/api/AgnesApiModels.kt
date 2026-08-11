package io.legado.app.video.api

import com.google.gson.annotations.SerializedName

data class AgnesImageRequest(
    val model: String = AgnesConfig.imageModel,
    val prompt: String,
    @SerializedName("negative_prompt")
    val negativePrompt: String? = null,
    val size: String = "1280x720",
    val n: Int = 1,
    @SerializedName("image")
    val images: List<String>? = null,
    @SerializedName("image_url")
    val imageUrls: List<String>? = null,
    val strength: Double = 0.75,
    val seed: Long? = null,
    @SerializedName("response_format")
    val responseFormat: String = "url"
)

data class AgnesImageResponse(
    val id: String? = null,
    @SerializedName("object")
    val objectType: String? = null,
    val created: Long? = null,
    val data: List<AgnesImageData>? = null,
    val error: AgnesError? = null
)

data class AgnesImageData(
    val url: String? = null,
    @SerializedName("b64_json")
    val b64Json: String? = null,
    val revisedPrompt: String? = null,
    @SerializedName("revised_prompt")
    val revisedPromptAlt: String? = null
)

data class AgnesVideoRequest(
    val model: String = AgnesConfig.videoModel,
    val prompt: String,
    @SerializedName("negative_prompt")
    val negativePrompt: String? = null,
    val duration: Int = 5,
    val ratio: String = "16:9",
    val resolution: String = "720p",
    @SerializedName("image")
    val images: List<String>? = null,
    @SerializedName("image_url")
    val imageUrls: List<String>? = null,
    @SerializedName("start_image")
    val startImage: String? = null,
    @SerializedName("end_image")
    val endImage: String? = null,
    @SerializedName("start_image_url")
    val startImageUrl: String? = null,
    @SerializedName("end_image_url")
    val endImageUrl: String? = null,
    @SerializedName("aspect_ratio")
    val aspectRatio: String = "16:9",
    val fps: Int = 24,
    val seed: Long? = null
)

data class AgnesVideoResponse(
    val id: String? = null,
    @SerializedName("task_id")
    val taskId: String? = null,
    @SerializedName("object")
    val objectType: String? = null,
    val created: Long? = null,
    @SerializedName("video_id")
    val videoId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val data: List<AgnesVideoData>? = null,
    val error: AgnesError? = null,
    val seconds: String? = null,
    val size: String? = null,
    val metadata: Map<String, Any>? = null
)

data class AgnesVideoData(
    val url: String? = null,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    val duration: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerializedName("content_type")
    val contentType: String? = null
)

data class AgnesVideoStatusResponse(
    val id: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val data: List<AgnesVideoData>? = null,
    val error: AgnesError? = null,
    val seconds: String? = null,
    val size: String? = null,
    val metadata: Map<String, Any>? = null
)

data class AgnesError(
    val message: String? = null,
    val code: String? = null,
    val type: String? = null,
    val param: String? = null
) {
    override fun toString(): String = message ?: "Unknown error"
}

data class AgnesTTSRequest(
    val model: String = "agnes-tts-v1",
    val input: String,
    val voice: String = "default",
    val speed: Double = 1.0,
    val format: String = "mp3",
    val sampleRate: Int = 24000
)

data class AgnesTTSResponse(
    val audioUrl: String? = null,
    @SerializedName("audio_url")
    val audioUrlAlt: String? = null,
    val error: AgnesError? = null
)

data class AgnesChatRequest(
    val model: String = "agnes-chat-v1",
    val messages: List<AgnesChatMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val stream: Boolean = false
)

data class AgnesChatMessage(
    val role: String,
    val content: String
)

data class AgnesChatResponse(
    val id: String? = null,
    @SerializedName("object")
    val objectType: String? = null,
    val created: Long? = null,
    val choices: List<AgnesChatChoice>? = null,
    val usage: AgnesUsage? = null,
    val error: AgnesError? = null
)

data class AgnesChatChoice(
    val index: Int = 0,
    val message: AgnesChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class AgnesUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)

data class AgnesCostEstimate(
    val inputCost: Double = 0.0,
    val outputCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val currency: String = "USD"
)