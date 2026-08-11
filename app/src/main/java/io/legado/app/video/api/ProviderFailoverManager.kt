package io.legado.app.video.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ProviderFailoverManager - 提供商故障转移
 *
 * 借鉴 ArcReel 的多 Provider 容错机制：
 * - 健康检查：定期检测各 Provider 可用性
 * - 自动降级：主 Provider 失败时自动切换到备用
 * - 熔断保护：连续失败达到阈值时暂时禁用
 * - 智能路由：根据能力匹配 + 健康状态选择最佳 Provider
 */

// ========== 健康检查 ==========

data class ProviderHealthStatus(
    val providerKey: String,
    val status: HealthStatus,
    val lastCheckTime: Long,
    val responseTimeMs: Long,
    val successRate: Float,
    val consecutiveFailures: Int,
    val circuitOpen: Boolean
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    CIRCUIT_OPEN
}

class ProviderHealthChecker(
    private val checkIntervalMs: Long = 60_000L,
    private val failureThreshold: Int = 3,
    private val recoveryTimeoutMs: Long = 300_000L
) {
    private val healthStatuses = mutableMapOf<String, ProviderHealthStatus>()
    private val circuitOpenUntil = mutableMapOf<String, Long>()

    fun getHealthStatus(providerKey: String): ProviderHealthStatus {
        return healthStatuses.getOrPut(providerKey) {
            ProviderHealthStatus(
                providerKey = providerKey,
                status = HealthStatus.HEALTHY,
                lastCheckTime = System.currentTimeMillis(),
                responseTimeMs = 0,
                successRate = 1.0f,
                consecutiveFailures = 0,
                circuitOpen = false
            )
        }
    }

    fun recordSuccess(providerKey: String, responseTimeMs: Long) {
        val current = getHealthStatus(providerKey)
        val newConsecutiveFailures = 0
        val totalCalls = (successRateDenominator(providerKey) + 1)
        val newSuccessRate = ((current.successRate * successRateDenominator(providerKey)) + 1) / totalCalls

        healthStatuses[providerKey] = current.copy(
            status = when {
                newSuccessRate >= 0.9f -> HealthStatus.HEALTHY
                newSuccessRate >= 0.7f -> HealthStatus.DEGRADED
                else -> HealthStatus.UNHEALTHY
            },
            responseTimeMs = responseTimeMs,
            successRate = newSuccessRate,
            consecutiveFailures = newConsecutiveFailures,
            circuitOpen = false
        )
    }

    fun recordFailure(providerKey: String) {
        val current = getHealthStatus(providerKey)
        val newFailures = current.consecutiveFailures + 1
        val newSuccessRate = (current.successRate * successRateDenominator(providerKey)) / (successRateDenominator(providerKey) + 1)

        val circuitOpen = newFailures >= failureThreshold
        if (circuitOpen) {
            circuitOpenUntil[providerKey] = System.currentTimeMillis() + recoveryTimeoutMs
        }

        healthStatuses[providerKey] = current.copy(
            status = when {
                circuitOpen -> HealthStatus.CIRCUIT_OPEN
                newSuccessRate < 0.5f -> HealthStatus.UNHEALTHY
                else -> HealthStatus.DEGRADED
            },
            successRate = newSuccessRate,
            consecutiveFailures = newFailures,
            circuitOpen = circuitOpen
        )
    }

    fun isAvailable(providerKey: String): Boolean {
        val status = getHealthStatus(providerKey)
        if (status.circuitOpen) {
            val openUntil = circuitOpenUntil[providerKey] ?: return false
            if (System.currentTimeMillis() > openUntil) {
                resetCircuit(providerKey)
                return true
            }
            return false
        }
        return status.status != HealthStatus.UNHEALTHY
    }

    fun getBestAvailableProvider(candidates: List<String>): String? {
        return candidates
            .filter { isAvailable(it) }
            .maxByOrNull { getHealthStatus(it).successRate }
    }

    private fun resetCircuit(providerKey: String) {
        val current = getHealthStatus(providerKey)
        healthStatuses[providerKey] = current.copy(
            status = HealthStatus.DEGRADED,
            circuitOpen = false,
            consecutiveFailures = 0
        )
        circuitOpenUntil.remove(providerKey)
    }

    private fun successRateDenominator(providerKey: String): Int {
        val status = healthStatuses[providerKey]
        return if (status != null && status.successRate > 0f) {
            (1f - status.successRate).coerceAtLeast(0.01f).let { (status.successRate / it).toInt() }
        } else {
            10
        }
    }

    suspend fun performHealthCheck(providerKey: String): ProviderHealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val status = getHealthStatus(providerKey)

        try {
            val result = BackendRouter.generateText(
                TextGenerationRequest(
                    messages = listOf(
                        ChatMessage(role = "user", content = "ping")
                    ),
                    maxTokens = 10
                )
            )

            val responseTime = System.currentTimeMillis() - startTime
            result.fold(
                onSuccess = { recordSuccess(providerKey, responseTime) },
                onFailure = { recordFailure(providerKey) }
            )
        } catch (e: Exception) {
            recordFailure(providerKey)
        }

        getHealthStatus(providerKey).copy(lastCheckTime = System.currentTimeMillis())
    }

    fun getAllStatuses(): Map<String, ProviderHealthStatus> = healthStatuses.toMap()
}

