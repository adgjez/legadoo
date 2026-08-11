package io.legado.app.video.ui.character

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.data.entities.VideoCharacter
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    characters: List<VideoCharacter>,
    onBack: () -> Unit,
    onCharacterClick: (VideoCharacter) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (VideoCharacter) -> Unit,
    onGenerateAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VideoColors.Background,
                    titleContentColor = VideoColors.OnBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCharacter,
                containerColor = VideoColors.Primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加角色")
            }
        },
        containerColor = VideoColors.Background
    ) { padding ->
        if (characters.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text("还没有角色", color = VideoColors.OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddCharacter,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.Primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加第一个角色")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${characters.size} 个角色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VideoColors.OnSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onGenerateAll,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.Primary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("一键生成形象")
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterCard(
                            character = character,
                            onClick = { onCharacterClick(character) },
                            onDelete = { onDeleteCharacter(character) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: VideoCharacter,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VideoColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (character.generatedImagePath.isNotBlank()) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = VideoColors.OnSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VideoColors.OnSurface
                    )
                    CharacterTypeChip(character.characterType)
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = character.appearance.ifBlank { character.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = VideoColors.OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (character.personality.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "性格：${character.personality}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (character.identityPrompt.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VideoColors.Success,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "AI形象已生成",
                            style = MaterialTheme.typography.labelSmall,
                            color = VideoColors.Success,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = VideoColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CharacterTypeChip(type: String) {
    val (text, color) = when (type) {
        VideoCharacter.TYPE_PROTAGONIST -> "主角" to VideoColors.Primary
        VideoCharacter.TYPE_SUPPORTING -> "配角" to VideoColors.Secondary
        VideoCharacter.TYPE_ANTAGONIST -> "反派" to VideoColors.Error
        else -> type to VideoColors.OnSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
    }
}