package io.legado.app.video.ui.pipeline

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.video.quality.QualityGrade
import io.legado.app.video.quality.QualityReport
import io.legado.app.video.quality.QualityTargetType
import io.legado.app.video.quality.DimensionScore

/**
 * QualityReportScreen - 质量报告查看 UI
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityReportScreen(
    report: QualityReport?,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("质量报告") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QualityScoreCard(report)

                if (report.getAllIssues().isNotEmpty()) {
                    IssuesSection(report.getAllIssues())
                }

                DimensionsBreakdown(report.scores)

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                    if (report.needsRegeneration()) {
                        Button(
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重新生成")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityScoreCard(report: QualityReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (report.grade) {
                QualityGrade.EXCELLENT -> Color(0xFFE8F5E9)
                QualityGrade.GOOD -> Color(0xFFF1F8E9)
                QualityGrade.ABOVE_AVERAGE -> Color(0xFFFFFDE7)
                QualityGrade.AVERAGE -> Color(0xFFFFF8E1)
                QualityGrade.BELOW_AVERAGE -> Color(0xFFFFF3E0)
                QualityGrade.POOR -> Color(0xFFFFEBEE)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { report.overallScore },
                    modifier = Modifier.size(100.dp),
                    color = gradeColor(report.grade),
                    strokeWidth = 8.dp
                )
                Text(
                    text = "${(report.overallScore * 100).toInt()}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = "${report.grade.emoji} ${report.grade.label}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = qualityDescription(report.grade),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "目标: ${report.targetType.displayName}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun IssuesSection(issues: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFC62828)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "发现 ${issues.size} 个问题",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }

            Spacer(Modifier.height(8.dp))

            issues.take(5).forEach { issue ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("• ", color = Color(0xFFC62828))
                    Text(
                        text = issue,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DimensionsBreakdown(scores: List<DimensionScore>) {
    Text(
        text = "维度分析",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        scores.sortedByDescending { it.score }.forEach { score ->
            DimensionBar(score)
        }
    }
}

@Composable
private fun DimensionBar(score: DimensionScore) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = score.dimension.label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${(score.score * 100).toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    score.score >= 0.8f -> Color(0xFF66BB6A)
                    score.score >= 0.6f -> Color(0xFFFFA726)
                    else -> Color(0xFFE57373)
                }
            )
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.score)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            score.score >= 0.8f -> Color(0xFF66BB6A)
                            score.score >= 0.6f -> Color(0xFFFFA726)
                            else -> Color(0xFFE57373)
                        }
                    )
            )
        }
    }
}

private fun gradeColor(grade: QualityGrade): Color = when (grade) {
    QualityGrade.EXCELLENT -> Color(0xFF66BB6A)
    QualityGrade.GOOD -> Color(0xFF81C784)
    QualityGrade.ABOVE_AVERAGE -> Color(0xFFFFD54F)
    QualityGrade.AVERAGE -> Color(0xFFFFB74D)
    QualityGrade.BELOW_AVERAGE -> Color(0xFFFF8A65)
    QualityGrade.POOR -> Color(0xFFE57373)
}

private fun qualityDescription(grade: QualityGrade): String = when (grade) {
    QualityGrade.EXCELLENT -> "质量优秀，可直接使用"
    QualityGrade.GOOD -> "质量良好，建议直接输出"
    QualityGrade.ABOVE_AVERAGE -> "质量中等偏上，可考虑微优化"
    QualityGrade.AVERAGE -> "质量中等，部分内容可能需要调整"
    QualityGrade.BELOW_AVERAGE -> "质量待改进，建议检查问题并重新生成"
    QualityGrade.POOR -> "质量不合格，强烈建议重新生成"
}
