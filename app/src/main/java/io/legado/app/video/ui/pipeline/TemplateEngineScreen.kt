package io.legado.app.video.ui.pipeline

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.pipeline.ProjectTemplate
import io.legado.app.video.pipeline.TemplateCategory
import io.legado.app.video.pipeline.TemplateEngine
import io.legado.app.video.pipeline.TemplateApplyResult
import io.legado.app.video.ui.theme.VideoColors

/**
 * TemplateEngineScreen - 模板选择界面
 *
 * 功能：
 * - 分类浏览项目模板
 * - 搜索模板
 * - 预览模板配置
 * - 一键应用模板创建项目
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEngineScreen(
    onDismiss: () -> Unit,
    onTemplateSelected: (TemplateApplyResult) -> Unit
) {
    val templates = remember { TemplateEngine.getBuiltInTemplates() }
    val categories = remember { TemplateEngine.getCategories() }
    var selectedCategory by remember { mutableStateOf<TemplateCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    val filteredTemplates = when {
        searchQuery.isNotBlank() -> TemplateEngine.searchTemplates(searchQuery)
        selectedCategory != null -> templates.filter { it.category == selectedCategory }
        else -> templates
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择项目模板") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Surface
                )
            )
        },
        containerColor = VideoColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索模板...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("全部") }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredTemplates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = VideoColors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "没有找到匹配的模板",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VideoColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTemplates) { template ->
                        TemplateCard(
                            template = template,
                            onClick = {
                                selectedTemplate = template
                                showPreview = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPreview && selectedTemplate != null) {
        TemplatePreviewDialog(
            template = selectedTemplate!!,
            onDismiss = { showPreview = false },
            onApply = {
                val result = TemplateEngine().applyTemplate(selectedTemplate!!, "")
                onTemplateSelected(result)
            }
        )
    }
}

@Composable
private fun TemplateCard(
    template: ProjectTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        color = templateColor(template),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    templateIcon(template.category),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(template.category.displayName, fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = VideoColors.SurfaceVariant
                    )
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${template.defaultSceneCount}场景", fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = VideoColors.SurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun TemplatePreviewDialog(
    template: ProjectTemplate,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(templateIcon(template.category), null)
                Spacer(Modifier.width(8.dp))
                Text(template.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(template.description, color = VideoColors.TextSecondary)

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = VideoColors.Border)
                Spacer(Modifier.height(16.dp))

                TemplateInfoRow("项目类型", template.category.displayName)
                TemplateInfoRow("视觉风格", template.visualStyle.styleName)
                TemplateInfoRow("旁白风格", template.narrationStyle.displayName)
                TemplateInfoRow("分辨率", template.targetResolution)
                TemplateInfoRow("比例", template.targetAspectRatio)
                TemplateInfoRow("默认场景数", "${template.defaultSceneCount}")
                TemplateInfoRow("单场景时长", "${template.sceneDurationSeconds}秒")
                TemplateInfoRow("背景音乐", template.bgmStyle.displayName)

                if (template.tags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("标签:", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(4.dp))
                Text("应用模板")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TemplateInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = VideoColors.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun templateColor(template: ProjectTemplate): Color = when (template.category) {
    TemplateCategory.NOVEL_ADAPTATION -> Color(0xFF667EEA)
    TemplateCategory.COMIC_ADAPTATION -> Color(0xFFF093FB)
    TemplateCategory.ORIGINAL_STORY -> Color(0xFF4FACFE)
    TemplateCategory.DOCUMENTARY -> Color(0xFF43E97B)
    TemplateCategory.EDUCATIONAL -> Color(0xFF30CFD0)
    TemplateCategory.MARKETING -> Color(0xFFFF6B6B)
    TemplateCategory.SOCIAL_MEDIA -> Color(0xFFFFD93D)
    TemplateCategory.ENTERTAINMENT -> Color(0xFFA18CD1)
}

private fun templateIcon(category: TemplateCategory): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    TemplateCategory.NOVEL_ADAPTATION -> Icons.Default.MenuBook
    TemplateCategory.COMIC_ADAPTATION -> Icons.Default.Book
    TemplateCategory.ORIGINAL_STORY -> Icons.Default.AutoStories
    TemplateCategory.DOCUMENTARY -> Icons.Default.VideoLibrary
    TemplateCategory.EDUCATIONAL -> Icons.Default.School
    TemplateCategory.MARKETING -> Icons.Default.LocalOffer
    TemplateCategory.SOCIAL_MEDIA -> Icons.Default.Share
    TemplateCategory.ENTERTAINMENT -> Icons.Default.SportsEsports
}
