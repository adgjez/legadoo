package io.legado.app.video.ui.pipeline

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
 * PipelineDashboardScreen - 管线状态总览仪表板
 *
 * 功能：
 * - 全局项目状态监控
 * - 成本追踪
 * - 实时统计
 * - 快速操作入口
 * - 批量操作支持
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineDashboardScreen(
    projects: List<VideoProject>,
    onBack: () -> Unit,
    onProjectClick: (VideoProject) -> Unit,
    onStartPipeline: (VideoProject) -> Unit,
    onViewReport: (VideoProject) -> Unit,
    onNewProject: () -> Unit
) {
    val stats = calculateDashboardStats(projects)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生产仪表板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VideoColors.Surface)
            )
        },
        containerColor = VideoColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProject,
                containerColor = VideoColors.Primary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatsOverviewCard(stats)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "项目列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${projects.size} 个项目",
                        style = MaterialTheme.typography.bodySmall,
                        color = VideoColors.TextSecondary
                    )
                }
            }

            if (projects.isEmpty()) {
                item {
                    EmptyDashboardState(onNewProject = onNewProject)
                }
            } else {
                items(projects) { project ->
                    DashboardProjectCard(
                        project = project,
                        onClick = { onProjectClick(project) },
                        onStartPipeline = { onStartPipeline(project) },
                        onViewReport = { onViewReport(project) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private data class DashboardStats(
    val totalProjects: Int,
    val activeProjects: Int,
    val completedProjects: Int,
    val failedProjects: Int,
    val totalScenes: Int,
    val completedScenes: Int,
    val totalCharacters: Int,
    val overallProgress: Float
)

private fun calculateDashboardStats(projects: List<VideoProject>): DashboardStats {
    val total = projects.size
    val active = projects.count {
        it.status != VideoProject.STATUS_DRAFT &&
        it.status != VideoProject.STATUS_COMPLETED &&
        it.status != VideoProject.STATUS_ARCHIVED &&
        it.status != VideoProject.STATUS_FAILED
    }
    val completed = projects.count { it.status == VideoProject.STATUS_COMPLETED }
    val failed = projects.count { it.status == VideoProject.STATUS_FAILED }
    val totalScenes = projects.sumOf { it.totalScenes }
    val completedScenes = projects.sumOf { it.completedScenes }
    val totalCharacters = projects.sumOf { it.totalCharacters }
    val overallProgress = if (totalScenes > 0) completedScenes.toFloat() / totalScenes else 0f

    return DashboardStats(
        totalProjects = total,
        activeProjects = active,
        completedProjects = completed,
        failedProjects = failed,
        totalScenes = totalScenes,
        completedScenes = completedScenes,
        totalCharacters = totalCharacters,
        overallProgress = overallProgress
    )
}

@Composable
private fun StatsOverviewCard(stats: DashboardStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "生产总览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatItem(
                    label = "活跃项目",
                    value = stats.activeProjects.toString(),
                    color = VideoColors.StatusGenerating,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "已完成",
                    value = stats.completedProjects.toString(),
                    color = VideoColors.StatusCompleted,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "失败",
                    value = stats.failedProjects.toString(),
                    color = VideoColors.StatusFailed,
                    icon = Icons.Default.Error,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = VideoColors.Border)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "总体进度",
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.TextSecondary
                )
                Text(
                    "${"%.0f".format(stats.overallProgress * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { stats.overallProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = VideoColors.Primary,
                trackColor = VideoColors.SurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStat("总分镜", stats.totalScenes.toString())
                MiniStat("已完成", stats.completedScenes.toString())
                MiniStat("角色数", stats.totalCharacters.toString())
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = VideoColors.TextSecondary)
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = VideoColors.TextSecondary)
    }
}

