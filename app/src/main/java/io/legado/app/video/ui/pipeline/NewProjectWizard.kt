package io.legado.app.video.ui.pipeline

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import io.legado.app.video.config.ProjectDefaults
import io.legado.app.video.config.ProjectType
import io.legado.app.video.config.QualityPreset
import io.legado.app.video.config.ConfigurationWizard

/**
 * NewProjectWizard - 新项目创建向导
 *
 * 借鉴 ArcReel 的项目创建流程：
 * - 分步引导
 * - 智能默认配置
 * - 实时预览
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectWizard(
    onDismiss: () -> Unit,
    onCreate: (WizardResult) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var projectType by remember { mutableStateOf(ProjectType.NOVEL_ADAPTATION) }
    var qualityPreset by remember { mutableStateOf(QualityPreset.HIGH_QUALITY) }
    var selectedProviders by remember { mutableStateOf(setOf("Agnes")) }
    var enableParallel by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var projectName by remember { mutableStateOf("") }

    val steps = listOf(
        WizardStep("项目类型", "选择您要创建的项目类型"),
        WizardStep("质量等级", "选择生成质量等级"),
        WizardStep("AI 服务商", "选择用于生成的 AI 服务商"),
        WizardStep("生成设置", "配置生成参数"),
        WizardStep("确认创建", "确认并创建项目")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建新项目") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / steps.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = steps[currentStep].title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = steps[currentStep].description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopStart
            ) {
                when (currentStep) {
                    0 -> ProjectTypeSelection(
                        selected = projectType,
                        onSelect = { projectType = it }
                    )
                    1 -> QualityPresetSelection(
                        selected = qualityPreset,
                        onSelect = { qualityPreset = it }
                    )
                    2 -> ProviderSelection(
                        selected = selectedProviders,
                        onToggle = { provider ->
                            selectedProviders = if (provider in selectedProviders) {
                                selectedProviders - provider
                            } else {
                                selectedProviders + provider
                            }
                        }
                    )
                    3 -> GenerationSettings(
                        enableParallel = enableParallel,
                        onParallelChange = { enableParallel = it },
                        aspectRatio = aspectRatio,
                        onAspectRatioChange = { aspectRatio = it },
                        projectName = projectName,
                        onProjectNameChange = { projectName = it }
                    )
                    4 -> ConfirmationStep(
                        projectType = projectType,
                        qualityPreset = qualityPreset,
                        providers = selectedProviders,
                        enableParallel = enableParallel,
                        aspectRatio = aspectRatio,
                        projectName = projectName
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (currentStep > 0) currentStep-- else onDismiss() },
                    enabled = currentStep > 0 || currentStep == 0
                ) {
                    Icon(
                        if (currentStep == 0) Icons.Default.Close else Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (currentStep == 0) "取消" else "上一步")
                }

                if (currentStep < steps.lastIndex) {
                    FilledTonalButton(
                        onClick = { currentStep++ }
                    ) {
                        Text("下一步")
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            onCreate(
                                WizardResult(
                                    projectName = projectName.ifBlank { "${projectType.displayName} 项目" },
                                    projectType = projectType,
                                    qualityPreset = qualityPreset,
                                    providers = selectedProviders.toList(),
                                    enableParallel = enableParallel,
                                    aspectRatio = aspectRatio
                                )
                            )
                        },
                        enabled = projectName.isNotBlank() || currentStep == 4
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("创建项目")
                    }
                }
            }
        }
    }
}

private data class WizardStep(val title: String, val description: String)

@Composable
private fun ProjectTypeSelection(
    selected: ProjectType,
    onSelect: (ProjectType) -> Unit
) {
    val types = ProjectType.entries

    LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(types.size) { index ->
            val type = types[index]
            val defaults = ProjectConfigPresets.getDefaults(type)
            val isSelected = type == selected

            Card(
                onClick = { onSelect(type) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = type.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "推荐: ${defaults.recommendedResolution}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityPresetSelection(
    selected: QualityPreset,
    onSelect: (QualityPreset) -> Unit
) {
    val presets = QualityPreset.entries

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = preset == selected
            ListItem(
                headlineContent = {
                    Text(
                        text = preset.name.replace("_", " "),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                supportingContent = {
                    Text(
                        text = qualityDescription(preset),
                        maxLines = 2
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(preset) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(preset) }
            )
        }
    }
}

private fun qualityDescription(preset: QualityPreset): String = when (preset) {
    QualityPreset.QUICK_PROTOTYPE -> "快速预览，速度优先，适合快速迭代创意"
    QualityPreset.STANDARD -> "标准质量，平衡速度和质量"
    QualityPreset.HIGH_QUALITY -> "高质量，更精细的生成细节"
    QualityPreset.PRODUCTION -> "生产级质量，适合正式输出"
    QualityPreset.CINEMATIC -> "电影级质量，最高画质输出"
}

@Composable
private fun ProviderSelection(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val providers = listOf("Agnes", "NewAPI", "Doubao", "Kling", "Seedance", "Grok", "Runway", "DALL·E")

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        providers.forEach { provider ->
            val isSelected = provider in selected
            ListItem(
                headlineContent = { Text(provider) },
                leadingContent = {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle(provider) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(provider) }
            )
        }
    }
}

@Composable
private fun GenerationSettings(
    enableParallel: Boolean,
    onParallelChange: (Boolean) -> Unit,
    aspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    projectName: String,
    onProjectNameChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = projectName,
            onValueChange = onProjectNameChange,
            label = { Text("项目名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            text = "画面比例",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        val ratios = listOf("16:9", "9:16", "1:1", "4:3")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratios.forEach { ratio ->
                FilterChip(
                    selected = ratio == aspectRatio,
                    onClick = { onAspectRatioChange(ratio) },
                    label = { Text(ratio) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "并行生成",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "同时生成多个分镜，更快完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enableParallel,
                onCheckedChange = onParallelChange
            )
        }
    }
}

@Composable
private fun ConfirmationStep(
    projectType: ProjectType,
    qualityPreset: QualityPreset,
    providers: Set<String>,
    enableParallel: Boolean,
    aspectRatio: String,
    projectName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "项目配置预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ConfirmationRow("项目类型", projectType.displayName)
            ConfirmationRow("质量等级", qualityPreset.name.replace("_", " "))
            ConfirmationRow("AI 服务商", providers.joinToString(", "))
            ConfirmationRow("并行生成", if (enableParallel) "已启用" else "已禁用")
            ConfirmationRow("画面比例", aspectRatio)
            ConfirmationRow("项目名称", projectName.ifBlank { "${projectType.displayName} 项目" })
        }
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

data class WizardResult(
    val projectName: String,
    val projectType: ProjectType,
    val qualityPreset: QualityPreset,
    val providers: List<String>,
    val enableParallel: Boolean,
    val aspectRatio: String
)
