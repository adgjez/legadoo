package io.legado.app.video.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile

data class ExportConfig(
    val outputFileName: String = "video_export_${System.currentTimeMillis()}.mp4",
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val bitrate: Int = 4_000_000,
    val frameRate: Int = 24,
    val includeTransitions: Boolean = true,
    val transitionDurationMs: Long = 500L,
    val burnSubtitles: Boolean = false,
    val addIntroOutro: Boolean = false,
    val format: ExportFormat = ExportFormat.MP4,
    val quality: ExportQuality = ExportQuality.HIGH
)

enum class ExportFormat {
    MP4, WEBM, MOV
}

enum class ExportQuality {
    LOW, MEDIUM, HIGH, ULTRA
}

data class ExportProgress(
    val stage: String = "准备中",
    val progress: Float = 0f,
    val currentScene: Int = 0,
    val totalScenes: Int = 0,
    val error: String? = null,
    val completed: Boolean = false,
    val outputUri: Uri? = null
)

class VideoExportManager(private val context: Context) {

    private val _progress = MutableStateFlow(ExportProgress())
    val progress: StateFlow<ExportProgress> = _progress

    suspend fun exportProject(
        scenePaths: List<String>,
        config: ExportConfig = ExportConfig()
    ): Result<Uri> = coroutineScope {
        try {
            _progress.value = ExportProgress(totalScenes = scenePaths.size)

            val validPaths = scenePaths.filter { it.isNotBlank() && File(it).exists() }
            if (validPaths.isEmpty()) {
                return@coroutineScope Result.failure(IOException("没有可导出的视频文件"))
            }

            val outputDir = getOutputDirectory()
            val outputFile = File(outputDir, config.outputFileName)

            if (outputFile.exists()) outputFile.delete()

            _progress.value = _progress.value.copy(stage = "视频拼接中", progress = 0.1f)

            if (validPaths.size == 1) {
                copyVideoFile(validPaths.first(), outputFile)
            } else {
                concatenateVideos(validPaths, outputFile, config)
            }

            _progress.value = _progress.value.copy(stage = "保存到媒体库", progress = 0.9f)

            val uri = saveToMediaStore(outputFile, config)

            _progress.value = ExportProgress(
                stage = "导出完成",
                progress = 1f,
                completed = true,
                outputUri = uri
            )

            Result.success(uri)
        } catch (e: Exception) {
            _progress.value = ExportProgress(error = e.message ?: "导出失败")
            Result.failure(e)
        }
    }

