package com.example.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MediaDownloadRepository
import com.example.domain.models.DownloadTask
import com.example.domain.models.LibraryFile
import com.example.domain.models.LibrarySortOrder
import com.example.domain.models.MediaType
import com.example.domain.models.ProcessingOperation
import com.example.domain.models.ProcessingTask
import com.example.domain.usecases.ProcessLibraryMediaUseCase
import com.example.downloader.engine.DownloadManager
import com.example.processing.router.MediaProcessingRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TasksMode { DOWNLOADS, LIBRARY }
enum class DownloadsTab { ACTIVE, COMPLETED, FAILED }

data class TasksUiState(
    val mode: TasksMode = TasksMode.DOWNLOADS,
    val downloadsTab: DownloadsTab = DownloadsTab.ACTIVE,
    val activeDownloads: List<DownloadTask> = emptyList(),
    val completedDownloads: List<DownloadTask> = emptyList(),
    val failedDownloads: List<DownloadTask> = emptyList(),
    val processingTasks: List<ProcessingTask> = emptyList(),
    val libraryFilter: MediaType? = null, // null = All, or VIDEO / IMAGE / AUDIO
    val librarySearchQuery: String = "",
    val librarySortOrder: LibrarySortOrder = LibrarySortOrder.DATE_DESC,
    val isLibraryGrid: Boolean = true,
    val filteredLibraryFiles: List<LibraryFile> = emptyList(),
    val totalLibraryCount: Int = 0
)

class TasksViewModel(
    private val downloadRepository: MediaDownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager,
    private val processingRouter: MediaProcessingRouter,
    private val processLibraryMediaUseCase: ProcessLibraryMediaUseCase
) : ViewModel() {

    private val modeFlow = MutableStateFlow(TasksMode.DOWNLOADS)
    private val downloadsTabFlow = MutableStateFlow(DownloadsTab.ACTIVE)
    private val libraryFilterFlow = MutableStateFlow<MediaType?>(null)
    private val librarySearchFlow = MutableStateFlow("")
    private val librarySortFlow = MutableStateFlow(LibrarySortOrder.DATE_DESC)
    private val isGridFlow = MutableStateFlow(true)

    private val downloadStreamsFlow = combine(
        downloadRepository.activeDownloads,
        downloadRepository.completedDownloads,
        downloadRepository.failedDownloads,
        downloadRepository.processingTasks
    ) { active, completed, failed, processing ->
        DownloadCollections(active, completed, failed, processing)
    }

    private val libraryControlsFlow = combine(
        libraryFilterFlow,
        librarySearchFlow,
        librarySortFlow,
        isGridFlow,
        libraryRepository.libraryFiles
    ) { filter, query, sort, isGrid, files ->
        val filtered = files
            .filter { file -> filter == null || file.mediaType == filter }
            .filter { file ->
                query.isBlank() ||
                    file.title.contains(query, ignoreCase = true) ||
                    file.platform.displayName.contains(query, ignoreCase = true) ||
                    file.fileFormat.contains(query, ignoreCase = true)
            }
            .let { list ->
                when (sort) {
                    LibrarySortOrder.DATE_DESC -> list.sortedByDescending { it.dateAdded }
                    LibrarySortOrder.DATE_ASC -> list.sortedBy { it.dateAdded }
                    LibrarySortOrder.NAME_ASC -> list.sortedBy { it.title.lowercase() }
                    LibrarySortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                }
            }
        LibraryViewData(filter, query, sort, isGrid, filtered, files.size)
    }

    val uiState: StateFlow<TasksUiState> = combine(
        modeFlow,
        downloadsTabFlow,
        downloadStreamsFlow,
        libraryControlsFlow
    ) { mode, tab, downloads, library ->
        TasksUiState(
            mode = mode,
            downloadsTab = tab,
            activeDownloads = downloads.active,
            completedDownloads = downloads.completed,
            failedDownloads = downloads.failed,
            processingTasks = downloads.processing,
            libraryFilter = library.filter,
            librarySearchQuery = library.query,
            librarySortOrder = library.sort,
            isLibraryGrid = library.isGrid,
            filteredLibraryFiles = library.filtered,
            totalLibraryCount = library.totalCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState()
    )

    fun setMode(mode: TasksMode) {
        modeFlow.value = mode
    }

    fun setDownloadsTab(tab: DownloadsTab) {
        downloadsTabFlow.value = tab
    }

    fun setLibraryFilter(filter: MediaType?) {
        libraryFilterFlow.value = filter
    }

    fun setLibrarySearch(query: String) {
        librarySearchFlow.value = query
    }

    fun setLibrarySort(order: LibrarySortOrder) {
        librarySortFlow.value = order
    }

    fun toggleLibraryGrid() {
        isGridFlow.value = !isGridFlow.value
    }

    fun pauseDownload(taskId: String) = downloadManager.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = downloadManager.resumeDownload(taskId)
    fun retryDownload(taskId: String) = downloadManager.retryDownload(taskId)
    fun cancelDownload(taskId: String) = downloadManager.cancelDownload(taskId)
    fun removeDownload(taskId: String, deleteFile: Boolean = false) =
        downloadManager.removeFromHistory(taskId, deleteFile)

    fun clearFailedDownloads() {
        viewModelScope.launch { downloadRepository.clearFailedHistory() }
    }

    fun deleteLibraryFile(file: LibraryFile) {
        viewModelScope.launch { libraryRepository.deleteLibraryFile(file) }
    }

    fun processMediaFile(
        file: LibraryFile,
        operation: ProcessingOperation,
        preferMp3: Boolean,
        trimStartMs: Long,
        trimEndMs: Long?
    ) {
        viewModelScope.launch {
            processLibraryMediaUseCase(
                file = file,
                operation = operation,
                preferMp3Audio = preferMp3,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs
            )
        }
    }

    fun cancelProcessing(taskId: String) = processingRouter.cancelProcessing(taskId)

    fun dismissProcessingTask(taskId: String) {
        viewModelScope.launch { downloadRepository.removeProcessingTask(taskId) }
    }

    private data class DownloadCollections(
        val active: List<DownloadTask>,
        val completed: List<DownloadTask>,
        val failed: List<DownloadTask>,
        val processing: List<ProcessingTask>
    )

    private data class LibraryViewData(
        val filter: MediaType?,
        val query: String,
        val sort: LibrarySortOrder,
        val isGrid: Boolean,
        val filtered: List<LibraryFile>,
        val totalCount: Int
    )

    class Factory(
        private val downloadRepository: MediaDownloadRepository,
        private val libraryRepository: LibraryRepository,
        private val downloadManager: DownloadManager,
        private val processingRouter: MediaProcessingRouter,
        private val processLibraryMediaUseCase: ProcessLibraryMediaUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TasksViewModel(
                downloadRepository,
                libraryRepository,
                downloadManager,
                processingRouter,
                processLibraryMediaUseCase
            ) as T
        }
    }
}
