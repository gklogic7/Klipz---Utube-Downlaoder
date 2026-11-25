package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.domain.models.BookmarkItem
import com.example.domain.models.LibraryFile
import com.example.domain.models.MediaType
import com.example.domain.models.PlatformShortcut
import com.example.domain.models.WebHistoryItem
import com.example.feature.components.EmptyStatePanel
import com.example.feature.components.KlipzBrandLogo
import com.example.feature.components.MonochromeCard
import com.example.feature.components.PlatformIconBadge
import com.example.feature.components.StatusDotPill
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.LocalKlipzColors
import com.example.ui.theme.LocalKlipzSpacing

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isWideLayout: Boolean,
    onInspectUrl: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onOpenTasksDownloads: () -> Unit,
    onOpenTasksLibrary: () -> Unit,
    onOpenLibraryFile: (LibraryFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val spacing = LocalKlipzSpacing.current

    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showDirectUrlDialog by remember { mutableStateOf(false) }

    // Non-intrusive clipboard link detection (asks consent via banner; never auto-executes)
    LaunchedEffect(Unit) {
        val clipText = runCatching { clipboardManager.getText()?.text }.getOrNull()
        viewModel.onClipboardTextChecked(clipText)
    }

    val submitInput: () -> Unit = {
        val raw = state.urlInput.trim()
        if (raw.isNotEmpty()) {
            val normalized = UrlValidator.normalizeUrl(raw)
            if (UrlValidator.isValidHttpUrl(normalized)) {
                onInspectUrl(normalized)
            } else {
                // Treat non-URL query as web search in Klipz Browser
                onOpenInBrowser("https://duckduckgo.com/?q=${java.net.URLEncoder.encode(raw, "UTF-8")}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
    ) {
        // Header: Klipz brand mark + Live Queue Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KlipzBrandLogo()
            StatusDotPill(
                label = if (state.activeDownloads.isNotEmpty()) {
                    "${state.activeDownloads.size} active"
                } else {
                    "Ready"
                },
                dotColor = if (state.activeDownloads.isNotEmpty()) {
                    LocalKlipzColors.current.success
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenTasksDownloads)
            )
        }

        // URL Paste & Search Input Bar (matches reference screenshot top bar in monochrome)
        HomeUrlInputBar(
            value = state.urlInput,
            onValueChange = viewModel::onUrlInputChange,
            onPasteClick = {
                val clip = runCatching { clipboardManager.getText()?.text }.getOrNull()
                viewModel.pasteIntoInput(clip)
            },
            onSubmit = submitInput
        )

        // Opt-in Clipboard Link Detection Consent Strip
        AnimatedVisibility(visible = state.clipboardCandidateUrl != null) {
            val candidate = state.clipboardCandidateUrl ?: return@AnimatedVisibility
            MonochromeCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "Clipboard link",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Link detected on clipboard",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = UrlValidator.redactForLog(candidate),
                            style = KlipzMonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.onUrlInputChange(candidate)
                            viewModel.dismissClipboardSuggestion()
                            onInspectUrl(candidate)
                        },
                        modifier = Modifier.testTag("clipboard_inspect_button")
                    ) {
                        Text("Inspect")
                    }
                    IconButton(
                        onClick = viewModel::dismissClipboardSuggestion,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss clipboard link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (isWideLayout) {
            // Adaptive Two-Column Tablet Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
                ) {
                    PlatformShortcutGridCard(
                        shortcuts = state.shortcuts,
                        onShortcutClick = { shortcut ->
                            if (shortcut.isDirectPrompt) {
                                showDirectUrlDialog = true
                            } else {
                                onOpenInBrowser(shortcut.launchUrl)
                            }
                        }
                    )
                    HistoryBookmarksStrip(
                        historyCount = state.historyCount,
                        bookmarkCount = state.bookmarkCount,
                        onHistoryClick = { showHistorySheet = true },
                        onBookmarksClick = { showBookmarksSheet = true }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
                ) {
                    ActiveDownloadsBanner(
                        activeCount = state.activeDownloads.size,
                        averageProgress = state.activeDownloads.map { it.progress }.average().toFloat().takeIf { !it.isNaN() } ?: 0f,
                        onClick = onOpenTasksDownloads
                    )
                    RecentDownloadsSection(
                        recentFiles = state.recentDownloads,
                        columns = 3,
                        onViewAll = onOpenTasksLibrary,
                        onOpenFile = onOpenLibraryFile
                    )
                }
            }
        } else {
            // Compact Phone Layout (faithful vertical cadence to reference screenshot)
            PlatformShortcutGridCard(
                shortcuts = state.shortcuts,
                onShortcutClick = { shortcut ->
                    if (shortcut.isDirectPrompt) {
                        showDirectUrlDialog = true
                    } else {
                        onOpenInBrowser(shortcut.launchUrl)
                    }
                }
            )

            HistoryBookmarksStrip(
                historyCount = state.historyCount,
                bookmarkCount = state.bookmarkCount,
                onHistoryClick = { showHistorySheet = true },
                onBookmarksClick = { showBookmarksSheet = true }
            )

            ActiveDownloadsBanner(
                activeCount = state.activeDownloads.size,
                averageProgress = state.activeDownloads.map { it.progress }.average().toFloat().takeIf { !it.isNaN() } ?: 0f,
                onClick = onOpenTasksDownloads
            )

            RecentDownloadsSection(
                recentFiles = state.recentDownloads,
                columns = 3,
                onViewAll = onOpenTasksLibrary,
                onOpenFile = onOpenLibraryFile
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showHistorySheet) {
        HistoryListBottomSheet(
            items = state.historyItems,
            onDismiss = { showHistorySheet = false },
            onSelectUrl = { url ->
                showHistorySheet = false
                onOpenInBrowser(url)
            },
            onDelete = viewModel::deleteHistoryItem,
            onClearAll = viewModel::clearHistory
        )
    }

    if (showBookmarksSheet) {
        BookmarksListBottomSheet(
            items = state.bookmarkItems,
            onDismiss = { showBookmarksSheet = false },
            onSelectUrl = { url ->
                showBookmarksSheet = false
                onOpenInBrowser(url)
            },
            onDelete = viewModel::deleteBookmark
        )
    }

    if (showDirectUrlDialog) {
        DirectUrlImportSheet(
            onDismiss = { showDirectUrlDialog = false },
            onInspect = { url ->
                showDirectUrlDialog = false
                viewModel.onUrlInputChange(url)
                onInspectUrl(url)
            }
        )
    }
}

@Composable
private fun HomeUrlInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .testTag("home_url_input"),
            placeholder = {
                Text(
                    text = "Search or enter media URL…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "URL",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onPasteClick,
                        modifier = Modifier.testTag("home_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Paste URL",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() })
        )

        Surface(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onSubmit)
                .testTag("home_inspect_button"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Inspect or Go",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlatformShortcutGridCard(
    shortcuts: List<PlatformShortcut>,
    onShortcutClick: (PlatformShortcut) -> Unit
) {
    MonochromeCard(contentPadding = 14.dp) {
        val rows = shortcuts.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowItems.forEach { shortcut ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onShortcutClick(shortcut) }
                                .padding(vertical = 4.dp)
                                .testTag("shortcut_${shortcut.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlatformIconBadge(
                                platform = shortcut.platform,
                                shortcutId = shortcut.id,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = shortcut.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryBookmarksStrip(
    historyCount: Int,
    bookmarkCount: Int,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onHistoryClick)
                    .padding(horizontal = 14.dp)
                    .testTag("home_history_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Web History",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Web History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                CountPill(count = historyCount)
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onBookmarksClick)
                    .padding(horizontal = 14.dp)
                    .testTag("home_bookmarks_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Bookmarks",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bookmarks",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                CountPill(count = bookmarkCount)
            }
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = KlipzMonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActiveDownloadsBanner(
    activeCount: Int,
    averageProgress: Float,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("home_active_downloads_banner"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Downloads",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = when (activeCount) {
                                0 -> "Download Queue Idle"
                                1 -> "You Have 1 Active Download"
                                else -> "You Have $activeCount Active Downloads"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeCount > 0) "Tap to manage progress, pause, or prioritize" else "Open Tasks to view queue & local library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Downloads",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (activeCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { averageProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentDownloadsSection(
    recentFiles: List<LibraryFile>,
    columns: Int,
    onViewAll: () -> Unit,
    onOpenFile: (LibraryFile) -> Unit
) {
    MonochromeCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewAll)
                .padding(bottom = 12.dp)
                .testTag("home_recent_view_all"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Library",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (recentFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = "Empty recent downloads",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No downloads yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste a direct media URL above or explore via Browser to save media locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val rows = recentFiles.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { file ->
                            RecentMediaThumbnailTile(
                                file = file,
                                onClick = { onOpenFile(file) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMediaThumbnailTile(
    file: LibraryFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("recent_tile_${file.id}")
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
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }

        // Top-left platform badge
        PlatformIconBadge(
            platform = file.platform,
            size = 20.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
        )

        // Bottom format/size pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = file.fileFormat,
                style = KlipzMonoTextStyle,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryListBottomSheet(
    items: List<WebHistoryItem>,
    onDismiss: () -> Unit,
    onSelectUrl: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Web History", style = MaterialTheme.typography.titleLarge)
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("Clear All") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (items.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Filled.History,
                    title = "No browsing history",
                    subtitle = "Pages visited in the Klipz Browser will appear here."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectUrl(item.url) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PlatformIconBadge(platform = item.platform, size = 32.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${UrlValidator.redactForLog(item.url)} • ${Formatters.formatDateTime(item.visitedAt)}",
                                        style = KlipzMonoTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove history item")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksListBottomSheet(
    items: List<BookmarkItem>,
    onDismiss: () -> Unit,
    onSelectUrl: (String) -> Unit,
    onDelete: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(10.dp))
            if (items.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Filled.Bookmark,
                    title = "No saved bookmarks",
                    subtitle = "Tap the bookmark star in the Browser address bar to save favorite media sources."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectUrl(item.url) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PlatformIconBadge(platform = item.platform, size = 32.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = UrlValidator.redactForLog(item.url),
                                        style = KlipzMonoTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete bookmark")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectUrlImportSheet(
    onDismiss: () -> Unit,
    onInspect: (String) -> Unit
) {
    var directUrl by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Direct Media URL Import", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Paste a direct HTTPS link to an MP4, WEBM, M4A, MP3, JPEG, PNG, or WEBP asset, or an authorized media webpage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = directUrl,
                onValueChange = { directUrl = it },
                label = { Text("https://example.com/video.mp4") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("direct_url_modal_input"),
                shape = RoundedCornerShape(12.dp)
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
                ) { Text("Cancel") }
                Button(
                    onClick = { onInspect(directUrl.trim()) },
                    enabled = UrlValidator.isValidHttpUrl(UrlValidator.normalizeUrl(directUrl)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("direct_url_inspect_button"),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Analyze URL") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
