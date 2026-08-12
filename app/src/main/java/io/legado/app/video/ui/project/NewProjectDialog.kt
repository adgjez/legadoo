package io.legado.app.video.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, sourceType: String, sourceContent: String, genre: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(VideoProject.SOURCE_IDEA) }
    var ideaText by remember { mutableStateOf("") }
    var novelText by remember { mutableStateOf("") }
    var scriptText by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoColors.Surface,
        title = {
            Text(
                text = "创建新项目",
                color = VideoColors.OnSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VideoColors.Primary,
                        unfocusedBorderColor = VideoColors.SurfaceVariant,
                        cursorColor = VideoColors.Primary
                    )
                )
                
                Text(
                    text = "选择创作方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = VideoColors.OnSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceOption(
                        label = "创意",
                        icon = Icons.Default.AutoAwesome,
                        selected = selectedSource == VideoProject.SOURCE_IDEA,
                        onClick = { selectedSource = VideoProject.SOURCE_IDEA }
                    )
                    SourceOption(
                        label = "小说",
                        icon = Icons.Default.MenuBook,
                        selected = selectedSource == VideoProject.SOURCE_NOVEL,
                        onClick = { selectedSource = VideoProject.SOURCE_NOVEL }
                    )
                    SourceOption(
                        label = "剧本",
                        icon = Icons.Default.Description,
                        selected = selectedSource == VideoProject.SOURCE_SCRIPT,
                        onClick = { selectedSource = VideoProject.SOURCE_SCRIPT }
                    )
                }
                
                when (selectedSource) {
                    VideoProject.SOURCE_IDEA -> {
                        OutlinedTextField(
                            value = ideaText,
                            onValueChange = { ideaText = it },
                            label = { Text("描述你的创意") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VideoColors.Primary,
                                unfocusedBorderColor = VideoColors.SurfaceVariant,
                                cursorColor = VideoColors.Primary
                            )
                        )
                    }
                    VideoProject.SOURCE_NOVEL -> {
                        OutlinedTextField(
                            value = novelText,
                            onValueChange = { novelText = it },
                            label = { Text("粘贴小说内容") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VideoColors.Primary,
                                unfocusedBorderColor = VideoColors.SurfaceVariant,
                                cursorColor = VideoColors.Primary
                            )
                        )
                    }
                    VideoProject.SOURCE_SCRIPT -> {
                        OutlinedTextField(
                            value = scriptText,
                            onValueChange = { scriptText = it },
                            label = { Text("粘贴剧本内容") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VideoColors.Primary,
                                unfocusedBorderColor = VideoColors.SurfaceVariant,
                                cursorColor = VideoColors.Primary
                            )
                        )
                    }
                }
                
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("风格（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VideoColors.Primary,
                        unfocusedBorderColor = VideoColors.SurfaceVariant,
                        cursorColor = VideoColors.Primary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val content = when (selectedSource) {
                        VideoProject.SOURCE_IDEA -> ideaText
                        VideoProject.SOURCE_NOVEL -> novelText
                        VideoProject.SOURCE_SCRIPT -> scriptText
                        else -> ""
                    }
                    if (name.isNotBlank() && content.isNotBlank()) {
                        onCreate(name, selectedSource, content, genre)
                    }
                },
                enabled = name.isNotBlank() && when (selectedSource) {
                    VideoProject.SOURCE_IDEA -> ideaText.isNotBlank()
                    VideoProject.SOURCE_NOVEL -> novelText.isNotBlank()
                    VideoProject.SOURCE_SCRIPT -> scriptText.isNotBlank()
                    else -> false
                },
                colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = VideoColors.OnSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VideoColors.Primary.copy(alpha = 0.2f) else VideoColors.SurfaceVariant,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, VideoColors.Primary)
        } else null,
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) VideoColors.Primary else VideoColors.OnSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) VideoColors.Primary else VideoColors.OnSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}