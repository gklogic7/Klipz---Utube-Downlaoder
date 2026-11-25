package com.example.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.data.repository.BrowserDataRepository
import com.example.domain.models.MediaFormat
import com.example.domain.models.Platform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

data class BrowserUiState(
    val currentUrl: String = "https://m.youtube.com",
    val addressInput: String = "https://m.youtube.com",
    val pageTitle: String = "Browser",
    val platform: Platform = Platform.YOUTUBE,
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isBookmarked: Boolean = false,
    val detectedStreams: List<MediaFormat> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val pendingLoadTrigger: Long = 0L
)

class BrowserViewModel(
    private val browserDataRepository: BrowserDataRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val currentUrlFlow = MutableStateFlow("https://m.youtube.com")
    private val addressInputFlow = MutableStateFlow("https://m.youtube.com")
    private val pageTitleFlow = MutableStateFlow("YouTube")
    private val loadingStateFlow = MutableStateFlow(false to 0)
    private val navCapabilitiesFlow = MutableStateFlow(false to false)
    private val detectedStreamsFlow = MutableStateFlow<List<MediaFormat>>(emptyList())
    private val loadTriggerFlow = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isBookmarkedFlow = currentUrlFlow.flatMapLatest { url ->
        browserDataRepository.isBookmarked(url)
    }

    private val coreMetaFlow = combine(
        currentUrlFlow,
        addressInputFlow,
        pageTitleFlow,
        loadingStateFlow,
        navCapabilitiesFlow
    ) { url, addr, title, (loading, progress), (canBack, canFwd) ->
        CoreBrowserMeta(url, addr, title, loading, progress, canBack, canFwd)
    }

    val uiState: StateFlow<BrowserUiState> = combine(
        coreMetaFlow,
        isBookmarkedFlow,
        detectedStreamsFlow,
        preferencesRepository.preferencesFlow,
        loadTriggerFlow
    ) { meta, bookmarked, streams, prefs, trigger ->
        BrowserUiState(
            currentUrl = meta.url,
            addressInput = meta.addressInput,
            pageTitle = meta.title,
            platform = UrlValidator.detectPlatform(meta.url),
            isLoading = meta.isLoading,
            loadProgress = meta.progress,
            canGoBack = meta.canGoBack,
            canGoForward = meta.canGoForward,
            isBookmarked = bookmarked,
            detectedStreams = streams,
            preferences = prefs,
            pendingLoadTrigger = trigger
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrowserUiState()
    )

    fun loadUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val normalized = UrlValidator.normalizeUrl(trimmed)
        val target = if (UrlValidator.isValidHttpUrl(normalized)) {
            normalized
        } else {
            "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }
        currentUrlFlow.value = target
        addressInputFlow.value = target
        detectedStreamsFlow.value = emptyList()
        loadTriggerFlow.update { it + 1L }
    }

    fun onAddressInputChanged(value: String) {
        addressInputFlow.value = value
    }

    fun onPageStarted(url: String) {
        currentUrlFlow.value = url
        addressInputFlow.value = url
        loadingStateFlow.value = true to 15
        detectedStreamsFlow.value = emptyList()
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        currentUrlFlow.value = url
        addressInputFlow.value = url
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: runCatching { URI(url).host }.getOrNull() ?: "Web"
        pageTitleFlow.value = resolvedTitle
        loadingStateFlow.value = false to 100
        navCapabilitiesFlow.value = canGoBack to canGoForward

        if (UrlValidator.isValidHttpUrl(url)) {
            viewModelScope.launch {
                browserDataRepository.recordVisit(
                    url = url,
                    title = resolvedTitle,
                    platform = UrlValidator.detectPlatform(url)
                )
            }
        }
    }

    /**
     * Handles SPA (History.pushState) route changes on sites like YouTube and Instagram
     * where onPageFinished does not fire again when opening a video, Short, or Reel.
     */
    fun onSpaUrlChanged(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        if (!UrlValidator.isValidHttpUrl(url)) return
        val previous = currentUrlFlow.value
        currentUrlFlow.value = url
        addressInputFlow.value = url
        if (!title.isNullOrBlank()) {
            pageTitleFlow.value = title
        }
        navCapabilitiesFlow.value = canGoBack to canGoForward
        if (previous != url) {
            detectedStreamsFlow.value = emptyList()
            viewModelScope.launch {
                browserDataRepository.recordVisit(
                    url = url,
                    title = title?.takeIf { it.isNotBlank() } ?: url,
                    platform = UrlValidator.detectPlatform(url)
                )
            }
        }
    }

    fun onProgressChanged(progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        loadingStateFlow.value = (progress < 100) to progress
        navCapabilitiesFlow.value = canGoBack to canGoForward
    }

    fun onMediaResourceDetected(mediaUrl: String, mimeHint: String? = null) {
        if (!UrlValidator.isValidHttpUrl(mediaUrl)) return
        val lower = mediaUrl.lowercase(Locale.US)
        // Skip tiny tracking pixels, DRM segments, or non-direct blobs
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("doubleclick") || lower.contains("favicon")) {
            return
        }
        val mime = mimeHint?.substringBefore(";")?.trim()?.lowercase(Locale.US)
            ?.takeIf { it.startsWith("video/") || it.startsWith("audio/") || it.startsWith("image/") }
            ?: guessMimeFromUrl(lower)
            ?: return

        val isAudio = mime.startsWith("audio/")
        val isVideo = mime.startsWith("video/")
        val ext = Formatters.extensionForMimeType(mime).uppercase(Locale.US)

        val format = MediaFormat(
            formatId = "web_${mediaUrl.hashCode().toUInt()}",
            label = when {
                isVideo -> "Detected Video"
                isAudio -> "Detected Audio"
                else -> "Detected Image"
            },
            resolution = when {
                isVideo -> "Original"
                isAudio -> "Audio"
                else -> "Image"
            },
            codec = ext,
            container = ext,
            mimeType = mime,
            isAudioOnly = isAudio,
            hasAudio = isAudio || isVideo,
            hasVideo = isVideo,
            directUrl = mediaUrl
        )

        detectedStreamsFlow.update { existing ->
            if (existing.any { it.directUrl == mediaUrl } || existing.size >= 9) {
                existing
            } else {
                // Prioritize video & audio over images
                (existing + format).sortedWith(
                    compareByDescending<MediaFormat> { it.hasVideo }
                        .thenByDescending { it.isAudioOnly }
                )
            }
        }
    }

    fun toggleBookmark() {
        val state = uiState.value
        if (!UrlValidator.isValidHttpUrl(state.currentUrl)) return
        viewModelScope.launch {
            browserDataRepository.toggleBookmark(
                url = state.currentUrl,
                title = state.pageTitle,
                platform = state.platform,
                currentlyBookmarked = state.isBookmarked
            )
        }
    }

    fun toggleAdBlocker(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setEnableAdBlocker(enabled) }
    }

    fun togglePopupBlocker(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockPopupAds(enabled) }
    }

    fun toggleGrabber(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowVideoGrabber(enabled) }
    }

    private fun guessMimeFromUrl(lowerUrl: String): String? {
        val path = runCatching { URI(lowerUrl).path?.lowercase(Locale.US) }.getOrNull() ?: lowerUrl
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") || path.endsWith(".aac") -> "audio/mp4"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            else -> null
        }
    }

    private data class CoreBrowserMeta(
        val url: String,
        val addressInput: String,
        val title: String,
        val isLoading: Boolean,
        val progress: Int,
        val canGoBack: Boolean,
        val canGoForward: Boolean
    )

    class Factory(
        private val browserDataRepository: BrowserDataRepository,
        private val preferencesRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(browserDataRepository, preferencesRepository) as T
        }
    }
}
