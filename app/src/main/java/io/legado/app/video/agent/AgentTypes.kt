package io.legado.app.video.agent

data class AgentContext(
    val projectId: String,
    val input: String,
    val metadata: Map<String, String> = emptyMap()
)

data class AgentResult(
    val success: Boolean,
    val output: String,
    val structuredData: Any? = null,
    val thinking: String = "",
    val tokensUsed: Int = 0,
    val durationMs: Long = 0,
    val error: String = ""
)

data class NovelAnalysisResult(
    val characters: List<CharacterInfo> = emptyList(),
    val scenes: List<SceneInfo> = emptyList(),
    val plotSegments: List<PlotSegment> = emptyList(),
    val genre: String = "",
    val style: String = "",
    val theme: String = "",
    val summary: String = "",
    val keyDialogues: List<String> = emptyList()
)

data class CharacterInfo(
    val name: String,
    val role: String = "",
    val description: String = "",
    val appearance: String = "",
    val personality: String = "",
    val keyTraits: List<String> = emptyList(),
    val relationships: Map<String, String> = emptyMap()
)

data class SceneInfo(
    val name: String,
    val location: String = "",
    val timeOfDay: String = "",
    val atmosphere: String = "",
    val keyCharacters: List<String> = emptyList(),
    val description: String = ""
)

data class PlotSegment(
    val title: String,
    val summary: String,
    val purpose: String = "",
    val characters: List<String> = emptyList(),
    val wordCount: Int = 0,
    val importance: Int = 3
)

data class StoryboardPlan(
    val scenes: List<StoryboardScene> = emptyList(),
    val totalDurationSeconds: Int = 0,
    val estimatedCost: Double = 0.0
)

data class StoryboardScene(
    val order: Int,
    val title: String,
    val summary: String,
    val novelText: String = "",
    val shotType: String = "medium",
    val cameraMovement: String = "static",
    val durationSeconds: Int = 5,
    val location: String = "",
    val timeOfDay: String = "",
    val mood: String = "",
    val characters: List<String> = emptyList(),
    val keyAction: String = "",
    val dialogue: String = "",
    val visualPrompt: String = "",
    val videoPrompt: String = "",
    val isKeyframe: Boolean = false
)