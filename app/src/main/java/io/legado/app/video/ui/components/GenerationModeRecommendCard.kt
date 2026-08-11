package io.legado.app.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.GenerationRecommendation
import io.legado.app.video.api.GenerationMode

/**
 * GenerationModeRecommendCard：
 *   UI 层的「生成模式推荐卡片」（ViewModel.generationRecommendation 的渲染）
 *
 * 设计要点:
 *   - 顶层标题行：AUTO/MANUAL 标签 + 模式名 + 确认勾选图标
 *   - 关键指标 4 格：成本 / 帧数 / 一致性% / 吞吐倍率
 *   - 警告行：如果 ModeCapabilityCatalog.validate 出 warnings 就黄色高亮显示
 *   - 动作区：模式下拉覆盖 / 恢复自动 / 确认 / 确认并开始生成
 *   - 底部折叠：为什么推荐这个？（显示 HeuristicInputs 5 项）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerationModeRecommendCard(
    recommendation: GenerationRecommendation?,
    modifier: Modifier = Modifier,
    onOverrideMode: (GenerationMode) -> Unit = {},
    onResetAuto: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onConfirmAndStart: () -> Unit = {}
) {
    var showModeDropdown by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                recommendation == null -> MaterialTheme.colorScheme.surfaceVariant
                recommendation.confirmed -> MaterialTheme.colorScheme.primaryContainer
                recommendation.hasWarnings -> Color(0xFFFFF4D6)   // 黄底，警告优先于确认
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, when {
                recommendation == null -> MaterialTheme.colorScheme.outlineVariant
                recommendation.confirmed -> MaterialTheme.colorScheme.primary
                recommendation.hasWarnings -> Color(0xFFE2A300)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        // 1. 空态：还没 load project
        if (recommendation == null) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "选择一个项目后，这里会自动推荐最合适的生成模式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                )
            }
            return@Card
        }

        Column(modifier = Modifier.padding(16.dp)) {

            // ============== 2. 标题行 ==============
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：Auto/MANUAL badge + 名称
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recommendation.source == "AUTO") {
                            Icon(Icons.Default.AutoAwesome, null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF6B5BFF))
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = true,
                                onClick = { /* badge 非点击 */ },
                                label = { Text("智能推荐", fontSize = 12.sp) },
                                leadingIcon = null
                            )
                        } else {
                            Icon(Icons.Default.Warning, null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFE2A300))
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = true,
                                onClick = { },
                                label = { Text("手动选择", fontSize = 12.sp) }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (recommendation.confirmed) {
                            Icon(Icons.Default.CheckCircle, null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = recommendation.profileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = recommendation.mode.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 右侧：一致性徽章 (大色块)
                val consistency = recommendation.consistencyPct
                val (consBg, consFg) = when {
                    consistency >= 90 -> Color(0xFFB7F0C5) to Color(0xFF0E7C3B)
                    consistency >= 70 -> Color(0xFFFFE6B8) to Color(0xFFB46500)
                    else -> Color(0xFFFFD1D1) to Color(0xFFB3261E)
                }
                Box(
                    modifier = Modifier
                        .background(consBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("一致性", fontSize = 10.sp, color = consFg)
                        Text("${consistency}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = consFg)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ============== 3. 4 格关键指标 ==============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCell(
                    title = "预算估算",
                    value = "%.1f×".format(recommendation.estimatedCost),
                    hint = "相对基准帧",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    title = "帧数",
                    value = "${recommendation.frameCount}",
                    hint = "分镜段数",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    title = "吞吐",
                    value = recommendation.throughputMul,
                    hint = "倍/并发",
                    modifier = Modifier.weight(1f)
                )
                val costPerFrame = recommendation.dryRun?.profile?.costPerSegment ?: 0f
                StatCell(
                    title = "成本/帧",
                    value = "%.2f×".format(costPerFrame),
                    hint = "REFERENCE=1.35",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ============== 4. warnings 区域 ==============
            AnimatedVisibility(visible = recommendation.hasWarnings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFFFF3CD),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color(0xFFF0B429), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null,
                            tint = Color(0xFFB08900), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("模式警告 (${recommendation.warnings.size})",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7A5A00),
                                fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            recommendation.warnings.take(2).forEach { w ->
                                Text("  · ${w.take(60)}",
                                    color = Color(0xFF7A5A00), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ============== 5. 动作按钮行 ==============
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 模式下拉覆盖
                Box {
                    OutlinedButton(onClick = { showModeDropdown = true }) {
                        Text("切换模式", fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = showModeDropdown,
                        onDismissRequest = { showModeDropdown = false }
                    ) {
                        GenerationMode.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    showModeDropdown = false
                                    onOverrideMode(m)
                                }
                            )
                        }
                    }
                }
                // 恢复自动
                if (recommendation.source == "MANUAL") {
                    OutlinedButton(onClick = onResetAuto) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复自动", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                // 确认
                OutlinedButton(
                    onClick = onConfirm,
                    enabled = !recommendation.confirmed
                ) {
                    Text(if (recommendation.confirmed) "已确认" else "确认", fontSize = 13.sp)
                }
                // 确认并开始生成 (主按钮)
                Button(
                    onClick = onConfirmAndStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recommendation.confirmed)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("确认并开始生成", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ============== 6. 折叠：为什么推荐这个？ ==============
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWhy = !showWhy }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "为什么推荐这个？",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (showWhy) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = showWhy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val h = recommendation.heuristicInputs
                    HeuristicRow("质量预设", h.qualityPreset)
                    HeuristicRow("角色数", "${h.distinctCharacters}")
                    HeuristicRow("含对话", if (h.hasDialogue) "是 (触发多角色一致性模式)" else "否")
                    HeuristicRow("总分镜段", "${h.totalSegments}")
                    HeuristicRow("预算档位", h.budgetTier)
                    Spacer(Modifier.height(6.dp))
                    ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                        Text(
                            text = "启发式规则：角色数≥2 且含对话 → REFERENCE_VIDEO 一致性优先；单角色无对白 → LOW_COST 速度优先；其他默认 BALANCED。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(title: String, value: String, hint: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp)
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(hint, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeuristicRow(label: String, v: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.padEnd(10),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = v,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
