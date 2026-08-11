package io.legado.app.video.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.ui.theme.VideoColors

/**
 * ProjectExportScreen - 项目导出界面
 *
 * 功能：
 * - 选择导出格式（MP4, MOV, WebM）
 * - 选择导出质量
 * - 添加字幕/水印
 * - 导出进度显示
 * - 分享选项
 */

enum class ExportFormat(val displayName: String, val extension: String) {
    MP4("MP4 (H.264)", ".mp4"),
    MOV("MOV (ProRes)", ".mov"),
    WEBM("WebM (VP9)", ".webm"),
    GIF("GIF动画", ".gif")
}

enum class ExportQuality(val displayName: String, val bitrateMbps: Int) {
    LOW("低质量 (480p)", 2),
    MEDIUM("中等质量 (720p)", 5),
    HIGH("高质量 (1080p)", 8),
    ULTRA_HD("超高清 (4K)", 15)
}

data class ExportConfig(
    val format: ExportFormat = ExportFormat.MP4,
    val quality: ExportQuality = ExportQuality.HIGH,
    val includeSubtitles: Boolean = true,
    val includeWatermark: Boolean = false,
    val watermarkText: String = "",
    val backgroundMusic: String? = null,
    val voiceoverTrack: String? = null,
    val frameRate: Int = 24,
    val resolution: String = "1080p"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectExportScreen(
    project: VideoProject,
    onBack: () -> Unit,
    onExport: (ExportConfig) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.MP4) }
    var selectedQuality by remember { mutableStateOf(ExportQuality.HIGH) }
    var includeSubtitles by remember { mutableStateOf(true) }
    var includeWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf(project.name) }
    var selectedFrameRate by remember { mutableIntStateOf(24) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出项目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VideoColors.Surface)
            )
        },
        containerColor = VideoColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = VideoColors.Primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "状态: ${project.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoColors.TextSecondary
                            )
                            Text(
                                "${project.completedScenes}/${project.totalScenes} 分镜已完成",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "导出格式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExportFormat.values().toList()) { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(format.displayName, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        format.extension,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VideoColors.TextSecondary
                                    )
                                }
                            },
                            modifier = Modifier.height(64.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    "导出质量",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportQuality.values().forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (selectedQuality == quality) VideoColors.Primary.copy(alpha = 0.15f)
                                    else VideoColors.SurfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedQuality = quality }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(quality.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    "码率: ${quality.bitrateMbps} Mbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "高级选项",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("添加字幕")
                            Switch(
                                checked = includeSubtitles,
                                onCheckedChange = { includeSubtitles = it }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("添加水印")
                            Switch(
                                checked = includeWatermark,
                                onCheckedChange = { includeWatermark = it }
                            )
                        }

                        if (includeWatermark) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("水印文字") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text("帧率: ${selectedFrameRate}fps")
                        Slider(
                            value = selectedFrameRate.toFloat(),
                            onValueChange = { selectedFrameRate = it.toInt() },
                            valueRange = 12f..60f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = VideoColors.Primary,
                                activeTrackColor = VideoColors.Primary
                            )
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        isExporting = true
                        val config = ExportConfig(
                            format = selectedFormat,
                            quality = selectedQuality,
                            includeSubtitles = includeSubtitles,
                            includeWatermark = includeWatermark,
                            watermarkText = watermarkText,
                            frameRate = selectedFrameRate
                        )
                        onExport(config)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("导出中... ${"%.0f".format(exportProgress * 100)}%")
                    } else {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始导出")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("分享")
                    }
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(4.dp))
                        Text("保存草稿")
                    }
                }
            }
        }
    }
}
