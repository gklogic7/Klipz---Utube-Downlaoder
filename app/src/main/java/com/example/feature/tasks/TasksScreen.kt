package com.example.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.domain.models.DownloadStatus
import com.example.domain.models.DownloadTask
import com.example.domain.models.LibraryFile
import com.example.domain.models.LibrarySortOrder
import com.example.domain.models.MediaType
import com.example.domain.models.ProcessingStatus
import com.example.domain.models.ProcessingTask
import com.example.feature.components.EmptyStatePanel
import com.example.feature.components.FileInfoBottomSheet
import com.example.feature.components.MediaProcessingBottomSheet
import com.example.feature.components.MonochromeCard
import com.example.feature.components.PlatformIconBadge
import com.example.feature.components.StatusDotPill
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.LocalKlipzColors
import com.example.ui.theme.LocalKlipzSpacing

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    isWideLayout: Boolean,
    onOpenLibraryFile: (LibraryFile) -> Unit,
    onShareUri: (uri: String, mimeType: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalKlipzSpacing.current

    var processingTarget by remember { mutableStateOf<LibraryFile?>(null) }
    var infoTarget by remember { mutableStateOf<LibraryFile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Mode Switcher (on phones) or Split-Panel Title (on tablets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isWideLayout) "Tasks & Library" else "Tasks",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!isWideLayout) {
                TasksModeSegmentedControl(
                    selected = state.mode,
                    activeCount = state.activeDownloads.size,
                    libraryCount = state.totalLibraryCount,
                    onSelect = viewModel::setMode
                )
            }
        }

        if (isWideLayout) {
            // Adaptive Tablet Split-Panel: Downloads Queue (Left) + Local Library (Right)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DownloadsPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenCompleted = { task ->
                        task.outputUri?.let { uri ->
                            onOpenLibraryFile(task.toLibraryFile(uri))
                        }
                    },
                    onShareTask = { task ->
                        task.outputUri?.let { uri ->
                            onShareUri(uri, task.mimeType, task.title)
                        }
                    },
                    onProcessTask = { task ->
                        task.outputUri?.let { uri ->
                            processingTarget = task.toLibraryFile(uri)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                LibraryPane(
                    state = state,
                    viewModel = viewModel,
                    gridColumns = 3,
                    onOpenFile = onOpenLibraryFile,
                    onShareFile = { file -> onShareUri(file.contentUri, file.mimeType, file.title) },
                    onProcessFile = { processingTarget = it },
                    onShowInfo = { infoTarget = it },
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                )
            }
        } else {
            when (state.mode) {
                TasksMode.DOWNLOADS -> DownloadsPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenCompleted = { task ->
                        task.outputUri?.let { uri ->
                            onOpenLibraryFile(task.toLibraryFile(uri))
                        }
                    },
                    onShareTask = { task ->
                        task.outputUri?.let { uri ->
                            onShareUri(uri, task.mimeType, task.title)
                        }
                    },
                    onProcessTask = { task ->
                        task.outputUri?.let { uri ->
                            processingTarget = task.toLibraryFile(uri)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                TasksMode.LIBRARY -> LibraryPane(
                    state = state,
                    viewModel = viewModel,
                    gridColumns = 2,
                    onOpenFile = onOpenLibraryFile,
                    onShareFile = { file -> onShareUri(file.contentUri, file.mimeType, file.title) },
                    onProcessFile = { processingTarget = it },
                    onShowInfo = { infoTarget = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    processingTarget?.let { target ->
        MediaProcessingBottomSheet(
            file = target,
            onDismiss = { processingTarget = null },
            onLaunchProcess = { file, op, preferMp3, startMs, endMs ->
                viewModel.processMediaFile(file, op, preferMp3, startMs, endMs)
            }
        )
    }

    infoTarget?.let { target ->
        FileInfoBottomSheet(
            file = target,
            onDismiss = { infoTarget = null },
            onOpen = onOpenLibraryFile,
            onShare = { file -> onShareUri(file.contentUri, file.mimeType, file.title) }
        )
    }
}

@Composable
private fun TasksModeSegmentedControl(
    selected: TasksMode,
    activeCount: Int,
    libraryCount: Int,
    onSelect: (TasksMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            SegmentedButtonPill(
                label = if (activeCount > 0) "Downloads ($activeCount)" else "Downloads",
                selected = selected == TasksMode.DOWNLOADS,
                onClick = { onSelect(TasksMode.DOWNLOADS) },
                testTag = "tasks_mode_downloads"
            )
            SegmentedButtonPill(
                label = if (libraryCount > 0) "Library ($libraryCount)" else "Library",
                selected = selected == TasksMode.LIBRARY,
                onClick = { onSelect(TasksMode.LIBRARY) },
                testTag = "tasks_mode_library"
            )
        }
    }
}

@Composable
private fun SegmentedButtonPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun DownloadsPane(
    state: TasksUiState,
    viewModel: TasksViewModel,
    onOpenCompleted: (DownloadTask) -> Unit,
    onShareTask: (DownloadTask) -> Unit,
    onProcessTask: (DownloadTask) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Active / Completed / Failed Tabs
        val tabs = listOf(
            DownloadsTab.ACTIVE to "Active (${state.activeDownloads.size})",
            DownloadsTab.COMPLETED to "Completed (${state.completedDownloads.size})",
            DownloadsTab.FAILED to "Failed (${state.failedDownloads.size})"
        )
        val selectedIdx = tabs.indexOfFirst { it.first == state.downloadsTab }.coerceAtLeast(0)

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            TabRow(
                selectedTabIndex = selectedIdx,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIdx]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                tabs.forEach { (tab, label) ->
                    Tab(
                        selected = state.downloadsTab == tab,
                        onClick = { viewModel.setDownloadsTab(tab) },
                        modifier = Modifier.testTag("downloads_tab_${tab.name.lowercase()}"),
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        // Processing tasks status cards (Media3 / FFmpeg) when present
        if (state.processingTasks.isNotEmpty() && state.downloadsTab == DownloadsTab.ACTIVE) {
            state.processingTasks.take(3).forEach { proc ->
                ProcessingTaskCard(
                    task = proc,
                    onCancel = { viewModel.cancelProcessing(proc.id) },
                    onDismiss = { viewModel.dismissProcessingTask(proc.id) }
                )
            }
        }

        val currentList = when (state.downloadsTab) {
            DownloadsTab.ACTIVE -> state.activeDownloads
            DownloadsTab.COMPLETED -> state.completedDownloads
            DownloadsTab.FAILED -> state.failedDownloads
        }

        if (state.downloadsTab == DownloadsTab.FAILED && currentList.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = viewModel::clearFailedDownloads) {
                    Text("Clear Failed")
                }
            }
        }

        if (currentList.isEmpty()) {
            EmptyStatePanel(
                icon = when (state.downloadsTab) {
                    DownloadsTab.ACTIVE -> Icons.Filled.Download
                    DownloadsTab.COMPLETED -> Icons.Filled.FolderOpen
                    DownloadsTab.FAILED -> Icons.Filled.ErrorOutline
                },
                title = when (state.downloadsTab) {
                    DownloadsTab.ACTIVE -> "No active downloads"
                    DownloadsTab.COMPLETED -> "No completed downloads yet"
                    DownloadsTab.FAILED -> "No failed transfers"
                },
                subtitle = when (state.downloadsTab) {
                    DownloadsTab.ACTIVE -> "Queued, downloading, and paused tasks appear here with live speed and ETA."
                    DownloadsTab.COMPLETED -> "Finished downloads saved to MediaStore or your Private Folder show here."
                    DownloadsTab.FAILED -> "Interrupted or cancelled downloads can be retried cleanly from this tab."
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task.id) },
                        onCancel = { viewModel.cancelDownload(task.id) },
                        onRetry = { viewModel.retryDownload(task.id) },
                        onRemove = { viewModel.removeDownload(task.id, deleteFile = false) },
                        onOpen = { onOpenCompleted(task) },
                        onShare = { onShareTask(task) },
                        onProcess = { onProcessTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingTaskCard(
    task: ProcessingTask,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalKlipzColors.current
    MonochromeCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = "Media processing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "${task.operation.displayName}: ${task.title}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.selectedEngine.displayName} • ${task.status.name}",
                        style = KlipzMonoTextStyle,
                        color = when (task.status) {
                            ProcessingStatus.COMPLETED -> colors.success
                            ProcessingStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (task.status == ProcessingStatus.RUNNING || task.status == ProcessingStatus.QUEUED) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Cancel processing")
                }
            } else {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss processing task")
                }
            }
        }
        if (task.status == ProcessingStatus.RUNNING) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
        if (!task.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onProcess: () -> Unit
) {
    val colors = LocalKlipzColors.current

    MonochromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_card_${task.id}"),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!task.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    PlatformIconBadge(platform = task.platform, size = 30.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${task.platform.displayName} • ${task.fileFormat} • ${task.qualityLabel}",
                    style = KlipzMonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.PROCESSING -> {
                        LinearProgressIndicator(
                            progress = { task.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = if (task.status == DownloadStatus.PAUSED) colors.warning else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sizeText = if (task.totalBytes > 0L) {
                            "${Formatters.formatBytes(task.downloadedBytes)} / ${Formatters.formatBytes(task.totalBytes)}"
                        } else {
                            Formatters.formatBytes(task.downloadedBytes)
                        }
                        val speedEta = if (task.status == DownloadStatus.DOWNLOADING) {
                            " • ${Formatters.formatSpeed(task.speedBytesPerSec)} • ETA ${Formatters.formatEta(task.etaSeconds)}"
                        } else {
                            " • ${task.status.name}"
                        }
                        Text(
                            text = sizeText + speedEta,
                            style = KlipzMonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DownloadStatus.COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusDotPill(label = "Saved", dotColor = colors.success)
                            Text(
                                text = Formatters.formatBytes(task.totalBytes.coerceAtLeast(task.downloadedBytes)),
                                style = KlipzMonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Text(
                            text = task.errorMessage ?: task.status.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    TextButton(onClick = onPause, modifier = Modifier.testTag("pause_btn_${task.id}")) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_btn_${task.id}")) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }

                DownloadStatus.PAUSED -> {
                    TextButton(onClick = onResume, modifier = Modifier.testTag("resume_btn_${task.id}")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }

                DownloadStatus.COMPLETED -> {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Open", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open")
                    }
                    if (task.mediaType != MediaType.IMAGE) {
                        TextButton(onClick = onProcess) {
                            Icon(Icons.Filled.Transform, contentDescription = "Process", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Process")
                        }
                    }
                    TextButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove from history", modifier = Modifier.size(18.dp))
                    }
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_btn_${task.id}")) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove")
                    }
                }

                DownloadStatus.PROCESSING -> Unit
            }
        }
    }
}

@Composable
private fun LibraryPane(
    state: TasksUiState,
    viewModel: TasksViewModel,
    gridColumns: Int,
    onOpenFile: (LibraryFile) -> Unit,
    onShareFile: (LibraryFile) -> Unit,
    onProcessFile: (LibraryFile) -> Unit,
    onShowInfo: (LibraryFile) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Search + Sort + Grid/List Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.librarySearchQuery,
                onValueChange = viewModel::setLibrarySearch,
                placeholder = { Text("Search library…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.librarySearchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setLibrarySearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("library_search_input")
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("library_sort_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort library")
                    }
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    LibrarySortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.displayName,
                                    fontWeight = if (order == state.librarySortOrder) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                viewModel.setLibrarySort(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                IconButton(
                    onClick = viewModel::toggleLibraryGrid,
                    modifier = Modifier.testTag("library_layout_toggle")
                ) {
                    Icon(
                        imageVector = if (state.isLibraryGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Toggle grid or list view"
                    )
                }
            }
        }

        // Videos / Images / Audio Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters: List<Pair<MediaType?, String>> = listOf(
                null to "All",
                MediaType.VIDEO to "Videos",
                MediaType.IMAGE to "Images",
                MediaType.AUDIO to "Audio"
            )
            filters.forEach { (type, label) ->
                val selected = state.libraryFilter == type
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setLibraryFilter(type) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("library_filter_${label.lowercase()}")
                )
            }
        }

        if (state.filteredLibraryFiles.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Filled.VideoLibrary,
                title = "Your library is empty",
                subtitle = "Completed videos, images, and audio saved to MediaStore (Downloads/Klipz) or your Private Folder will appear here."
            )
        } else if (state.isLibraryGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.filteredLibraryFiles, key = { it.id }) { file ->
                    LibraryGridCard(
                        file = file,
                        onOpen = { onOpenFile(file) },
                        onShare = { onShareFile(file) },
                        onProcess = { onProcessFile(file) },
                        onInfo = { onShowInfo(file) },
                        onDelete = { viewModel.deleteLibraryFile(file) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.filteredLibraryFiles, key = { it.id }) { file ->
                    LibraryListCard(
                        file = file,
                        onOpen = { onOpenFile(file) },
                        onShare = { onShareFile(file) },
                        onProcess = { onProcessFile(file) },
                        onInfo = { onShowInfo(file) },
                        onDelete = { viewModel.deleteLibraryFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridCard(
    file: LibraryFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onProcess: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    MonochromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        contentPadding = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!file.thumbnailUri.isNullOrBlank()) {
                AsyncImage(
                    model = file.thumbnailUri,
                    contentDescription = file.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = when (file.mediaType) {
                        MediaType.VIDEO -> Icons.Filled.PlayArrow
                        MediaType.AUDIO -> Icons.Filled.Audiotrack
                        MediaType.IMAGE -> Icons.Filled.Image
                    },
                    contentDescription = file.title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }

            PlatformIconBadge(
                platform = file.platform,
                size = 22.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${file.fileFormat} • ${Formatters.formatBytes(file.sizeBytes)}",
                    style = KlipzMonoTextStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatters.formatDate(file.dateAdded),
                    style = KlipzMonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "File actions", modifier = Modifier.size(18.dp))
                }
                LibraryFileActionMenu(
                    expanded = menuExpanded,
                    file = file,
                    onDismiss = { menuExpanded = false },
                    onOpen = onOpen,
                    onProcess = onProcess,
                    onShare = onShare,
                    onInfo = onInfo,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun LibraryListCard(
    file: LibraryFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onProcess: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    MonochromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        contentPadding = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!file.thumbnailUri.isNullOrBlank()) {
                    AsyncImage(
                        model = file.thumbnailUri,
                        contentDescription = file.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = when (file.mediaType) {
                            MediaType.VIDEO -> Icons.Filled.PlayArrow
                            MediaType.AUDIO -> Icons.Filled.Audiotrack
                            MediaType.IMAGE -> Icons.Filled.Image
                        },
                        contentDescription = file.title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.fileFormat} • ${file.qualityLabel} • ${Formatters.formatBytes(file.sizeBytes)}",
                    style = KlipzMonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatDate(file.dateAdded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
                LibraryFileActionMenu(
                    expanded = menuExpanded,
                    file = file,
                    onDismiss = { menuExpanded = false },
                    onOpen = onOpen,
                    onProcess = onProcess,
                    onShare = onShare,
                    onInfo = onInfo,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun LibraryFileActionMenu(
    expanded: Boolean,
    file: LibraryFile,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onProcess: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Open / Play") },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            onClick = { onDismiss(); onOpen() }
        )
        if (file.mediaType != MediaType.IMAGE) {
            DropdownMenuItem(
                text = { Text("Process / Convert") },
                leadingIcon = { Icon(Icons.Filled.Transform, contentDescription = null) },
                onClick = { onDismiss(); onProcess() }
            )
        }
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("File Info") },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = { onDismiss(); onInfo() }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

private fun DownloadTask.toLibraryFile(uri: String): LibraryFile = LibraryFile(
    id = id,
    taskId = id,
    title = title,
    mediaType = mediaType,
    mimeType = mimeType,
    fileFormat = fileFormat,
    qualityLabel = qualityLabel,
    platform = platform,
    sizeBytes = totalBytes.coerceAtLeast(downloadedBytes),
    durationMs = null,
    contentUri = uri,
    thumbnailUri = thumbnailUrl ?: if (mediaType == MediaType.IMAGE) uri else null,
    dateAdded = completedAt ?: createdAt,
    isPrivateFolder = isPrivateFolder,
    existsOnDisk = true
)