    private suspend fun copyVideoFile(sourcePath: String, destFile: File) {
        withContext(Dispatchers.IO) {
            FileInputStream(sourcePath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun concatenateVideos(
        inputPaths: List<String>,
        outputFile: File,
        config: ExportConfig
    ) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val normalizedFiles = normalizeVideos(inputPaths, tempDir, config)

            val concatListFile = File(tempDir, "concat_list.txt")
            concatListFile.writeText(
                normalizedFiles.joinToString("\n") { "file '${it.absolutePath}'" }
            )

            mergeMediaFiles(normalizedFiles, outputFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun normalizeVideos(
        inputPaths: List<String>,
        tempDir: File,
        config: ExportConfig
    ): List<File> {
        val normalized = mutableListOf<File>()

        inputPaths.forEachIndexed { index, path ->
            val extractor = MediaExtractor()
            extractor.setDataSource(path)

            val trackCount = extractor.trackCount
            val videoTrackIndex = findTrack(extractor, MediaExtractor.MEDIA_TRACK_TYPE_VIDEO)
            val audioTrackIndex = findTrack(extractor, MediaExtractor.MEDIA_TRACK_TYPE_AUDIO)

            if (videoTrackIndex < 0) {
                extractor.release()
                return@forEachIndexed
            }

            val videoFormat = extractor.getTrackFormat(videoTrackIndex)
            val durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)

            val outputFile = File(tempDir, "scene_${String.format("%04d", index)}.mp4")
            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val muxVideoTrack = muxer.addTrack(videoFormat)

            var muxAudioTrack = -1
            if (audioTrackIndex >= 0) {
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                muxAudioTrack = muxer.addTrack(audioFormat)
            }

            muxer.start()

            var videoDone = false
            var audioDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteArray(2 * 1024 * 1024)

            while (!videoDone || !audioDone) {
                if (!videoDone) {
                    extractor.selectTrack(videoTrackIndex)
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        videoDone = true
                    } else {
                        val presentationTime = extractor.sampleTime
                        bufferInfo.set(0, sampleSize, presentationTime, 0)
                        muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                }

                if (!audioDone && audioTrackIndex >= 0) {
                    extractor.selectTrack(audioTrackIndex)
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        audioDone = true
                    } else {
                        val presentationTime = extractor.sampleTime
                        bufferInfo.set(0, sampleSize, presentationTime, 0)
                        muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                }
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            normalized.add(outputFile)

            _progress.value = _progress.value.copy(
                currentScene = index + 1,
                progress = 0.1f + (0.7f * (index + 1) / inputPaths.size)
            )
        }

        return normalized
    }

    private fun findTrack(extractor: MediaExtractor, type: Int): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(
                    getMimeForTrackType(type)
                ) == true
            ) {
                return i
            }
        }
        return -1
    }

    private fun getMimeForTrackType(type: Int): String = when (type) {
        MediaExtractor.MEDIA_TRACK_TYPE_VIDEO -> "video/"
        MediaExtractor.MEDIA_TRACK_TYPE_AUDIO -> "audio/"
        MediaExtractor.MEDIA_TRACK_TYPE_SUBTITLE -> "text/"
        else -> ""
    }

    private fun mergeMediaFiles(inputFiles: List<File>, outputFile: File) {
        if (inputFiles.isEmpty()) return

        val firstExtractor = MediaExtractor()
        firstExtractor.setDataSource(inputFiles[0].absolutePath)

        val firstVideoTrack = findTrack(firstExtractor, MediaExtractor.MEDIA_TRACK_TYPE_VIDEO)
        val firstAudioTrack = findTrack(firstExtractor, MediaExtractor.MEDIA_TRACK_TYPE_AUDIO)

        val videoFormat = firstExtractor.getTrackFormat(firstVideoTrack)

        val muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val muxVideoTrack = muxer.addTrack(videoFormat)
        var muxAudioTrack = -1

        if (firstAudioTrack >= 0) {
            val audioFormat = firstExtractor.getTrackFormat(firstAudioTrack)
            muxAudioTrack = muxer.addTrack(audioFormat)
        }

        muxer.start()

        var videoOffsetUs = 0L
        var audioOffsetUs = 0L

        val buffer = ByteArray(2 * 1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        for ((index, inputFile) in inputFiles.withIndex()) {
            val extractor = if (index == 0) firstExtractor else MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            val videoTrack = findTrack(extractor, MediaExtractor.MEDIA_TRACK_TYPE_VIDEO)
            val audioTrack = findTrack(extractor, MediaExtractor.MEDIA_TRACK_TYPE_AUDIO)

            var videoDone = false
            var audioDone = false

            while (!videoDone || !audioDone) {
                if (!videoDone) {
                    extractor.selectTrack(videoTrack)
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        videoDone = true
                    } else {
                        val pts = extractor.sampleTime + videoOffsetUs
                        bufferInfo.set(0, sampleSize, pts, 0)
                        muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                }

                if (!audioDone && audioTrack >= 0 && muxAudioTrack >= 0) {
                    extractor.selectTrack(audioTrack)
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        audioDone = true
                    } else {
                        val pts = extractor.sampleTime + audioOffsetUs
                        bufferInfo.set(0, sampleSize, pts, 0)
                        muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                }
            }

            val videoDuration = extractor.getTrackFormat(videoTrack)
                .getLong(MediaFormat.KEY_DURATION)
            videoOffsetUs += videoDuration

            if (audioTrack >= 0) {
                val audioDuration = extractor.getTrackFormat(audioTrack)
                    .getLong(MediaFormat.KEY_DURATION)
                audioOffsetUs += audioDuration
            }

            if (index > 0) extractor.release()
        }

        muxer.stop()
        muxer.release()
        firstExtractor.release()
    }

    private suspend fun saveToMediaStore(outputFile: File, config: ExportConfig): Uri = withContext(Dispatchers.IO) {
        val mimeType = when (config.format) {
            ExportFormat.MP4 -> "video/mp4"
            ExportFormat.WEBM -> "video/webm"
            ExportFormat.MOV -> "video/quicktime"
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, config.outputFileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LegadoVideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法创建媒体文件")

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(outputFile).use { input ->
                input.copyTo(outputStream)
            }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        if (outputFile.exists()) outputFile.delete()

        uri
    }

    private fun getOutputDirectory(): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: context.filesDir,
            "exports"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cancelExport() {
        _progress.value = ExportProgress(error = "已取消")
    }
}
