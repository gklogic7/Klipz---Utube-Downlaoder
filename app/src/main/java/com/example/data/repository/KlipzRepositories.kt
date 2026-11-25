package com.example.data.repository

import com.example.core.database.BookmarkDao
import com.example.core.database.BookmarkEntity
import com.example.core.database.DownloadTaskDao
import com.example.core.database.DownloadTaskEntity
import com.example.core.database.ProcessingTaskDao
import com.example.core.database.ProcessingTaskEntity
import com.example.core.database.WebHistoryDao
import com.example.core.database.WebHistoryEntity
import com.example.core.storage.MediaStoreManager
import com.example.domain.models.BookmarkItem
import com.example.domain.models.DownloadStatus
import com.example.domain.models.DownloadTask
import com.example.domain.models.LibraryFile
import com.example.domain.models.MediaType
import com.example.domain.models.Platform
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ProcessingOperation
import com.example.domain.models.ProcessingStatus
import com.example.domain.models.ProcessingTask
import com.example.domain.models.VideoContainer
import com.example.domain.models.WebHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun DownloadTaskEntity.toDomain(): DownloadTask = DownloadTask(
    id = id,
    mediaId = mediaId,
    formatId = formatId,
    title = title,
    sourceUrl = sourceUrl,
    platform = Platform.fromName(platform),
    mediaType = MediaType.fromName(mediaType),
    thumbnailUrl = thumbnailUrl,
    qualityLabel = qualityLabel,
    fileFormat = fileFormat,
    mimeType = mimeType,
    outputContainer = VideoContainer.fromName(outputContainer),
    isYtDlpManaged = !ytDlpFormatSelector.isNullOrBlank(),
    status = DownloadStatus.fromName(status),
    progress = progress,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    speedBytesPerSec = speedBytesPerSec,
    etaSeconds = etaSeconds,
    supportsRange = supportsRange,
    outputUri = outputUri,
    isPrivateFolder = isPrivateFolder,
    createdAt = createdAt,
    completedAt = completedAt,
    errorMessage = errorMessage
)

fun ProcessingTaskEntity.toDomain(): ProcessingTask = ProcessingTask(
    id = id,
    downloadId = downloadId,
    title = title,
    selectedEngine = ProcessingEngine.fromName(selectedEngine),
    operation = ProcessingOperation.fromName(operation),
    status = ProcessingStatus.fromName(status),
    inputPath = inputPath,
    outputPath = outputPath,
    progress = progress,
    errorMessage = errorMessage,
    createdAt = createdAt
)

class MediaDownloadRepository(
    private val downloadTaskDao: DownloadTaskDao,
    private val processingTaskDao: ProcessingTaskDao
) {
    val allDownloads: Flow<List<DownloadTask>> =
        downloadTaskDao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    val activeDownloads: Flow<List<DownloadTask>> =
        downloadTaskDao.observeActiveTasks().map { list -> list.map { it.toDomain() } }

    val completedDownloads: Flow<List<DownloadTask>> =
        downloadTaskDao.observeCompletedTasks().map { list -> list.map { it.toDomain() } }

    val failedDownloads: Flow<List<DownloadTask>> =
        downloadTaskDao.observeFailedTasks().map { list -> list.map { it.toDomain() } }

    val processingTasks: Flow<List<ProcessingTask>> =
        processingTaskDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun clearFailedHistory() = downloadTaskDao.clearFailedTasks()

    suspend fun removeProcessingTask(id: String) = processingTaskDao.deleteById(id)
}

class LibraryRepository(
    private val downloadTaskDao: DownloadTaskDao,
    private val storageManager: MediaStoreManager
) {
    val libraryFiles: Flow<List<LibraryFile>> = downloadTaskDao.observeCompletedTasks().map { tasks ->
        withContext(Dispatchers.IO) {
            tasks.mapNotNull { entity ->
                val uri = entity.outputUri ?: return@mapNotNull null
                LibraryFile(
                    id = entity.id,
                    taskId = entity.id,
                    title = entity.title,
                    mediaType = MediaType.fromName(entity.mediaType),
                    mimeType = entity.mimeType,
                    fileFormat = entity.fileFormat,
                    qualityLabel = entity.qualityLabel,
                    platform = Platform.fromName(entity.platform),
                    sizeBytes = entity.totalBytes.coerceAtLeast(entity.downloadedBytes),
                    durationMs = null,
                    contentUri = uri,
                    thumbnailUri = entity.thumbnailUrl ?: if (entity.mediaType == MediaType.IMAGE.name) uri else null,
                    dateAdded = entity.completedAt ?: entity.createdAt,
                    isPrivateFolder = entity.isPrivateFolder,
                    existsOnDisk = storageManager.verifyUriExists(uri)
                )
            }
        }
    }

    suspend fun deleteLibraryFile(file: LibraryFile) {
        storageManager.deleteUri(file.contentUri)
        file.taskId?.let { downloadTaskDao.deleteById(it) }
    }
}

class BrowserDataRepository(
    private val webHistoryDao: WebHistoryDao,
    private val bookmarkDao: BookmarkDao
) {
    val history: Flow<List<WebHistoryItem>> = webHistoryDao.observeHistory().map { list ->
        list.map {
            WebHistoryItem(
                id = it.id,
                url = it.url,
                title = it.title,
                platform = Platform.fromName(it.platform),
                visitedAt = it.visitedAt
            )
        }
    }

    val historyCount: Flow<Int> = webHistoryDao.observeCount()

    val bookmarks: Flow<List<BookmarkItem>> = bookmarkDao.observeBookmarks().map { list ->
        list.map {
            BookmarkItem(
                id = it.id,
                url = it.url,
                title = it.title,
                platform = Platform.fromName(it.platform),
                createdAt = it.createdAt
            )
        }
    }

    val bookmarkCount: Flow<Int> = bookmarkDao.observeCount()

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

    suspend fun recordVisit(url: String, title: String, platform: Platform) {
        webHistoryDao.insert(
            WebHistoryEntity(
                url = url,
                title = title.ifBlank { url },
                platform = platform.name,
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) = webHistoryDao.deleteById(id)

    suspend fun clearHistory() = webHistoryDao.clearAll()

    suspend fun toggleBookmark(url: String, title: String, platform: Platform, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) {
            bookmarkDao.deleteByUrl(url)
        } else {
            bookmarkDao.insert(
                BookmarkEntity(
                    url = url,
                    title = title.ifBlank { url },
                    platform = platform.name,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)
}
