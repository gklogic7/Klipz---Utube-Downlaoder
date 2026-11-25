package com.example.feature.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.KlipzAppContainer
import com.example.domain.models.LibraryFile
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.VideoContainer
import com.example.feature.browser.BrowserScreen
import com.example.feature.browser.BrowserViewModel
import com.example.feature.components.InAppMediaPlayerSheet
import com.example.feature.components.KlipzBrandLogo
import com.example.feature.components.MediaInspectionModalHost
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.tasks.TasksMode
import com.example.feature.tasks.TasksScreen
import com.example.feature.tasks.TasksViewModel
import com.example.ui.theme.KlipzMonoTextStyle
import com.example.ui.theme.KlipzTheme

private data class PendingDownloadRequest(
    val info: MediaInfo,
    val format: MediaFormat,
    val outputContainer: VideoContainer,
    val isPrivateFolder: Boolean
)

enum class KlipzDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BROWSER("browser", "Browser", Icons.Filled.Public, Icons.Outlined.Public),
    TASKS("tasks", "Tasks", Icons.Filled.Download, Icons.Outlined.Download),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun KlipzAppRoot(
    container: KlipzAppContainer,
    widthSizeClass: WindowWidthSizeClass,
    incomingSharedUrl: String?,
    onSharedUrlConsumed: () -> Unit
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(
            inspectMediaUrlUseCase = container.inspectMediaUrlUseCase,
            enqueueMediaDownloadUseCase = container.enqueueMediaDownloadUseCase,
            ytDlpBridge = container.ytDlpBridge,
            preferencesRepository = container.preferencesRepository,
            downloadRepository = container.mediaDownloadRepository
        )
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            downloadRepository = container.mediaDownloadRepository,
            libraryRepository = container.libraryRepository,
            browserDataRepository = container.browserDataRepository
        )
    )
    val browserViewModel: BrowserViewModel = viewModel(
        factory = BrowserViewModel.Factory(
            browserDataRepository = container.browserDataRepository,
            preferencesRepository = container.preferencesRepository
        )
    )
    val tasksViewModel: TasksViewModel = viewModel(
        factory = TasksViewModel.Factory(
            downloadRepository = container.mediaDownloadRepository,
            libraryRepository = container.libraryRepository,
            downloadManager = container.downloadManager,
            processingRouter = container.processingRouter,
            processLibraryMediaUseCase = container.processLibraryMediaUseCase
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            preferencesRepository = container.preferencesRepository,
            storageManager = container.storageManager,
            libraryRepository = container.libraryRepository,
            ytDlpBridge = container.ytDlpBridge
        )
    )

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val activeCount by mainViewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val inspectionState by mainViewModel.inspectionState.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(KlipzDestination.HOME) }
    var activePlayerFile by remember { mutableStateOf<LibraryFile?>(null) }
    var pendingDownloadAfterPermission by remember { mutableStateOf<PendingDownloadRequest?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingDownloadAfterPermission?.let { req ->
            mainViewModel.startDownload(req.info, req.format, req.outputContainer, req.isPrivateFolder)
            pendingDownloadAfterPermission = null
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Handle native Android Share Intent (ACTION_SEND text/plain URL) safely without auto-executing
    LaunchedEffect(incomingSharedUrl) {
        if (!incomingSharedUrl.isNullOrBlank()) {
            homeViewModel.onUrlInputChange(incomingSharedUrl)
            mainViewModel.inspectUrl(incomingSharedUrl)
            onSharedUrlConsumed()
        }
    }

    // Return to Home on system back from secondary tabs
    BackHandler(enabled = currentDestination != KlipzDestination.HOME) {
        currentDestination = KlipzDestination.HOME
    }

    val isWideLayout = widthSizeClass != WindowWidthSizeClass.Compact

    val triggerDownload: (MediaInfo, MediaFormat, VideoContainer, Boolean) -> Unit = { info, format, outputContainer, isPrivate ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            preferences.showNotifications &&
            !container.notificationManager.hasNotificationPermission()
        ) {
            pendingDownloadAfterPermission = PendingDownloadRequest(info, format, outputContainer, isPrivate)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            mainViewModel.startDownload(info, format, outputContainer, isPrivate)
        }
    }

    KlipzTheme(
        themeMode = preferences.themeMode,
        layoutDensity = preferences.layoutDensity
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideLayout) {
                    KlipzBottomNavBar(
                        current = currentDestination,
                        activeCount = activeCount,
                        onSelect = { currentDestination = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideLayout) {
                    KlipzNavigationRail(
                        current = currentDestination,
                        activeCount = activeCount,
                        onSelect = { currentDestination = it }
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        KlipzDestination.HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            isWideLayout = isWideLayout,
                            onInspectUrl = { url -> mainViewModel.inspectUrl(url) },
                            onOpenInBrowser = { url ->
                                browserViewModel.loadUrl(url)
                                currentDestination = KlipzDestination.BROWSER
                            },
                            onOpenTasksDownloads = {
                                tasksViewModel.setMode(TasksMode.DOWNLOADS)
                                currentDestination = KlipzDestination.TASKS
                            },
                            onOpenTasksLibrary = {
                                tasksViewModel.setMode(TasksMode.LIBRARY)
                                currentDestination = KlipzDestination.TASKS
                            },
                            onOpenLibraryFile = { file -> activePlayerFile = file }
                        )

                        KlipzDestination.BROWSER -> BrowserScreen(
                            viewModel = browserViewModel,
                            onAnalyzeCurrentPage = { url, streams, titleHint ->
                                mainViewModel.inspectUrl(url, streams, titleHint)
                            },
                            onNavigateHome = { currentDestination = KlipzDestination.HOME }
                        )

                        KlipzDestination.TASKS -> TasksScreen(
                            viewModel = tasksViewModel,
                            isWideLayout = isWideLayout,
                            onOpenLibraryFile = { file -> activePlayerFile = file },
                            onShareUri = { uri, mimeType, title ->
                                shareContentUri(context, uri, mimeType, title)
                            }
                        )

                        KlipzDestination.SETTINGS -> SettingsScreen(
                            viewModel = settingsViewModel
                        )
                    }
                }
            }
        }

        // Global Analyzing -> Media Preview & 3x3 Quality Selection Modal Sheet
        MediaInspectionModalHost(
            state = inspectionState,
            defaultVideoContainer = preferences.defaultVideoContainer,
            onCancelAnalysis = mainViewModel::cancelInspection,
            onDismiss = mainViewModel::dismissInspection,
            onRetryInspect = { url -> mainViewModel.inspectUrl(url) },
            onStartDownload = triggerDownload,
            onOpenTasksTab = {
                mainViewModel.dismissInspection()
                tasksViewModel.setMode(TasksMode.DOWNLOADS)
                currentDestination = KlipzDestination.TASKS
            },
            onOpenInBrowser = { url ->
                mainViewModel.dismissInspection()
                browserViewModel.loadUrl(url)
                currentDestination = KlipzDestination.BROWSER
            },
            onOpenSettings = {
                mainViewModel.dismissInspection()
                currentDestination = KlipzDestination.SETTINGS
            }
        )

        // Global In-App Media3 ExoPlayer / Image Viewer Modal
        activePlayerFile?.let { file ->
            InAppMediaPlayerSheet(
                file = file,
                onDismiss = { activePlayerFile = null },
                onShare = { target -> shareContentUri(context, target.contentUri, target.mimeType, target.title) },
                onProcess = {
                    activePlayerFile = null
                    tasksViewModel.setMode(TasksMode.LIBRARY)
                    currentDestination = KlipzDestination.TASKS
                    mainViewModel.emitMessage("Select Process from \"${file.title}\" menu in Library.")
                }
            )
        }
    }
}

