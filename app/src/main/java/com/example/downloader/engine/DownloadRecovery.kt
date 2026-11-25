package com.example.downloader.engine

import com.example.core.database.DownloadTaskDao
import com.example.domain.models.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Recovers interrupted download tasks after process termination or reboot,
 * keeping database state consistent with temporary files on disk.
 */
class DownloadRecovery(private val downloadTaskDao: DownloadTaskDao) {

    suspend fun recoverInterruptedTasks(): Int = withContext(Dispatchers.IO) {
        val interrupted = downloadTaskDao.getInterruptedTasks()
        interrupted.forEach { task ->
            val tempExists = task.tempFilePath?.let { File(it).exists() } ?: false
            if (tempExists && task.supportsRange) {
                downloadTaskDao.updateStatus(
                    id = task.id,
                    status = DownloadStatus.PAUSED.name,
                    errorMessage = "Paused after app restart — tap Resume to continue."
                )
            } else {
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                downloadTaskDao.updateStatus(
                    id = task.id,
                    status = DownloadStatus.PAUSED.name,
                    errorMessage = "Interrupted — tap Resume to restart transfer."
                )
            }
        }
        interrupted.size
    }
}
