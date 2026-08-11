package io.legado.app.video.states

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 状态管理 - 借鉴 ArcReel 的 Zustand 模式
 *
 * ArcReel 使用 Zustand stores (projects-store, cost-store, app-store, config-status-store)
 * 我们在 Kotlin Multiplatform 中用 MutableStateFlow + StateFlow 实现相同模式
 *
 * 设计原则：
 * - 单一数据源 (Single Source of Truth)
 * - 不可变状态更新 (Immutable State Updates)
 * - 订阅式访问 (Subscribe via collect)
 */

interface Store<T> {
    val state: StateFlow<T>
    fun getCurrent(): T
}

class ProjectsStore private constructor() : Store<ProjectsState> {

    private val _state = MutableStateFlow(ProjectsState())
    override val state: StateFlow<ProjectsState> = _state.asStateFlow()
    override fun getCurrent(): ProjectsState = _state.value

    fun addProject(project: ProjectSummary) {
        _state.update { it.copy(projects = it.projects + project) }
    }

    fun removeProject(projectId: String) {
        _state.update { it.copy(projects = it.projects.filter { it.projectId != projectId }) }
    }

    fun updateProject(projectId: String, update: (ProjectSummary) -> ProjectSummary) {
        _state.update { state ->
            state.copy(projects = state.projects.map {
                if (it.projectId == projectId) update(it) else it
            })
        }
    }

    fun setActiveProject(projectId: String?) {
        _state.update { it.copy(activeProjectId = projectId) }
    }

    fun updateProjectStatus(projectId: String, status: ProjectStatus) {
        updateProject(projectId) { it.copy(status = status) }
    }

    fun clear() {
        _state.value = ProjectsState()
    }

    companion object {
        val instance = ProjectsStore()
    }
}

data class ProjectsState(
    val projects: List<ProjectSummary> = emptyList(),
    val activeProjectId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ProjectSummary(
    val projectId: String,
    val name: String,
    val overview: String? = null,
    val status: ProjectStatus? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProjectStatus(
    val currentPhase: String,
    val phaseProgress: Float = 0f,
    val episodeCount: Int = 0,
    val completedSegments: Int = 0,
    val totalSegments: Int = 0,
    val estimatedCost: Double = 0.0,
    val actualCost: Double? = null
)

class CostStore private constructor() : Store<CostState> {

    private val _state = MutableStateFlow(CostState())
    override val state: StateFlow<CostState> = _state.asStateFlow()
    override fun getCurrent(): CostState = _state.value

    fun addRecord(record: CostRecord) {
        _state.update { it.copy(records = it.records + record) }
    }

    fun updateRecord(recordId: String, actualCost: Double) {
        _state.update { state ->
            state.copy(records = state.records.map {
                if (it.recordId == recordId) it.copy(actualCost = actualCost) else it
            })
        }
    }

    fun getProjectCost(projectId: String): ProjectCostSummary {
        val records = _state.value.records.filter { it.projectId == projectId }
        return ProjectCostSummary(
            projectId = projectId,
            estimatedTotal = records.sumOf { it.estimatedCost },
            actualTotal = records.mapNotNull { it.actualCost }.sum(),
            recordCount = records.size,
            byProvider = records.groupBy { it.providerKey }.mapValues { (_, records) ->
                records.sumOf { it.estimatedCost }
            }
        )
    }

    fun clearProject(projectId: String) {
        _state.update { it.copy(records = it.records.filter { it.projectId != projectId }) }
    }

    fun clear() {
        _state.value = CostState()
    }

    companion object {
        val instance = CostStore()
    }
}

data class CostState(
    val records: List<CostRecord> = emptyList(),
    val totalEstimated: Double = 0.0,
    val totalActual: Double = 0.0,
    val currency: String = "USD"
)

data class CostRecord(
    val recordId: String,
    val projectId: String,
    val providerKey: String,
    val model: String,
    val operation: String,
    val estimatedCost: Double,
    val actualCost: Double? = null,
    val currency: String = "USD",
    val createdAt: Long = System.currentTimeMillis()
)

data class ProjectCostSummary(
    val projectId: String,
    val estimatedTotal: Double,
    val actualTotal: Double,
    val recordCount: Int,
    val byProvider: Map<String, Double>
)

class AppStore private constructor() : Store<AppState> {

    private val _state = MutableStateFlow(AppState())
    override val state: StateFlow<AppState> = _state.asStateFlow()
    override fun getCurrent(): AppState = _state.value

    fun setConfiguredProviders(providers: List<String>) {
        _state.update { it.copy(configuredProviders = providers) }
    }

    fun setActiveProvider(providerKey: String) {
        _state.update { it.copy(activeProvider = providerKey) }
    }

    fun setIsGenerating(isGenerating: Boolean) {
        _state.update { it.copy(isGenerating = isGenerating) }
    }

    fun setAIThinking(thinking: Boolean) {
        _state.update { it.copy(aiThinking = thinking) }
    }

    fun pushToast(message: String, type: ToastType = ToastType.SUCCESS) {
        val toast = Toast(message, type, System.currentTimeMillis())
        _state.update { it.copy(toasts = it.toasts + toast) }
    }

    fun dismissToast(timestamp: Long) {
        _state.update { it.copy(toasts = it.toasts.filter { it.timestamp != timestamp }) }
    }

    fun setConnectionStatus(status: ConnectionStatus) {
        _state.update { it.copy(connectionStatus = status) }
    }

    companion object {
        val instance = AppStore()
    }
}

data class AppState(
    val configuredProviders: List<String> = emptyList(),
    val activeProvider: String? = null,
    val isGenerating: Boolean = false,
    val aiThinking: Boolean = false,
    val toasts: List<Toast> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val apiConfigured: Boolean = false
)

data class Toast(
    val message: String,
    val type: ToastType,
    val timestamp: Long
)

enum class ToastType {
    SUCCESS,
    ERROR,
    INFO,
    WARNING
}

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

class ConfigStatusStore private constructor() : Store<ConfigStatusState> {

    private val _state = MutableStateFlow(ConfigStatusState())
    override val state: StateFlow<ConfigStatusState> = _state.asStateFlow()
    override fun getCurrent(): ConfigStatusState = _state.value

    fun setTextProviderConfigured(configured: Boolean) {
        _state.update { it.copy(textProviderConfigured = configured) }
    }

    fun setImageProviderConfigured(configured: Boolean) {
        _state.update { it.copy(imageProviderConfigured = configured) }
    }

    fun setVideoProviderConfigured(configured: Boolean) {
        _state.update { it.copy(videoProviderConfigured = configured) }
    }

    fun setAgentConfigured(configured: Boolean) {
        _state.update { it.copy(agentConfigured = configured) }
    }

    fun isFullyConfigured(): Boolean = _state.value.isComplete()

    companion object {
        val instance = ConfigStatusStore()
    }
}

data class ConfigStatusState(
    val textProviderConfigured: Boolean = false,
    val imageProviderConfigured: Boolean = false,
    val videoProviderConfigured: Boolean = false,
    val agentConfigured: Boolean = false
) {
    fun isComplete(): Boolean =
        textProviderConfigured && imageProviderConfigured && videoProviderConfigured && agentConfigured

    fun missingComponents(): List<String> {
        val missing = mutableListOf<String>()
        if (!agentConfigured) missing.add("AI 助手")
        if (!textProviderConfigured) missing.add("文本生成")
        if (!imageProviderConfigured) missing.add("图像生成")
        if (!videoProviderConfigured) missing.add("视频生成")
        return missing
    }
}
