package io.legado.app.video.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoProject
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoProjectListScreen(
    projects: List<VideoProject>,
    onProjectClick: (VideoProject) -> Unit,
    onNewProject: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteProject: (VideoProject) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI 视频工作台",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = VideoColors.OnBackground
                        )
                        Text(
                            text = "让创作无限可能 🎬",
                            fontSize = 11.sp,
                            color = VideoColors.OnSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = VideoColors.OnBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Background,
                    titleContentColor = VideoColors.OnBackground
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 600, delayMillis = 300)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 400)
                )
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNewProject,
                    containerColor = VideoColors.Primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建项目", fontWeight = FontWeight.Medium)
                }
            }
        },
        containerColor = VideoColors.Background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val brush = Brush.radialGradient(
                    colors = listOf(
                        VideoColors.Primary.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.3f),
                    radius = size.width / 3
                )
                drawRect(brush)

                val brush2 = Brush.radialGradient(
                    colors = listOf(
                        VideoColors.Secondary.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.7f),
                    radius = size.width / 4
                )
                drawRect(brush2)
            }

            if (projects.isEmpty()) {
                EmptyStateAnimated(
                    modifier = Modifier.padding(padding),
                    onNewProject = onNewProject
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(
                                animationSpec = tween(durationMillis = 300, delayMillis = 50)
                            ) + slideInVertically(
                                animationSpec = tween(durationMillis = 300)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "我的项目",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VideoColors.OnSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${projects.size} 个项目",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VideoColors.OnSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = VideoColors.Primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✨ AI 驱动",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VideoColors.Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    items(projects, key = { it.id }) { project ->
                        var animated by remember { mutableStateOf(false) }
                        LaunchedEffect(project.id) {
                            kotlinx.coroutines.delay(100)
                            animated = true
                        }

                        AnimatedVisibility(
                            visible = animated,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = FastOutSlowInEasing
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            VideoProjectCardAnimated(
                                project = project,
                                onClick = { onProjectClick(project) },
                                onDelete = { onDeleteProject(project) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateAnimated(
    modifier: Modifier = Modifier,
    onNewProject: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        visible = true
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 500)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 500)
            )
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VideoColors.GradientStart.copy(alpha = 0.3f),
                                VideoColors.GradientEnd.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = VideoColors.Primary
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 500, delayMillis = 100)
            )
        ) {
            Text(
                text = "开始你的创作旅程",
                style = MaterialTheme.typography.titleLarge,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 500, delayMillis = 150)
            )
        ) {
            Text(
                text = "将小说、剧本或创意转化为精美的 AI 视频",
                style = MaterialTheme.typography.bodyMedium,
                color = VideoColors.OnSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 500, delayMillis = 250)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 500, delayMillis = 250)
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoColors.SurfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            null,
                            tint = VideoColors.Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "小说改编",
                            style = MaterialTheme.typography.labelMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoColors.SurfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Description,
                            null,
                            tint = VideoColors.Secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "剧本创作",
                            style = MaterialTheme.typography.labelMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoColors.SurfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            null,
                            tint = VideoColors.Warning,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "AI 创意",
                            style = MaterialTheme.typography.labelMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 500, delayMillis = 350)
            )
        ) {
            OutlinedButton(
                onClick = onNewProject,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VideoColors.Primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("创建第一个项目", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun VideoProjectCardAnimated(
    project: VideoProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onClick() }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        when (project.sourceType) {
                            VideoProject.SOURCE_NOVEL -> Brush.linearGradient(
                                colors = listOf(
                                    VideoColors.GradientStart,
                                    VideoColors.GradientEnd
                                )
                            )
                            VideoProject.SOURCE_SCRIPT -> Brush.linearGradient(
                                colors = listOf(
                                    VideoColors.GradientWarmStart,
                                    VideoColors.GradientWarmEnd
                                )
                            )
                            else -> Brush.linearGradient(
                                colors = listOf(
                                    VideoColors.GradientCoolStart,
                                    VideoColors.GradientCoolEnd
                                )
                            )
                        }
                    )
                    .then(
                        Modifier.graphicsLayer {
                            alpha = if (isPressed) 0.9f else 1f
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (project.sourceType) {
                            VideoProject.SOURCE_NOVEL -> Icons.Default.MenuBook
                            VideoProject.SOURCE_SCRIPT -> Icons.Default.Description
                            else -> Icons.Default.AutoAwesome
                        },
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    StatusChip(status = project.status)
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VideoColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(6.dp))
                
                Text(
                    text = project.description.ifBlank { getSourceLabel(project.sourceType) },
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                
                if (project.status == VideoProject.STATUS_GENERATING ||
                    project.status == VideoProject.STATUS_ANALYZING ||
                    project.status == VideoProject.STATUS_PLANNING) {
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "进度 ${project.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.OnSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { project.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = VideoColors.Primary,
                        trackColor = VideoColors.SurfaceVariant
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = VideoColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${project.completedScenes}/${project.totalScenes} 分镜",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = VideoColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${project.totalCharacters} 角色",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }
                    }
                    
                    Text(
                        text = formatDate(project.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        VideoProject.STATUS_DRAFT -> "草稿" to VideoColors.StatusDraft
        VideoProject.STATUS_ANALYZING -> "解析中" to VideoColors.StatusAnalyzing
        VideoProject.STATUS_PLANNING -> "规划中" to VideoColors.StatusPlanning
        VideoProject.STATUS_STORYBOARD -> "分镜完成" to VideoColors.StatusPlanning
        VideoProject.STATUS_GENERATING -> "生成中" to VideoColors.StatusGenerating
        VideoProject.STATUS_COMPLETED -> "已完成" to VideoColors.StatusCompleted
        VideoProject.STATUS_FAILED -> "失败" to VideoColors.StatusFailed
        else -> status to VideoColors.StatusDraft
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

private fun getSourceLabel(type: String): String = when (type) {
    VideoProject.SOURCE_NOVEL -> "小说改编"
    VideoProject.SOURCE_SCRIPT -> "剧本创作"
    VideoProject.SOURCE_IDEA -> "创意生成"
    else -> "未知来源"
}

private fun formatDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> "${diff / 86_400_000}天前"
    }
}
