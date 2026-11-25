package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.models.LayoutDensity
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ThemeMode
import com.example.domain.models.VideoContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.klipzDataStore: DataStore<Preferences> by preferencesDataStore(name = "klipz_prefs")

data class UserPreferences(
    // Download settings
    val defaultVideoQuality: String = "1080p",
    val defaultVideoContainer: VideoContainer = VideoContainer.MP4,
    val defaultAudioQuality: String = "192 kbps",
    val downloadLocationLabel: String = "Downloads/Klipz",
    val maxSimultaneousDownloads: Int = 3,
    val wifiOnlyDownloads: Boolean = false,
    val autoQueueProcessing: Boolean = true,
    val showNotifications: Boolean = true,
    val playNotificationSound: Boolean = true,
    val singleClickToOpen: Boolean = true,

    // Processing settings
    val processingEngine: ProcessingEngine = ProcessingEngine.AUTO,
    val preserveOriginalQuality: Boolean = true,
    val automaticFormatSelection: Boolean = true,

    // Browser settings
    val browserHomepage: String = "https://www.youtube.com",
    val enableAdBlocker: Boolean = true,
    val blockPopupAds: Boolean = true,
    val showWebImages: Boolean = true,
    val showVideoGrabber: Boolean = true,

    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,
    val layoutDensity: LayoutDensity = LayoutDensity.COMFORTABLE
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val DEFAULT_VIDEO_CONTAINER = stringPreferencesKey("default_video_container")
        val DEFAULT_AUDIO_QUALITY = stringPreferencesKey("default_audio_quality")
        val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_QUEUE = booleanPreferencesKey("auto_queue")
        val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val PLAY_NOTIF_SOUND = booleanPreferencesKey("play_notif_sound")
        val SINGLE_CLICK_OPEN = booleanPreferencesKey("single_click_open")

        val PROCESSING_ENGINE = stringPreferencesKey("processing_engine")
        val PRESERVE_ORIGINAL = booleanPreferencesKey("preserve_original")
        val AUTO_FORMAT = booleanPreferencesKey("auto_format")

        val BROWSER_HOMEPAGE = stringPreferencesKey("browser_homepage")
        val ENABLE_ADBLOCKER = booleanPreferencesKey("enable_adblocker")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups")
        val SHOW_WEB_IMAGES = booleanPreferencesKey("show_web_images")
        val SHOW_VIDEO_GRABBER = booleanPreferencesKey("show_video_grabber")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_DENSITY = stringPreferencesKey("layout_density")
    }

    val preferencesFlow: Flow<UserPreferences> = context.klipzDataStore.data.map { prefs ->
        UserPreferences(
            defaultVideoQuality = prefs[Keys.DEFAULT_VIDEO_QUALITY] ?: "1080p",
            defaultVideoContainer = VideoContainer.fromName(prefs[Keys.DEFAULT_VIDEO_CONTAINER] ?: VideoContainer.MP4.name),
            defaultAudioQuality = prefs[Keys.DEFAULT_AUDIO_QUALITY] ?: "192 kbps",
            downloadLocationLabel = prefs[Keys.DOWNLOAD_LOCATION] ?: "Downloads/Klipz",
            maxSimultaneousDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: false,
            autoQueueProcessing = prefs[Keys.AUTO_QUEUE] ?: true,
            showNotifications = prefs[Keys.SHOW_NOTIFICATIONS] ?: true,
            playNotificationSound = prefs[Keys.PLAY_NOTIF_SOUND] ?: true,
            singleClickToOpen = prefs[Keys.SINGLE_CLICK_OPEN] ?: true,
            processingEngine = ProcessingEngine.fromName(prefs[Keys.PROCESSING_ENGINE] ?: ProcessingEngine.AUTO.name),
            preserveOriginalQuality = prefs[Keys.PRESERVE_ORIGINAL] ?: true,
            automaticFormatSelection = prefs[Keys.AUTO_FORMAT] ?: true,
            browserHomepage = prefs[Keys.BROWSER_HOMEPAGE] ?: "https://www.youtube.com",
            enableAdBlocker = prefs[Keys.ENABLE_ADBLOCKER] ?: true,
            blockPopupAds = prefs[Keys.BLOCK_POPUPS] ?: true,
            showWebImages = prefs[Keys.SHOW_WEB_IMAGES] ?: true,
            showVideoGrabber = prefs[Keys.SHOW_VIDEO_GRABBER] ?: true,
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name),
            layoutDensity = LayoutDensity.fromName(prefs[Keys.LAYOUT_DENSITY] ?: LayoutDensity.COMFORTABLE.name)
        )
    }

    suspend fun setDefaultVideoQuality(quality: String) = edit { it[Keys.DEFAULT_VIDEO_QUALITY] = quality }
    suspend fun setDefaultVideoContainer(container: VideoContainer) = edit { it[Keys.DEFAULT_VIDEO_CONTAINER] = container.name }
    suspend fun setDefaultAudioQuality(quality: String) = edit { it[Keys.DEFAULT_AUDIO_QUALITY] = quality }
    suspend fun setMaxSimultaneousDownloads(count: Int) = edit { it[Keys.MAX_CONCURRENT] = count.coerceIn(1, 5) }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = edit { it[Keys.WIFI_ONLY] = enabled }
    suspend fun setAutoQueueProcessing(enabled: Boolean) = edit { it[Keys.AUTO_QUEUE] = enabled }
    suspend fun setShowNotifications(enabled: Boolean) = edit { it[Keys.SHOW_NOTIFICATIONS] = enabled }
    suspend fun setPlayNotificationSound(enabled: Boolean) = edit { it[Keys.PLAY_NOTIF_SOUND] = enabled }
    suspend fun setSingleClickToOpen(enabled: Boolean) = edit { it[Keys.SINGLE_CLICK_OPEN] = enabled }

    suspend fun setProcessingEngine(engine: ProcessingEngine) = edit { it[Keys.PROCESSING_ENGINE] = engine.name }
    suspend fun setPreserveOriginalQuality(enabled: Boolean) = edit { it[Keys.PRESERVE_ORIGINAL] = enabled }
    suspend fun setAutomaticFormatSelection(enabled: Boolean) = edit { it[Keys.AUTO_FORMAT] = enabled }

    suspend fun setBrowserHomepage(url: String) = edit { it[Keys.BROWSER_HOMEPAGE] = url }
    suspend fun setEnableAdBlocker(enabled: Boolean) = edit { it[Keys.ENABLE_ADBLOCKER] = enabled }
    suspend fun setBlockPopupAds(enabled: Boolean) = edit { it[Keys.BLOCK_POPUPS] = enabled }
    suspend fun setShowWebImages(enabled: Boolean) = edit { it[Keys.SHOW_WEB_IMAGES] = enabled }
    suspend fun setShowVideoGrabber(enabled: Boolean) = edit { it[Keys.SHOW_VIDEO_GRABBER] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setLayoutDensity(density: LayoutDensity) = edit { it[Keys.LAYOUT_DENSITY] = density.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.klipzDataStore.edit(block)
    }
}
