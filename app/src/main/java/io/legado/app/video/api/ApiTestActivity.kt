package io.legado.app.video.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.ui.theme.VideoColors
import kotlinx.coroutines.launch

class ApiTestActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ApiTestActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ApiTestScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTestScreen(onBack: () -> Unit) {
    var apiKey by remember { mutableStateOf(AgnesConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(AgnesConfig.baseUrl) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var progressStage by remember { mutableStateOf("") }
    var chatResponse by remember { mutableStateOf("") }
    var chatTestRunning by remember { mutableStateOf(false) }
    var imageTestResult by remember { mutableStateOf("") }
    var imageTestRunning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val client = remember { AgnesApiClient(AgnesConfigProvider()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 连接测试", color = VideoColors.OnBackground) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔌 连接设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = VideoColors.OnSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; AgnesConfig.baseUrl = it },
                        label = { Text("API Base URL", color = VideoColors.OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = outlinedColors()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; AgnesConfig.apiKey = it },
                        label = { Text("API Key", color = VideoColors.OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = outlinedColors()
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AgnesConfig.apiKey = apiKey
                                AgnesConfig.baseUrl = baseUrl
                                scope.launch {
                                    isTesting = true
                                    progressStage = "连接测试中..."
                                    val result = client.testConnection()
                                    testResult = result.getOrDefault(ConnectionTestResult(success = false, message = "未知错误"))
                                    isTesting = false
                                    progressStage = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = VideoColors.OnPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("测试中...", color = VideoColors.OnPrimary)
                            } else {
                                Icon(Icons.Default.Link, null, tint = VideoColors.OnPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("测试连接", color = VideoColors.OnPrimary)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AgnesConfig.apiKey = ""
                                AgnesConfig.baseUrl = "https://api.agnes-ai.com"
                                apiKey = ""
                                baseUrl = "https://api.agnes-ai.com"
                                testResult = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.OnSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("重置")
                        }
                    }
                }
            }

            testResult?.let { result ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) VideoColors.Success.copy(alpha = 0.1f)
                        else VideoColors.Error.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                null,
                                tint = if (result.success) VideoColors.Success else VideoColors.Error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (result.success) "连接成功" else "连接失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (result.success) VideoColors.Success else VideoColors.Error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VideoColors.OnSurface
                        )

                        if (result.latencyMs > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "延迟: ${result.latencyMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoColors.OnSurfaceVariant
                            )
                        }

                        if (result.success) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = VideoColors.SurfaceVariant)
                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "🤖 AI 对话测试",
                                style = MaterialTheme.typography.titleSmall,
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        chatTestRunning = true
                                        chatResponse = ""
                                        val chatResult = client.chatCompletion(
                                            AgnesChatRequest(
                                                model = AgnesConfig.chatModel,
                                                messages = listOf(
                                                    AgnesChatMessage(role = "user", content = "你好，请用一句话介绍你自己")
                                                ),
                                                maxTokens = 100
                                            )
                                        )
                                        chatResult.onSuccess { response ->
                                            chatResponse = response.choices?.firstOrNull()?.message?.content ?: "无响应"
                                        }.onFailure {
                                            chatResponse = "错误: ${it.message}"
                                        }
                                        chatTestRunning = false
                                    }
                                },
                                enabled = !chatTestRunning,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.Primary)
                            ) {
                                Icon(Icons.Default.Chat, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (chatTestRunning) "测试中..." else "测试 AI 对话")
                            }

                            if (chatResponse.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "响应: $chatResponse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = VideoColors.SurfaceVariant)
                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "🎨 图片生成测试",
                                style = MaterialTheme.typography.titleSmall,
                                color = VideoColors.OnSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        imageTestRunning = true
                                        imageTestResult = ""
                                        val imageResult = client.generateImage(
                                            AgnesImageRequest(
                                                model = AgnesConfig.imageModel,
                                                prompt = "一个美丽的日落风景，金色的阳光洒在山脉上，电影级画质",
                                                size = "1280x720",
                                                n = 1
                                            )
                                        )
                                        imageResult.onSuccess { response ->
                                            val urls = response.data?.mapNotNull { it.url } ?: emptyList()
                                            imageTestResult = if (urls.isNotEmpty()) {
                                                "✅ 成功生成 ${urls.size} 张图片\nURL: ${urls.first().take(80)}..."
                                            } else {
                                                "⚠️ 响应中没有图片数据"
                                            }
                                        }.onFailure {
                                            imageTestResult = "❌ 错误: ${it.message}"
                                        }
                                        imageTestRunning = false
                                    }
                                },
                                enabled = !imageTestRunning,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.Primary)
                            ) {
                                Icon(Icons.Default.Image, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (imageTestRunning) "生成中..." else "测试图片生成")
                            }

                            if (imageTestResult.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = imageTestResult,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 测试说明",
                        style = MaterialTheme.typography.titleSmall,
                        color = VideoColors.OnSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "1. 连接测试：验证 API Key 和网络连接\n2. AI 对话测试：调用 Chat API 验证\n3. 图片生成测试：调用 Image API 验证\n\n建议先进行连接测试，成功后再进行后续测试。图片生成会消耗 API 额度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = VideoColors.OnSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VideoColors.Primary,
    unfocusedBorderColor = VideoColors.SurfaceVariant,
    cursorColor = VideoColors.Primary,
    focusedContainerColor = VideoColors.Surface,
    unfocusedContainerColor = VideoColors.Surface,
    focusedLabelColor = VideoColors.Primary
)
