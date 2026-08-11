package io.legado.app.video.ui.pipeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.pipeline.EpisodePlan
import io.legado.app.video.pipeline.WorldBuilding
import io.legado.app.video.ui.theme.VideoColors

/**
 * MultiEpisodeScreen - 多集编排界面
 *
 * 借鉴 ArcReel 的多集编排设计哲学：
 * - 横向显示各集进度与状态
 * - 世界观/角色连续性可视化
 * - 单集快速定位与跳转
 * - 批量生产控制
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiEpisodeScreen(
    projectName: String,
    episodes: List<EpisodePlan>,
    worldBuilding: WorldBuilding? = null,
    characterContinuity: Map<String, Float> = emptyMap(),
    onBack: () -> Unit,
    onEpisodeClick: (EpisodePlan) -> Unit,
    onPlanEpisodes: () -> Unit = {},
    onGenerateAll: () -> Unit = {},
    onSetWorldBuilding: () -> Unit = {}
) {
    var showWorldBuildingDialog by remember { mutableStateOf(false) }
    var expandedEpisode by remember { mutableStateOf<Int?>(null) }

    val completedEpisodes = episodes.count { true }
    val totalEpisodes = episodes.size
    val overallProgress = if (totalEpisodes > 0) completedEpisodes.toFloat() / totalEpisodes else 0f
    val continuityScore = if (characterContinuity.isNotEmpty()) {
        characterContinuity.values.average().toFloat()
    } else 1.0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("多集编排") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showWorldBuildingDialog = true }) {
                        Icon(Icons.Default.Public, null)
                    }
                    IconButton(onClick = onGenerateAll) {
                        Icon(Icons.Default.PlayArrow, null)
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                null,
                                tint = VideoColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                projectName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EpisodeSummaryCard(
                                label = "总集数",
                                value = totalEpisodes.toString(),
                                color = VideoColors.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            EpisodeSummaryCard(
                                label = "角色连续性",
                                value = "${"%.0f".format(continuityScore * 100)}%",
                                color = when {
                                    continuityScore >= 0.9f -> VideoColors.StatusCompleted
                                    continuityScore >= 0.7f -> VideoColors.StatusGenerating
                                    else -> VideoColors.StatusFailed
                                },
                                modifier = Modifier.weight(1f)
                            )
                            EpisodeSummaryCard(
                                label = "世界观",
                                value = if (worldBuilding != null) "已设定" else "未设定",
                                color = if (worldBuilding != null) VideoColors.StatusCompleted else VideoColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = VideoColors.Primary,
                            trackColor = VideoColors.SurfaceVariant,
                        )
                        Text(
                            "总体进度: ${"%.0f".format(overallProgress * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (worldBuilding != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Public, null, tint = VideoColors.Primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "世界观设定",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "时代: ${worldBuilding.era} | 地点: ${worldBuilding.location}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "风格: ${worldBuilding.visualStyle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.TextSecondary
                            )
                            if (worldBuilding.coreThemes.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    worldBuilding.coreThemes.take(3).forEach { theme ->
                                        SuggestionChip(onClick = {}, label = { Text(theme, fontSize = 10.sp) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "剧集列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = onPlanEpisodes,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI 规划", fontSize = 12.sp)
                    }
                }
            }

            if (episodes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = VideoColors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "尚未规划剧集",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "使用 AI 自动规划剧集结构",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VideoColors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onPlanEpisodes) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(4.dp))
                            Text("开始规划")
                        }
                    }
                }
            } else {
                itemsIndexed(episodes) { index, episode ->
                    EpisodeCard(
                        episode = episode,
                        index = index,
                        isExpanded = expandedEpisode == index,
                        onClick = {
                            expandedEpisode = if (expandedEpisode == index) null else index
                        },
                        onGenerate = { onEpisodeClick(episode) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showWorldBuildingDialog) {
        WorldBuildingDialog(
            onDismiss = { showWorldBuildingDialog = false },
            onSave = { /* world building saved */ }
        )
    }
}

@Composable
private fun EpisodeSummaryCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 10.sp,
                color = VideoColors.TextSecondary
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodePlan,
    index: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            VideoColors.Primary.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${episode.index}",
                        fontWeight = FontWeight.Bold,
                        color = VideoColors.Primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        episode.theme,
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "规划中",
                    style = MaterialTheme.typography.labelSmall,
                    color = VideoColors.StatusAnalyzing
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = VideoColors.Border)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "剧情概要",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        episode.summary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (episode.keyCharacters.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "关键角色",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            episode.keyCharacters.take(5).forEach { character ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(character, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    episode.cliffhanger?.let { cliff ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                null,
                                tint = VideoColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "悬念: $cliff",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.Primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onGenerate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始生产", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("编辑", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldBuildingDialog(
    onDismiss: () -> Unit,
    onSave: (WorldBuilding) -> Unit
) {
    var era by remember { mutableStateOf("古代") }
    var location by remember { mutableStateOf("中原") }
    var socialContext by remember { mutableStateOf("封建社会") }
    var technologyLevel by remember { mutableStateOf("冷兵器时代") }
    var culturalStyle by remember { mutableStateOf("东方文化") }
    var visualStyle by remember { mutableStateOf("guofeng_wuxia") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("世界观设定") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = era,
                        onValueChange = { era = it },
                        label = { Text("时代背景") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("地点") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = socialContext,
                        onValueChange = { socialContext = it },
                        label = { Text("社会背景") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = technologyLevel,
                        onValueChange = { technologyLevel = it },
                        label = { Text("科技水平") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = culturalStyle,
                        onValueChange = { culturalStyle = it },
                        label = { Text("文化风格") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = visualStyle,
                        onValueChange = { visualStyle = it },
                        label = { Text("视觉风格") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    WorldBuilding(
                        worldId = "world_${System.currentTimeMillis()}",
                        projectId = "",
                        era = era,
                        location = location,
                        socialContext = socialContext,
                        technologyLevel = technologyLevel,
                        culturalStyle = culturalStyle,
                        coreThemes = emptyList(),
                        visualStyle = visualStyle,
                        colorPalette = emptyList(),
                        timeline = ""
                    )
                )
            }) {
                Text("保存世界观")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
