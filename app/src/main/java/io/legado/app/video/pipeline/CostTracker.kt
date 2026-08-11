package io.legado.app.video.pipeline

/**
 * 多币种费用追踪与预估
 *
 * 借鉴 ArcReel 的成本可见性：
 * - 按项目、集、镜头级别预估
 * - 支持 USD 和 CNY 多币种
 * - 预估 vs 实际对比
 */

data class CostRecord(
    val recordId: String,
    val projectId: String,
    val episodeId: String?,
    val segmentId: String?,
    val providerKey: String,
    val model: String,
    val operation: CostOperation,
    val estimatedCost: Double,
    val actualCost: Double? = null,
    val currency: Currency = Currency.USD,
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

enum class CostOperation {
    TEXT_GENERATION,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    TTS,
    ASSEMBLY,
    OTHER
}

enum class Currency {
    USD,
    CNY
}

class CostTracker {

    private val records = mutableListOf<CostRecord>()
    private val exchangeRate = 7.25

    fun estimateCost(
        providerKey: String,
        model: String,
        operation: CostOperation,
        parameters: Map<String, Any?>
    ): CostRecord {
        val unitPrice = getUnitPrice(providerKey, model, operation)
        val quantity = when (operation) {
            CostOperation.VIDEO_GENERATION -> {
                val duration = (parameters["duration"] as? Number)?.toInt() ?: 5
                duration
            }
            CostOperation.IMAGE_GENERATION -> {
                (parameters["count"] as? Number)?.toInt() ?: 1
            }
            CostOperation.TEXT_GENERATION -> {
                val tokens = (parameters["maxTokens"] as? Number)?.toInt() ?: 2048
                tokens / 1000
            }
            else -> 1
        }

        val estimatedCost = unitPrice * quantity

        return CostRecord(
            recordId = "cost_${System.currentTimeMillis()}_${records.size}",
            projectId = parameters["projectId"] as? String ?: "",
            episodeId = parameters["episodeId"] as? String,
            segmentId = parameters["segmentId"] as? String,
            providerKey = providerKey,
            model = model,
            operation = operation,
            estimatedCost = estimatedCost,
            currency = getDefaultCurrency(providerKey),
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    fun recordActualCost(record: CostRecord, actualCost: Double): CostRecord {
        val updated = record.copy(actualCost = actualCost)
        val index = records.indexOfFirst { it.recordId == record.recordId }
        if (index >= 0) {
            records[index] = updated
        } else {
            records.add(updated)
        }
        return updated
    }

    fun getProjectCost(projectId: String): CostSummary {
        val projectRecords = records.filter { it.projectId == projectId }
        return buildSummary(projectRecords)
    }

    fun getEpisodeCost(projectId: String, episodeId: String): CostSummary {
        val episodeRecords = records.filter { it.projectId == projectId && it.episodeId == episodeId }
        return buildSummary(episodeRecords)
    }

    private fun buildSummary(list: List<CostRecord>): CostSummary {
        val estimated = list.sumOf { toUSD(it.estimatedCost, it.currency) }
        val actual = list.mapNotNull { it.actualCost?.let { cost -> toUSD(cost, it.currency) } }.sum()
        val byOperation = list.groupBy { it.operation }.mapValues { (_, records) ->
            records.sumOf { toUSD(it.estimatedCost, it.currency) }
        }
        val byProvider = list.groupBy { it.providerKey }.mapValues { (_, records) ->
            records.sumOf { toUSD(it.estimatedCost, it.currency) }
        }

        return CostSummary(
            totalEstimatedUSD = estimated,
            totalActualUSD = if (actual > 0) actual else null,
            byOperation = byOperation,
            byProvider = byProvider,
            recordCount = list.size
        )
    }

    private fun toUSD(amount: Double, currency: Currency): Double {
        return when (currency) {
            Currency.USD -> amount
            Currency.CNY -> amount / exchangeRate
        }
    }

    private fun getUnitPrice(providerKey: String, model: String, operation: CostOperation): Double {
        return when (operation) {
            CostOperation.IMAGE_GENERATION -> when {
                model.contains("4.0") || model.contains("pro", true) -> 0.134
                model.contains("flash", true) -> 0.067
                model.contains("gpt-image", true) -> 0.080
                model.contains("agnes", true) -> 0.050
                else -> 0.100
            }
            CostOperation.VIDEO_GENERATION -> when {
                model.contains("veo", true) && model.contains("fast", true) -> 0.15
                model.contains("veo", true) -> 0.40
                model.contains("sora", true) -> 0.30
                model.contains("seedance", true) -> 0.20
                model.contains("agnes", true) -> 0.10
                else -> 0.25
            }
            CostOperation.TEXT_GENERATION -> when {
                model.contains("ultra", true) -> 0.005
                model.contains("pro", true) -> 0.003
                model.contains("flash", true) -> 0.001
                else -> 0.002
            }
            CostOperation.TTS -> 0.01
            CostOperation.ASSEMBLY -> 0.50
            CostOperation.OTHER -> 0.10
        }
    }

    private fun getDefaultCurrency(providerKey: String): Currency {
        return when (providerKey) {
            "volcengine", "minimax", "kling" -> Currency.CNY
            else -> Currency.USD
        }
    }
}

data class CostSummary(
    val totalEstimatedUSD: Double,
    val totalActualUSD: Double?,
    val byOperation: Map<CostOperation, Double>,
    val byProvider: Map<String, Double>,
    val recordCount: Int
) {
    fun difference(): Double? {
        val actual = totalActualUSD ?: return null
        return actual - totalEstimatedUSD
    }

    fun formattedUSD(): String {
        val estimated = String.format("$%.2f", totalEstimatedUSD)
        val actual = totalActualUSD?.let { String.format("$%.2f", it) }
        return if (actual != null) "$estimated (实际: $actual)" else estimated
    }
}
