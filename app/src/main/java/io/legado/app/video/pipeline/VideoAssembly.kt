package io.legado.app.video.pipeline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 视频装配层 (VideoAssembly) - 三策略后端
 *
 * 借鉴 ArcReel 的最终装配，但针对 Android 生产环境做了分级降级：
 *
 *   Tier 1 (最佳)  MobileFFmpegBackend
 *     · 依赖 com.arthenica:mobile-ffmpeg-full-gpl:5.1.LTS
 *     · 支持真正的滤镜、转场、混流、字幕烧录
 *
 *   Tier 2 (兼容)  RuntimeFFmpegBackend
 *     · 检测 /system/bin/ffmpeg 或 root/termux 提供的 ffmpeg
 *     · 直接 Runtime.exec() 调用（TV/平板/某些 ROM 上存在）
 *
 *   Tier 3 (兜底)  ManifestExporter（纯 Kotlin 零依赖）
 *     · 不做真正视频合成
 *     · 输出:
 *       - segments_manifest.json (所有片段元数据+时间戳)
 *       - 全部字幕 SRT / ASS 文件
 *       - 合成 ffmpeg.txt 命令清单（用户在电脑上跑）
 *       - 或直接转 JianyingDraftExporter 导出剪映草稿
 *     · 永远可用
 *
 * 装配优先级：Tier1 → Tier2 → Tier3，失败自动降级。
 */

// ==================================================================
// 装配后端接口
// ==================================================================

enum class AssemblyBackendTier {
    MOBILE_FFMPEG,     // Tier 1
    RUNTIME_FFMPEG,    // Tier 2
    MANIFEST_EXPORT    // Tier 3 (fallback)
}

interface VideoAssemblyBackend {
    val tier: AssemblyBackendTier
    val name: String
    suspend fun isAvailable(context: Context): Boolean

    suspend fun assemble(
        context: Context,
        request: AssemblyRequest,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<AssemblyResult>
}

data class AssemblyRequest(
    val segments: List<VideoSegment>,
    val outputPath: String,
    val backgroundMusicPath: String? = null,
    val musicVolume: Float = 0.3f,
    val aspectRatio: String = "9:16",
    val subtitleStyle: SubtitleStyle? = null,
    val transitionDurationMs: Int = 500,
    val outputResolution: String = "1080p"
)

data class AssemblyResult(
    val outputFile: File?,
    val usedBackend: String,
    val tier: AssemblyBackendTier,
    val durationMs: Long,
    val warnings: List<String> = emptyList(),
    val artifacts: List<AssemblyArtifact> = emptyList()
)

data class AssemblyArtifact(
    val artifactType: ArtifactType,
    val file: File,
    val description: String
)

enum class ArtifactType {
    FINAL_VIDEO,
    SEGMENT_MANIFEST,
    SUBTITLE_SRT,
    SUBTITLE_ASS,
    FFMPEG_COMMAND_SCRIPT,
    JIANYING_DRAFT,
    PLAYLIST_M3U,
    SIDELOADED_AUDIO
}

// ==================================================================
// Tier 1: MobileFFmpegBackend (需要引入 mobile-ffmpeg 依赖)
// ==================================================================

class MobileFFmpegBackend : VideoAssemblyBackend {
    override val tier = AssemblyBackendTier.MOBILE_FFMPEG
    override val name = "MobileFFmpeg"

