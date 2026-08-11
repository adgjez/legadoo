package io.legado.app.video.export

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    projectName: String,
    scenes: List<VideoScene>,
    onDismiss: () -> Unit,
    onExport: (ExportConfig) -> Unit,
    exportProgress: ExportProgress? = null
) {
    var resolution by remember { mutableStateOf("1280x720") }
    var quality by remember { mutableStateOf(ExportQuality.HIGH) }
    var format by remember { mutableStateOf(ExportFormat.MP4) }
    var includeTransitions by remember { mutableStateOf(true) }
    var burnSubtitles by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("${projectName}_export.mp4") }
    var expandedResolution by remember { mutableStateOf(false) }
    var expandedQuality by remember { mutableStateOf(false) }

    val isExporting = exportProgress != null && !exportProgress!!.completed && exportProgress!!.error == null
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        containerColor = VideoColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(WindowInsets.navigationBars = WindowInsets.PaddingValues(bottom = 8.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "导出视频",
                    style = MaterialTheme.typography.titleLarge,
                    color = VideoColors.OnSurface,
                    fontWeight = FontWeight.Bold
                )
                if (!isExporting) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = VideoColors.OnSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isExporting) {
                ExportProgressView(exportProgress!!)
            } else if (exportProgress?.completed == true) {
                ExportCompleteView(exportProgress!!, onDismiss)
            } else {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("文件名", color = VideoColors.OnSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VideoColors.Primary,
                        unfocusedBorderColor = VideoColors.SurfaceVariant,
                        cursorColor = VideoColors.Primary,
                        focusedContainerColor = VideoColors.Surface,
                        unfocusedContainerColor = VideoColors.Surface
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "分辨率",
                    style = MaterialTheme.typography.labelMedium,
                    color = VideoColors.OnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                Box {
                    OutlinedTextField(
                        value = resolution,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "展开", tint = VideoColors.OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VideoColors.Primary,
                            unfocusedBorderColor = VideoColors.SurfaceVariant
                        )
                    )
                    DropdownMenu(
                        expanded = expandedResolution,
                        onDismissRequest = { expandedResolution = false }
                    ) {
                        listOf("1920x1080", "1280x720", "854x480", "640x360").forEach { res ->
                            DropdownMenuItem(
                                text = { Text(res) },
                                onClick = { resolution = res; expandedResolution = false }
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { expandedResolution = true }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "画质",
                    style = MaterialTheme.typography.labelMedium,
                    color = VideoColors.OnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportQuality.values().forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = {
                                Text(
                                    when (q) {
                                        ExportQuality.LOW -> "低"
                                        ExportQuality.MEDIUM -> "中"
                                        ExportQuality.HIGH -> "高"
                                        ExportQuality.ULTRA -> "极高"
                                    },
                                    color = if (quality == q) VideoColors.OnPrimary else VideoColors.OnSurfaceVariant
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoColors.Primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "输出格式",
                    style = MaterialTheme.typography.labelMedium,
                    color = VideoColors.OnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.values().forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = {
                                Text(
                                    f.name.uppercase(),
                                    color = if (format == f) VideoColors.OnPrimary else VideoColors.OnSurfaceVariant
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoColors.Primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "添加转场效果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VideoColors.OnSurface
                    )
                    Switch(
                        checked = includeTransitions,
                        onCheckedChange = { includeTransitions = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = VideoColors.Primary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "烧录字幕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VideoColors.OnSurface
                    )
                    Switch(
                        checked = burnSubtitles,
                        onCheckedChange = { burnSubtitles = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = VideoColors.Primary
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                val completedScenes = scenes.count { it.videoStatus == VideoScene.STATUS_COMPLETED }
                val totalDuration = scenes.filter { it.videoStatus == VideoScene.STATUS_COMPLETED }
                    .sumOf { it.durationSeconds }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("就绪分镜", color = VideoColors.OnSurfaceVariant, fontSize = 12.sp)
                            Text(
                                "$completedScenes/${scenes.size}",
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("预计时长", color = VideoColors.OnSurfaceVariant, fontSize = 12.sp)
                            Text(
                                "${totalDuration}秒",
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val width = resolution.split("x")[0].toIntOrNull() ?: 1280
                val height = resolution.split("x")[1].toIntOrNull() ?: 720
                val bitrate = when (quality) {
                    ExportQuality.LOW -> 1_000_000
                    ExportQuality.MEDIUM -> 2_500_000
                    ExportQuality.HIGH -> 5_000_000
                    ExportQuality.ULTRA -> 8_000_000
                }

                Button(
                    onClick = {
                        onExport(
                            ExportConfig(
                                outputFileName = fileName,
                                resolutionWidth = width,
                                resolutionHeight = height,
                                bitrate = bitrate,
                                includeTransitions = includeTransitions,
                                burnSubtitles = burnSubtitles,
                                format = format,
                                quality = quality
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary),
                    enabled = completedScenes > 0
                ) {
                    Icon(Icons.Default.IosShare, null, tint = VideoColors.OnPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "开始导出 (${completedScenes}个分镜)",
                        color = VideoColors.OnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportProgressView(progress: ExportProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(VideoColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.size(64.dp),
                color = VideoColors.Primary,
                strokeWidth = 4.dp
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = progress.stage,
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${(progress.progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            color = VideoColors.Primary,
            fontWeight = FontWeight.Bold
        )

        if (progress.currentScene > 0 && progress.totalScenes > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "处理分镜 ${progress.currentScene}/${progress.totalScenes}",
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.OnSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = VideoColors.Primary,
            trackColor = VideoColors.SurfaceVariant,
        )
    }
}

@Composable
private fun ExportCompleteView(progress: ExportProgress, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(VideoColors.Success.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = VideoColors.Success,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "导出成功！",
            style = MaterialTheme.typography.titleLarge,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "视频已保存到 Movies/LegadoVideo",
            style = MaterialTheme.typography.bodyMedium,
            color = VideoColors.OnSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        progress.outputUri?.let { uri ->
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.type = "video/*"
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, "打开视频"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("播放视频")
            }

            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
        ) {
            Text("完成", color = VideoColors.OnPrimary)
        }
    }
}
