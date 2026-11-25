package com.example.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.data.repository.MediaDownloadRepository
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.VideoContainer
import com.example.domain.usecases.EnqueueMediaDownloadUseCase
import com.example.domain.usecases.InspectMediaUrlUseCase
import com.example.downloader.engine.EnqueueOutcome
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.feature.components.InspectionSheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    private val inspectMediaUrlUseCase: InspectMediaUrlUseCase,
    private val enqueueMediaDownloadUseCase: EnqueueMediaDownloadUseCase,
    private val ytDlpBridge: YtDlpBridge,
    preferencesRepository: UserPreferencesRepository,
    downloadRepository: MediaDownloadRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    val activeDownloadCount: StateFlow<Int> = downloadRepository.activeDownloads
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _inspectionState = MutableStateFlow<InspectionSheetState>(InspectionSheetState.Hidden)
    val inspectionState: StateFlow<InspectionSheetState> = _inspectionState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var activeInspectionJob: Job? = null
    private var activeInspectionProcessId: String? = null

    fun inspectUrl(
        url: String,
        browserDetectedFormats: List<MediaFormat> = emptyList(),
        pageTitleHint: String? = null
    ) {
        activeInspectionProcessId?.let { ytDlpBridge.cancelProcess(it) }
        activeInspectionJob?.cancel()

        val processId = "inspect_${UUID.randomUUID()}"
        activeInspectionProcessId = processId
        _inspectionState.value = InspectionSheetState.Analyzing(url)

        activeInspectionJob = viewModelScope.launch {
            try {
                val result = inspectMediaUrlUseCase(
                    rawUrl = url,
                    browserDetectedFormats = browserDetectedFormats,
                    pageTitleHint = pageTitleHint,
                    processId = processId
                )
                _inspectionState.value = when (result) {
                    is MediaInspectionResult.Success -> InspectionSheetState.PreviewReady(result.mediaInfo)
                    is MediaInspectionResult.Restricted -> InspectionSheetState.Restricted(
                        mediaInfo = result.mediaInfo,
                        reason = result.reason,
                        guidance = result.guidance
                    )
                    is MediaInspectionResult.Error -> InspectionSheetState.Failed(
                        url = url,
                        errorMessage = result.message
                    )
                }
            } catch (_: CancellationException) {
                _inspectionState.value = InspectionSheetState.Hidden
            } finally {
                if (activeInspectionProcessId == processId) {
                    activeInspectionProcessId = null
                }
            }
        }
    }

    fun cancelInspection() {
        activeInspectionProcessId?.let { ytDlpBridge.cancelProcess(it) }
        activeInspectionProcessId = null
        activeInspectionJob?.cancel()
        activeInspectionJob = null
        _inspectionState.value = InspectionSheetState.Hidden
    }

    fun dismissInspection() {
        _inspectionState.value = InspectionSheetState.Hidden
    }

    fun startDownload(
        mediaInfo: MediaInfo,
        format: MediaFormat,
        outputContainer: VideoContainer,
        isPrivateFolder: Boolean
    ) {
        viewModelScope.launch {
            val outcome = enqueueMediaDownloadUseCase(
                mediaInfo = mediaInfo,
                format = format,
                outputContainer = outputContainer,
                isPrivateFolder = isPrivateFolder
            )
            when (outcome) {
                is EnqueueOutcome.Enqueued -> {
                    _inspectionState.value = InspectionSheetState.Hidden
                    val containerTag = if (!format.isAudioOnly && format.hasVideo && format.isYtDlpManaged) {
                        " ${outputContainer.displayName}"
                    } else ""
                    _snackbarMessages.tryEmit("Queued \"${mediaInfo.title}\" (${format.resolution}$containerTag)")
                }
                is EnqueueOutcome.Duplicate -> {
                    _snackbarMessages.tryEmit(outcome.message)
                }
                is EnqueueOutcome.Rejected -> {
                    _snackbarMessages.tryEmit(outcome.reason)
                }
            }
        }
    }

    fun emitMessage(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    class Factory(
        private val inspectMediaUrlUseCase: InspectMediaUrlUseCase,
        private val enqueueMediaDownloadUseCase: EnqueueMediaDownloadUseCase,
        private val ytDlpBridge: YtDlpBridge,
        private val preferencesRepository: UserPreferencesRepository,
        private val downloadRepository: MediaDownloadRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                inspectMediaUrlUseCase,
                enqueueMediaDownloadUseCase,
                ytDlpBridge,
                preferencesRepository,
                downloadRepository
            ) as T
        }
    }
}