@Composable
private fun KlipzBottomNavBar(
    current: KlipzDestination,
    activeCount: Int,
    onSelect: (KlipzDestination) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KlipzDestination.entries.forEach { dest ->
                    val selected = dest == current
                    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val fgColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(dest) }
                            .testTag("nav_${dest.route}"),
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = if (selected) null else BorderStroke(0.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (dest == KlipzDestination.TASKS && activeCount > 0) {
                                        Badge(
                                            containerColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(activeCount.toString(), style = KlipzMonoTextStyle)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                    tint = fgColor,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = fgColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KlipzNavigationRail(
    current: KlipzDestination,
    activeCount: Int,
    onSelect: (KlipzDestination) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                KlipzBrandLogo()
            }
            Spacer(modifier = Modifier.height(16.dp))
        },
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .statusBarsPadding()
    ) {
        KlipzDestination.entries.forEach { dest ->
            val selected = dest == current
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(dest) },
                modifier = Modifier.testTag("rail_${dest.route}"),
                icon = {
                    BadgedBox(
                        badge = {
                            if (dest == KlipzDestination.TASKS && activeCount > 0) {
                                Badge { Text(activeCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label
                        )
                    }
                },
                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun shareContentUri(context: Context, uriString: String, mimeType: String, title: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }
}
