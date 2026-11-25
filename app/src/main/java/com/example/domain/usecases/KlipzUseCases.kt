package com.example.domain.usecases

import com.example.core.common.UrlValidator
import com.example.core.datastore.UserPreferencesRepository
import com.example.domain.models.LibraryFile
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.ProcessingOperation
import com.example.domain.models.VideoContainer
import com.example.downloader.engine.DownloadManager
import com.example.downloader.engine.EnqueueOutcome
import com.example.downloader.providers.MediaProviderRegistry
import com.example.processing.router.MediaProcessingRouter
import kotlinx.coroutines.flow.first
import java.util.UUID

class InspectMediaUrlUseCase(
    private val providerRegistry: MediaProviderRegistry
) {
    suspend operator fun invoke(
        rawUrl: String,
        browserDetectedFormats: List<MediaFormat> = emptyList(),
        pageTitleHint: String? = null,
        processId: String = "inspect_${UUID.randomUUID()}"
    ): MediaInspectionResult {
        val normalized = UrlValidator.normalizeUrl(rawUrl)
        if (!UrlValidator.isValidHttpUrl(normalized)) {
            return MediaInspectionResult.Error("Please enter a valid http:// or https:// URL.")
        }
        return providerRegistry.inspect(
            url = normalized,
            browserDetectedFormats = browserDetectedFormats,
            pageTitleHint = pageTitleHint,
            processId = processId
        )
    }
}

class EnqueueMediaDownloadUseCase(
    private val downloadManager: DownloadManager
) {
    suspend operator fun invoke(
        mediaInfo: MediaInfo,
        format: MediaFormat,
        outputContainer: VideoContainer = VideoContainer.MP4,
        isPrivateFolder: Boolean
    ): EnqueueOutcome {
        return downloadManager.enqueueDownload(
            mediaInfo = mediaInfo,
            format = format,
            outputContainer = outputContainer,
            isPrivateFolder = isPrivateFolder
        )
    }
}

class ProcessLibraryMediaUseCase(
    private val router: MediaProcessingRouter,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        file: LibraryFile,
        operation: ProcessingOperation,
        preferMp3Audio: Boolean = false,
        trimStartMs: Long = 0L,
        trimEndMs: Long? = null
    ): String {
        val prefs = preferencesRepository.preferencesFlow.first()
        return router.enqueueProcessing(
            downloadId = file.taskId ?: file.id,
            sourceUri = file.contentUri,
            title = file.title,
            operation = operation,
            preference = prefs.processingEngine,
            preserveOriginalQuality = prefs.preserveOriginalQuality,
            preferMp3Audio = preferMp3Audio,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            isPrivateFolder = file.isPrivateFolder
        )
    }
}
