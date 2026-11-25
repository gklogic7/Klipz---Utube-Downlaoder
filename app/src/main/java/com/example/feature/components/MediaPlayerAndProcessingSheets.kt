package com.example.feature.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.domain.models.LibraryFile
import com.example.domain.models.MediaType
import com.example.domain.models.ProcessingOperation
import com.example.processing.ffmpeg.FfmpegNativeBridge
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.LocalKlipzColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppMediaPlayerSheet(
    file: LibraryFile,
    onDismiss: () -> Unit,
    onShare: (LibraryFile) -> Unit,
    onProcess: (LibraryFile) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${file.fileFormat} • ${file.qualityLabel} • ${Formatters.formatBytes(file.sizeBytes)}",
                        style = KlipzMonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close player")
                }
            }

            if (file.mediaType == MediaType.IMAGE) {
                AsyncImage(
                    model = Uri.parse(file.contentUri),
                    contentDescription = file.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background)
                )
            } else {
                val exoPlayer = remember(file.contentUri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(file.contentUri)))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(exoPlayer) {
                    onDispose {
                        exoPlayer.release()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onShare(file) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("player_share_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
                if (file.mediaType != MediaType.IMAGE) {
                    Button(
                        onClick = {
                            onDismiss()
                            onProcess(file)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("player_process_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Transform, contentDescription = "Process media", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Process")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaProcessingBottomSheet(
    file: LibraryFile,
    onDismiss: () -> Unit,
    onLaunchProcess: (
        file: LibraryFile,
        operation: ProcessingOperation,
        preferMp3: Boolean,
        trimStartMs: Long,
        trimEndMs: Long?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val operations = remember(file.mediaType) {
        if (file.mediaType == MediaType.AUDIO) {
            listOf(ProcessingOperation.CONVERT_AUDIO)
        } else {
            listOf(
                ProcessingOperation.CONVERT_VIDEO,
                ProcessingOperation.COMPRESS_VIDEO,
                ProcessingOperation.EXTRACT_AUDIO,
                ProcessingOperation.TRIM_VIDEO
            )
        }
    }
    var selectedOp by remember { mutableStateOf(operations.first()) }
    var preferMp3 by remember { mutableStateOf(false) }
    var trimStartSec by remember { mutableStateOf("0") }
    var trimEndSec by remember { mutableStateOf("15") }
    val ffmpegReady = remember { FfmpegNativeBridge.isAvailable() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Local Media Processing",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = file.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            operations.forEach { op ->
                val isSelected = selectedOp == op
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedOp = op }
                        .testTag("process_op_${op.name}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (op) {
                                ProcessingOperation.EXTRACT_AUDIO, ProcessingOperation.CONVERT_AUDIO -> Icons.Filled.Audiotrack
                                ProcessingOperation.TRIM_VIDEO -> Icons.Filled.ContentCut
                                else -> Icons.Filled.Movie
                            },
                            contentDescription = op.displayName,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = op.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = op.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedOp == ProcessingOperation.EXTRACT_AUDIO || selectedOp == ProcessingOperation.CONVERT_AUDIO) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Encode as MP3 (FFmpeg NDK)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (ffmpegReady) {
                                "FFmpeg JNI bridge linked — MP3 ready"
                            } else {
                                "Off = Media3 hardware M4A/AAC (recommended). MP3 requires NDK FFmpeg build."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = preferMp3, onCheckedChange = { preferMp3 = it })
                }
            }

            if (selectedOp == ProcessingOperation.TRIM_VIDEO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = trimStartSec,
                        onValueChange = { trimStartSec = it.filter(Char::isDigit) },
                        label = { Text("Start (sec)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = trimEndSec,
                        onValueChange = { trimEndSec = it.filter(Char::isDigit) },
                        label = { Text("End (sec)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = "Engine router",
                    tint = LocalKlipzColors.current.success,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (preferMp3 && (selectedOp == ProcessingOperation.EXTRACT_AUDIO || selectedOp == ProcessingOperation.CONVERT_AUDIO)) {
                        "Router engine: FFmpeg JNI"
                    } else {
                        "Router engine: Media3 Transformer (Hardware Accelerated)"
                    },
                    style = KlipzMonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val startMs = (trimStartSec.toLongOrNull() ?: 0L) * 1000L
                    val endMs = trimEndSec.toLongOrNull()?.times(1000L)
                    onLaunchProcess(file, selectedOp, preferMp3, startMs, endMs)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("start_processing_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Processing", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoBottomSheet(
    file: LibraryFile,
    onDismiss: () -> Unit,
    onOpen: (LibraryFile) -> Unit,
    onShare: (LibraryFile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("File Information", style = MaterialTheme.typography.titleLarge)
            MonochromeCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                InfoRow("Title", file.title)
                InfoRow("Platform", file.platform.displayName)
                InfoRow("Type", file.mediaType.displayName)
                InfoRow("Format / Quality", "${file.fileFormat} • ${file.qualityLabel}")
                InfoRow("MIME", file.mimeType)
                InfoRow("Size", Formatters.formatBytes(file.sizeBytes))
                InfoRow("Saved", Formatters.formatDateTime(file.dateAdded))
                InfoRow(
                    "Location",
                    if (file.isPrivateFolder) "App Private Folder" else "Downloads/Klipz/${file.mediaType.subfolder}"
                )
                InfoRow("Status", if (file.existsOnDisk) "Verified on device" else "Missing on storage")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onShare(file) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Share")
                }
                Button(
                    onClick = {
                        onDismiss()
                        onOpen(file)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open / Play")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = KlipzMonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
