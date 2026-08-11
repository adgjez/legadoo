package io.legado.app.video.pipeline

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class ScriptVersion(
    val versionId: String,
    val projectId: String,
    val stage: ScriptStage,
    val contentSnapshot: String,
    val visualSnapshot: String?,
    val createdAt: Long,
    val changeSummary: String
)

data class VersionHistory(
    val projectId: String,
    val versions: List<ScriptVersion>,
    val currentVersionId: String?
) {
    fun getCurrent(): ScriptVersion? =
        versions.find { it.versionId == currentVersionId }

    fun getPrevious(): ScriptVersion? {
        val currentIndex = versions.indexOfFirst { it.versionId == currentVersionId }
        return if (currentIndex > 0) versions[currentIndex - 1] else null
    }

    fun canRollback(): Boolean = versions.size > 1

    fun rollbackTo(versionId: String): VersionHistory {
        return copy(currentVersionId = versionId)
    }

    fun addVersion(version: ScriptVersion): VersionHistory {
        return copy(
            versions = versions + version,
            currentVersionId = version.versionId
        )
    }
}

object VersionManager {

    private const val PREF_NAME = "video_script_versions"
    private const val KEY_PREFIX = "history_"

    private val gson: Gson = GsonBuilder().create()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getHistory(projectId: String): VersionHistory? {
        val json = prefs.getString("$KEY_PREFIX$projectId", null) ?: return null
        return try {
            gson.fromJson(json, VersionHistory::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveHistory(history: VersionHistory) {
        val json = gson.toJson(history)
        prefs.edit().putString("$KEY_PREFIX${history.projectId}", json).apply()
    }

    fun snapshotNarrationScript(
        projectId: String,
        script: NarrationScript,
        changeSummary: String
    ): ScriptVersion {
        val versionId = "v_${System.currentTimeMillis()}"
        val contentJson = gson.toJson(script.segments.map { seg ->
            mapOf(
                "segmentId" to seg.segmentId,
                "novelText" to seg.novelText,
                "duration" to seg.readingDuration,
                "characters" to seg.referencedCharacters,
                "clues" to seg.referencedClues
            )
        })
        val visualJson = gson.toJson(script.segments.map { seg ->
            mapOf(
                "segmentId" to seg.segmentId,
                "imagePrompt" to seg.imagePrompt,
                "videoPrompt" to seg.videoPrompt
            )
        })

        val version = ScriptVersion(
            versionId = versionId,
            projectId = projectId,
            stage = script.stage,
            contentSnapshot = contentJson,
            visualSnapshot = visualJson,
            createdAt = System.currentTimeMillis(),
            changeSummary = changeSummary
        )

        val history = getHistory(projectId) ?: VersionHistory(projectId, emptyList(), null)
        saveHistory(history.addVersion(version))

        return version
    }

    fun snapshotDramaScript(
        projectId: String,
        script: DramaScript,
        changeSummary: String
    ): ScriptVersion {
        val versionId = "v_${System.currentTimeMillis()}"
        val contentJson = gson.toJson(script.utterances.map { utt ->
            mapOf(
                "utteranceId" to utt.utteranceId,
                "speaker" to utt.speaker,
                "dialogue" to utt.dialogue,
                "action" to utt.action,
                "sceneDescription" to utt.sceneDescription
            )
        })
        val visualJson = gson.toJson(script.utterances.map { utt ->
            mapOf(
                "utteranceId" to utt.utteranceId,
                "imagePrompt" to utt.imagePrompt,
                "videoPrompt" to utt.videoPrompt
            )
        })

        val version = ScriptVersion(
            versionId = versionId,
            projectId = projectId,
            stage = script.stage,
            contentSnapshot = contentJson,
            visualSnapshot = visualJson,
            createdAt = System.currentTimeMillis(),
            changeSummary = changeSummary
        )

        val history = getHistory(projectId) ?: VersionHistory(projectId, emptyList(), null)
        saveHistory(history.addVersion(version))

        return version
    }

    fun rollback(projectId: String, versionId: String): Boolean {
        val history = getHistory(projectId) ?: return false
        if (history.versions.none { it.versionId == versionId }) return false
        saveHistory(history.rollbackTo(versionId))
        return true
    }

    fun getVersionsList(projectId: String): List<ScriptVersion> {
        return getHistory(projectId)?.versions?.reversed() ?: emptyList()
    }

    fun clearHistory(projectId: String) {
        prefs.edit().remove("$KEY_PREFIX$projectId").apply()
    }

    fun restoreNarrationScript(
        projectId: String,
        versionId: String
    ): NarrationScript? {
        val history = getHistory(projectId) ?: return null
        val version = history.versions.find { it.versionId == versionId } ?: return null

        val segments = try {
            val contentList = gson.fromJson(version.contentSnapshot, List::class.java)
            val visualList = version.visualSnapshot?.let {
                gson.fromJson(it, List::class.java)
            } ?: emptyList<Any>()

            contentList.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val visualItem = visualList.firstOrNull {
                    (it as? Map<*, *>)?.get("segmentId") == map["segmentId"]
                } as? Map<*, *>

                NarrationSegment(
                    segmentId = map["segmentId"] as? String ?: "",
                    index = 0,
                    novelText = map["novelText"] as? String ?: "",
                    readingDuration = (map["duration"] as? Number)?.toInt() ?: 10,
                    imagePrompt = visualItem?.get("imagePrompt") as? String,
                    videoPrompt = visualItem?.get("videoPrompt") as? String,
                    referencedCharacters = (map["characters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    referencedClues = (map["clues"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    status = SegmentStatus.APPROVED
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        return NarrationScript(
            scriptId = version.versionId,
            episodeId = projectId,
            segments = segments,
            stage = version.stage,
            totalSegments = segments.size
        )
    }
}
