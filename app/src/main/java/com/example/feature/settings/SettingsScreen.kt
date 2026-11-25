package com.example.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.core.common.Formatters
import com.example.domain.models.LayoutDensity
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ThemeMode
import com.example.domain.models.VideoContainer
import com.example.feature.components.MonochromeCard
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.LocalKlipzColors
import com.example.ui.theme.LocalKlipzSpacing
import kotlin.math.roundToInt

/**
 * Monochrome Settings screen grouped by Download, Processing, Browser, Appearance, Storage, and About.
 * Strictly omits any Customer Service / Feedback / Support section per user requirement.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences
    val spacing = LocalKlipzSpacing.current

    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showVideoContainerDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDensityDialog by remember { mutableStateOf(false) }
    var showHomepageDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showEngineInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorage()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 1. Download Settings
            SettingsSectionHeader("Download Settings")
            MonochromeCard(contentPadding = 0.dp) {
                SettingsValueRow(
                    icon = Icons.Filled.HighQuality,
                    title = "Default Video Quality",
                    value = prefs.defaultVideoQuality,
                    onClick = { showVideoQualityDialog = true },
                    testTag = "setting_default_video_quality"
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.VideoFile,
                    title = "Default Video Container",
                    value = prefs.defaultVideoContainer.displayName,
                    onClick = { showVideoContainerDialog = true },
                    testTag = "setting_default_video_container"
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Audiotrack,
                    title = "Default Audio Quality",
                    value = prefs.defaultAudioQuality,
                    onClick = { showAudioQualityDialog = true }
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Folder,
                    title = "Download Location",
                    value = prefs.downloadLocationLabel,
                    onClick = {}
                )
                SettingsDivider()
                // Max simultaneous downloads slider (1..5)
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Queue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Max Simultaneous Downloads",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Text(
                            text = "${prefs.maxSimultaneousDownloads}",
                            style = KlipzMonoTextStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = prefs.maxSimultaneousDownloads.toFloat(),
                        onValueChange = { viewModel.setMaxSimultaneousDownloads(it.roundToInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.testTag("setting_max_concurrent_slider")
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Wifi,
                    title = "Wi-Fi Only Downloads",
                    subtitle = "Pause transfers on metered cellular networks",
                    checked = prefs.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnly,
                    testTag = "setting_wifi_only"
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Queue,
                    title = "Automatic Queue Processing",
                    subtitle = "Start queued items automatically as slots open",
                    checked = prefs.autoQueueProcessing,
                    onCheckedChange = viewModel::setAutoQueue
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Download Notifications",
                    subtitle = "Show progress and completion alerts",
                    checked = prefs.showNotifications,
                    onCheckedChange = viewModel::setShowNotifications
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Play Notification Sound",
                    checked = prefs.playNotificationSound,
                    onCheckedChange = viewModel::setPlayNotificationSound
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.VideoFile,
                    title = "Single Click to Open",
                    subtitle = "Play completed files immediately on tap",
                    checked = prefs.singleClickToOpen,
                    onCheckedChange = viewModel::setSingleClickToOpen
                )
            }

            // 2. Processing Settings
            SettingsSectionHeader("Processing Settings")
            MonochromeCard(contentPadding = 0.dp) {
                SettingsValueRow(
                    icon = Icons.Filled.Memory,
                    title = "Processing Engine",
                    value = prefs.processingEngine.displayName,
                    onClick = { showEngineDialog = true },
                    testTag = "setting_processing_engine"
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.HighQuality,
                    title = "Preserve Original Quality",
                    subtitle = "Avoid re-encoding when source container/codec is already compatible",
                    checked = prefs.preserveOriginalQuality,
                    onCheckedChange = viewModel::setPreserveOriginalQuality
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Tune,
                    title = "Automatic Format Selection",
                    subtitle = "Pick optimal container (MP4/M4A) based on device codecs",
                    checked = prefs.automaticFormatSelection,
                    onCheckedChange = viewModel::setAutomaticFormatSelection
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.CleaningServices,
                    title = "Temporary File Cleanup",
                    value = Formatters.formatBytes(state.storageInfo.tempCacheBytes),
                    onClick = viewModel::clearTempCache
                )
            }

            // 3. Browser Settings
            SettingsSectionHeader("Browser Settings")
            MonochromeCard(contentPadding = 0.dp) {
                SettingsValueRow(
                    icon = Icons.Filled.Home,
                    title = "Browser Homepage",
                    value = prefs.browserHomepage.removePrefix("https://"),
                    onClick = { showHomepageDialog = true }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Block,
                    title = "Enable Ad-Host Blocker",
                    checked = prefs.enableAdBlocker,
                    onCheckedChange = viewModel::setEnableAdBlocker
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Block,
                    title = "Block Popup Ads",
                    checked = prefs.blockPopupAds,
                    onCheckedChange = viewModel::setBlockPopupAds
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Image,
                    title = "Show Web Images",
                    checked = prefs.showWebImages,
                    onCheckedChange = viewModel::setShowWebImages
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.VideoFile,
                    title = "Show Video Grabber",
                    subtitle = "Floating media analyzer button in Browser",
                    checked = prefs.showVideoGrabber,
                    onCheckedChange = viewModel::setShowVideoGrabber
                )
            }

            // 4. Appearance
            SettingsSectionHeader("Appearance")
            MonochromeCard(contentPadding = 0.dp) {
                SettingsValueRow(
                    icon = Icons.Filled.DarkMode,
                    title = "Theme",
                    value = prefs.themeMode.displayName,
                    onClick = { showThemeDialog = true },
                    testTag = "setting_theme_mode"
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.ViewCompact,
                    title = "Layout Density",
                    value = prefs.layoutDensity.displayName,
                    onClick = { showDensityDialog = true },
                    testTag = "setting_layout_density"
                )
            }

            // 5. Storage
            SettingsSectionHeader("Storage")
            MonochromeCard(contentPadding = 14.dp) {
                val storage = state.storageInfo
                val usedRatio = if (storage.totalBytes > 0L) {
                    (storage.usedBytes.toDouble() / storage.totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                } else 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "Device storage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Device Storage", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "${Formatters.formatBytes(storage.availableBytes)} free",
                        style = KlipzMonoTextStyle,
                        color = LocalKlipzColors.current.success
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { usedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageMetric("Used", Formatters.formatBytes(storage.usedBytes))
                    StorageMetric("Klipz Files", Formatters.formatBytes(storage.klipzLibraryBytes))
                    StorageMetric("Temp Cache", Formatters.formatBytes(storage.tempCacheBytes))
                }
                if (state.cacheClearedBanner != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.cacheClearedBanner!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalKlipzColors.current.success
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::clearTempCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("settings_clear_cache_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Cache")
                }
            }

            // 6. About (No Customer Service UI!)
            SettingsSectionHeader("About")
            MonochromeCard(contentPadding = 0.dp) {
                SettingsValueRow(
                    icon = Icons.Filled.Info,
                    title = "Klipz Version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = {}
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Memory,
                    title = "Engine Information",
                    value = if (state.ytDlpStatus.pythonRuntimeBundled) "yt-dlp + FFmpeg" else "Media3 Active",
                    onClick = { showEngineInfoDialog = true },
                    testTag = "setting_engine_info"
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.Description,
                    title = "Open-Source Licenses",
                    value = "View",
                    onClick = { showLicensesDialog = true },
                    testTag = "setting_licenses"
                )
                SettingsDivider()
                SettingsValueRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Privacy Information",
                    value = "Local Only",
                    onClick = { showPrivacyDialog = true },
                    testTag = "setting_privacy"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showVideoQualityDialog) {
        SingleChoiceDialog(
            title = "Default Video Quality",
            options = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "Original"),
            selected = prefs.defaultVideoQuality,
            onSelect = { viewModel.setDefaultVideoQuality(it); showVideoQualityDialog = false },
            onDismiss = { showVideoQualityDialog = false }
        )
    }

    if (showVideoContainerDialog) {
        SingleChoiceDialog(
            title = "Default Video Container",
            options = VideoContainer.entries.map { "${it.displayName} — ${if (it == VideoContainer.MP4) "Universal playback" else "Lossless remux"}" },
            selected = "${prefs.defaultVideoContainer.displayName} — ${if (prefs.defaultVideoContainer == VideoContainer.MP4) "Universal playback" else "Lossless remux"}",
            onSelect = { label ->
                val picked = if (label.startsWith("MKV")) VideoContainer.MKV else VideoContainer.MP4
                viewModel.setDefaultVideoContainer(picked)
                showVideoContainerDialog = false
            },
            onDismiss = { showVideoContainerDialog = false }
        )
    }

    if (showAudioQualityDialog) {
        SingleChoiceDialog(
            title = "Default Audio Quality",
            options = listOf("320 kbps", "192 kbps", "128 kbps", "Original"),
            selected = prefs.defaultAudioQuality,
            onSelect = { viewModel.setDefaultAudioQuality(it); showAudioQualityDialog = false },
            onDismiss = { showAudioQualityDialog = false }
        )
    }

    if (showEngineDialog) {
        SingleChoiceDialog(
            title = "Processing Engine Preference",
            options = ProcessingEngine.entries.map { it.displayName },
            selected = prefs.processingEngine.displayName,
            onSelect = { label ->
                ProcessingEngine.entries.firstOrNull { it.displayName == label }?.let(viewModel::setProcessingEngine)
                showEngineDialog = false
            },
            onDismiss = { showEngineDialog = false }
        )
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "App Theme",
            options = ThemeMode.entries.map { it.displayName },
            selected = prefs.themeMode.displayName,
            onSelect = { label ->
                ThemeMode.entries.firstOrNull { it.displayName == label }?.let(viewModel::setThemeMode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showDensityDialog) {
        SingleChoiceDialog(
            title = "Layout Density",
            options = LayoutDensity.entries.map { it.displayName },
            selected = prefs.layoutDensity.displayName,
            onSelect = { label ->
                LayoutDensity.entries.firstOrNull { it.displayName == label }?.let(viewModel::setLayoutDensity)
                showDensityDialog = false
            },
            onDismiss = { showDensityDialog = false }
        )
    }

    if (showHomepageDialog) {
        SingleChoiceDialog(
            title = "Browser Homepage",
            options = listOf(
                "https://m.youtube.com",
                "https://www.ted.com",
                "https://duckduckgo.com",
                "https://www.dailymotion.com"
            ),
            selected = prefs.browserHomepage,
            onSelect = { viewModel.setBrowserHomepage(it); showHomepageDialog = false },
            onDismiss = { showHomepageDialog = false }
        )
    }

    if (showEngineInfoDialog) {
        AlertDialog(
            onDismissRequest = { showEngineInfoDialog = false },
            title = { Text("Engine Diagnostics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Extraction Engine: ${state.ytDlpStatus.statusSummary}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• QuickJS / EJS Challenge Solver: ${if (state.ytDlpStatus.jsRuntimeAvailable) "Bundled (QuickJS)" else "Unavailable"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Auto-Merge Muxer (MP4/MKV): ${if (state.ytDlpStatus.ffmpegBundled) "Bundled FFmpeg active" else "Not bundled"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Media3 ExoPlayer & Transformer: Bundled & Active (hardware-accelerated MP4/AAC/Trim/Compress).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• NDK/JNI Bridge: ${state.ffmpegCapabilities.versionInfo} (ABIs: ${state.ffmpegCapabilities.supportedAbis.joinToString(", ")}; ${state.ffmpegCapabilities.licenseMode}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEngineInfoDialog = false }) { Text("Done") }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Licenses") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• Jetpack Compose, Material 3, Room, WorkManager, DataStore, Media3 — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• OkHttp & Coil — Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                    Text("• FFmpeg (NDK LGPL build profile) — GNU LGPL v2.1+", style = MaterialTheme.typography.bodySmall)
                    Text("• yt-dlp (build-time Python bridge profile) — The Unlicense", style = MaterialTheme.typography.bodySmall)
                    Text("• Space Grotesk, Inter, JetBrains Mono — SIL Open Font License 1.1", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Information") },
            text = {
                Text(
                    text = "Klipz operates completely on-device with no account, no cloud backend, and no telemetry or analytics tracking. Download history, bookmarks, and preferences are stored locally in Android Room and DataStore. Temporary signed media URLs are never persisted long-term, and query tokens are redacted from diagnostics.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Understood") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = KlipzMonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun StorageMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = KlipzMonoTextStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
