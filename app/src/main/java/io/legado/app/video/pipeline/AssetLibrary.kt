package io.legado.app.video.pipeline

import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProp

data class CharacterSheet(
    val characterId: String,
    val name: String,
    val locked: Boolean,
    val referenceImageUrl: String?,
    val referenceImagePath: String?,
    val visualDescription: String,
    val traits: List<CharacterTrait>,
    val costumes: List<CharacterCostume>,
    val accessories: List<String>,
    val colorPalette: List<String>,
    val styleTags: List<String>,
    val voiceStyle: String?,
    val lockedAt: Long?,
    val version: Int = 1
) {
    fun toPromptReference(): String {
        val parts = mutableListOf<String>()
        parts.add(visualDescription)
        if (costumes.isNotEmpty()) {
            parts.add(costumes.joinToString(", ") { "${it.name}(${it.color})" })
        }
        if (accessories.isNotEmpty()) {
            parts.add("配饰: ${accessories.joinToString(", ")}")
        }
        if (colorPalette.isNotEmpty()) {
            parts.add("主色调: ${colorPalette.take(3).joinToString("/")}")
        }
        if (styleTags.isNotEmpty()) {
            parts.add(styleTags.joinToString(", "))
        }
        return parts.joinToString(". ")
    }
}

data class CharacterTrait(
    val name: String,
    val description: String,
    val visualWeight: Float = 1.0f
)

data class CharacterCostume(
    val name: String,
    val color: String,
    val material: String? = null,
    val isDefault: Boolean = true
)

data class ClueSheet(
    val clueId: String,
    val name: String,
    val type: ClueType,
    val locked: Boolean,
    val referenceImageUrl: String?,
    val referenceImagePath: String?,
    val visualDescription: String,
    val importance: ClueImportance,
    val location: String?,
    val version: Int = 1
)

enum class ClueType {
    LOCATION,
    PROP,
    VEHICLE,
    ANIMAL,
    ENSEMBLE
}

enum class ClueImportance {
    MAJOR,
    RECURRING,
    MINOR
}

data class AssetLibrary(
    val projectId: String,
    val characters: List<CharacterSheet>,
    val clues: List<ClueSheet>,
    val styleReferenceUrl: String?,
    val updatedAt: Long
) {
    fun getCharacter(name: String): CharacterSheet? =
        characters.find { it.name == name }

    fun getClue(name: String): ClueSheet? =
        clues.find { it.name == name }

    fun getMajorClues(): List<ClueSheet> =
        clues.filter { it.importance == ClueImportance.MAJOR }

    fun isComplete(): Boolean =
        characters.isNotEmpty() && characters.all { it.locked }
}

object AssetLibraryManager {

    private val libraries = mutableMapOf<String, AssetLibrary>()

    fun getLibrary(projectId: String): AssetLibrary? = libraries[projectId]

    fun saveLibrary(library: AssetLibrary) {
        libraries[library.projectId] = library
    }

    fun isReady(projectId: String): Boolean {
        val lib = libraries[projectId] ?: return false
        return lib.isComplete()
    }

    fun addCharacter(projectId: String, character: CharacterSheet) {
        val lib = libraries.getOrPut(projectId) {
            AssetLibrary(projectId, emptyList(), emptyList(), null, System.currentTimeMillis())
        }
        val updated = lib.copy(
            characters = lib.characters.filter { it.characterId != character.characterId } + character,
            updatedAt = System.currentTimeMillis()
        )
        libraries[projectId] = updated
    }

    fun addClue(projectId: String, clue: ClueSheet) {
        val lib = libraries.getOrPut(projectId) {
            AssetLibrary(projectId, emptyList(), emptyList(), null, System.currentTimeMillis())
        }
        val updated = lib.copy(
            clues = lib.clues.filter { it.clueId != clue.clueId } + clue,
            updatedAt = System.currentTimeMillis()
        )
        libraries[projectId] = updated
    }

    fun lockCharacter(projectId: String, characterId: String) {
        val lib = libraries[projectId] ?: return
        val updated = lib.copy(
            characters = lib.characters.map {
                if (it.characterId == characterId) it.copy(locked = true, lockedAt = System.currentTimeMillis())
                else it
            },
            updatedAt = System.currentTimeMillis()
        )
        libraries[projectId] = updated
    }

    fun lockClue(projectId: String, clueId: String) {
        val lib = libraries[projectId] ?: return
        val updated = lib.copy(
            clues = lib.clues.map {
                if (it.clueId == clueId) it.copy(locked = true)
                else it
            },
            updatedAt = System.currentTimeMillis()
        )
        libraries[projectId] = updated
    }

    fun getReferenceImagesForScene(
        projectId: String,
        characterNames: List<String>,
        clueNames: List<String>
    ): List<String> {
        val lib = libraries[projectId] ?: return emptyList()
        val refs = mutableListOf<String>()
        characterNames.forEach { name ->
            lib.getCharacter(name)?.referenceImagePath?.let { refs.add(it) }
        }
        clueNames.forEach { name ->
            lib.getClue(name)?.referenceImagePath?.let { refs.add(it) }
        }
        return refs
    }

    fun clear(projectId: String) {
        libraries.remove(projectId)
    }
}
