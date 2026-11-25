package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.storage.MediaStoreManager
import com.example.data.repository.LibraryRepository
import com.example.domain.models.LayoutDensity
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.StorageInfo
import com.example.domain.models.ThemeMode
import com.example.domain.models.VideoContainer
import com.example.domain.models.YtDlpUpdateChannel
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.downloader.ytdlp.YtDlpEngineStatus
import com.example.downloader.ytdlp.YtDlpUpdateResult
import com.example.processing.ffmpeg.FfmpegCapabilities
import com.example.processing.ffmpeg.FfmpegNativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val storageInfo: StorageInfo = StorageInfo(0L, 0L, 0L, 0L, 0L),
    val ffmpegCapabilities: FfmpegCapabilities = FfmpegNativeBridge.getCapabilities(),
    val ytDlpStatus: YtDlpEngineStatus = YtDlpEngineStatus(false, null, false, false, ""),
    val cacheClearedBanner: String? = null,
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val storageManager: MediaStoreManager,
    private val libraryRepository: LibraryRepository,
    private val ytDlpBridge: YtDlpBridge
) : ViewModel() {

    private val storageInfoFlow = MutableStateFlow(StorageInfo(0L, 0L, 0L, 0L, 0L))
    private val cacheBannerFlow = MutableStateFlow<String?>(null)
    private val isUpdatingYtDlpFlow = MutableStateFlow(false)
    private val ytDlpUpdateMessageFlow = MutableStateFlow<String?>(null)
    private val ytDlpStatusRefreshTick = MutableStateFlow(0)

    private val ytDlpUpdateStateFlow = combine(
        isUpdatingYtDlpFlow,
        ytDlpUpdateMessageFlow,
        ytDlpStatusRefreshTick
    ) { updating, message, _ ->
        Triple(updating, message, ytDlpBridge.getStatus())
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        storageInfoFlow,
        cacheBannerFlow,
        ytDlpUpdateStateFlow
    ) { prefs, storage, banner, (updating, updateMsg, engineStatus) ->
        SettingsUiState(
            preferences = prefs,
            storageInfo = storage,
            ffmpegCapabilities = FfmpegNativeBridge.getCapabilities(),
            ytDlpStatus = engineStatus,
            cacheClearedBanner = banner,
            isUpdatingYtDlp = updating,
            ytDlpUpdateMessage = updateMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(ytDlpStatus = ytDlpBridge.getStatus())
    )

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val libraryBytes = runCatching {
                libraryRepository.libraryFiles.first().sumOf { it.sizeBytes }
            }.getOrDefault(0L)
            storageInfoFlow.value = storageManager.getStorageInfo(libraryBytes)
            ytDlpStatusRefreshTick.value = ytDlpStatusRefreshTick.value + 1
        }
    }

    fun clearTempCache() {
        viewModelScope.launch {
            val freed = storageManager.clearTempCache()
            cacheBannerFlow.value = "Cleared ${com.example.core.common.Formatters.formatBytes(freed)} of temporary cache."
            refreshStorage()
        }
    }

    fun dismissBanner() {
        cacheBannerFlow.value = null
    }

    fun updateYtDlp(channel: YtDlpUpdateChannel) {
        if (isUpdatingYtDlpFlow.value) return
        viewModelScope.launch {
            isUpdatingYtDlpFlow.value = true
            ytDlpUpdateMessageFlow.value = "Checking ${channel.displayName} channel for yt-dlp updates…"
            val result = ytDlpBridge.updateEngine(channel)
            ytDlpUpdateMessageFlow.value = when (result) {
                is YtDlpUpdateResult.Updated ->
                    "Updated yt-dlp to ${result.version} (${channel.displayName})."
                is YtDlpUpdateResult.AlreadyUpToDate ->
                    "yt-dlp is already up to date (${result.version})."
                is YtDlpUpdateResult.Failed ->
                    "Update failed: ${result.reason}"
            }
            isUpdatingYtDlpFlow.value = false
            ytDlpStatusRefreshTick.value = ytDlpStatusRefreshTick.value + 1
        }
    }

    fun setDefaultVideoQuality(q: String) = launchPref { preferencesRepository.setDefaultVideoQuality(q) }
    fun setDefaultVideoContainer(container: VideoContainer) = launchPref { preferencesRepository.setDefaultVideoContainer(container) }
    fun setDefaultAudioQuality(q: String) = launchPref { preferencesRepository.setDefaultAudioQuality(q) }
    fun setMaxSimultaneousDownloads(n: Int) = launchPref { preferencesRepository.setMaxSimultaneousDownloads(n) }
    fun setWifiOnly(v: Boolean) = launchPref { preferencesRepository.setWifiOnlyDownloads(v) }
    fun setAutoQueue(v: Boolean) = launchPref { preferencesRepository.setAutoQueueProcessing(v) }
    fun setShowNotifications(v: Boolean) = launchPref { preferencesRepository.setShowNotifications(v) }
    fun setPlayNotificationSound(v: Boolean) = launchPref { preferencesRepository.setPlayNotificationSound(v) }
    fun setSingleClickToOpen(v: Boolean) = launchPref { preferencesRepository.setSingleClickToOpen(v) }

    fun setProcessingEngine(engine: ProcessingEngine) = launchPref { preferencesRepository.setProcessingEngine(engine) }
    fun setPreserveOriginalQuality(v: Boolean) = launchPref { preferencesRepository.setPreserveOriginalQuality(v) }
    fun setAutomaticFormatSelection(v: Boolean) = launchPref { preferencesRepository.setAutomaticFormatSelection(v) }

    fun setBrowserHomepage(url: String) = launchPref { preferencesRepository.setBrowserHomepage(url) }
    fun setEnableAdBlocker(v: Boolean) = launchPref { preferencesRepository.setEnableAdBlocker(v) }
    fun setBlockPopupAds(v: Boolean) = launchPref { preferencesRepository.setBlockPopupAds(v) }
    fun setShowWebImages(v: Boolean) = launchPref { preferencesRepository.setShowWebImages(v) }
    fun setShowVideoGrabber(v: Boolean) = launchPref { preferencesRepository.setShowVideoGrabber(v) }

    fun setThemeMode(mode: ThemeMode) = launchPref { preferencesRepository.setThemeMode(mode) }
    fun setLayoutDensity(density: LayoutDensity) = launchPref { preferencesRepository.setLayoutDensity(density) }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val preferencesRepository: UserPreferencesRepository,
        private val storageManager: MediaStoreManager,
        private val libraryRepository: LibraryRepository,
        private val ytDlpBridge: YtDlpBridge
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesRepository, storageManager, libraryRepository, ytDlpBridge) as T
        }
    }
}
