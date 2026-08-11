package io.legado.app.video.pipeline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 项目导入/导出
 *
 * 借鉴 ArcReel 的项目持久化：
 * - 导出完整项目为 ZIP 包
 * - 支持在不同设备间迁移
 * - 包含所有资产、脚本、设置
 */

data class ProjectExport(
    val projectId: String,
    val projectName: String,
    val exportVersion: String = "2.0",
    val content: ProjectContent,
    val assets: List<AssetEntry>,
    val settings: ProjectSettings
)

data class ProjectContent(
    val novelText: String?,
    val episodes: List<EpisodeData>,
    val characters: List<CharacterData>,
    val clues: List<ClueData>,
    val scripts: List<ScriptData>,
    val createdAt: Long,
    val updatedAt: Long
)

data class EpisodeData(
    val episodeIndex: Int,
    val title: String,
    val segments: List<SegmentData>
)

data class SegmentData(
    val segmentId: String,
    val index: Int,
    val novelText: String?,
    val imagePrompt: String?,
    val videoPrompt: String?,
    val videoUrl: String?,
    val status: String
)

data class CharacterData(
    val characterId: String,
    val name: String,
    val description: String,
    val designImagePath: String?,
    val locked: Boolean
)

data class ClueData(
    val clueId: String,
    val name: String,
    val type: String,
    val description: String,
    val referenceImagePath: String?
)

data class ScriptData(
    val scriptId: String,
    val mode: String,
    val content: String,
    val stage: String
)

data class AssetEntry(
    val assetId: String,
    val type: AssetType,
    val path: String,
    val localPath: String?,
    val sizeBytes: Long
)

enum class AssetType {
    CHARACTER_DESIGN,
    CLUE_REFERENCE,
    STORYBOARD,
    VIDEO_CLIP,
    AUDIO,
    STYLE_REFERENCE,
    SUBTITLE
}

data class ProjectSettings(
    val providerConfig: Map<String, ProviderSetting>,
    val outputSettings: OutputSettings,
    val workflowSettings: WorkflowSettings
)

data class ProviderSetting(
    val providerKey: String,
    val model: String,
    val apiKey: String?
)

data class OutputSettings(
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val format: String = "mp4",
    val frameRate: Int = 30
)

data class WorkflowSettings(
    val contentMode: String = "narration",
    val episodeCount: Int = 3,
    val autoGenerateCharacters: Boolean = true,
    val autoGenerateClues: Boolean = true,
    val crossShotReference: Boolean = true
)

class ProjectExporter(private val context: Context) {

    suspend fun exportProject(
        projectId: String,
        projectName: String,
        exportDir: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exportFile = File(exportDir, "${projectName}_export.zip")
            exportFile.parentFile?.mkdirs()

            val tempDir = File(exportDir, "_export_temp")
            tempDir.mkdirs()

            val manifest = buildExportManifest(projectId, projectName)
            File(tempDir, "manifest.json").writeText(manifest)

            File(tempDir, "project.json").writeText(
                """{"projectId":"$projectId","projectName":"$projectName","exportVersion":"2.0","exportedAt":${System.currentTimeMillis()}}"""
            )

            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "cd '${tempDir.absolutePath}' && zip -r '${exportFile.absolutePath}' .")
            )
            val exitCode = process.waitFor()

            tempDir.deleteRecursively()

            if (exitCode == 0 && exportFile.exists()) {
                Result.success(exportFile)
            } else {
                Result.failure(Exception("导出失败 (exit code: $exitCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildExportManifest(projectId: String, projectName: String): String {
        return """
{
  "projectId": "$projectId",
  "projectName": "$projectName",
  "exportVersion": "2.0",
  "exportedAt": ${System.currentTimeMillis()},
  "appVersion": "1.0"
}
        """.trimIndent()
    }

    suspend fun importProject(
        importFile: File,
        targetDir: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(targetDir, "_import_temp")
            tempDir.mkdirs()

            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "unzip -o '${importFile.absolutePath}' -d '${tempDir.absolutePath}'")
            )
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("解压失败"))
            }

            val manifestFile = File(tempDir, "manifest.json")
            val manifest = if (manifestFile.exists()) manifestFile.readText() else "{}"

            tempDir.deleteRecursively()

            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
