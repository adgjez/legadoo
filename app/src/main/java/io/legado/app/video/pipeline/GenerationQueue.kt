package io.legado.app.video.pipeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * 批量生成队列
 *
 * 借鉴 ArcReel 的任务队列设计：
 * - Image/Video 独立并发通道
 * - RPM 速率限制
 * - lease-based 调度
 * - 任务依赖 + 级联失败
 */

enum class QueueChannel {
    IMAGE,
    VIDEO,
    TEXT
}

data class QueueTask(
    val taskId: String,
    val channel: QueueChannel,
    val action: suspend () -> Result<Any>,
    val dependencies: List<String> = emptyList(),
    val priority: Int = 0,
    val maxRetries: Int = 2,
    val timeoutMs: Long = 120_000L,
    var status: TaskStatus = TaskStatus.PENDING,
    var result: Any? = null,
    var error: String? = null,
    var retryCount: Int = 0,
    var createdAt: Long = System.currentTimeMillis()
)

enum class TaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

class GenerationQueue(
    private val imageConcurrency: Int = 3,
    private val videoConcurrency: Int = 2,
    private val textConcurrency: Int = 4,
    private val rpmLimit: Int = 20
) {
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, QueueTask>()
    private val completedIds = mutableSetOf<String>()
    private val failedIds = mutableSetOf<String>()

    private val imageSemaphore = java.util.concurrent.Semaphore(imageConcurrency)
    private val videoSemaphore = java.util.concurrent.Semaphore(videoConcurrency)
    private val textSemaphore = java.util.concurrent.Semaphore(textConcurrency)

    private var requestCount = 0
    private var windowStart = System.currentTimeMillis()

    fun enqueue(task: QueueTask): String {
        tasks[task.taskId] = task
        return task.taskId
    }

    fun enqueueBatch(tasks: List<QueueTask>): List<String> {
        tasks.forEach { this.tasks[it.taskId] = it }
        return tasks.map { it.taskId }
    }

    suspend fun execute(taskId: String): Result<Any> = withContext(Dispatchers.IO) {
        val task = tasks[taskId] ?: return@withContext Result.failure(IllegalStateException("Task not found"))

        task.status = TaskStatus.RUNNING

        val semaphore = when (task.channel) {
            QueueChannel.IMAGE -> imageSemaphore
            QueueChannel.VIDEO -> videoSemaphore
            QueueChannel.TEXT -> textSemaphore
        }

        semaphore.acquire()
        try {
            rateLimitWait()

            for (attempt in 0..task.maxRetries) {
                val result = task.action()
                result.fold(
                    onSuccess = { value ->
                        task.status = TaskStatus.COMPLETED
                        task.result = value
                        completedIds.add(taskId)
                        return@withContext Result.success(value)
                    },
                    onFailure = { error ->
                        task.retryCount = attempt + 1
                        task.error = error.message
                        if (attempt < task.maxRetries) {
                            delay(1000L * (attempt + 1))
                        } else {
                            task.status = TaskStatus.FAILED
                            failedIds.add(taskId)
                            handleCascadeFailure(taskId)
                            return@withContext Result.failure(error)
                        }
                    }
                )
            }
            Result.failure(IllegalStateException("Task failed after ${task.maxRetries} retries"))
        } finally {
            semaphore.release()
        }
    }

    private suspend fun rateLimitWait() {
        val now = System.currentTimeMillis()
        if (now - windowStart > 60_000) {
            requestCount = 0
            windowStart = now
        }
        requestCount++
        if (requestCount > rpmLimit) {
            delay(60_000 - (now - windowStart))
            requestCount = 0
            windowStart = System.currentTimeMillis()
        }
    }

    private fun handleCascadeFailure(failedTaskId: String) {
        val dependentTasks = tasks.filter { (_, task) ->
            task.dependencies.contains(failedTaskId) && task.status == TaskStatus.PENDING
        }
        dependentTasks.forEach { (_, task) ->
            task.status = TaskStatus.CANCELLED
            task.error = "Dependency '$failedTaskId' failed"
            failedIds.add(task.taskId)
        }
    }

    suspend fun executeBatch(taskIds: List<String>): Map<String, Result<Any>> {
        val results = mutableMapOf<String, Result<Any>>()

        taskIds.forEach { taskId ->
            val task = tasks[taskId]
            if (task != null && task.dependencies.all { completedIds.contains(it) }) {
                results[taskId] = execute(taskId)
            } else {
                results[taskId] = Result.failure(IllegalStateException("Dependencies not met"))
            }
        }

        return results
    }

    fun getTask(taskId: String): QueueTask? = tasks[taskId]

    fun getPendingTasks(channel: QueueChannel? = null): List<QueueTask> {
        return tasks.values.filter { task ->
            task.status == TaskStatus.PENDING &&
                (channel == null || task.channel == channel) &&
                task.dependencies.all { completedIds.contains(it) }
        }.sortedBy { it.priority }
    }

    fun getStats(): QueueStats {
        return QueueStats(
            total = tasks.size,
            pending = tasks.values.count { it.status == TaskStatus.PENDING },
            running = tasks.values.count { it.status == TaskStatus.RUNNING },
            completed = tasks.values.count { it.status == TaskStatus.COMPLETED },
            failed = tasks.values.count { it.status == TaskStatus.FAILED },
            imageConcurrency = imageConcurrency,
            videoConcurrency = videoConcurrency,
            activeImage = imageConcurrency - imageSemaphore.availablePermits(),
            activeVideo = videoConcurrency - videoSemaphore.availablePermits()
        )
    }

    fun cancelTask(taskId: String) {
        tasks[taskId]?.status = TaskStatus.CANCELLED
    }

    fun clear() {
        tasks.clear()
        completedIds.clear()
        failedIds.clear()
    }
}

data class QueueStats(
    val total: Int,
    val pending: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
    val imageConcurrency: Int,
    val videoConcurrency: Int,
    val activeImage: Int,
    val activeVideo: Int
)
