package io.legado.app.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.theme.VideoColors

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val config = when (status) {
        VideoScene.STATUS_PENDING -> StatusConfig("待处理", VideoColors.StatusDraft, Icons.Default.HourglassEmpty)
        VideoScene.STATUS_GENERATING_STORYBOARD -> StatusConfig("分镜中", VideoColors.StatusPlanning, Icons.Default.AutoAwesome)
        VideoScene.STATUS_STORYBOARD_READY -> StatusConfig("分镜完成", VideoColors.StatusPlanning, Icons.Default.Check)
        VideoScene.STATUS_GENERATING_VIDEO -> StatusConfig("视频生成中", VideoColors.StatusGenerating, Icons.Default.VideoLibrary)
        VideoScene.STATUS_COMPLETED -> StatusConfig("已完成", VideoColors.StatusCompleted, Icons.Default.CheckCircle)
        VideoScene.STATUS_FAILED -> StatusConfig("失败", VideoColors.StatusFailed, Icons.Default.Error)
        VideoScene.STATUS_SKIPPED -> StatusConfig("已跳过", VideoColors.StatusDraft, Icons.Default.SkipNext)
        else -> StatusConfig(status, VideoColors.StatusDraft, Icons.Default.HourglassEmpty)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = config.color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = config.color
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = config.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = config.color
            )
        }
    }
}

private data class StatusConfig(
    val label: String,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun VideoProgressIndicator(
    progress: Float,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(48.dp),
                color = when (status) {
                    VideoScene.STATUS_COMPLETED -> VideoColors.StatusCompleted
                    VideoScene.STATUS_FAILED -> VideoColors.StatusFailed
                    else -> VideoColors.Primary
                },
                strokeWidth = 3.dp
            )

            if (status == VideoScene.STATUS_COMPLETED) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = VideoColors.StatusCompleted,
                    modifier = Modifier.size(24.dp)
                )
            } else if (status == VideoScene.STATUS_FAILED) {
                Icon(
                    Icons.Default.Error,
                    null,
                    tint = VideoColors.StatusFailed,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "${(progress * 100).toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VideoColors.OnSurface
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        content()
    }
}

@Composable
fun VideoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it, color = VideoColors.OnSurfaceVariant) } },
        placeholder = placeholder?.let { { Text(it, color = VideoColors.OnSurfaceVariant) } },
        singleLine = singleLine,
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VideoColors.Primary,
            unfocusedBorderColor = VideoColors.SurfaceVariant,
            cursorColor = VideoColors.Primary,
            focusedContainerColor = VideoColors.Surface,
            unfocusedContainerColor = VideoColors.Surface,
            focusedLabelColor = VideoColors.Primary
        )
    )
}

@Composable
fun VideoButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    icon: ImageVector? = null
) {
    val background = if (primary) VideoColors.Primary else VideoColors.SurfaceVariant
    val textColor = if (primary) VideoColors.OnPrimary else VideoColors.OnSurface

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = textColor,
            disabledContainerColor = background.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ExpandableSection(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .background(VideoColors.SurfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.Primary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SceneCard(
    scene: VideoScene,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${scene.order + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = VideoColors.Primary,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(status = scene.videoStatus)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = scene.title,
                style = MaterialTheme.typography.titleMedium,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = scene.summary,
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.OnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VideoColors.SurfaceVariant
                ) {
                    Text(
                        text = "⏱ ${scene.durationSeconds}s",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VideoColors.Primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = scene.shotType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.Primary
                    )
                }

                if (scene.characterIds.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VideoColors.Secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "👥 ${scene.characterIds.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.Secondary
                        )
                    }
                }
            }

            if (scene.videoStatus == VideoScene.STATUS_GENERATING_VIDEO) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (0.3f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = VideoColors.Primary,
                    trackColor = VideoColors.SurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Bold
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = VideoColors.Primary,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(VideoColors.SurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VideoColors.OnSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Bold
        )

        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VideoColors.OnSurfaceVariant
            )
        }

        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
