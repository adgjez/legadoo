package io.legado.app.video.ui.storyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryboardWorkbenchScreen(
    project: VideoProject,
    scenes: List<VideoScene>,
    characters: List<VideoCharacter>,
    onBack: () -> Unit,
    onSceneClick: (VideoScene) -> Unit,
    onAddScene: () -> Unit,
    onDeleteScene: (VideoScene) -> Unit,
    onReorderScenes: (List<VideoScene>) -> Unit,
    onStartGeneration: () -> Unit,
    onCharactersClick: () -> Unit,
    onExportClick: () -> Unit,
    /** 可选：分镜列表上方的「生成模式推荐」等 Hero 卡片。不提供时布局保持不变 */
    header: @Composable () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCharactersClick) {
                        Badge(if (characters.isNotEmpty()) characters.size else null) {
                            Icon(Icons.Default.People, contentDescription = "角色", tint = VideoColors.OnSurface)
                        }
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.IosShare, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Background,
                    titleContentColor = VideoColors.OnBackground,
                    navigationIconContentColor = VideoColors.OnBackground
                )
            )
        },
        containerColor = VideoColors.Background,
        bottomBar = {
            BottomAppBar(
                containerColor = VideoColors.Surface,
                contentColor = VideoColors.OnSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${scenes.size} 个分镜",
                            style = MaterialTheme.typography.titleMedium,
                            color = VideoColors.OnSurface
                        )
                        Text(
                            text = "总时长 ${scenes.sumOf { it.durationSeconds }}秒",
                            style = MaterialTheme.typography.bodySmall,
                            color = VideoColors.OnSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onStartGeneration,
                        enabled = scenes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("开始生成")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddScene,
                containerColor = VideoColors.Primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加分镜")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero header slot：一般放「生成模式推荐卡片」之类的
            header()
            if (scenes.isEmpty()) {
                EmptyStoryboard(onAddScene)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(scenes, key = { _, scene -> scene.id }) { index, scene ->
                        StoryboardSceneCard(
                            scene = scene,
                            characters = characters,
                            onClick = { onSceneClick(scene) },
                            onDelete = { onDeleteScene(scene) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStoryboard(onAddScene: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Movie,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有分镜",
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "添加分镜或让 AI 自动规划",
            style = MaterialTheme.typography.bodyMedium,
            color = VideoColors.OnSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onAddScene,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = VideoColors.Primary.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VideoColors.Primary)
            Spacer(Modifier.width(8.dp))
            Text("AI 自动规划", color = VideoColors.Primary)
        }
    }
}

@Composable
private fun StoryboardSceneCard(
    scene: VideoScene,
    characters: List<VideoCharacter>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sceneCharacters = characters.filter { it.id in scene.characterIds }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        if (scene.generatedStoryboardPath.isNotBlank()) {
                            VideoColors.SurfaceVariant
                        } else {
                            VideoColors.SurfaceVariant
                        }
                    )
            ) {
                if (scene.generatedStoryboardPath.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = null,
                                tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = getSceneStatusText(scene.videoStatus),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = VideoColors.CardOverlay
                ) {
                    Text(
                        text = "分镜 ${scene.order}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = VideoColors.CardOverlay
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${scene.durationSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (scene.generatedVideoPath.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = VideoColors.Success
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "视频就绪",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scene.title.ifBlank { "分镜 ${scene.order}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VideoColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = VideoColors.OnSurfaceVariant
                        )
                    }
                }
                
                Spacer(Modifier.height(6.dp))
                
                Text(
                    text = scene.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TagItem(scene.shotType, Icons.Default.VideoCamera)
                    TagItem(scene.cameraMovement, Icons.Default.OpenWith)
                    if (scene.location.isNotBlank()) {
                        TagItem(scene.location, Icons.Default.LocationOn)
                    }
                    if (sceneCharacters.isNotEmpty()) {
                        TagItem("${sceneCharacters.size}角色", Icons.Default.People)
                    }
                }
                
                if (scene.errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VideoColors.Error.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = scene.errorMessage,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.Error,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VideoColors.SurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = VideoColors.OnSurfaceVariant
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = VideoColors.OnSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

private fun getSceneStatusText(status: String): String = when (status) {
    VideoScene.STATUS_PENDING -> "待生成"
    VideoScene.STATUS_GENERATING_STORYBOARD -> "生成分镜图中..."
    VideoScene.STATUS_STORYBOARD_READY -> "分镜图已就绪"
    VideoScene.STATUS_GENERATING_VIDEO -> "生成视频中..."
    VideoScene.STATUS_COMPLETED -> "已完成"
    VideoScene.STATUS_FAILED -> "失败"
    else -> status
}