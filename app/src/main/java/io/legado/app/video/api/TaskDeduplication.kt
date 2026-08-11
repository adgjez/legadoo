package io.legado.app.video.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 任务去重 + 死锁防护
 *
 * 借鉴 ArcReel 的任务队列改进：
 * - 唯一索引：(project, task_type, resource_id, script_file) 防止重复
 * - 死锁防护：超时自动释放、优先级调度、依赖拓扑排序
 * - 幂等操作：重复提交返回同一个任务 ID
 */

data class TaskDedupeKey(
    val projectId: String,
    val taskType: String,
    val resourceId: String,
    val scriptFile: String
) {
    fun toCompositeKey(): String = "$projectId:$taskType:$resourceId:$scriptFile"
}

class TaskDeduplicationManager {

    private val activeTasks = mutableSetOf<String>()
    private val taskIdMap = mutableMapOf<String, String>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun tryAcquire(key: TaskDedupeKey): String? {
        return mutex.withLock {
            val compositeKey = key.toCompositeKey()
            if (compositeKey in activeTasks) {
                taskIdMap[compositeKey]
            } else {
                val taskId = "task_${System.currentTimeMillis()}_${activeTasks.size}"
                activeTasks.add(compositeKey)
                taskIdMap[compositeKey] = taskId
                null
            }
        }
    }

    suspend fun complete(key: TaskDedupeKey) {
        mutex.withLock {
            val compositeKey = key.toCompositeKey()
            activeTasks.remove(compositeKey)
            taskIdMap.remove(compositeKey)
        }
    }

    suspend fun forceRelease(key: TaskDedupeKey) {
        mutex.withLock {
            activeTasks.remove(key.toCompositeKey())
        }
    }

    fun isActive(key: TaskDedupeKey): Boolean {
        return activeTasks.contains(key.toCompositeKey())
    }

    fun getActiveCount(): Int = activeTasks.size
}

/**
 * 死锁防护任务调度器
 */

class DeadlockSafeScheduler(
    private val maxConcurrent: Int = 5,
    private val timeoutMs: Long = 300_000L,
    private val deadlockDetectionIntervalMs: Long = 30_000L
) {
    private val runningTasks = mutableMapOf<String, Long>()
    private val pendingTasks = mutableListOf<ScheduledTask>()
    private val completedTasks = mutableSetOf<String>()

    data class ScheduledTask(
        val taskId: String,
        val priority: Int,
        val dependencies: List<String>,
        val action: suspend () -> Result<Any>,
        val timeoutMs: Long = 300_000L
    )

    suspend fun schedule(task: ScheduledTask): String {
        synchronized(runningTasks) {
            if (runningTasks.size >= maxConcurrent) {
                pendingTasks.add(task)
                return task.taskId
            }
            runningTasks[task.taskId] = System.currentTimeMillis()
        }

        executeWithTimeout(task)
        return task.taskId
    }

    private suspend fun executeWithTimeout(task: ScheduledTask) {
        try {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(task.timeoutMs) {
                    task.action()
                }
            }
            result.fold(
                onSuccess = { completedTasks.add(task.taskId) },
                onFailure = { runningTasks.remove(task.taskId) }
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            runningTasks.remove(task.taskId)
            completedTasks.add(task.taskId)
        } catch (e: Exception) {
            runningTasks.remove(task.taskId)
        }
    }

    suspend fun detectAndResolveDeadlocks(): List<String> {
        val timedOut = mutableListOf<String>()
        val now = System.currentTimeMillis()

        synchronized(runningTasks) {
            val iterator = runningTasks.entries.iterator()
            while (iterator.hasNext()) {
                val (taskId, startTime) = iterator.next()
                if (now - startTime > timeoutMs) {
                    timedOut.add(taskId)
                    iterator.remove()
                }
            }
        }

        return timedOut
    }

    fun getActiveTaskCount(): Int = runningTasks.size
    fun getPendingCount(): Int = pendingTasks.size
    fun getCompletedCount(): Int = completedTasks.size

    fun isTaskCompleted(taskId: String): Boolean = completedTasks.contains(taskId)

    suspend fun drainPending() {
        val readyTasks = synchronized(pendingTasks) {
            pendingTasks.filter { task ->
                task.dependencies.all { completedTasks.contains(it) }
            }.sortedBy { -it.priority }
        }

        readyTasks.forEach { task ->
            synchronized(pendingTasks) {
                pendingTasks.remove(task)
            }
            schedule(task)
        }
    }
}

/**
 * 任务状态持久化
 */
data class PersistedTask(
    val taskId: String,
    val projectId: String,
    val taskType: String,
    val status: String,
    val resourceId: String?,
    val progress: Int,
    val resultUrl: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int,
    val priority: Int
) {
    fun isFinal(): Boolean = status in listOf("completed", "failed", "cancelled")
}

class TaskStateManager {

    private val tasks = mutableMapOf<String, PersistedTask>()
    private val listeners = mutableListOf<(PersistedTask) -> Unit>()

    fun create(task: PersistedTask): String {
        tasks[task.taskId] = task
        notifyListeners(task)
        return task.taskId
    }

    fun update(taskId: String, update: (PersistedTask) -> PersistedTask) {
        tasks[taskId]?.let { current ->
            val updated = update(current.copy(updatedAt = System.currentTimeMillis()))
            tasks[taskId] = updated
            notifyListeners(updated)
        }
    }

    fun get(taskId: String): PersistedTask? = tasks[taskId]

    fun getProjectTasks(projectId: String): List<PersistedTask> {
        return tasks.values.filter { it.projectId == projectId }
    }

    fun getActiveTasks(projectId: String): List<PersistedTask> {
        return tasks.values.filter {
            it.projectId == projectId && !it.isFinal()
        }
    }

    fun subscribe(listener: (PersistedTask) -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners(task: PersistedTask) {
        listeners.forEach { listener ->
            try {
                listener(task)
            } catch (_: Exception) { }
        }
    }

    fun getStats(projectId: String): TaskStats {
        val projectTasks = getProjectTasks(projectId)
        return TaskStats(
            total = projectTasks.size,
            completed = projectTasks.count { it.status == "completed" },
            failed = projectTasks.count { it.status == "failed" },
            active = projectTasks.count { !it.isFinal() },
            pending = projectTasks.count { it.status == "pending" || it.status == "queued" }
        )
    }
}

data class TaskStats(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val active: Int,
    val pending: Int
)