// ========== 故障转移路由器 ==========

class FailoverRouter(
    private val healthChecker: ProviderHealthChecker = ProviderHealthChecker()
) {
    private val fallbackChain = mutableMapOf<String, List<String>>()

    fun setFallbackChain(providerKey: String, fallbacks: List<String>) {
        fallbackChain[providerKey] = fallbacks
    }

    suspend fun executeWithFailover(
        primaryProvider: String,
        action: suspend (String) -> Result<String>,
        maxRetries: Int = 2
    ): FailoverResult = withContext(Dispatchers.IO) {
        val providers = mutableListOf(primaryProvider)
        fallbackChain[primaryProvider]?.let { providers.addAll(it) }

        val attemptedProviders = mutableListOf<String>()
        var lastError: Exception? = null

        for (provider in providers.take(maxRetries + 1)) {
            if (!healthChecker.isAvailable(provider)) {
                attemptedProviders.add("$provider (circuit_open)")
                continue
            }

            attemptedProviders.add(provider)
            val startTime = System.currentTimeMillis()

            try {
                val result = action(provider)
                val responseTime = System.currentTimeMillis() - startTime

                result.fold(
                    onSuccess = {
                        healthChecker.recordSuccess(provider, responseTime)
                        return@withContext FailoverResult(
                            success = true,
                            result = it,
                            usedProvider = provider,
                            attemptedProviders = attemptedProviders
                        )
                    },
                    onFailure = { error ->
                        healthChecker.recordFailure(provider)
                        lastError = error as? Exception ?: Exception(error.toString())
                    }
                )
            } catch (e: Exception) {
                healthChecker.recordFailure(provider)
                lastError = e
            }

            delay(500)
        }

        FailoverResult(
            success = false,
            error = lastError?.message ?: "所有 Provider 均不可用",
            attemptedProviders = attemptedProviders
        )
    }

    fun getHealthChecker(): ProviderHealthChecker = healthChecker
}

data class FailoverResult(
    val success: Boolean,
    val result: String? = null,
    val usedProvider: String? = null,
    val error: String? = null,
    val attemptedProviders: List<String> = emptyList()
)

// ========== 能力路由表 ==========

class CapabilityRouteTable {

    data class RouteEntry(
        val capability: ProviderCapability,
        val preferredProvider: String,
        val fallbackProviders: List<String>,
        val minQuality: Float = 0.7f
    )

    private val routes = mutableMapOf<ProviderCapability, RouteEntry>()

    fun registerRoute(entry: RouteEntry) {
        routes[entry.capability] = entry
    }

    fun getRoute(capability: ProviderCapability): RouteEntry? = routes[capability]

    fun resolveProvider(
        capability: ProviderCapability,
        healthChecker: ProviderHealthChecker
    ): String? {
        val route = routes[capability] ?: return null

        if (healthChecker.isAvailable(route.preferredProvider)) {
            val health = healthChecker.getHealthStatus(route.preferredProvider)
            if (health.successRate >= route.minQuality) {
                return route.preferredProvider
            }
        }

        return route.fallbackProviders.firstOrNull { healthChecker.isAvailable(it) }
    }

    fun getRegisteredCapabilities(): Set<ProviderCapability> = routes.keys
}

enum class ProviderCapability {
    TEXT_GENERATION,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    RAPID_PROTOTYPING,
    HIGH_QUALITY_RENDERING,
    REAL_TIME_GENERATION
}
