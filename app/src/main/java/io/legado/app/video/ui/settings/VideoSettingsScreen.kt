package io.legado.app.video.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.video.api.AgnesConfig
import io.legado.app.video.ui.theme.VideoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsScreen(
    onBack: () -> Unit
) {
    var apiKey by remember { mutableStateOf(AgnesConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(AgnesConfig.baseUrl) }
    var imageModel by remember { mutableStateOf(AgnesConfig.imageModel) }
    var videoModel by remember { mutableStateOf(AgnesConfig.videoModel) }
    var timeout by remember { mutableStateOf(AgnesConfig.timeoutSeconds.toString()) }
    var showApiKey by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 视频设置") },
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
        containerColor = VideoColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // API Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (AgnesConfig.isConfigured()) VideoColors.Success.copy(alpha = 0.1f) else VideoColors.Error.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (AgnesConfig.isConfigured()) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (AgnesConfig.isConfigured()) VideoColors.Success else VideoColors.Error
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (AgnesConfig.isConfigured()) "API 已配置" else "API 未配置",
                            style = MaterialTheme.typography.titleMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (AgnesConfig.isConfigured()) "可以开始生成视频了" else "请填入 API Key 以使用 AI 功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = VideoColors.OnSurfaceVariant
                        )
                    }
                }
            }
            
            // API Key Section
            SectionHeader("API 配置")
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            
            // Model Section
            SectionHeader("模型配置")
            
            OutlinedTextField(
                value = imageModel,
                onValueChange = { imageModel = it },
                label = { Text("图像生成模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            
            OutlinedTextField(
                value = videoModel,
                onValueChange = { videoModel = it },
                label = { Text("视频生成模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            
            OutlinedTextField(
                value = timeout,
                onValueChange = { timeout = it },
                label = { Text("超时时间（秒）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            
            if (savedMessage.isNotBlank()) {
                Text(
                    text = savedMessage,
                    color = VideoColors.Success,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Save Button
            Button(
                onClick = {
                    AgnesConfig.apiKey = apiKey
                    AgnesConfig.baseUrl = baseUrl
                    AgnesConfig.imageModel = imageModel
                    AgnesConfig.videoModel = videoModel
                    timeout.toIntOrNull()?.let { AgnesConfig.timeoutSeconds = it }
                    savedMessage = "设置已保存"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存设置")
            }
            
            // Info Section
            SectionHeader("说明")
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前支持的模型：",
                        style = MaterialTheme.typography.bodySmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "• Agnes Image 2.1 Flash - 文生图/图生图\n• Agnes Video V2.0 - 文生视频/图生视频\n• Agnes Chat V1 - AI 对话/Agent",
                        style = MaterialTheme.typography.bodySmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = VideoColors.OnSurface,
        fontWeight = FontWeight.Bold
    )
    HorizontalDivider(color = VideoColors.Divider)
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VideoColors.Primary,
    unfocusedBorderColor = VideoColors.SurfaceVariant,
    cursorColor = VideoColors.Primary,
    focusedContainerColor = VideoColors.Surface,
    unfocusedContainerColor = VideoColors.Surface
)