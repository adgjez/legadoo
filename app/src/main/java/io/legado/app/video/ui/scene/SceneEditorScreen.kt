package io.legado.app.video.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.data.entities.VideoScene
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(
    scene: VideoScene,
    characters: List<VideoCharacter>,
    onBack: () -> Unit,
    onSave: (VideoScene) -> Unit,
    onGenerateImage: () -> Unit,
    onGenerateVideo: () -> Unit
) {
    var editedScene by remember { mutableStateOf(scene) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分镜 ${scene.order} 编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(editedScene) }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Background,
                    titleContentColor = VideoColors.OnBackground
                )
            )
        },
        containerColor = VideoColors.Background,
        bottomBar = {
            BottomAppBar(
                containerColor = VideoColors.Surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onGenerateImage,
                        modifier = Modifier.weight(1f),
                        enabled = editedScene.visualPrompt.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.Primary)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("生成分镜图")
                    }
                    Button(
                        onClick = onGenerateVideo,
                        modifier = Modifier.weight(1f),
                        enabled = editedScene.generatedStoryboardPath.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("生成视频")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(VideoColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (editedScene.generatedStoryboardPath.isNotBlank()) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = null,
                                tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "分镜图预览",
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTitle("基本信息")

                OutlinedTextField(
                    value = editedScene.title,
                    onValueChange = { editedScene = editedScene.copy(title = it) },
                    label = { Text("分镜标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedColors()
                )

                OutlinedTextField(
                    value = editedScene.summary,
                    onValueChange = { editedScene = editedScene.copy(summary = it) },
                    label = { Text("分镜摘要") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedColors()
                )

                SectionTitle("镜头设置")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DropDownSelector(
                        label = "景别",
                        value = editedScene.shotType,
                        options = listOf("extreme_long" to "极远景", "long" to "远景", "medium" to "中景", "close_up" to "近景", "extreme_close_up" to "特写", "bird_eye" to "鸟瞰", "worm_eye" to "虫视", "over_shoulder" to "过肩"),
                        onSelect = { editedScene = editedScene.copy(shotType = it) },
                        modifier = Modifier.weight(1f)
                    )

                    DropDownSelector(
                        label = "运镜",
                        value = editedScene.cameraMovement,
                        options = listOf("static" to "固定", "pan" to "摇", "tilt" to "俯", "dolly" to "推", "zoom" to "变焦", "tracking" to "跟随", "aerial" to "航拍", "handheld" to "手持"),
                        onSelect = { editedScene = editedScene.copy(cameraMovement = it) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editedScene.durationSeconds.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { d ->
                                editedScene = editedScene.copy(durationSeconds = d.coerceIn(3, 30))
                            }
                        },
                        label = { Text("时长(秒)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(),
                        modifier = Modifier.weight(1f),
                        colors = outlinedColors()
                    )

                    OutlinedTextField(
                        value = editedScene.location,
                        onValueChange = { editedScene = editedScene.copy(location = it) },
                        label = { Text("地点") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = outlinedColors()
                    )
                }

                SectionTitle("参与角色")

                CharacterSelector(
                    characters = characters,
                    selectedIds = editedScene.characterIds,
                    onToggle = { charId ->
                        val newIds = if (charId in editedScene.characterIds) {
                            editedScene.characterIds.filter { it != charId }
                        } else {
                            editedScene.characterIds + charId
                        }
                        editedScene = editedScene.copy(characterIds = newIds)
                    }
                )

                SectionTitle("视觉提示词")

                OutlinedTextField(
                    value = editedScene.visualPrompt,
                    onValueChange = { editedScene = editedScene.copy(visualPrompt = it) },
                    label = { Text("视觉描述 (用于图片生成)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedColors()
                )

                SectionTitle("视频提示词")

                OutlinedTextField(
                    value = editedScene.videoPrompt,
                    onValueChange = { editedScene = editedScene.copy(videoPrompt = it) },
                    label = { Text("视频运动描述 (用于视频生成)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedColors()
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = VideoColors.OnSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VideoColors.Primary,
    unfocusedBorderColor = VideoColors.SurfaceVariant,
    cursorColor = VideoColors.Primary,
    focusedContainerColor = VideoColors.Surface,
    unfocusedContainerColor = VideoColors.Surface
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropDownSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            colors = outlinedColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterSelector(
    characters: List<VideoCharacter>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit
) {
    if (characters.isEmpty()) {
        Text(
            text = "请先添加角色",
            style = MaterialTheme.typography.bodySmall,
            color = VideoColors.OnSurfaceVariant
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            characters.forEach { char ->
                FilterChip(
                    selected = char.id in selectedIds,
                    onClick = { onToggle(char.id) },
                    label = { Text(char.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VideoColors.Primary.copy(alpha = 0.2f),
                        selectedLabelColor = VideoColors.Primary
                    )
                )
            }
        }
    }
}