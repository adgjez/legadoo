package io.legado.app.video.agent

import io.legado.app.video.data.entities.VideoScene

class AgentMemory {

    private var novelAnalysis: NovelAnalysisResult? = null
    private val characterPrompts = mutableMapOf<String, String>()
    private val sceneHistory = mutableListOf<VideoScene>()
    private val characterAppearances = mutableMapOf<String, MutableList<String>>()
    private val styleContext = mutableMapOf<String, String>()

    fun storeAnalysis(analysis: NovelAnalysisResult?) {
        novelAnalysis = analysis
        analysis?.characters?.forEach { char ->
            characterAppearances.getOrPut(char.name) { mutableListOf() }
                .add(char.appearance)
        }
    }

    fun storeCharacterPrompts(prompts: Map<String, String>) {
        characterPrompts.putAll(prompts)
    }

    fun getCharacterPrompt(name: String): String? = characterPrompts[name]

    fun getCharacterPrompts(): Map<String, String> = characterPrompts.toMap()

    fun getCharactersForScene(text: String): List<String> {
        return novelAnalysis?.characters
            ?.filter { it.name in text }
            ?.map { it.name }
            ?: emptyList()
    }

    fun getCharacterConsistencyPrompt(characterIds: List<String>): String {
        val relevantCharacters = novelAnalysis?.characters
            ?.filter { it.name in characterIds }
            ?: return ""

        return relevantCharacters.joinToString("\n") { char ->
            buildString {
                appendLine("- ${char.name} (${char.role})")
                appendLine("  外貌: ${char.appearance}")
                appendLine("  性格: ${char.personality}")
                if (char.keyTraits.isNotEmpty()) {
                    appendLine("  关键特征: ${char.keyTraits.joinToString("、")}")
                }
                val prompt = characterPrompts[char.name]
                if (!prompt.isNullOrBlank()) {
                    appendLine("  视觉描述: ${prompt.take(100)}")
                }
            }
        }
    }

    fun getSceneContinuityPrompt(previousScene: VideoScene, currentScene: VideoScene): String {
        return buildString {
            appendLine("前情摘要: ${previousScene.summary.take(100)}")
            appendLine("前情视觉: ${previousScene.visualPrompt.take(100)}")
            appendLine("当前场景: ${currentScene.title}")
            appendLine("当前摘要: ${currentScene.summary.take(100)}")
            if (previousScene.mood.isNotBlank()) {
                appendLine("前情情绪: ${previousScene.mood}")
            }
        }
    }

    fun addSceneToHistory(scene: VideoScene) {
        sceneHistory.add(scene)
        if (sceneHistory.size > 20) {
            sceneHistory.removeAt(0)
        }
    }

    fun getRecentScenes(count: Int = 5): List<VideoScene> {
        return sceneHistory.takeLast(count)
    }

    fun getFullAnalysis(): NovelAnalysisResult? = novelAnalysis

    fun getCharacterAppearanceHistory(name: String): List<String> {
        return characterAppearances[name] ?: emptyList()
    }

    fun checkCharacterConsistency(scenes: List<VideoScene>): List<String> {
        val warnings = mutableListOf<String>()

        val characterLastAppearance = mutableMapOf<String, String>()
        scenes.forEachIndexed { index, scene ->
            scene.characterIds.forEach { charId ->
                val charName = charId.removePrefix("char_")
                characterLastAppearance[charName] = scene.visualPrompt
            }
        }

        val charactersWithMultiplePrompts = mutableMapOf<String, MutableSet<String>>()
        scenes.forEach { scene ->
            scene.characterIds.forEach { charId ->
                val charName = charId.removePrefix("char_")
                if (scene.visualPrompt.isNotBlank()) {
                    charactersWithMultiplePrompts
                        .getOrPut(charName) { mutableSetOf() }
                        .add(scene.visualPrompt.take(50))
                }
            }
        }

        charactersWithMultiplePrompts.forEach { (name, prompts) ->
            if (prompts.size > 1) {
                warnings.add("角色[$name]在不同分镜中描述可能不一致，建议检查")
            }
        }

        return warnings
    }

    fun setStyleContext(key: String, value: String) {
        styleContext[key] = value
    }

    fun getStyleContext(key: String): String? = styleContext[key]

    fun clear() {
        novelAnalysis = null
        characterPrompts.clear()
        sceneHistory.clear()
        characterAppearances.clear()
        styleContext.clear()
    }
}
