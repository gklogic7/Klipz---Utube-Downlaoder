package com.example.downloader.engine

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

data class QueuedDownloadItem(
    val taskId: String,
    val priority: Int,
    val enqueuedAt: Long = System.currentTimeMillis()
) : Comparable<QueuedDownloadItem> {
    override fun compareTo(other: QueuedDownloadItem): Int {
        val prioCmp = other.priority.compareTo(this.priority)
        return if (prioCmp != 0) prioCmp else this.enqueuedAt.compareTo(other.enqueuedAt)
    }
}

/**
 * Thread-safe priority queue managing pending and active download slots with configurable concurrency.
 */
class DownloadQueue(initialMaxConcurrent: Int = 3) {

    private val lock = Any()
    private val pending = PriorityQueue<QueuedDownloadItem>()
    private val activeIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var maxConcurrent: Int = initialMaxConcurrent.coerceIn(1, 5)
        private set

    fun updateConcurrency(limit: Int) {
        synchronized(lock) {
            maxConcurrent = limit.coerceIn(1, 5)
        }
    }

    fun enqueue(taskId: String, priority: Int = 0): Boolean {
        synchronized(lock) {
            if (activeIds.contains(taskId) || pending.any { it.taskId == taskId }) {
                return false
            }
            pending.add(QueuedDownloadItem(taskId, priority))
            return true
        }
    }

    fun pollNextRunnable(): String? {
        synchronized(lock) {
            if (activeIds.size >= maxConcurrent) return null
            val next = pending.poll() ?: return null
            activeIds.add(next.taskId)
            return next.taskId
        }
    }

    fun markFinished(taskId: String) {
        synchronized(lock) {
            activeIds.remove(taskId)
            pending.removeAll { it.taskId == taskId }
        }
    }

    fun remove(taskId: String) {
        synchronized(lock) {
            pending.removeAll { it.taskId == taskId }
            activeIds.remove(taskId)
        }
    }

    fun isActive(taskId: String): Boolean = activeIds.contains(taskId)

    fun activeCount(): Int = activeIds.size

    fun pendingCount(): Int = synchronized(lock) { pending.size }
}
