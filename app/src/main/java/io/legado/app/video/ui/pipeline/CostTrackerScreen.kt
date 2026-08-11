package io.legado.app.video.ui.pipeline

import androidx.compose.foundation.background
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
import io.legado.app.video.states.CostRecord
import io.legado.app.video.states.CostStore
import io.legado.app.video.ui.theme.VideoColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow

/**
 * CostTrackerScreen - 成本追踪界面
 *
 * 借鉴 ArcReel 的成本管理设计哲学：
 * - 实时 API 调用成本追踪
 * - 按项目/类型分类统计
 * - 预算预警
 * - 使用效率分析
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostTrackerScreen(
    projectId: String,
    onBack: () -> Unit
) {
    val costStore = CostStore.instance
    val costState by costStore.state.collectAsState()

    val projectRecords = costState.records.filter { it.projectId == projectId }
    val summary = costStore.getProjectCost(projectId)

    var selectedFilter by remember { mutableStateOf(CostFilter.ALL) }
    var budget by remember { mutableFloatStateOf(10.0f) }

    val filteredRecords = when (selectedFilter) {
        CostFilter.ALL -> projectRecords
        CostFilter.IMAGE -> projectRecords.filter { it.operation.contains("image", true) }
        CostFilter.VIDEO -> projectRecords.filter { it.operation.contains("video", true) }
        CostFilter.TEXT -> projectRecords.filter { it.operation.contains("text", true) }
        CostFilter.AUDIO -> projectRecords.filter { it.operation.contains("tts", true) || it.operation.contains("audio", true) }
    }

    val totalCost = summary.actualTotal.ifZero { summary.estimatedTotal }
    val budgetRemaining = budget - totalCost
    val budgetPercentage = if (budget > 0) (totalCost / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    val isOverBudget = budgetRemaining < 0

    val usageBreakdown = calculateUsageBreakdown(projectRecords)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成本追踪") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "总成本",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.TextSecondary
                                )
                                Text(
                                    "$${"%.2f".format(totalCost)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverBudget) VideoColors.StatusFailed else VideoColors.Primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "预算",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VideoColors.TextSecondary
                                )
                                Text(
                                    "$${"%.2f".format(budget.toDouble())}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { budgetPercentage },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = when {
                                isOverBudget -> VideoColors.StatusFailed
                                budgetPercentage > 0.8f -> Color(0xFFFF9800)
                                else -> VideoColors.Primary
                            },
                            trackColor = VideoColors.SurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when {
                                    isOverBudget -> "⚠️ 超出预算 $${"%.2f".format(kotlin.math.abs(budgetRemaining.toDouble()))}"
                                    budgetPercentage > 0.8f -> "⚠️ 即将超出预算"
                                    else -> "剩余 $${"%.2f".format(budgetRemaining.toDouble())}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isOverBudget -> VideoColors.StatusFailed
                                    budgetPercentage > 0.8f -> Color(0xFFFF9800)
                                    else -> VideoColors.StatusCompleted
                                }
                            )
                            Text(
                                "${"%.0f".format(budgetPercentage * 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "使用分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${projectRecords.size} 次调用",
                        style = MaterialTheme.typography.bodySmall,
                        color = VideoColors.TextSecondary
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (usageBreakdown.isEmpty()) {
                            Text(
                                "暂无使用数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = VideoColors.TextSecondary
                            )
                        } else {
                            usageBreakdown.forEach { (type, data) ->
                                val percentage = if (totalCost > 0) (data.totalCost / totalCost * 100).toFloat() else 0f
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                typeIcon(type),
                                                null,
                                                tint = typeColor(type),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                typeDisplayName(type),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            "$${"%.2f".format(data.totalCost)} (${"%.0f".format(percentage)}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percentage / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = typeColor(type),
                                        trackColor = VideoColors.SurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "成本明细",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == CostFilter.ALL,
                            onClick = { selectedFilter = CostFilter.ALL },
                            label = { Text("全部") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == CostFilter.IMAGE,
                            onClick = { selectedFilter = CostFilter.IMAGE },
                            label = { Text("图像") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == CostFilter.VIDEO,
                            onClick = { selectedFilter = CostFilter.VIDEO },
                            label = { Text("视频") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == CostFilter.TEXT,
                            onClick = { selectedFilter = CostFilter.TEXT },
                            label = { Text("文本") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == CostFilter.AUDIO,
                            onClick = { selectedFilter = CostFilter.AUDIO },
                            label = { Text("配音") }
                        )
                    }
                }
            }

            if (filteredRecords.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Paid,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = VideoColors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("暂无成本记录")
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredRecords.take(20).reversed().forEach { record ->
                            CostRecordItem(record)
                        }
                    }
                }
            }
        }
    }
}

private data class UsageData(
    val totalCost: Double,
    val callCount: Int
)

private fun calculateUsageBreakdown(records: List<CostRecord>): Map<OperationType, UsageData> {
    val breakdown = mutableMapOf<OperationType, UsageData>()
    records.forEach { record ->
        val type = classifyOperation(record.operation)
        val cost = record.actualCost ?: record.estimatedCost
        val current = breakdown[type] ?: UsageData(0.0, 0)
        breakdown[type] = UsageData(
            totalCost = current.totalCost + cost,
            callCount = current.callCount + 1
        )
    }
    return breakdown
}

private fun classifyOperation(operation: String): OperationType {
    val lower = operation.lowercase()
    return when {
        lower.contains("image") -> OperationType.IMAGE
        lower.contains("video") -> OperationType.VIDEO
        lower.contains("text") || lower.contains("script") || lower.contains("analysis") -> OperationType.TEXT
        lower.contains("tts") || lower.contains("audio") || lower.contains("voice") -> OperationType.AUDIO
        else -> OperationType.TEXT
    }
}

@Composable
private fun CostRecordItem(record: CostRecord) {
    val cost = record.actualCost ?: record.estimatedCost
    val type = classifyOperation(record.operation)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VideoColors.Surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        typeColor(type).copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    typeIcon(type),
                    null,
                    tint = typeColor(type),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.operation.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${record.providerKey} · ${record.model}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VideoColors.TextSecondary
                )
            }
            Text(
                "$${"%.2f".format(cost)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private enum class CostFilter { ALL, IMAGE, VIDEO, TEXT, AUDIO }

private enum class OperationType {
    IMAGE, VIDEO, TEXT, AUDIO
}

private fun typeIcon(type: OperationType) = when (type) {
    OperationType.IMAGE -> Icons.Default.Image
    OperationType.VIDEO -> Icons.Default.VideoLibrary
    OperationType.TEXT -> Icons.Default.Description
    OperationType.AUDIO -> Icons.Default.RecordVoiceOver
}

private fun typeColor(type: OperationType) = when (type) {
    OperationType.IMAGE -> Color(0xFF667EEA)
    OperationType.VIDEO -> Color(0xFFF093FB)
    OperationType.TEXT -> Color(0xFF4FACFE)
    OperationType.AUDIO -> Color(0xFF43E97B)
}

private fun typeDisplayName(type: OperationType) = when (type) {
    OperationType.IMAGE -> "图像生成"
    OperationType.VIDEO -> "视频生成"
    OperationType.TEXT -> "文本生成"
    OperationType.AUDIO -> "配音生成"
}
