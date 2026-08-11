package io.legado.app.video.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateBrowseScreen(
    onSelectTemplate: (WorkflowTemplate) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<TemplateCategory?>(null) }
    var selectedDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showTemplateDetail by remember { mutableStateOf<WorkflowTemplate?>(null) }

    val filteredTemplates = remember(selectedCategory, selectedDifficulty, searchQuery) {
        var templates = WorkflowTemplateManager.getBuiltInTemplates()
        if (selectedCategory != null) {
            templates = templates.filter { it.category == selectedCategory }
        }
        if (selectedDifficulty != null) {
            templates = templates.filter { it.difficulty == selectedDifficulty }
        }
        if (searchQuery.isNotBlank()) {
            templates = WorkflowTemplateManager.searchTemplates(searchQuery)
        }
        templates
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "工作流模板",
                        color = VideoColors.OnBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = VideoColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VideoColors.Background)
            )
        },
        containerColor = VideoColors.Background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val brush = Brush.radialGradient(
                    colors = listOf(
                        VideoColors.Primary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 4, size.height / 4),
                    radius = size.width / 3
                )
                drawRect(brush)

                val brush2 = Brush.radialGradient(
                    colors = listOf(
                        VideoColors.Secondary.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.8f),
                    radius = size.width / 4
                )
                drawRect(brush2)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "✨ 选择适合你项目的模板",
                            style = MaterialTheme.typography.titleSmall,
                            color = VideoColors.OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        VideoTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "🔍 搜索模板名称或描述...",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))
                    }
                }

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300, delayMillis = 100)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📂 分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = VideoColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            AnimatedVisibility(
                                visible = selectedCategory != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                TextButton(onClick = { selectedCategory = null }) {
                                    Text("清除", color = VideoColors.Primary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("全部", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VideoColors.Primary,
                                        selectedLabelColor = VideoColors.OnPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            items(TemplateCategory.values().toList()) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(getCategoryDisplayName(category), fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VideoColors.Primary,
                                        selectedLabelColor = VideoColors.OnPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300, delayMillis = 150)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ 难度",
                                style = MaterialTheme.typography.labelMedium,
                                color = VideoColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            AnimatedVisibility(
                                visible = selectedDifficulty != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                TextButton(onClick = { selectedDifficulty = null }) {
                                    Text("清除", color = VideoColors.Primary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedDifficulty == null,
                                    onClick = { selectedDifficulty = null },
                                    label = { Text("全部", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VideoColors.Primary,
                                        selectedLabelColor = VideoColors.OnPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            items(Difficulty.values().toList()) { difficulty ->
                                FilterChip(
                                    selected = selectedDifficulty == difficulty,
                                    onClick = { selectedDifficulty = difficulty },
                                    label = { Text(getDifficultyDisplayName(difficulty), fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = getDifficultyColor(difficulty),
                                        selectedLabelColor = VideoColors.OnPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "发现 ${filteredTemplates.size} 个模板",
                        style = MaterialTheme.typography.titleMedium,
                        color = VideoColors.OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (filteredTemplates.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VideoColors.Primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "精选",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = filteredTemplates.isEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    EmptyTemplateState()
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTemplates) { template ->
                        var animated by remember { mutableStateOf(false) }
                        LaunchedEffect(template.id) {
                            kotlinx.coroutines.delay(50)
                            animated = true
                        }

                        AnimatedVisibility(
                            visible = animated,
                            enter = fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 400,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            ) + scaleIn(
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 400
                                )
                            ),
                            exit = fadeOut()
                        ) {
                            TemplateCardGlass(
                                template = template,
                                onClick = { showTemplateDetail = template }
                            )
                        }
                    }
                }
            }
        }

        showTemplateDetail?.let { template ->
            TemplateDetailDialog(
                template = template,
                onDismiss = { showTemplateDetail = null },
                onApply = {
                    onSelectTemplate(template)
                    showTemplateDetail = null
                }
            )
        }
    }
}

@Composable
private fun TemplateCardGlass(
    template: WorkflowTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VideoColors.GradientStart.copy(alpha = 0.2f),
                                VideoColors.GradientEnd.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = template.emoji,
                        fontSize = 32.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (template.isPremium) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VideoColors.Premium.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "👑 PRO",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.Premium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VideoColors.Primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = template.category.name.replace("_", " ").lowercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.Primary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.OnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = getDifficultyColor(template.difficulty).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = getDifficultyShortName(template.difficulty),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = getDifficultyColor(template.difficulty),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = VideoColors.OnSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = template.estimatedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTemplateState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(VideoColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SearchOff,
                null,
                tint = VideoColors.OnSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "没有找到匹配的模板",
            style = MaterialTheme.typography.titleMedium,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "尝试调整筛选条件或搜索关键词",
            style = MaterialTheme.typography.bodyMedium,
            color = VideoColors.OnSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDetailDialog(
    template: WorkflowTemplate,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VideoColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VideoColors.GradientStart.copy(alpha = 0.3f),
                                VideoColors.GradientEnd.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = template.emoji,
                                fontSize = 32.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭", tint = VideoColors.OnSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VideoColors.OnSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(
                    label = "难度",
                    value = getDifficultyDisplayName(template.difficulty),
                    color = getDifficultyColor(template.difficulty),
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    label = "预计耗时",
                    value = template.estimatedDuration,
                    color = VideoColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    label = "章节策略",
                    value = "${template.novelConfig.chaptersPerScene}章",
                    color = VideoColors.Secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "⚙️ 配置预览",
                style = MaterialTheme.typography.titleMedium,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    ConfigRow("📖 章节策略", "${template.novelConfig.chaptersPerScene}章/分镜")
                    ConfigRow("📝 分镜长度", "${template.novelConfig.maxSceneLength}字")
                    ConfigRow("🎨 视觉风格", template.stylePreset)
                    ConfigRow("📐 画面比例", template.novelConfig.aspectRatio)
                    ConfigRow("🎯 分辨率", template.novelConfig.targetResolution)
                    ConfigRow("👤 角色识别", if (template.novelConfig.includeCharacters) "✅ 开启" else "❌ 关闭")
                    ConfigRow("🎬 输出分辨率", "${template.exportConfig.resolutionWidth}×${template.exportConfig.resolutionHeight}")
                    ConfigRow("💾 码率", "${template.exportConfig.bitrate / 1_000_000}Mbps")
                    ConfigRow("🔄 转场效果", if (template.exportConfig.includeTransitions) "✅ 开启" else "❌ 关闭")
                    ConfigRow("📦 水印", if (template.exportConfig.enableWatermark) "✅ 启用" else "❌ 禁用")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "💡 使用建议",
                style = MaterialTheme.typography.titleMedium,
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                template.tips.forEachIndexed { index, tip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(VideoColors.SurfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(VideoColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = VideoColors.OnSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VideoColors.OnSurfaceVariant
                    )
                ) {
                    Text("取消", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = VideoColors.OnPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "使用此模板",
                        color = VideoColors.OnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VideoColors.OnSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = VideoColors.OnSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun getCategoryDisplayName(category: TemplateCategory): String = when (category) {
    TemplateCategory.NOVEL_ADAPTATION -> "小说改编"
    TemplateCategory.SHORT_FILM -> "短片"
    TemplateCategory.MUSIC_VIDEO -> "MV"
    TemplateCategory.TRAILER -> "预告片"
    TemplateCategory.TUTORIAL -> "教程"
    TemplateCategory.SOCIAL_MEDIA -> "社交媒体"
    TemplateCategory.CINEMATIC -> "电影级"
}

private fun getDifficultyDisplayName(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.BEGINNER -> "新手"
    Difficulty.INTERMEDIATE -> "进阶"
    Difficulty.ADVANCED -> "高级"
    Difficulty.EXPERT -> "专家"
}

private fun getDifficultyShortName(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.BEGINNER -> "⭐"
    Difficulty.INTERMEDIATE -> "⭐⭐"
    Difficulty.ADVANCED -> "⭐⭐⭐"
    Difficulty.EXPERT -> "⭐⭐⭐⭐"
}

private fun getDifficultyColor(difficulty: Difficulty): Color = when (difficulty) {
    Difficulty.BEGINNER -> VideoColors.Success
    Difficulty.INTERMEDIATE -> VideoColors.Primary
    Difficulty.ADVANCED -> VideoColors.Warning
    Difficulty.EXPERT -> VideoColors.Error
}
