package io.legado.app.video.ui.preview

import android.widget.VideoView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewScreen(
    project: VideoProject,
    scenes: List<VideoScene>,
    onBack: () -> Unit,
    onSceneClick: (VideoScene) -> Unit,
    onExport: () -> Unit
) {
    var selectedScene by remember { mutableStateOf<VideoScene?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.IosShare, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Background,
                    titleContentColor = VideoColors.OnBackground
                )
            )
        },
        containerColor = VideoColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedScene != null && selectedScene!!.generatedVideoPath.isNotBlank()) {
                VideoPlayerPreview(
                    videoPath = selectedScene!!.generatedVideoPath,
                    title = selectedScene!!.title,
                    onClose = { selectedScene = null }
                )
            } else {
                // Project info header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = VideoColors.OnSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = project.description.ifBlank { project.genre },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.OnSurfaceVariant
                                )
                            }
                            ProjectStatusBadge(project.status)
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("分镜", "${project.completedScenes}/${project.totalScenes}")
                            StatItem("状态", getStatusText(project.status))
                            StatItem("进度", "${project.progress}%")
                        }
                    }
                }
                
                // Scenes list
                Text(
                    text = "分镜列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = VideoColors.OnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scenes, key = { it.id }) { scene ->
                        ScenePreviewItem(
                            scene = scene,
                            onClick = { onSceneClick(scene) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerPreview(
    videoPath: String,
    title: String,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VideoColors.OnSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(videoPath)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = true
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        )
    }
}

@Composable
private fun ScenePreviewItem(
    scene: VideoScene,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VideoColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (scene.generatedVideoPath.isNotBlank()) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = VideoColors.Primary,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.title.ifBlank { "分镜 ${scene.order}" },
                    style = MaterialTheme.typography.titleSmall,
                    color = VideoColors.OnSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = scene.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            VideoStatusChip(scene.videoStatus)
        }
    }
}

@Composable
private fun VideoStatusChip(status: String) {
    val (text, color) = when (status) {
        VideoScene.STATUS_COMPLETED -> "就绪" to VideoColors.Success
        VideoScene.STATUS_GENERATING_VIDEO -> "生成中" to VideoColors.StatusGenerating
        VideoScene.STATUS_STORYBOARD_READY -> "待生成" to VideoColors.OnSurfaceVariant
        VideoScene.STATUS_FAILED -> "失败" to VideoColors.Error
        else -> "待处理" to VideoColors.OnSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ProjectStatusBadge(status: String) {
    val (text, color) = when (status) {
        VideoProject.STATUS_COMPLETED -> "已完成" to VideoColors.Success
        VideoProject.STATUS_GENERATING -> "生成中" to VideoColors.StatusGenerating
        VideoProject.STATUS_ANALYZING -> "解析中" to VideoColors.StatusAnalyzing
        VideoProject.STATUS_PLANNING -> "规划中" to VideoColors.StatusPlanning
        VideoProject.STATUS_FAILED -> "失败" to VideoColors.Error
        VideoProject.STATUS_DRAFT -> "草稿" to VideoColors.OnSurfaceVariant
        else -> status to VideoColors.OnSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VideoColors.OnSurfaceVariant
        )
    }
}

private fun getStatusText(status: String): String = when (status) {
    VideoProject.STATUS_DRAFT -> "草稿"
    VideoProject.STATUS_ANALYZING -> "解析中"
    VideoProject.STATUS_PLANNING -> "规划中"
    VideoProject.STATUS_STORYBOARD -> "分镜完成"
    VideoProject.STATUS_GENERATING -> "生成中"
    VideoProject.STATUS_COMPLETED -> "已完成"
    VideoProject.STATUS_FAILED -> "失败"
    else -> status
}