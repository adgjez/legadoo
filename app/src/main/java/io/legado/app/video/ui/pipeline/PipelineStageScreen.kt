package io.legado.app.video.ui.pipeline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.pipeline.PipelineStage
import io.legado.app.video.pipeline.StageProgress
import io.legado.app.video.pipeline.StageStatus

/**
 * PipelineStageScreen - 管线阶段实时进度 UI
 *
 * 借鉴 ArcReel 的生产进度界面
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineStageScreen(
    projectId: String,
    stages: Map<PipelineStage, StageProgress>,
    overallProgress: Float,
    isPaused: Boolean,
    isCancelled: Boolean,
    errors: List<String>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetryFailed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生产管线") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (isPaused) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, "继续")
                        }
                    } else {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, "暂停")
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Stop, "取消")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OverallProgressCard(
                overallProgress = overallProgress,
                isPaused = isPaused,
                isCancelled = isCancelled,
                stageCount = stages.size,
                completedCount = stages.values.count { it.isComplete() }
            )

            if (errors.isNotEmpty()) {
                ErrorBanner(errors.first())
            }

            Text(
                text = "管线阶段",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            PipelineStageTimeline(
                stages = stages,
                onRetryStage = { onRetryFailed() }
            )
        }
    }
}

@Composable
private fun OverallProgressCard(
    overallProgress: Float,
    isPaused: Boolean,
    isCancelled: Boolean,
    stageCount: Int,
    completedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCancelled -> Color(0xFFFFEBEE)
                isPaused -> Color(0xFFFFF3E0)
                overallProgress >= 1.0f -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = overallProgress,
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 8.dp,
                        color = when {
                            isCancelled -> Color(0xFFE57373)
                            isPaused -> Color(0xFFFFB74D)
                            overallProgress >= 1.0f -> Color(0xFF66BB6A)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = "${(overallProgress * 100).toInt()}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = when {
                            isCancelled -> "已取消"
                            isPaused -> "已暂停"
                            overallProgress >= 1.0f -> "完成！"
                            else -> "进行中..."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "阶段 $completedCount / $stageCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "错误",
                tint = Color(0xFFC62828)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = Color(0xFFC62828),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PipelineStageTimeline(
    stages: Map<PipelineStage, StageProgress>,
    onRetryStage: () -> Unit
) {
    val sortedStages = stages.entries.sortedBy { it.key.order }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        sortedStages.forEachIndexed { index, (stage, progress) ->
            StageTimelineItem(
                stage = stage,
                progress = progress,
                isLast = index == sortedStages.lastIndex,
                onRetry = onRetryStage
            )
        }
    }
}

@Composable
private fun StageTimelineItem(
    stage: PipelineStage,
    progress: StageProgress,
    isLast: Boolean,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        crossAxisAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            StageIndicator(progress.status)

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(
                            color = if (progress.isComplete()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stage.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (progress.isActive()) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(Modifier.width(8.dp))

                StageStatusChip(progress.status)
            }

            if (progress.isActive()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVert()
                ) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${(progress.progress * 100).toInt()}% " +
                                    "(${progress.itemsCompleted}/${progress.itemsTotal})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (progress.isFailed()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.errorMessage ?: "未知错误",
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, "重试", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试")
                }
            }

            if (progress.isComplete()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "耗时 ${formatDuration(progress.elapsedMs())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StageIndicator(status: StageStatus) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                when (status) {
                    StageStatus.COMPLETED -> Color(0xFF66BB6A)
                    StageStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    StageStatus.FAILED -> Color(0xFFE57373)
                    StageStatus.PAUSED -> Color(0xFFFFB74D)
                    StageStatus.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
                    StageStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            StageStatus.COMPLETED -> Icon(
                Icons.Default.Check, "完成",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            StageStatus.RUNNING -> {
                val infiniteTransition = rememberInfiniteTransition(label = "stage_progress")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatableAnimation(
                        animation = tween(durationMillis = 600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Box(
                    modifier = Modifier
                        .size(16.dp * scale)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            StageStatus.FAILED -> Icon(
                Icons.Default.Close, "失败",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            StageStatus.PAUSED -> Icon(
                Icons.Default.Pause, "暂停",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            StageStatus.SKIPPED -> Icon(
                Icons.Default.SkipNext, "跳过",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            StageStatus.PENDING -> Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun StageStatusChip(status: StageStatus) {
    val (text, color) = when (status) {
        StageStatus.COMPLETED -> "完成" to Color(0xFF66BB6A)
        StageStatus.RUNNING -> "进行中" to MaterialTheme.colorScheme.primary
        StageStatus.FAILED -> "失败" to Color(0xFFE57373)
        StageStatus.PAUSED -> "暂停" to Color(0xFFFFB74D)
        StageStatus.SKIPPED -> "跳过" to MaterialTheme.colorScheme.outline
        StageStatus.PENDING -> "等待中" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    SuggestionChip(
        onClick = {},
        label = { Text(text, fontSize = 11.sp) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = color
        )
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    return when {
        minutes > 0 -> "${minutes}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}
