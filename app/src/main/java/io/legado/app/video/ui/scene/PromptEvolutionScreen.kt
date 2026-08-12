package io.legado.app.video.ui.scene

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.pipeline.PromptEvolutionEngine.ChangeType
import io.legado.app.video.pipeline.PromptEvolutionEngine.EvolutionResult
import io.legado.app.video.pipeline.PromptEvolutionEngine.EvolutionTechnique
import io.legado.app.video.pipeline.PromptEvolutionEngine.PromptChange
import io.legado.app.video.pipeline.PromptTemplates
import io.legado.app.video.ui.theme.VideoColors

/**
 * PromptEvolutionScreen - 提示词进化可视化编辑器
 *
 * 借鉴 ArcReel 的提示词进化系统设计哲学：
 * - 可视化 Prompt 演进过程
 * - 技术路线选择（6种进化技术）
 * - 实时质量评估
 * - Prompt 模板库快速应用
 * - 迭代历史追踪
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEvolutionScreen(
    originalPrompt: String,
    sceneTitle: String,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    initialResult: EvolutionResult? = null
) {
    var currentPrompt by remember { mutableStateOf(originalPrompt) }
    var selectedTechniques by remember {
        mutableStateOf(setOf(EvolutionTechnique.VISUAL_ENRICHMENT))
    }
    var iteration by remember { mutableIntStateOf(0) }
    var qualityScore by remember { mutableFloatStateOf(0.5f) }
    var isEvolving by remember { mutableStateOf(false) }
    var changeHistory by remember { mutableStateOf<List<PromptChange>>(emptyList()) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val templateKeys = PromptTemplates.listTemplates()

    initialResult?.let { result ->
        LaunchedEffect(result) {
            currentPrompt = result.evolvedPrompt
            iteration = result.iterationsUsed
            qualityScore = 0.75f
            changeHistory = result.changes
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提示词进化器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showTemplatePicker = true }) {
                        Icon(Icons.Default.AutoAwesome, null)
                    }
                    IconButton(onClick = { showAdvancedSettings = !showAdvancedSettings }) {
                        Icon(Icons.Default.Tune, null)
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
                                Icons.Default.MovieCreation,
                                null,
                                tint = VideoColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                sceneTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "质量评分: ${"%.0f".format(qualityScore * 100)}",
                                color = when {
                                    qualityScore >= 0.85f -> VideoColors.StatusCompleted
                                    qualityScore >= 0.65f -> VideoColors.StatusGenerating
                                    else -> VideoColors.StatusFailed
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "迭代: $iteration",
                                color = VideoColors.TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "当前提示词",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = currentPrompt,
                            onValueChange = {
                                currentPrompt = it
                                qualityScore = estimateLocalQuality(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 200.dp),
                            placeholder = { Text("在此输入或编辑提示词...") },
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 8,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${currentPrompt.length} 字符",
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.TextSecondary
                            )
                            Text(
                                "当前: ${"%.0f".format(qualityScore * 100)}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    qualityScore >= 0.85f -> VideoColors.StatusCompleted
                                    qualityScore >= 0.65f -> VideoColors.StatusGenerating
                                    else -> VideoColors.StatusFailed
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "进化技术",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvolutionTechnique.values().forEach { technique ->
                        val isSelected = technique in selectedTechniques
                        val description = techniqueDescription(technique)
                        val icon = techniqueIcon(technique)
                        val color = techniqueColor(technique)

                        TechniqueCard(
                            technique = technique,
                            isSelected = isSelected,
                            description = description,
                            icon = icon,
                            color = color,
                            onToggle = {
                                selectedTechniques = if (isSelected) {
                                    selectedTechniques - technique
                                } else {
                                    selectedTechniques + technique
                                }
                            }
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = showAdvancedSettings,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "高级设置",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("目标质量: ${"%.0f".format(qualityScore * 100)}分")
                            Slider(
                                value = qualityScore,
                                onValueChange = { qualityScore = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = VideoColors.Primary,
                                    activeTrackColor = VideoColors.Primary
                                )
                            )
                        }
                    }
                }
            }

            item {
                if (changeHistory.isNotEmpty()) {
                    Text(
                        "变更历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        changeHistory.takeLast(5).reversed().forEach { change ->
                            ChangeHistoryItem(change)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    BottomAppBar(
        containerColor = VideoColors.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { currentPrompt = originalPrompt },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("重置")
            }
            FilledTonalButton(
                onClick = {
                    val evolved = applyEvolution(
                        currentPrompt,
                        selectedTechniques.toList(),
                        qualityScore
                    )
                    val changes = diffChanges(currentPrompt, evolved)
                    currentPrompt = evolved
                    iteration++
                    qualityScore = estimateLocalQuality(evolved)
                    changeHistory = changeHistory + changes
                },
                modifier = Modifier.weight(1f),
                enabled = !isEvolving && selectedTechniques.isNotEmpty()
            ) {
                if (isEvolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = VideoColors.Primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("进化")
                }
            }
            Button(
                onClick = { onApply(currentPrompt) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("应用")
            }
        }
    }

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            containerColor = VideoColors.Surface,
            title = { Text("选择 Prompt 模板") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templateKeys) { key ->
                        val template = PromptTemplates.getTemplate(key) ?: return@items
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentPrompt = template
                                    showTemplatePicker = false
                                },
                            colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    key.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    template.take(120) + if (template.length > 120) "..." else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatePicker = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun TechniqueCard(
    technique: EvolutionTechnique,
    isSelected: Boolean,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                color.copy(alpha = 0.15f)
            } else {
                VideoColors.Surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { _ -> onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = color)
            )
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(technique.name.replace("_", " "), fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = VideoColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChangeHistoryItem(change: PromptChange) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    changeTypeIcon(change.type),
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = VideoColors.Primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    changeTypeLabel(change.type),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                change.reason,
                style = MaterialTheme.typography.labelSmall,
                color = VideoColors.TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                change.suggestion,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun estimateLocalQuality(prompt: String): Float {
    var score = 0.5f
    if (prompt.length >= 100) score += 0.15f
    if (prompt.length >= 200) score += 0.1f
    if (prompt.contains("cinematic", ignoreCase = true)) score += 0.05f
    if (prompt.contains("lighting", ignoreCase = true)) score += 0.05f
    if (prompt.contains("8K", ignoreCase = true)) score += 0.03f
    if (prompt.contains("masterpiece", ignoreCase = true)) score += 0.03f
    if (prompt.contains("composition", ignoreCase = true)) score += 0.03f
    return score.coerceAtMost(1.0f)
}

private fun applyEvolution(
    prompt: String,
    techniques: List<EvolutionTechnique>,
    currentQuality: Float
): String {
    var result = prompt
    techniques.forEach { technique ->
        val additions = when (technique) {
            EvolutionTechnique.VISUAL_ENRICHMENT -> listOf(
                "highly detailed", "intricate details", "masterpiece", "best quality"
            )
            EvolutionTechnique.STYLE_TRANSFER -> listOf(
                "cinematic style", "professional photography", "film-like quality"
            )
            EvolutionTechnique.COMPOSITION_BALANCING -> listOf(
                "rule of thirds", "balanced composition", "depth of field"
            )
            EvolutionTechnique.EMOTION_AMPLIFICATION -> listOf(
                "dramatic atmosphere", "emotional moment", "powerful scene"
            )
            EvolutionTechnique.SUBJECT_CLARIFICATION -> listOf(
                "prominent subject", "centered composition", "focused attention"
            )
            EvolutionTechnique.CINEMATIC_ENHANCEMENT -> listOf(
                "cinematic lighting", "dramatic shadows", "8K quality", "volumetric lighting"
            )
        }

        val existing = result.lowercase()
        val missing = additions.filter {
            !existing.contains(it.lowercase().split(" ")[0])
        }
        if (missing.isNotEmpty()) {
            result = buildString {
                append(result)
                append(". ")
                append(missing.take(3).joinToString(", "))
            }
        }
    }
    return result
}

private fun diffChanges(original: String, evolved: String): List<PromptChange> {
    val changes = mutableListOf<PromptChange>()
    val originalWords = original.lowercase().split(" ")
    val evolvedWords = evolved.lowercase().split(" ")
    val newWords = evolvedWords.filter { it !in originalWords && it.length > 2 }

    if (newWords.isNotEmpty()) {
        changes.add(
            PromptChange(
                iteration = 0,
                type = ChangeType.ADD_VISUAL_DETAIL,
                original = original.take(100),
                suggestion = newWords.take(4).joinToString(", "),
                reason = "自动进化: 添加视觉增强描述"
            )
        )
    }
    return changes
}

private fun techniqueDescription(technique: EvolutionTechnique) = when (technique) {
    EvolutionTechnique.VISUAL_ENRICHMENT -> "添加视觉细节描述（高度详细、杰作等）"
    EvolutionTechnique.STYLE_TRANSFER -> "添加风格参考（电影感、专业摄影等）"
    EvolutionTechnique.COMPOSITION_BALANCING -> "优化构图（三分法则、景深等）"
    EvolutionTechnique.EMOTION_AMPLIFICATION -> "放大情感氛围（戏剧性、情绪性等）"
    EvolutionTechnique.SUBJECT_CLARIFICATION -> "强化主体表现（居中构图、聚焦注意力等）"
    EvolutionTechnique.CINEMATIC_ENHANCEMENT -> "电影化增强（光影、8K质量、体积光等）"
}

private fun techniqueIcon(technique: EvolutionTechnique) = when (technique) {
    EvolutionTechnique.VISUAL_ENRICHMENT -> Icons.Default.AutoAwesome
    EvolutionTechnique.STYLE_TRANSFER -> Icons.Default.Style
    EvolutionTechnique.COMPOSITION_BALANCING -> Icons.Default.Crop
    EvolutionTechnique.EMOTION_AMPLIFICATION -> Icons.Default.Favorite
    EvolutionTechnique.SUBJECT_CLARIFICATION -> Icons.Default.Focus
    EvolutionTechnique.CINEMATIC_ENHANCEMENT -> Icons.Default.Movie
}

private fun techniqueColor(technique: EvolutionTechnique) = when (technique) {
    EvolutionTechnique.VISUAL_ENRICHMENT -> Color(0xFF667EEA)
    EvolutionTechnique.STYLE_TRANSFER -> Color(0xFFF093FB)
    EvolutionTechnique.COMPOSITION_BALANCING -> Color(0xFF4FACFE)
    EvolutionTechnique.EMOTION_AMPLIFICATION -> Color(0xFF43E97B)
    EvolutionTechnique.SUBJECT_CLARIFICATION -> Color(0xFFFF6B6B)
    EvolutionTechnique.CINEMATIC_ENHANCEMENT -> Color(0xFFFFD93D)
}

private fun changeTypeIcon(type: ChangeType) = when (type) {
    ChangeType.ADD_VISUAL_DETAIL -> Icons.Default.Visibility
    ChangeType.ADD_LIGHTING -> Icons.Default.Lightbulb
    ChangeType.ADD_COMPOSITION -> Icons.Default.Crop
    ChangeType.ADD_STYLE_REFERENCE -> Icons.Default.Style
    ChangeType.ADD_EMOTION -> Icons.Default.Favorite
    ChangeType.FIX_AMBIGUITY -> Icons.Default.Search
    ChangeType.REMOVE_REDUNDANCY -> Icons.Default.Delete
    ChangeType.ENHANCE_SUBJECT -> Icons.Default.Focus
    ChangeType.ADD_COLOR_PALETTE -> Icons.Default.Palette
    ChangeType.ADD_CAMERA_ANGLE -> Icons.Default.VideoCamera
}

private fun changeTypeLabel(type: ChangeType) = when (type) {
    ChangeType.ADD_VISUAL_DETAIL -> "添加视觉细节"
    ChangeType.ADD_LIGHTING -> "添加光影"
    ChangeType.ADD_COMPOSITION -> "优化构图"
    ChangeType.ADD_STYLE_REFERENCE -> "风格迁移"
    ChangeType.ADD_EMOTION -> "情感放大"
    ChangeType.FIX_AMBIGUITY -> "消除歧义"
    ChangeType.REMOVE_REDUNDANCY -> "精简冗余"
    ChangeType.ENHANCE_SUBJECT -> "强化主体"
    ChangeType.ADD_COLOR_PALETTE -> "添加色调"
    ChangeType.ADD_CAMERA_ANGLE -> "添加镜头角度"
}
