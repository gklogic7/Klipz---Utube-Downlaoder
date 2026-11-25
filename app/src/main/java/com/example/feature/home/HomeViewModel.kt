package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.UrlValidator
import com.example.data.repository.BrowserDataRepository
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MediaDownloadRepository
import com.example.domain.models.BookmarkItem
import com.example.domain.models.DownloadTask
import com.example.domain.models.LibraryFile
import com.example.domain.models.Platform
import com.example.domain.models.PlatformShortcut
import com.example.domain.models.WebHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val clipboardCandidateUrl: String? = null,
    val clipboardDismissedUrl: String? = null,
    val shortcuts: List<PlatformShortcut> = defaultPlatformShortcuts(),
    val historyCount: Int = 0,
    val bookmarkCount: Int = 0,
    val historyItems: List<WebHistoryItem> = emptyList(),
    val bookmarkItems: List<BookmarkItem> = emptyList(),
    val activeDownloads: List<DownloadTask> = emptyList(),
    val recentDownloads: List<LibraryFile> = emptyList()
)

fun defaultPlatformShortcuts(): List<PlatformShortcut> = listOf(
    PlatformShortcut("youtube", "YouTube", Platform.YOUTUBE, "https://m.youtube.com"),
    PlatformShortcut("facebook", "Facebook", Platform.FACEBOOK, "https://m.facebook.com"),
    PlatformShortcut("instagram", "Instagram", Platform.INSTAGRAM, "https://www.instagram.com"),
    PlatformShortcut("direct", "Direct URL", Platform.DIRECT, "", isDirectPrompt = true),
    PlatformShortcut("yt_music", "YT Music", Platform.YOUTUBE, "https://music.youtube.com"),
    PlatformShortcut("soundcloud", "SoundCloud", Platform.SOUNDCLOUD, "https://m.soundcloud.com"),
    PlatformShortcut("pinterest", "Pinterest", Platform.PINTEREST, "https://www.pinterest.com"),
    PlatformShortcut("tiktok", "TikTok", Platform.TIKTOK, "https://www.tiktok.com"),
    PlatformShortcut("dailymotion", "Dailymotion", Platform.DAILYMOTION, "https://www.dailymotion.com"),
    PlatformShortcut("reddit", "Reddit", Platform.REDDIT, "https://www.reddit.com"),
    PlatformShortcut("x_com", "X.com", Platform.X, "https://x.com"),
    PlatformShortcut("ted", "TED Talks", Platform.TED, "https://www.ted.com")
)

class HomeViewModel(
    private val downloadRepository: MediaDownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val browserDataRepository: BrowserDataRepository
) : ViewModel() {

    private val urlInputFlow = MutableStateFlow("")
    private val clipboardCandidateFlow = MutableStateFlow<String?>(null)
    private val dismissedClipboardFlow = MutableStateFlow<String?>(null)

    private val browserCountsFlow = combine(
        browserDataRepository.historyCount,
        browserDataRepository.bookmarkCount,
        browserDataRepository.history,
        browserDataRepository.bookmarks
    ) { hCount, bCount, hist, bmarks ->
        BrowserStripData(hCount, bCount, hist, bmarks)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        urlInputFlow,
        clipboardCandidateFlow,
        dismissedClipboardFlow,
        browserCountsFlow,
        combine(
            downloadRepository.activeDownloads,
            libraryRepository.libraryFiles
        ) { active, library -> active to library.take(9) }
    ) { input, clip, dismissed, browserData, (active, recent) ->
        HomeUiState(
            urlInput = input,
            clipboardCandidateUrl = clip?.takeIf { it != dismissed },
            clipboardDismissedUrl = dismissed,
            historyCount = browserData.historyCount,
            bookmarkCount = browserData.bookmarkCount,
            historyItems = browserData.history,
            bookmarkItems = browserData.bookmarks,
            activeDownloads = active,
            recentDownloads = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onUrlInputChange(value: String) {
        urlInputFlow.value = value
    }

    fun onClipboardTextChecked(rawText: String?) {
        val extracted = UrlValidator.extractFirstUrl(rawText)
        if (extracted != null && extracted != dismissedClipboardFlow.value) {
            clipboardCandidateFlow.value = extracted
        }
    }

    fun dismissClipboardSuggestion() {
        dismissedClipboardFlow.value = clipboardCandidateFlow.value
        clipboardCandidateFlow.value = null
    }

    fun pasteIntoInput(rawText: String?) {
        val extracted = UrlValidator.extractFirstUrl(rawText) ?: rawText?.trim().orEmpty()
        if (extracted.isNotBlank()) {
            urlInputFlow.value = extracted
        }
    }

    fun clearHistory() {
        viewModelScope.launch { browserDataRepository.clearHistory() }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { browserDataRepository.deleteHistoryItem(id) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { browserDataRepository.deleteBookmark(id) }
    }

    private data class BrowserStripData(
        val historyCount: Int,
        val bookmarkCount: Int,
        val history: List<WebHistoryItem>,
        val bookmarks: List<BookmarkItem>
    )

    class Factory(
        private val downloadRepository: MediaDownloadRepository,
        private val libraryRepository: LibraryRepository,
        private val browserDataRepository: BrowserDataRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(downloadRepository, libraryRepository, browserDataRepository) as T
        }
    }
}