    @Suppress("TooGenericExceptionCaught")
    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        // 反射检测是否集成了 MobileFFmpeg
        try {
            Class.forName("com.arthenica.mobileffmpeg.FFmpeg")
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun assemble(
        context: Context,
        request: AssemblyRequest,
        onProgress: (Int, Int) -> Unit
    ): Result<AssemblyResult> = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(request.outputPath)
            outputFile.parentFile?.mkdirs()
            val totalSteps = request.segments.size + 3
            var step = 0

            // Step 1: SRT 字幕文件
            val subtitlePath = request.subtitleStyle?.filePath
                ?: File(outputFile.parentFile, "subtitles.srt").absolutePath
            val srtFile = generateSRTFile(request.segments, subtitlePath)

            // 构建 ffmpeg 命令字符串
            val cmd = buildFFmpegCommand(request, srtFile.absolutePath)
            onProgress(++step, totalSteps)

            // 反射调用: com.arthenica.mobileffmpeg.FFmpeg.execute(cmd)
            val ffmpegClass = Class.forName("com.arthenica.mobileffmpeg.FFmpeg")
            val executeMethod = ffmpegClass.getMethod("execute", String::class.java)
            val rc = (executeMethod.invoke(null, cmd.joinToString(" ")) as? Int) ?: -1

            onProgress(totalSteps - 1, totalSteps)

            return@withContext if (rc == 0 && outputFile.exists()) {
                Result.success(
                    AssemblyResult(
                        outputFile = outputFile,
                        usedBackend = name,
                        tier = tier,
                        durationMs = request.segments.sumOf { it.endTimeMs - it.startTimeMs },
                        artifacts = listOf(
                            AssemblyArtifact(ArtifactType.FINAL_VIDEO, outputFile, "合成视频"),
                            AssemblyArtifact(ArtifactType.SUBTITLE_SRT, srtFile, "SRT 字幕")
                        )
                    )
                )
            } else {
                Result.failure(Exception("MobileFFmpeg 执行失败 rc=$rc"))
            }
        } catch (t: Throwable) {
            Result.failure(Exception("MobileFFmpeg backend 异常: ${t.message}", t))
        }
    }

    private fun buildFFmpegCommand(req: AssemblyRequest, srtPath: String): Array<String> {
        val args = mutableListOf<String>()
        args += "-y"
        req.segments.forEach { s -> args += listOf("-i", s.videoPath) }
        req.backgroundMusicPath?.let { args += listOf("-i", it) }

        val complexFilter = buildString {
            val concatInputs = req.segments.indices.joinToString("") { "[$it:v][$it:a]" }
            append("${concatInputs}concat=n=${req.segments.size}:v=1:a=1[v0][a0]; ")
            if (req.backgroundMusicPath != null) {
                val mIdx = req.segments.size
                append("[a0]volume=1.0[va]; ")
                append("[$mIdx:a]volume=${req.musicVolume}[ma]; ")
                append("[va][ma]amix=inputs=2:normalize=2[a1] ")
            }
        }
        if (complexFilter.isNotBlank()) args += listOf("-filter_complex", complexFilter)

        // 字幕烧录 (仅当指定了 style)
        if (req.subtitleStyle != null) {
            val styleArg = req.subtitleStyle.toFFmpegStyle()
            args += listOf("-vf", "subtitles='$srtPath':force_style='$styleArg'")
        }

        args += listOf("-map", "[v0]")
        args += if (req.backgroundMusicPath != null) listOf("-map", "[a1]") else listOf("-map", "[a0]")
        args += listOf("-c:v", "libx264", "-preset", "medium", "-crf", "23")
        args += listOf("-c:a", "aac", "-b:a", "128k")
        val (w, h) = when (req.aspectRatio) {
            "9:16" -> "1080" to "1920"
            "16:9" -> "1920" to "1080"
            "1:1" -> "1080" to "1080"
            "4:3" -> "1440" to "1080"
            else -> "1920" to "1080"
        }
        args += listOf("-s", "${w}x${h}")
        args += listOf("-r", "30", "-movflags", "+faststart")
        args += req.outputPath
        return args.toTypedArray()
    }
}

// ==================================================================
// Tier 2: RuntimeFFmpegBackend (检测系统 ffmpeg)
// ==================================================================

