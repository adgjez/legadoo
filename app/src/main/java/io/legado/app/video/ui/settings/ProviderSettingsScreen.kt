package io.legado.app.video.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.api.*
import io.legado.app.video.ui.components.VideoTextField
import io.legado.app.video.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onBack: () -> Unit
) {
    val providers = remember { ProviderRegistry.getAll() }
    var activeProviderId by remember { mutableStateOf(VideoApiConfigManager.activeProviderId) }
    var selectedProvider by remember { mutableStateOf<ProviderDescriptor?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "API Provider 设置",
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
                    colors = listOf(VideoColors.Primary.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.2f),
                    radius = size.width / 3
                )
                drawRect(brush)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = VideoColors.SurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Api,
                                        null,
                                        tint = VideoColors.Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "当前激活: ${ProviderRegistry.get(activeProviderId)?.displayName ?: "未知"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VideoColors.OnSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "选择主要使用的 AI API 提供商，支持随时切换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(300, delayMillis = 100)
                        )
                    ) {
                        Text(
                            text = "📡 可用 Provider",
                            style = MaterialTheme.typography.titleMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(providers.size) { index ->
                    val provider = providers[index]
                    var animated by remember { mutableStateOf(false) }
                    LaunchedEffect(provider.key) {
                        kotlinx.coroutines.delay((index * 80).toLong())
                        animated = true
                    }

                    AnimatedVisibility(
                        visible = animated,
                        enter = fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(400)
                        ) + scaleIn(
                            animationSpec = androidx.compose.animation.core.tween(400)
                        )
                    ) {
                        ProviderCard(
                            provider = provider,
                            isActive = provider.key == activeProviderId,
                            isConfigured = VideoApiConfigManager.isProviderConfigured(provider.key),
                            onClick = { selectedProvider = provider },
                            onSetActive = {
                                activeProviderId = provider.key
                                VideoApiConfigManager.activeProviderId = provider.key
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(
                        visible = testResult != null,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        testResult?.let { result ->
                            val isSuccess = result.success
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSuccess) VideoColors.Success.copy(alpha = 0.15f) else VideoColors.Error.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        null,
                                        tint = if (isSuccess) VideoColors.Success else VideoColors.Error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = result.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VideoColors.OnSurface
                                        )
                                        if (isSuccess) {
                                            Text(
                                                text = "延迟: ${result.latencyMs}ms",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = VideoColors.OnSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedProvider?.let { provider ->
            ProviderConfigDialog(
                provider = provider,
                onDismiss = { selectedProvider = null },
                onTestConnection = { client ->
                    isTesting = true
                    testResult = null
                    scope.launch {
                        val result = client.testConnection()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            testResult = result.getOrNull()
                            isTesting = false
                        }
                    }
                },
                isTesting = isTesting
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderDescriptor,
    isActive: Boolean,
    isConfigured: Boolean,
    onClick: () -> Unit,
    onSetActive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) VideoColors.Primary.copy(alpha = 0.15f) else VideoColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        VideoColors.GradientStart.copy(alpha = 0.3f),
                                        VideoColors.GradientEnd.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                provider.capabilities.supportsImage -> Icons.Default.Image
                                provider.capabilities.supportsVideo -> Icons.Default.VideoLibrary
                                provider.capabilities.supportsText -> Icons.Default.Chat
                                else -> Icons.Default.Api
                            },
                            null,
                            tint = VideoColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = VideoColors.OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (provider.capabilities.supportsImage) {
                                Text(
                                    text = "🖼 图像",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            if (provider.capabilities.supportsVideo) {
                                Text(
                                    text = "🎬 视频",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            if (provider.capabilities.supportsText) {
                                Text(
                                    text = "💬 对话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VideoColors.OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VideoColors.Primary
                        ) {
                            Text(
                                text = "使用中",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.OnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (!isConfigured) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VideoColors.Warning.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "未配置",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VideoColors.Warning,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.OnSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VideoColors.OnSurfaceVariant)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("配置", fontSize = 12.sp)
                }

                if (!isActive) {
                    Button(
                        onClick = onSetActive,
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("设为默认", fontSize = 12.sp, color = VideoColors.OnPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderConfigDialog(
    provider: ProviderDescriptor,
    onDismiss: () -> Unit,
    onTestConnection: (VideoApiClient) -> Unit,
    isTesting: Boolean
) {
    val config = VideoApiConfigManager.getProviderConfig(provider.key)
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var imageModel by remember { mutableStateOf(config.imageModel ?: "") }
    var videoModel by remember { mutableStateOf(config.videoModel ?: "") }
    var chatModel by remember { mutableStateOf(config.chatModel ?: "") }
    var timeout by remember { mutableStateOf(config.timeoutSeconds.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VideoColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        VideoColors.GradientStart.copy(alpha = 0.3f),
                                        VideoColors.GradientEnd.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Api,
                            null,
                            tint = VideoColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = provider.displayName,
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
                text = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = VideoColors.OnSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "🔑 API 密钥",
                style = MaterialTheme.typography.labelMedium,
                color = VideoColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            VideoTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "输入 API Key",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "🌐 API 地址",
                style = MaterialTheme.typography.labelMedium,
                color = VideoColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            VideoTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = "https://api.example.com",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🖼 图像模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    VideoTextField(
                        value = imageModel,
                        onValueChange = { imageModel = it },
                        placeholder = "model-name",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎬 视频模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    VideoTextField(
                        value = videoModel,
                        onValueChange = { videoModel = it },
                        placeholder = "model-name",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💬 对话模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    VideoTextField(
                        value = chatModel,
                        onValueChange = { chatModel = it },
                        placeholder = "model-name",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⏱ 超时(秒)",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoColors.OnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    VideoTextField(
                        value = timeout,
                        onValueChange = { timeout = it },
                        placeholder = "300",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val client = ApiProviderFactory.getClient(provider.key)
                        if (client != null) {
                            onTestConnection(client)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTesting && apiKey.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VideoColors.Primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "测试中..." else "测试连接")
                }

                Button(
                    onClick = {
                        VideoApiConfigManager.saveProviderConfig(
                            VideoApiConfig(
                                providerId = provider.key,
                                providerName = provider.displayName,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                timeoutSeconds = timeout.toIntOrNull() ?: 300,
                                imageModel = imageModel.ifBlank { null },
                                videoModel = videoModel.ifBlank { null },
                                chatModel = chatModel.ifBlank { null }
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, tint = VideoColors.OnPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("保存配置", color = VideoColors.OnPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
