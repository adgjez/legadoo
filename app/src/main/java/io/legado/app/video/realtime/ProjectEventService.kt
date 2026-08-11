package io.legado.app.video.realtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * SSE 实时事件总线
 *
 * 借鉴 ArcReel 的 ProjectEventService：
 * - 后端数据变更自动推送到前端
 * - 基于 fingerprint 的快照 diffing
 * - 变更提示 pub/sub bus
 * - 支持多订阅者
 */

enum class EventType {
    SNAPSHOT,
    CHANGES,
    TASK_UPDATE,
    PROJECT_UPDATE,
    CHARACTER_UPDATE,
    CLUE_UPDATE,
    SCRIPT_UPDATE,
    VIDEO_COMPLETE,
    COST_UPDATE,
    VERSION_SAVED
}

data class ProjectEvent(
    val eventId: String,
    val type: EventType,
    val projectId: String,
    val data: Map<String, Any?> = emptyMap(),
    val diff: Map<String, Any?>? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSSEFormat(): String = buildString {
        append("event: ${type.name}\n")
        append("data: ")
        append(org.json.JSONObject().apply {
            put("eventId", eventId)
            put("projectId", projectId)
            put("type", type.name)
            put("data", org.json.JSONObject(data))
            put("timestamp", timestamp)
        }.toString())
        append("\n\n")
    }
}

class ProjectEventService {

    private val mutex = Mutex()
    private val subscribers = mutableMapOf<String, MutableList<(ProjectEvent) -> Unit>>()
    private val snapshots = mutableMapOf<String, String>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun subscribe(projectId: String, callback: (ProjectEvent) -> Unit): String {
        val subscriberId = "sub_${System.currentTimeMillis()}_${subscribers.size}"
        mutex.withLock {
            subscribers.getOrPut(projectId) { mutableListOf() }.add(callback)
        }
        return subscriberId
    }

    fun unsubscribe(projectId: String, subscriberId: String) {
        mutex.withLock {
            val list = subscribers[projectId] ?: return@withLock
            val index = list.indexOfFirst { it.hashCode().toString() == subscriberId }
            if (index >= 0) list.removeAt(index)
        }
    }

    suspend fun publish(event: ProjectEvent) {
        mutex.withLock {
            val callbacks = subscribers[event.projectId]?.toList() ?: emptyList()
            callbacks.forEach { callback ->
                try {
                    callback(event)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun publishSnapshot(
        projectId: String,
        snapshot: String,
        changedKeys: Set<String>
    ) {
        val oldSnapshot = snapshots[projectId]
        val diff = computeDiff(oldSnapshot, snapshot, changedKeys)

        snapshots[projectId] = snapshot

        val event = ProjectEvent(
            eventId = "evt_${System.currentTimeMillis()}",
            type = if (oldSnapshot == null) EventType.SNAPSHOT else EventType.CHANGES,
            projectId = projectId,
            data = mapOf("snapshot" to snapshot),
            diff = diff
        )

        publish(event)
    }

    suspend fun publishTaskUpdate(
        projectId: String,
        taskId: String,
        status: String,
        progress: Int,
        resultUrl: String? = null
    ) {
        publish(
            ProjectEvent(
                eventId = "evt_${System.currentTimeMillis()}_$taskId",
                type = EventType.TASK_UPDATE,
                projectId = projectId,
                data = mapOf(
                    "taskId" to taskId,
                    "status" to status,
                    "progress" to progress,
                    "resultUrl" to resultUrl
                )
            )
        )
    }

    suspend fun publishCostUpdate(
        projectId: String,
        estimatedCost: Double,
        actualCost: Double?,
        currency: String = "USD"
    ) {
        publish(
            ProjectEvent(
                eventId = "evt_cost_${System.currentTimeMillis()}",
                type = EventType.COST_UPDATE,
                projectId = projectId,
                data = mapOf(
                    "estimatedCost" to estimatedCost,
                    "actualCost" to actualCost,
                    "currency" to currency
                )
            )
        )
    }

    private fun computeDiff(
        oldSnapshot: String?,
        newSnapshot: String,
        changedKeys: Set<String>
    ): Map<String, Any?> {
        if (oldSnapshot == null) return emptyMap()

        val diff = mutableMapOf<String, Any?>()
        changedKeys.forEach { key ->
            diff[key] = mapOf("changed" to true)
        }
        return diff
    }

    fun getSnapshot(projectId: String): String? = snapshots[projectId]

    fun clear(projectId: String) {
        snapshots.remove(projectId)
        subscribers.remove(projectId)
    }
}

/**
 * 状态机 - 延迟聚焦
 *
 * 借鉴 ArcReel 的 Deferred Workspace Focus：
 * 当用户正在编辑时 AI 修改数据，不自动导航，显示浮动提示横幅
 */

enum class WorkspaceFocusState {
    IDLE,
    EDITING,
    EXTERNAL_CHANGE_PENDING,
    NAVIGATED
}

class DeferredFocusManager {

    private val _state = MutableStateFlow(WorkspaceFocusState.IDLE)
    val state: StateFlow<WorkspaceFocusState> = _state.asStateFlow()

    private val _pendingChanges = MutableStateFlow<List<String>>(emptyList())
    val pendingChanges: StateFlow<List<String>> = _pendingChanges.asStateFlow()

    fun userStartedEditing() {
        _state.value = WorkspaceFocusState.EDITING
    }

    fun userStoppedEditing() {
        if (_state.value == WorkspaceFocusState.EDITING) {
            _state.value = WorkspaceFocusState.IDLE
        }
    }

    fun onExternalChange(targetIds: List<String>) {
        if (_state.value == WorkspaceFocusState.EDITING) {
            _state.value = WorkspaceFocusState.EXTERNAL_CHANGE_PENDING
            _pendingChanges.value = targetIds
        } else {
            _state.value = WorkspaceFocusState.NAVIGATED
        }
    }

    fun acceptChanges() {
        _state.value = WorkspaceFocusState.NAVIGATED
        _pendingChanges.value = emptyList()
    }

    fun dismissChanges() {
        _state.value = WorkspaceFocusState.IDLE
        _pendingChanges.value = emptyList()
    }
}
