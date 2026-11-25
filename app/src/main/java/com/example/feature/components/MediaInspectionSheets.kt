package com.example.feature.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.VideoContainer
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.LocalKlipzColors

sealed interface InspectionSheetState {
    data object Hidden : InspectionSheetState
    data class Analyzing(val url: String) : InspectionSheetState
    data class PreviewReady(val mediaInfo: MediaInfo) : InspectionSheetState
    data class Restricted(val mediaInfo: MediaInfo?, val reason: String, val guidance: String) : InspectionSheetState
    data class Failed(val url: String, val errorMessage: String) : InspectionSheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInspectionModalHost(
    state: InspectionSheetState,
    defaultVideoContainer: VideoContainer,
    onCancelAnalysis: () -> Unit,
    onDismiss: () -> Unit,
    onRetryInspect: (String) -> Unit,
    onStartDownload: (MediaInfo, MediaFormat, VideoContainer, Boolean) -> Unit,
    onOpenTasksTab: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    if (state is InspectionSheetState.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            if (state is InspectionSheetState.Analyzing) onCancelAnalysis() else onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        when (state) {
            is InspectionSheetState.Analyzing -> {
                AnalyzingUrlContent(
                    url = state.url,
                    onCancel = onCancelAnalysis
                )
            }
            is InspectionSheetState.PreviewReady -> {
                MediaPreviewQualityContent(
                    mediaInfo = state.mediaInfo,
                    defaultVideoContainer = defaultVideoContainer,
                    onDownload = { format, container, privateFolder ->
                        onStartDownload(state.mediaInfo, format, container, privateFolder)
                    },
                    onOpenTasks = onOpenTasksTab,
                    onOpenInBrowser = { onOpenInBrowser(state.mediaInfo.sourceUrl) }
                )
            }
            is InspectionSheetState.Restricted -> {
                RestrictedSourceContent(
                    mediaInfo = state.mediaInfo,
                    reason = state.reason,
                    guidance = state.guidance,
                    onOpenInBrowser = {
                        state.mediaInfo?.sourceUrl?.let(onOpenInBrowser)
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            }
            is InspectionSheetState.Failed -> {
                InspectionErrorContent(
                    url = state.url,
                    message = state.errorMessage,
                    onRetry = { onRetryInspect(state.url) },
                    onDismiss = onDismiss
                )
            }
            InspectionSheetState.Hidden -> Unit
        }
    }
}

@Composable
private fun AnalyzingUrlContent(
    url: String,
    onCancel: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "analyzing_spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hourglass_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline
            )
            Icon(
                imageVector = Icons.Filled.HourglassTop,
                contentDescription = "Analyzing URL",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(angle)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Analyzing URL, Please Wait…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = UrlValidator.redactForLog(url),
            style = KlipzMonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cancel_analyze_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = "Cancel",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MediaPreviewQualityContent(
    mediaInfo: MediaInfo,
    defaultVideoContainer: VideoContainer,
    onDownload: (MediaFormat, VideoContainer, Boolean) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    var selectedFormatId by remember(mediaInfo.id) {
        // Default to the highest resolution video format if present, otherwise first available format
        val initial = mediaInfo.formats.firstOrNull { !it.isAudioOnly && it.hasVideo }
            ?: mediaInfo.formats.firstOrNull()
        mutableStateOf(initial?.formatId)
    }
    var selectedContainer by remember(mediaInfo.id, defaultVideoContainer) {
        mutableStateOf(defaultVideoContainer)
    }
    var saveToPrivateFolder by remember(mediaInfo.id) { mutableStateOf(false) }

    val selectedFormat = mediaInfo.formats.firstOrNull { it.formatId == selectedFormatId }
        ?: mediaInfo.formats.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Media Preview Header (inspired by reference screenshot 2 in monochrome)
        MonochromeCard(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (!mediaInfo.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = mediaInfo.thumbnailUrl,
                            contentDescription = mediaInfo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        PlatformIconBadge(platform = mediaInfo.platform, size = 34.dp)
                    }
                    if (mediaInfo.durationMs != null && mediaInfo.durationMs > 0L) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = Formatters.formatDuration(mediaInfo.durationMs),
                                style = KlipzMonoTextStyle,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = UrlValidator.redactForLog(mediaInfo.sourceUrl),
                        style = KlipzMonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusDotPill(
                            label = mediaInfo.platform.displayName,
                            dotColor = LocalKlipzColors.current.success
                        )
                        StatusDotPill(
                            label = mediaInfo.mediaType.displayName,
                            dotColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.testTag("preview_open_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Open source URL in Browser",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Restriction / Transparency notice if partial formats only
        if (!mediaInfo.restrictionNotice.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, LocalKlipzColors.current.warning.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Authorization notice",
                        tint = LocalKlipzColors.current.warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = mediaInfo.restrictionNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3-Column Quality Selection Grid (only actual discovered Video & Audio formats — never invented!)
        if (mediaInfo.formats.isNotEmpty()) {
            Text(
                text = "Available Formats (${mediaInfo.formats.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp, max = 270.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaInfo.formats, key = { it.formatId }) { format ->
                    val isSelected = format.formatId == selectedFormat?.formatId
                    QualityFormatTile(
                        format = format,
                        selected = isSelected,
                        onClick = { selectedFormatId = format.formatId }
                    )
                }
            }

            // Output Container Selector (MP4 / MKV) for auto-merged full-resolution video downloads
            if (selectedFormat != null && selectedFormat.hasVideo && !selectedFormat.isAudioOnly && selectedFormat.isYtDlpManaged) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Output Container",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedContainer == VideoContainer.MP4) "Universal MP4 auto-merge" else "Lossless MKV full-res remux",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoContainer.entries.forEach { container ->
                            val isSelected = container == selectedContainer
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedContainer = container }
                                    .testTag("container_option_${container.extension}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (container == VideoContainer.MP4) "MP4" else "MKV",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected stream native resolution, aspect ratio, fps, codec & estimated size details
            if (selectedFormat != null) {
                val dimensionPart = if (selectedFormat.widthPx > 0 && selectedFormat.heightPx > 0) {
                    "${selectedFormat.widthPx}×${selectedFormat.heightPx}"
                } else null
                val detailItems = buildList {
                    if (dimensionPart != null) add(dimensionPart)
                    selectedFormat.aspectRatioLabel?.let { add(it) }
                    selectedFormat.fps?.takeIf { it > 0 }?.let { add("${it}fps") }
                    add(
                        if (selectedFormat.hasVideo && !selectedFormat.isAudioOnly && selectedFormat.isYtDlpManaged) {
                            selectedContainer.displayName
                        } else {
                            selectedFormat.container
                        }
                    )
                    if (selectedFormat.codec.isNotBlank()) add(selectedFormat.codec)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detailItems.joinToString(" • "),
                        style = KlipzMonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatBytes(selectedFormat.fileSizeBytes),
                        style = KlipzMonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Private Folder Toggle Row (matching reference screenshot 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { saveToPrivateFolder = !saveToPrivateFolder }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (saveToPrivateFolder) Icons.Filled.Lock else Icons.Filled.Folder,
                        contentDescription = "Destination folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (saveToPrivateFolder) "Download to Private Folder" else "Save to Downloads/Klipz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (saveToPrivateFolder) "App-private storage (hidden from gallery)" else "Public MediaStore Downloads/${mediaInfo.mediaType.subfolder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = saveToPrivateFolder,
                    onCheckedChange = { saveToPrivateFolder = it },
                    modifier = Modifier.testTag("private_folder_switch")
                )
            }

            val canDownload = selectedFormat != null &&
                (!selectedFormat.directUrl.isNullOrBlank() || selectedFormat.isYtDlpManaged)

            // Primary CTA Button
            Button(
                onClick = {
                    selectedFormat?.let { onDownload(it, selectedContainer, saveToPrivateFolder) }
                },
                enabled = canDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("download_now_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download Now",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Now", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                text = "Monitor download progress in the Tasks tab",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTasks)
                    .padding(vertical = 4.dp)
            )
        } else {
            EmptyStatePanel(
                icon = Icons.Filled.Lock,
                title = "No Direct Streams Detected",
                subtitle = "Only verified, non-DRM streams are shown. Try opening this link in Klipz Browser or provide a direct media URL."
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun QualityFormatTile(
    format: MediaFormat,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("quality_tile_${format.formatId}"),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = format.resolution,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = format.fileSizeBytes?.let { Formatters.formatBytes(it) } ?: format.container,
                style = KlipzMonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RestrictedSourceContent(
    mediaInfo: MediaInfo?,
    reason: String,
    guidance: String,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Restricted source",
                tint = LocalKlipzColors.current.warning,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Authorized Access Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = guidance,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("restricted_close_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
            if (mediaInfo != null) {
                Button(
                    onClick = onOpenInBrowser,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("restricted_open_browser_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open in Browser")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InspectionErrorContent(
    url: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Inspection error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Could Not Inspect URL",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = UrlValidator.redactForLog(url),
            style = KlipzMonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("retry_inspect_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
