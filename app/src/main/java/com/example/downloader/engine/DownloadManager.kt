package com.example.downloader.engine

import com.example.core.common.NetworkMonitor
import com.example.core.common.UrlValidator
import com.example.core.database.DownloadTaskDao
import com.example.core.database.DownloadTaskEntity
import com.example.core.database.MediaDao
import com.example.core.database.MediaEntity
import com.example.core.database.MediaFormatEntity
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.notifications.KlipzNotificationManager
import com.example.core.storage.MediaStoreManager
import com.example.domain.models.DownloadStatus
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaType
import com.example.domain.models.VideoContainer
import com.example.downloader.providers.MediaProviderRegistry
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.downloader.ytdlp.YtDlpDownloadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface EnqueueOutcome {
    data class Enqueued(val taskId: String) : EnqueueOutcome
    data class Duplicate(val existingTaskId: String, val message: String) : EnqueueOutcome
    data class Rejected(val reason: String) : EnqueueOutcome
}

/**
 * Coordinates the Klipz download lifecycle across both direct OkHttp resumable transfers and
 * embedded Python + yt-dlp + QuickJS/EJS + FFmpeg downloads (auto-merging high-resolution
 * adaptive video+audio into MP4 or MKV at full native resolution).
 */
class DownloadManager(
    private val mediaDao: MediaDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadEngine: DownloadEngine,
    private val downloadQueue: DownloadQueue,
    private val downloadScheduler: DownloadScheduler,
    private val downloadRecovery: DownloadRecovery,
    private val storageManager: MediaStoreManager,
    private val notificationManager: KlipzNotificationManager,
    private val providerRegistry: MediaProviderRegistry,
    private val ytDlpBridge: YtDlpBridge,
    private val networkMonitor: NetworkMonitor
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ephemeral transfer URLs kept strictly in memory so temporary signed links are never stored long-term in Room
    private val ephemeralTransferUrls = ConcurrentHashMap<String, String>()
    private val pauseFlags = ConcurrentHashMap.newKeySet<String>()
    private val cancelFlags = ConcurrentHashMap.newKeySet<String>()
    private val runningJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            downloadRecovery.recoverInterruptedTasks()
        }
        scope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                downloadQueue.updateConcurrency(prefs.maxSimultaneousDownloads)
                if (prefs.autoQueueProcessing) {
                    pumpQueue()
                }
            }
        }
    }

    suspend fun enqueueDownload(
        mediaInfo: MediaInfo,
        format: MediaFormat,
        outputContainer: VideoContainer = VideoContainer.MP4,
        isPrivateFolder: Boolean,
        priority: Int = 0
    ): EnqueueOutcome {
        val useYtDlp = format.isYtDlpManaged && !format.ytDlpFormatSelector.isNullOrBlank()
        val resolvedUrl = format.directUrl
        if (!useYtDlp && (resolvedUrl.isNullOrBlank() || !UrlValidator.isValidHttpUrl(resolvedUrl))) {
            return EnqueueOutcome.Rejected("No downloadable media stream available for ${format.label}.")
        }

        val effectiveFormatId = if (useYtDlp && !format.isAudioOnly && format.hasVideo) {
            "${format.formatId}_${outputContainer.extension}"
        } else {
            format.formatId
        }

        val duplicate = downloadTaskDao.findDuplicateTask(mediaInfo.sourceUrl, effectiveFormatId)
        if (duplicate != null) {
            return EnqueueOutcome.Duplicate(
                existingTaskId = duplicate.id,
                message = "\"${duplicate.title}\" (${duplicate.qualityLabel} ${duplicate.fileFormat}) is already in your queue or completed."
            )
        }

        val estimatedSize = format.fileSizeBytes ?: 0L
        if (!storageManager.hasSufficientStorage(estimatedSize)) {
            return EnqueueOutcome.Rejected("Insufficient storage space available on device.")
        }

        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val mediaType = when {
            format.isAudioOnly -> MediaType.AUDIO
            format.mimeType.startsWith("image/") -> MediaType.IMAGE
            else -> mediaInfo.mediaType
        }
        val effectiveFileFormat = when {
            useYtDlp && !format.isAudioOnly && format.hasVideo -> outputContainer.displayName
            else -> format.container
        }
        val effectiveMimeType = when {
            useYtDlp && !format.isAudioOnly && format.hasVideo -> outputContainer.mimeType
            else -> format.mimeType
        }

        mediaDao.insertMedia(
            MediaEntity(
                id = mediaInfo.id,
                title = mediaInfo.title,
                sourceUrl = mediaInfo.sourceUrl,
                platform = mediaInfo.platform.name,
                mediaType = mediaType.name,
                thumbnail = mediaInfo.thumbnailUrl,
                duration = mediaInfo.durationMs,
                createdAt = now
            )
        )
        mediaDao.insertFormats(
            mediaInfo.formats.map { fmt ->
                MediaFormatEntity(
                    mediaId = mediaInfo.id,
                    formatId = fmt.formatId,
                    resolution = fmt.resolution,
                    codec = fmt.codec,
                    mimeType = fmt.mimeType,
                    fileSize = fmt.fileSizeBytes
                )
            }
        )

        val entity = DownloadTaskEntity(
            id = taskId,
            mediaId = mediaInfo.id,
            formatId = effectiveFormatId,
            title = mediaInfo.title,
            sourceUrl = mediaInfo.sourceUrl,
            platform = mediaInfo.platform.name,
            mediaType = mediaType.name,
            thumbnailUrl = mediaInfo.thumbnailUrl,
            qualityLabel = format.resolution,
            fileFormat = effectiveFileFormat,
            mimeType = effectiveMimeType,
            ytDlpFormatSelector = if (useYtDlp) format.ytDlpFormatSelector else null,
            outputContainer = outputContainer.name,
            status = DownloadStatus.QUEUED.name,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = estimatedSize,
            speedBytesPerSec = 0L,
            etaSeconds = null,
            eTag = null,
            lastModified = null,
            supportsRange = !useYtDlp,
            tempFilePath = null,
            outputUri = null,
            isPrivateFolder = isPrivateFolder,
            priority = priority,
            createdAt = now,
            completedAt = null,
            errorMessage = null
        )

        downloadTaskDao.upsertTask(entity)
        if (!useYtDlp && !resolvedUrl.isNullOrBlank()) {
            ephemeralTransferUrls[taskId] = resolvedUrl
        }
        pauseFlags.remove(taskId)
        cancelFlags.remove(taskId)
        downloadQueue.enqueue(taskId, priority)

        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.autoQueueProcessing) {
            pumpQueue()
        }
        return EnqueueOutcome.Enqueued(taskId)
    }

    fun pauseDownload(taskId: String) {
        pauseFlags.add(taskId)
        downloadQueue.remove(taskId)
        downloadScheduler.cancel(taskId)
        ytDlpBridge.cancelProcess(taskId)
        scope.launch {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED.name, null)
            pumpQueue()
        }
    }

    fun resumeDownload(taskId: String) {
        scope.launch {
            val task = downloadTaskDao.getTaskById(taskId) ?: return@launch
            pauseFlags.remove(taskId)
            cancelFlags.remove(taskId)
            downloadTaskDao.updateStatus(taskId, DownloadStatus.QUEUED.name, null)
            downloadQueue.enqueue(taskId, task.priority + 1)
            pumpQueue()
        }
    }

    fun retryDownload(taskId: String) {
        scope.launch {
            val task = downloadTaskDao.getTaskById(taskId) ?: return@launch
            pauseFlags.remove(taskId)
            cancelFlags.remove(taskId)
            downloadTaskDao.updateStatus(taskId, DownloadStatus.QUEUED.name, null)
            downloadQueue.enqueue(taskId, task.priority)
            pumpQueue()
        }
    }

    fun cancelDownload(taskId: String) {
        cancelFlags.add(taskId)
        downloadQueue.remove(taskId)
        downloadScheduler.cancel(taskId)
        ytDlpBridge.cancelProcess(taskId)
        runningJobs.remove(taskId)?.cancel()
        ephemeralTransferUrls.remove(taskId)
        scope.launch {
            val task = downloadTaskDao.getTaskById(taskId)
            task?.tempFilePath?.let { runCatching { File(it).delete() } }
            runCatching { File(storageManager.getTempDir(), "ytdlp_$taskId").deleteRecursively() }
            downloadTaskDao.updateStatus(taskId, DownloadStatus.CANCELLED.name, "Cancelled by user")
            pumpQueue()
        }
    }

    fun removeFromHistory(taskId: String, deleteSavedFile: Boolean = false) {
        cancelFlags.add(taskId)
        downloadQueue.remove(taskId)
        downloadScheduler.cancel(taskId)
        ytDlpBridge.cancelProcess(taskId)
        runningJobs.remove(taskId)?.cancel()
        ephemeralTransferUrls.remove(taskId)
        scope.launch {
            val task = downloadTaskDao.getTaskById(taskId)
            task?.tempFilePath?.let { runCatching { File(it).delete() } }
            runCatching { File(storageManager.getTempDir(), "ytdlp_$taskId").deleteRecursively() }
            if (deleteSavedFile) {
                storageManager.deleteUri(task?.outputUri)
            }
            downloadTaskDao.deleteById(taskId)
            pumpQueue()
        }
    }

    fun pumpQueue() {
        scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            while (true) {
                val nextId = downloadQueue.pollNextRunnable() ?: break
                val job = scope.launch {
                    executeTaskInternal(nextId)
                }
                runningJobs[nextId] = job
                // Also register background worker safeguard for resilient completion
                downloadScheduler.schedule(nextId, wifiOnly = prefs.wifiOnlyDownloads, preferUserInitiated = true)
            }
        }
    }

    suspend fun runTaskInWorker(taskId: String): Boolean {
        if (runningJobs[taskId]?.isActive == true) {
            runningJobs[taskId]?.join()
            val finalTask = downloadTaskDao.getTaskById(taskId)
            return finalTask?.status == DownloadStatus.COMPLETED.name
        }
        return executeTaskInternal(taskId)
    }

    private suspend fun executeTaskInternal(taskId: String): Boolean {
        try {
            val task = downloadTaskDao.getTaskById(taskId) ?: return false
            if (task.status == DownloadStatus.COMPLETED.name || task.status == DownloadStatus.CANCELLED.name) {
                return task.status == DownloadStatus.COMPLETED.name
            }

            val prefs = preferencesRepository.preferencesFlow.first()
            if (!task.ytDlpFormatSelector.isNullOrBlank()) {
                return executeYtDlpTask(task, prefs)
            }

            val transferUrl = resolveFreshTransferUrl(task)
            if (transferUrl == null) {
                downloadTaskDao.updateStatus(
                    taskId,
                    DownloadStatus.FAILED.name,
                    "Authorized media stream URL expired. Re-inspect source link to resume."
                )
                return false
            }

            downloadTaskDao.updateStatus(taskId, DownloadStatus.DOWNLOADING.name, null)

            val result = downloadEngine.execute(
                request = DownloadTransferRequest(
                    taskId = task.id,
                    downloadUrl = transferUrl,
                    title = task.title,
                    mimeType = task.mimeType,
                    mediaType = MediaType.fromName(task.mediaType),
                    isPrivateFolder = task.isPrivateFolder,
                    existingTempPath = task.tempFilePath,
                    expectedETag = task.eTag,
                    expectedLastModified = task.lastModified,
                    wifiOnly = prefs.wifiOnlyDownloads
                ),
                isPauseRequested = { pauseFlags.contains(taskId) },
                isCancelRequested = { cancelFlags.contains(taskId) },
                onProgress = { prog, eTag, lastMod, tempPath ->
                    downloadTaskDao.updateProgress(
                        id = taskId,
                        status = DownloadStatus.DOWNLOADING.name,
                        progress = prog.fraction,
                        downloadedBytes = prog.downloadedBytes,
                        totalBytes = prog.totalBytes,
                        speedBytesPerSec = prog.speedBytesPerSec,
                        etaSeconds = prog.etaSeconds,
                        eTag = eTag,
                        lastModified = lastMod,
                        supportsRange = prog.supportsRange,
                        tempFilePath = tempPath,
                        errorMessage = null
                    )
                }
            )

            return when (result) {
                is DownloadExecutionResult.Completed -> {
                    ephemeralTransferUrls.remove(taskId)
                    downloadTaskDao.markCompleted(
                        id = taskId,
                        finalBytes = result.finalBytes,
                        outputUri = result.outputUri.toString(),
                        completedAt = System.currentTimeMillis()
                    )
                    notificationManager.notifyCompleted(
                        taskId = taskId,
                        title = task.title,
                        playSound = prefs.playNotificationSound,
                        enabled = prefs.showNotifications
                    )
                    true
                }
                is DownloadExecutionResult.Paused -> {
                    downloadTaskDao.updateProgress(
                        id = taskId,
                        status = DownloadStatus.PAUSED.name,
                        progress = if (result.totalBytes > 0L) {
                            (result.downloadedBytes.toFloat() / result.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else task.progress,
                        downloadedBytes = result.downloadedBytes,
                        totalBytes = result.totalBytes.coerceAtLeast(task.totalBytes),
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        eTag = result.eTag,
                        lastModified = result.lastModified,
                        supportsRange = result.supportsRange,
                        tempFilePath = result.tempFilePath,
                        errorMessage = null
                    )
                    true
                }
                is DownloadExecutionResult.Cancelled -> {
                    ephemeralTransferUrls.remove(taskId)
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.CANCELLED.name, "Cancelled by user")
                    true
                }
                is DownloadExecutionResult.Failed -> {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.FAILED.name, result.reason)
                    notificationManager.notifyFailed(
                        taskId = taskId,
                        title = task.title,
                        reason = result.reason,
                        enabled = prefs.showNotifications
                    )
                    false
                }
            }
        } finally {
            runningJobs.remove(taskId)
            downloadQueue.markFinished(taskId)
            pumpQueue()
        }
    }

    private suspend fun executeYtDlpTask(task: DownloadTaskEntity, prefs: UserPreferences): Boolean {
        val taskId = task.id
        if (cancelFlags.contains(taskId)) {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.CANCELLED.name, "Cancelled by user")
            return true
        }
        if (pauseFlags.contains(taskId)) {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED.name, null)
            return true
        }
        if (!networkMonitor.isConnected()) {
            val reason = "No active internet connection."
            downloadTaskDao.updateStatus(taskId, DownloadStatus.FAILED.name, reason)
            notificationManager.notifyFailed(taskId, task.title, reason, prefs.showNotifications)
            return false
        }
        if (prefs.wifiOnlyDownloads && !networkMonitor.isOnWifiOrUnmetered()) {
            downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED.name, "Waiting for Wi-Fi connection")
            return true
        }

        downloadTaskDao.updateStatus(taskId, DownloadStatus.DOWNLOADING.name, null)
        val isAudio = task.mediaType == MediaType.AUDIO.name
        val container = VideoContainer.fromName(task.outputContainer)
        val selector = task.ytDlpFormatSelector ?: return false

        val result = ytDlpBridge.downloadWithYtDlp(
            sourceUrl = task.sourceUrl,
            formatSelector = selector,
            isAudioOnly = isAudio,
            audioExtension = task.fileFormat.lowercase(Locale.US),
            outputContainer = container,
            stagingDir = storageManager.getTempDir(),
            taskId = taskId,
            onProgress = { prog ->
                downloadTaskDao.updateProgress(
                    id = taskId,
                    status = DownloadStatus.DOWNLOADING.name,
                    progress = prog.fraction,
                    downloadedBytes = prog.downloadedBytes,
                    totalBytes = prog.totalBytes.coerceAtLeast(task.totalBytes),
                    speedBytesPerSec = prog.speedBytesPerSec,
                    etaSeconds = prog.etaSeconds,
                    eTag = null,
                    lastModified = null,
                    supportsRange = false,
                    tempFilePath = null,
                    errorMessage = null
                )
            }
        )

        return when (result) {
            is YtDlpDownloadResult.Completed -> {
                val publishResult = storageManager.publishFile(
                    sourceFile = result.outputFile,
                    title = task.title,
                    mimeType = result.mimeType,
                    mediaType = MediaType.fromName(task.mediaType),
                    isPrivateFolder = task.isPrivateFolder
                )
                runCatching { File(storageManager.getTempDir(), "ytdlp_$taskId").deleteRecursively() }

                publishResult.fold(
                    onSuccess = { outputUri ->
                        downloadTaskDao.markCompleted(
                            id = taskId,
                            finalBytes = result.finalBytes,
                            outputUri = outputUri.toString(),
                            completedAt = System.currentTimeMillis()
                        )
                        notificationManager.notifyCompleted(
                            taskId = taskId,
                            title = task.title,
                            playSound = prefs.playNotificationSound,
                            enabled = prefs.showNotifications
                        )
                        true
                    },
                    onFailure = { err ->
                        val reason = err.localizedMessage ?: "Failed to save merged media to storage."
                        downloadTaskDao.updateStatus(taskId, DownloadStatus.FAILED.name, reason)
                        notificationManager.notifyFailed(taskId, task.title, reason, prefs.showNotifications)
                        false
                    }
                )
            }
            is YtDlpDownloadResult.Cancelled -> {
                if (pauseFlags.contains(taskId)) {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED.name, null)
                } else {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.CANCELLED.name, "Cancelled by user")
                }
                true
            }
            is YtDlpDownloadResult.Failed -> {
                if (pauseFlags.contains(taskId)) {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.PAUSED.name, null)
                    true
                } else if (cancelFlags.contains(taskId)) {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.CANCELLED.name, "Cancelled by user")
                    true
                } else {
                    downloadTaskDao.updateStatus(taskId, DownloadStatus.FAILED.name, result.reason)
                    notificationManager.notifyFailed(taskId, task.title, result.reason, prefs.showNotifications)
                    false
                }
            }
        }
    }

    private suspend fun resolveFreshTransferUrl(task: DownloadTaskEntity): String? {
        ephemeralTransferUrls[task.id]?.let { return it }
        if (UrlValidator.looksLikeDirectMediaUrl(task.sourceUrl)) {
            return task.sourceUrl
        }
        val inspected = providerRegistry.inspect(task.sourceUrl)
        if (inspected is com.example.domain.models.MediaInspectionResult.Success) {
            val match = inspected.mediaInfo.formats.firstOrNull { it.formatId == task.formatId }
                ?: inspected.mediaInfo.formats.firstOrNull()
            val fresh = match?.directUrl
            if (!fresh.isNullOrBlank()) {
                ephemeralTransferUrls[task.id] = fresh
                return fresh
            }
        }
        return null
    }
}