@Composable
private fun DashboardProjectCard(
    project: VideoProject,
    onClick: () -> Unit,
    onStartPipeline: () -> Unit,
    onViewReport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            projectStatusColor(project.status).copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        projectStatusIcon(project.status),
                        contentDescription = null,
                        tint = projectStatusColor(project.status),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            projectStatusLabel(project.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = projectStatusColor(project.status)
                        )
                    }
                }

                AnimatedPulseDot(project.status)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${project.completedScenes}/${project.totalScenes} 分镜",
                    style = MaterialTheme.typography.labelSmall,
                    color = VideoColors.TextSecondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "·",
                    color = VideoColors.TextSecondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${project.totalCharacters} 角色",
                    style = MaterialTheme.typography.labelSmall,
                    color = VideoColors.TextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = {
                    if (project.totalScenes > 0) {
                        project.completedScenes.toFloat() / project.totalScenes
                    } else 0f
                },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = projectStatusColor(project.status),
                trackColor = VideoColors.SurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (project.status == VideoProject.STATUS_DRAFT ||
                    project.status == VideoProject.STATUS_FAILED) {
                    FilledTonalButton(
                        onClick = onStartPipeline,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("开始生产", fontSize = 12.sp)
                    }
                }

                if (project.status == VideoProject.STATUS_COMPLETED) {
                    FilledTonalButton(
                        onClick = onViewReport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Assessment, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("质量报告", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AnimatedPulseDot(status: String) {
    val isActive = status == VideoProject.STATUS_ANALYZING ||
        status == VideoProject.STATUS_PLANNING ||
        status == VideoProject.STATUS_STORYBOARD ||
        status == VideoProject.STATUS_GENERATING

    if (isActive) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    projectStatusColor(status),
                    RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun EmptyDashboardState(onNewProject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = VideoColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "暂无项目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "创建您的第一个视频项目",
            style = MaterialTheme.typography.bodyMedium,
            color = VideoColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewProject) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("新建项目")
        }
    }
}

private fun projectStatusColor(status: String): Color = when (status) {
    VideoProject.STATUS_DRAFT -> VideoColors.StatusDraft
    VideoProject.STATUS_ANALYZING -> VideoColors.StatusAnalyzing
    VideoProject.STATUS_PLANNING -> VideoColors.StatusPlanning
    VideoProject.STATUS_STORYBOARD -> VideoColors.StatusPlanning
    VideoProject.STATUS_GENERATING -> VideoColors.StatusGenerating
    VideoProject.STATUS_COMPLETED -> VideoColors.StatusCompleted
    VideoProject.STATUS_FAILED -> VideoColors.StatusFailed
    VideoProject.STATUS_ARCHIVED -> VideoColors.TextSecondary
    else -> VideoColors.TextSecondary
}

private fun projectStatusIcon(status: String) = when (status) {
    VideoProject.STATUS_DRAFT -> Icons.Default.Draft
    VideoProject.STATUS_ANALYZING -> Icons.Default.Psychology
    VideoProject.STATUS_PLANNING -> Icons.Default.AccountTree
    VideoProject.STATUS_STORYBOARD -> Icons.Default.Image
    VideoProject.STATUS_GENERATING -> Icons.Default.AutoAwesome
    VideoProject.STATUS_COMPLETED -> Icons.Default.CheckCircle
    VideoProject.STATUS_FAILED -> Icons.Default.Error
    VideoProject.STATUS_ARCHIVED -> Icons.Default.Archive
    else -> Icons.Default.Help
}

private fun projectStatusLabel(status: String) = when (status) {
    VideoProject.STATUS_DRAFT -> "草稿"
    VideoProject.STATUS_ANALYZING -> "分析中"
    VideoProject.STATUS_PLANNING -> "规划中"
    VideoProject.STATUS_STORYBOARD -> "分镜中"
    VideoProject.STATUS_GENERATING -> "生成中"
    VideoProject.STATUS_COMPLETED -> "已完成"
    VideoProject.STATUS_FAILED -> "失败"
    VideoProject.STATUS_ARCHIVED -> "已归档"
    else -> "未知"
}
