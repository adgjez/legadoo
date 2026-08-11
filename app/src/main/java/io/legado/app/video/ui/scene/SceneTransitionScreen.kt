package io.legado.app.video.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyVerticalGrid
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
import io.legado.app.video.pipeline.EasingFunction
import io.legado.app.video.pipeline.TransitionConfig
import io.legado.app.video.pipeline.TransitionDirection
import io.legado.app.video.pipeline.TransitionType
import io.legado.app.video.ui.theme.VideoColors

/**
 * SceneTransitionScreen - 场景转场配置界面
 *
 * 功能：
 * - 为场景间选择转场效果
 * - 调整转场参数（时长、强度、方向、缓动）
 * - 使用智能推荐
 * - 保存为转场预设
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneTransitionScreen(
    fromSceneTitle: String,
    toSceneTitle: String,
    initialConfig: TransitionConfig? = null,
    onBack: () -> Unit,
    onConfirm: (TransitionConfig) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(initialConfig?.type ?: TransitionType.CROSS_FADE)
    }
    var durationMs by remember {
        mutableFloatStateOf((initialConfig?.durationMs ?: 500L).toFloat())
    }
    var intensity by remember {
        mutableFloatStateOf(initialConfig?.intensity ?: 1.0f)
    }
    var selectedDirection by remember {
        mutableStateOf(initialConfig?.direction ?: TransitionDirection.AUTO)
    }
    var selectedEasing by remember {
        mutableStateOf(initialConfig?.easing ?: EasingFunction.EASE_IN_OUT)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("场景转场配置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fromSceneTitle, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "当前场景",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.TextSecondary
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = VideoColors.Primary
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(toSceneTitle, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "下一场景",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.TextSecondary
                        )
                    }
                }
            }

            Text(
                "转场效果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(TransitionType.values()) { type ->
                    TransitionTypeChip(
                        type = type,
                        isSelected = selectedType == type,
                        onClick = { selectedType = type }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "转场参数",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "时长: ${durationMs.toInt()}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = durationMs,
                        onValueChange = { durationMs = it },
                        valueRange = 0f..2000f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = VideoColors.Primary,
                            activeTrackColor = VideoColors.Primary
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "强度: ${"%.1f".format(intensity)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0.1f..2.0f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = VideoColors.Primary,
                            activeTrackColor = VideoColors.Primary
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("方向", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TransitionDirection.values().toList()) { direction ->
                            FilterChip(
                                selected = selectedDirection == direction,
                                onClick = { selectedDirection = direction },
                                label = { Text(direction.name.replace("_", " ")) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("缓动函数", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(EasingFunction.values().toList()) { easing ->
                            FilterChip(
                                selected = selectedEasing == easing,
                                onClick = { selectedEasing = easing },
                                label = { Text(easing.name) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onConfirm(
                            TransitionConfig(
                                type = selectedType,
                                durationMs = durationMs.toLong(),
                                intensity = intensity,
                                direction = selectedDirection,
                                easing = selectedEasing
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("应用转场")
                }
            }
        }
    }
}

@Composable
private fun TransitionTypeChip(
    type: TransitionType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) VideoColors.Primary else VideoColors.SurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                transitionIcon(type),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) Color.White else VideoColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else VideoColors.OnSurface,
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}

private fun transitionIcon(type: TransitionType) = when (type) {
    TransitionType.FADE -> Icons.Default.Remove
    TransitionType.DISSOLVE -> Icons.Default.BlurOn
    TransitionType.SLIDE_LEFT -> Icons.Default.KeyboardArrowLeft
    TransitionType.SLIDE_RIGHT -> Icons.Default.KeyboardArrowRight
    TransitionType.SLIDE_UP -> Icons.Default.KeyboardArrowUp
    TransitionType.SLIDE_DOWN -> Icons.Default.KeyboardArrowDown
    TransitionType.ZOOM_IN -> Icons.Default.ZoomIn
    TransitionType.ZOOM_OUT -> Icons.Default.ZoomOut
    TransitionType.ROTATE -> Icons.Default.RotateRight
    TransitionType.WIPE -> Icons.Default.CleaningServices
    TransitionType.FLASH -> Icons.Default.FlashOn
    TransitionType.BLUR -> Icons.Default.BlurLinear
    TransitionType.GLITCH -> Icons.Default.BugReport
    TransitionType.MORPH -> Icons.Default.AutoFixHigh
    TransitionType.CUT -> Icons.Default.ContentCut
    TransitionType.CROSS_FADE -> Icons.Default.AllInclusive
    TransitionType.PUSH -> Icons.Default.ArrowForward
    TransitionType.SPLIT -> Icons.Default.CallSplit
    TransitionType.PAGE_TURN -> Icons.Default.MenuBook
    TransitionType.ENERGY -> Icons.Default.Bolt
}