class RuntimeFFmpegBackend : VideoAssemblyBackend {
    override val tier = AssemblyBackendTier.RUNTIME_FFMPEG
    override val name = "RuntimeSystemFFmpeg"

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        candidates.any { path ->
            try {
                val p = Runtime.getRuntime().exec(arrayOf(path, "-version"))
                p.waitFor() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }

    private val candidates = listOf(
        "/system/bin/ffmpeg",
        "/system/xbin/ffmpeg",
        "/data/local/tmp/ffmpeg",
        "/data/data/com.termux/files/usr/bin/ffmpeg",
        "ffmpeg"
    )

    private fun resolveFfmpeg(): String? = candidates.firstOrNull { path ->
        try {
            val p = Runtime.getRuntime().exec(arrayOf(path, "-version"))
            p.waitFor() == 0
        } catch (_: Throwable) { false }
    }

    override suspend fun assemble(
        context: Context,
        request: AssemblyRequest,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<AssemblyResult> = withContext(Dispatchers.IO) {
        val ffmpeg = resolveFfmpeg()
            ?: return@withContext Result.failure(IllegalStateException("系统没有可用 ffmpeg"))
        try {
            val outputFile = File(request.outputPath)
            outputFile.parentFile?.mkdirs()
            val totalSteps = request.segments.size + 2
            var step = 0

            val srtFile = generateSRTFile(
                request.segments,
                File(outputFile.parentFile, "subtitles.srt").absolutePath
            )

            // 用 concat demuxer 更稳: 生成 filelist.txt
            val listFile = File(outputFile.parentFile, "ffmpeg_concat.txt")
            listFile.writeText(request.segments.joinToString("\n") { s ->
                "file '${s.videoPath.replace("'", "'\\''")}'"
            })

            onProgress(++step, totalSteps)

            val cmd = buildList<String> {
                add(ffmpeg); add("-y")
                add("-f"); add("concat"); add("-safe"); add("0")
                add("-i"); add(listFile.absolutePath)
                request.backgroundMusicPath?.let { addAll(listOf("-i", it)) }
                if (request.backgroundMusicPath != null) {
                    addAll(listOf(
                        "-filter_complex",
                        "[0:a]volume=1.0[va];" +
                                "[1:a]volume=${request.musicVolume}[ma];" +
                                "[va][ma]amix=inputs=2:normalize=0[aout]"
                    ))
                }
                if (request.subtitleStyle != null) {
                    addAll(listOf(
                        "-vf",
                        "subtitles='${srtFile.absolutePath}':force_style='${request.subtitleStyle.toFFmpegStyle()}'"
                    ))
                }
                val (w, h) = when (request.aspectRatio) {
                    "9:16" -> "1080" to "1920"
                    else -> "1920" to "1080"
                }
                addAll(listOf("-s", "${w}x${h}"))
                addAll(listOf("-c:v", "libx264", "-preset", "medium", "-crf", "23"))
                addAll(listOf("-c:a", "aac", "-b:a", "128k", "-r", "30"))
                if (request.backgroundMusicPath != null) {
                    addAll(listOf("-map", "0:v"))
                    addAll(listOf("-map", "[aout]"))
                }
                add(request.outputPath)
            }

            val process = Runtime.getRuntime().exec(cmd.toTypedArray())
            val exitCode = process.waitFor()
            onProgress(totalSteps, totalSteps)

            if (exitCode == 0 && outputFile.exists()) {
                Result.success(
                    AssemblyResult(
                        outputFile = outputFile,
                        usedBackend = name,
                        tier = tier,
                        durationMs = request.segments.sumOf { it.endTimeMs - it.startTimeMs },
                        warnings = listOf("使用了系统 ffmpeg 二进制，非所有设备均可用"),
                        artifacts = listOf(
                            AssemblyArtifact(ArtifactType.FINAL_VIDEO, outputFile, "合成视频"),
                            AssemblyArtifact(ArtifactType.SUBTITLE_SRT, srtFile, "SRT 字幕")
                        )
                    )
                )
            } else {
                Result.failure(Exception("Runtime ffmpeg 失败 exit=$exitCode"))
            }
        } catch (t: Throwable) {
            Result.failure(Exception("Runtime ffmpeg 异常 ${t.message}", t))
        }
    }
}

// ==================================================================
// Tier 3: ManifestExporter (纯 Kotlin 零依赖，永远可用的兜底)
// ==================================================================

class ManifestExporterBackend : VideoAssemblyBackend {
    override val tier = AssemblyBackendTier.MANIFEST_EXPORT
    override val name = "ManifestJSON+SRT (Fallback)"
    override suspend fun isAvailable(context: Context) = true

    override suspend fun assemble(
        context: Context,
        request: AssemblyRequest,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<AssemblyResult> = withContext(Dispatchers.IO) {
        val outDir = File(request.outputPath).parentFile
            ?: File(request.outputPath).apply { mkdirs() }
        outDir.mkdirs()
        val baseName = File(request.outputPath).nameWithoutExtension

        val totalSteps = 6
        var step = 0

        // 1) SRT 字幕
        val srtFile = generateSRTFile(
            request.segments,
            File(outDir, "$baseName.srt").absolutePath
        )
        onProgress(++step, totalSteps)

        // 2) Manifest JSON (所有片段 + 元数据)
        val manifest = buildManifestJson(request)
        val manifestFile = File(outDir, "$baseName.manifest.json").apply { writeText(manifest) }
        onProgress(++step, totalSteps)

        // 3) ffmpeg 批处理命令脚本 (用户可在 PC 端直接跑)
        val script = buildFFmpegScript(request, srtFile.absolutePath, baseName)
        val scriptFile = File(outDir, "$baseName.ffmpeg.sh").apply { writeText(script) }
        onProgress(++step, totalSteps)

        // 4) M3U Playlist: 顺序播放所有片段 (支持 VLC / 视频播放器直接打开)
        val playlist = buildPlaylist(request)
        val m3uFile = File(outDir, "$baseName.m3u8").apply { writeText(playlist) }
        onProgress(++step, totalSteps)

        // 5) 若背景音乐单独存在 → 输出合并说明
        val bgmNote = request.backgroundMusicPath?.let { bgm ->
            val bgmCopy = File(outDir, "$baseName.bgm${File(bgm).extension.let { if (it.isNotBlank()) ".$it" else ".mp3" }}")
            runCatching { File(bgm).copyTo(bgmCopy, overwrite = true) }
            AssemblyArtifact(ArtifactType.SIDELOADED_AUDIO, bgmCopy, "背景音乐原始文件")
        }
        onProgress(++step, totalSteps)

        // 6) 转剪映草稿（用户导入后在剪映里手动精修）
        val jianyingDir = File(outDir, "jianying_draft")
        val draftExporter = JianyingDraftExporter()
        val jianyingArtifact = runCatching {
            val cfg = JianyingDraftConfig(
                projectName = baseName,
                episodeIndex = 1,
                aspectRatio = request.aspectRatio
            )
            val dr = draftExporter.export(cfg, request.segments, outDir.absolutePath, jianyingDir.absolutePath)
            dr.getOrNull()?.let { dir ->
                AssemblyArtifact(ArtifactType.JIANYING_DRAFT, dir, "剪映草稿：可直接导入剪映")
            }
        }.getOrNull()
        onProgress(totalSteps, totalSteps)

        val warnings = buildList {
            add("当前设备没有可用的视频合成后端，已降级为素材+清单导出模式")
            add("方法 A: 运行 *.ffmpeg.sh 在 PC 上合成")
            add("方法 B: 把 jianying_draft/ 文件夹导入剪映 App 手动精修")
            add("方法 C: 在设置中集成 MobileFFmpeg，App 内即可一键合成")
        }

        val artifacts = buildList {
            add(AssemblyArtifact(ArtifactType.SEGMENT_MANIFEST, manifestFile, "所有片段元数据(JSON)"))
            add(AssemblyArtifact(ArtifactType.SUBTITLE_SRT, srtFile, "SRT 字幕"))
            add(AssemblyArtifact(ArtifactType.FFMPEG_COMMAND_SCRIPT, scriptFile, "PC 端 ffmpeg 合成脚本"))
            add(AssemblyArtifact(ArtifactType.PLAYLIST_M3U, m3uFile, "片段播放列表 (VLC 可播)"))
            bgmNote?.let { add(it) }
            jianyingArtifact?.let { add(it) }
        }

        Result.success(
            AssemblyResult(
                outputFile = manifestFile,
                usedBackend = name,
                tier = tier,
                durationMs = request.segments.sumOf { it.endTimeMs - it.startTimeMs },
                warnings = warnings,
                artifacts = artifacts
            )
        )
    }

    private fun buildManifestJson(req: AssemblyRequest): String {
        val totalDuration = req.segments.sumOf { it.endTimeMs - it.startTimeMs }
        val bgmValue = req.backgroundMusicPath
            ?.let { path -> '"' + path.replace("\"", "\\\"") + '"' }
            ?: "null"
        return buildString {
            append("{\n")
            append("  \"aspectRatio\": \"").append(req.aspectRatio).append("\",\n")
            append("  \"outputResolution\": \"").append(req.outputResolution).append("\",\n")
            append("  \"totalSegments\": ").append(req.segments.size).append(",\n")
            append("  \"totalDurationMs\": ").append(totalDuration).append(",\n")
            append("  \"bgmPath\": ").append(bgmValue).append(",\n")
            append("  \"bgmVolume\": ").append(req.musicVolume).append(",\n")
            append("  \"segments\": [\n")
            req.segments.forEachIndexed { i, s ->
                val subVal = s.subtitleText
                    ?.let { txt -> '"' + txt.replace("\"", "\\\"").replace("\n", "\\n") + '"' }
                    ?: "null"
                append("    {\n")
                append("      \"id\": \"").append(s.segmentId).append("\",\n")
                append("      \"videoPath\": \"").append(s.videoPath.replace("\"", "\\\"")).append("\",\n")
                append("      \"startMs\": ").append(s.startTimeMs).append(",\n")
                append("      \"endMs\": ").append(s.endTimeMs).append(",\n")
                append("      \"transition\": \"").append(s.transitionType).append("\",\n")
                append("      \"subtitle\": ").append(subVal).append("\n")
                append("    }")
                if (i < req.segments.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }
    }

    private fun buildFFmpegScript(req: AssemblyRequest, srtPath: String, baseName: String): String {
        val script = buildString {
            append("#!/bin/sh\n")
            append("# 自动生成：请在已安装 ffmpeg 的电脑上运行此脚本，与素材在同一目录\n")
            append("# 输出文件: ${baseName}.final.mp4\n\n")
            append("set -e\n\n")

            // ----------------------------------------------------------
            // 关键：每个片段先 trim 到 Manifest/Playlist 精确承诺的 durationMs
            //   主路径：重编码 (libx264 + aac) → 精确到帧级别（每帧 ≈ 33ms/30fps）
            //   fallback: -c copy → 不精确（关键帧/AAC帧边界导致每段~100ms 误差），
            //             只在 libx264/aac 不可用时触发
            // 原因：用户实际提供的 seg_*.mp4 可能比预期长/短，trim 保证端到端时长与 storyboard 一致
            // ----------------------------------------------------------
            append("# ===== Step A: 逐段精确 trim (主: 重编码=故事板时长; fallback: stream copy) =====\n")
            req.segments.forEachIndexed { i, s ->
                val durationSec = (s.endTimeMs - s.startTimeMs) / 1000.0
                val inPath = s.videoPath
                val outPath = "_trimmed_${baseName}_seg_$i.mp4"
                append("echo \"  [trim] seg_$i  -> %.3fs\"\n".format(durationSec))
                append("ffmpeg -y -i \"$inPath\" -t %.3f -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac \"$outPath\" 2>/dev/null || \\\n".format(durationSec))
                append("  ffmpeg -y -i \"$inPath\" -t %.3f -c copy -map 0 \"$outPath\"\n\n".format(durationSec))
            }

            append("# ===== Step B: concat trim 后的片段 =====\n")
            append("cat > concat.txt <<'EOF'\n")
            req.segments.forEachIndexed { i, _ -> append("file '_trimmed_${baseName}_seg_$i.mp4'\n") }
            append("EOF\n\n")
            append("ffmpeg -y \\\n")
            append("  -f concat -safe 0 -i concat.txt \\\n")
            req.backgroundMusicPath?.let { append("  -i \"$it\" \\\n") }
            if (req.backgroundMusicPath != null) {
                append("  -filter_complex \"[0:a]volume=1.0[va];[1:a]volume=${req.musicVolume}[ma];[va][ma]amix=inputs=2:normalize=0[a]\" \\\n")
            }
            if (req.subtitleStyle != null) {
                val st = req.subtitleStyle.toFFmpegStyle()
                append("  -vf \"subtitles='$srtPath':force_style='$st'\" \\\n")
            }
            val (w, h) = when (req.aspectRatio) {
                "9:16" -> "1080" to "1920"
                "16:9" -> "1920" to "1080"
                "1:1" -> "1080" to "1080"
                else -> "1920" to "1080"
            }
            append("  -s ${w}x${h} -r 30 \\\n")
            append("  -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k \\\n")
            append("  -movflags +faststart \\\n")
            append("  ${baseName}.final.mp4\n\n")

            append("# ===== Step C (可选): 清理 trim 临时文件 =====\n")
            append("rm -f _trimmed_${baseName}_seg_*.mp4 concat.txt\n\n")
            append("echo 'Done => ${baseName}.final.mp4'\n")
        }
        return script
    }

    private fun buildPlaylist(req: AssemblyRequest): String {
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:3\n")
            append("#EXT-X-TARGETDURATION:10\n")
            append("#EXT-X-MEDIA-SEQUENCE:0\n")
            req.segments.forEach { s ->
                val dur = (s.endTimeMs - s.startTimeMs) / 1000.0
                append("#EXTINF:%.3f,\n".format(dur))
                append("${s.videoPath}\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
    }
}

// ==================================================================
// 顶层装配入口：三策略调度
// ==================================================================

class VideoAssembler(private val context: Context) {

    private val backends: List<VideoAssemblyBackend> by lazy {
        listOf(
            MobileFFmpegBackend(),
            RuntimeFFmpegBackend(),
            ManifestExporterBackend()  // 永远可用
        )
    }

    fun availableBackends(): List<Pair<VideoAssemblyBackend, Boolean>> {
        return backends.map { it to runCatching { it.isAvailable(context) }.getOrDefault(false) }
    }

    suspend fun assemble(
        segments: List<VideoSegment>,
        outputPath: String,
        backgroundMusicPath: String? = null,
        musicVolume: Float = 0.3f,
        aspectRatio: String = "9:16",
        subtitleStyle: SubtitleStyle? = null,
        transitionDurationMs: Int = 500,
        outputResolution: String = "1080p",
        forceTier: AssemblyBackendTier? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<AssemblyResult> = withContext(Dispatchers.IO) {
        require(segments.isNotEmpty()) { "segments 不能为空" }

        val request = AssemblyRequest(
            segments = segments,
            outputPath = outputPath,
            backgroundMusicPath = backgroundMusicPath,
            musicVolume = musicVolume,
            aspectRatio = aspectRatio,
            subtitleStyle = subtitleStyle,
            transitionDurationMs = transitionDurationMs,
            outputResolution = outputResolution
        )

        val candidates = if (forceTier != null) {
            backends.filter { it.tier == forceTier }
        } else backends

        val warnings = mutableListOf<String>()
        var lastException: Throwable? = null

        for (backend in candidates) {
            val available = runCatching { backend.isAvailable(context) }.getOrDefault(false)
            if (!available && forceTier == null) {
                warnings += "跳过 ${backend.name} (不可用)"
                continue
            }
            val r = runCatching { backend.assemble(context, request, onProgress) }
                .getOrElse { Result.failure(it) }
            if (r.isSuccess) {
                return@withContext r
            }
            val ex = r.exceptionOrNull()
            lastException = ex
            warnings += "${backend.name} 失败: ${ex?.message}"
            // 尝试下一级
        }

        // 理论不可能：Tier 3 永远可用。万一：
        Result.failure(
            lastException ?: IllegalStateException("所有装配后端均失败")
        )
    }

    /**
     * 生成 SRT 字幕文件，保存在 outputSrtPath
     */
    fun generateSRT(
        segments: List<VideoSegment>,
        outputSrtPath: String
    ): File = generateSRTFile(segments, outputSrtPath)
}

// ==================================================================
// 公共工具 (SRT / 时间戳格式化)
// ==================================================================

internal fun generateSRTFile(segments: List<VideoSegment>, outputSrtPath: String): File {
    val out = File(outputSrtPath).also {
        it.parentFile?.mkdirs()
    }
    out.writeText(
        buildString {
            segments.forEachIndexed { index, segment ->
                val text = segment.subtitleText ?: return@forEachIndexed
                append(index + 1).append("\n")
                append(formatTimestamp(segment.startTimeMs))
                append(" --> ")
                append(formatTimestamp(segment.endTimeMs))
                append("\n")
                append(text.trim())
                append("\n\n")
            }
        }
    )
    return out
}

internal fun formatTimestamp(ms: Long): String {
    val hours = ms / 3_600_000
    val mins = (ms % 3_600_000) / 60_000
    val secs = (ms % 60_000) / 1000
    val millis = ms % 1000
    return "%02d:%02d:%02d,%03d".format(hours, mins, secs, millis)
}

// ==================================================================
// 数据结构：片段/字幕/转场
// ==================================================================

data class VideoSegment(
    val segmentId: String,
    val videoPath: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val subtitleText: String? = null,
    val transitionType: TransitionType = TransitionType.NONE
)

enum class TransitionType {
    NONE,
    FADE,
    DISSOLVE,
    WIPE,
    MATCH_CUT,
    JUMP_CUT,
    L_CUT,
    J_CUT,
    CROSS_FADE,
    IRIS_IN,
    IRIS_OUT,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    SLIDE_DOWN,
    ZOOM_IN,
    ZOOM_OUT,
    BLUR,
    FLASH_WHITE,
    FLASH_BLACK
}

data class SubtitleStyle(
    val fontSize: Int = 24,
    val fontColor: String = "white",
    val outlineColor: String = "black",
    val outlineWidth: Float = 2f,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val filePath: String = ""
) {
    fun toFFmpegStyle(): String {
        val colorHex = when (fontColor.lowercase()) {
            "white" -> "&H00FFFFFF"
            "yellow" -> "&H00FFFF00"
            "green" -> "&H0000FF00"
            "red" -> "&H000000FF"
            "cyan" -> "&H0000FFFF"
            else -> "&H00FFFFFF"
        }
        val outlineHex = when (outlineColor.lowercase()) {
            "black" -> "&H00000000"
            "white" -> "&H00FFFFFF"
            else -> "&H00000000"
        }
        val alignment = when (position) {
            SubtitlePosition.TOP -> 8
            SubtitlePosition.CENTER -> 5
            SubtitlePosition.BOTTOM -> 2
        }
        return "FontSize=$fontSize,PrimaryColour=$colorHex,OutlineColour=$outlineHex," +
                "Outline=$outlineWidth,Alignment=$alignment,BorderStyle=1,Shadow=0"
    }
}

enum class SubtitlePosition { TOP, CENTER, BOTTOM }

// ==================================================================
// 剪映草稿导出 (保持与旧版兼容，略做增强)
// ==================================================================

data class JianyingDraftConfig(
    val projectName: String,
    val episodeIndex: Int,
    val version: JianyingVersion = JianyingVersion.V6,
    val aspectRatio: String = "9:16"
)

enum class JianyingVersion { V5, V6 }

class JianyingDraftExporter {

    suspend fun export(
        config: JianyingDraftConfig,
        segments: List<VideoSegment>,
        videoDir: String,
        outputDir: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val draftDir = File(outputDir, "${config.projectName}_第${config.episodeIndex}集")
            draftDir.mkdirs()
            val assetsDir = File(draftDir, "assets").also { it.mkdirs() }

            segments.forEach { seg ->
                val target = File(assetsDir, "seg_${seg.segmentId}.mp4")
                runCatching { File(seg.videoPath).copyTo(target, overwrite = true) }
            }

            File(draftDir, "draft_info.json").writeText(buildDraftInfoJson(config, segments))
            File(draftDir, "draft_meta_info.json").writeText(buildMetaInfoJson(config))
            File(draftDir, "README.txt").writeText(
                "导入方法：\n" +
                        "1) 把整个文件夹拷贝到手机：/sdcard/Drafts/com.lemon.lv/{这里}/\n" +
                        "2) 打开剪映 → 本地草稿 → 即可看到项目\n"
            )
            Result.success(draftDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildDraftInfoJson(config: JianyingDraftConfig, segments: List<VideoSegment>): String {
        val totalDuration = segments.sumOf { it.endTimeMs - it.startTimeMs }
        val width = if (config.aspectRatio == "9:16") 1080 else 1920
        val height = if (config.aspectRatio == "9:16") 1920 else 1080
        return buildString {
            append("{\"version\":\"").append(config.version).append('"')
            append(",\"name\":\"").append(config.projectName).append('"')
            append(",\"fps\":30,\"width\":").append(width)
            append(",\"height\":").append(height)
            append(",\"duration\":").append(totalDuration)
            append(",\"segments\":[")
            segments.forEachIndexed { i, s ->
                val subQuoted = s.subtitleText
                    ?.let { txt -> '"' + txt.replace("\"", "\\\"") + '"' }
                    ?: "null"
                append("{\"id\":\"").append(s.segmentId).append('"')
                append(",\"path\":\"assets/seg_").append(s.segmentId).append(".mp4\"")
                append(",\"start\":").append(s.startTimeMs)
                append(",\"end\":").append(s.endTimeMs)
                append(",\"transition\":\"").append(s.transitionType).append('"')
                append(",\"subtitle\":").append(subQuoted)
                append("}")
                if (i < segments.size - 1) append(",")
            }
            append("]}")
        }
    }

    private fun buildMetaInfoJson(config: JianyingDraftConfig): String {
        return buildString {
            append("{\"project_name\":\"${config.projectName}\",")
            append("\"episode_index\":${config.episodeIndex},")
            append("\"aspect_ratio\":\"${config.aspectRatio}\",")
            append("\"created_at\":${System.currentTimeMillis()}")
            append("}")
        }
    }
}
