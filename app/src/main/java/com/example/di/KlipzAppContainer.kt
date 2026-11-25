package com.example.di

import android.content.Context
import com.example.core.common.NetworkMonitor
import com.example.core.database.KlipzDatabase
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.network.NetworkClientFactory
import com.example.core.notifications.KlipzNotificationManager
import com.example.core.storage.MediaStoreManager
import com.example.data.repository.BrowserDataRepository
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MediaDownloadRepository
import com.example.domain.usecases.EnqueueMediaDownloadUseCase
import com.example.domain.usecases.InspectMediaUrlUseCase
import com.example.domain.usecases.ProcessLibraryMediaUseCase
import com.example.downloader.engine.DownloadEngine
import com.example.downloader.engine.DownloadManager
import com.example.downloader.engine.DownloadQueue
import com.example.downloader.engine.DownloadRecovery
import com.example.downloader.engine.DownloadScheduler
import com.example.downloader.providers.DirectMediaProvider
import com.example.downloader.providers.FacebookProvider
import com.example.downloader.providers.GeneralYtDlpPlatformProvider
import com.example.downloader.providers.InstagramProvider
import com.example.downloader.providers.MediaProviderRegistry
import com.example.downloader.providers.YouTubeProvider
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.processing.ffmpeg.FfmpegProcessingEngine
import com.example.processing.media3.Media3ProcessingEngine
import com.example.processing.router.MediaProcessingRouter
import com.example.processing.validation.MediaInspector
import com.example.processing.validation.OutputValidator

/**
 * Clean constructor-injection dependency graph for Klipz.
 */
class KlipzAppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: KlipzDatabase = KlipzDatabase.create(appContext)
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(appContext)
    val storageManager: MediaStoreManager = MediaStoreManager(appContext)
    val notificationManager: KlipzNotificationManager = KlipzNotificationManager(appContext)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

    private val inspectionClient = NetworkClientFactory.createInspectionClient()
    private val downloadClient = NetworkClientFactory.createDownloadClient()

    val ytDlpBridge: YtDlpBridge = YtDlpBridge(appContext)
    private val directMediaProvider = DirectMediaProvider(inspectionClient)
    val providerRegistry: MediaProviderRegistry = MediaProviderRegistry(
        providers = listOf(
            YouTubeProvider(ytDlpBridge),
            FacebookProvider(directMediaProvider, ytDlpBridge),
            InstagramProvider(directMediaProvider, ytDlpBridge),
            GeneralYtDlpPlatformProvider(ytDlpBridge),
            directMediaProvider
        ),
        fallbackDirectProvider = directMediaProvider,
        ytDlpBridge = ytDlpBridge
    )

    val downloadQueue: DownloadQueue = DownloadQueue(initialMaxConcurrent = 3)
    val downloadEngine: DownloadEngine = DownloadEngine(downloadClient, storageManager, networkMonitor)
    val downloadScheduler: DownloadScheduler = DownloadScheduler(appContext)
    val downloadRecovery: DownloadRecovery = DownloadRecovery(database.downloadTaskDao())

    val downloadManager: DownloadManager = DownloadManager(
        mediaDao = database.mediaDao(),
        downloadTaskDao = database.downloadTaskDao(),
        preferencesRepository = preferencesRepository,
        downloadEngine = downloadEngine,
        downloadQueue = downloadQueue,
        downloadScheduler = downloadScheduler,
        downloadRecovery = downloadRecovery,
        storageManager = storageManager,
        notificationManager = notificationManager,
        providerRegistry = providerRegistry,
        ytDlpBridge = ytDlpBridge,
        networkMonitor = networkMonitor
    )

    val mediaInspector: MediaInspector = MediaInspector()
    val outputValidator: OutputValidator = OutputValidator()
    val media3Engine: Media3ProcessingEngine = Media3ProcessingEngine(appContext)
    val ffmpegEngine: FfmpegProcessingEngine = FfmpegProcessingEngine()
    val processingRouter: MediaProcessingRouter = MediaProcessingRouter(
        media3Engine = media3Engine,
        ffmpegEngine = ffmpegEngine,
        inspector = mediaInspector,
        validator = outputValidator,
        storageManager = storageManager,
        processingTaskDao = database.processingTaskDao()
    )

    val mediaDownloadRepository: MediaDownloadRepository = MediaDownloadRepository(
        downloadTaskDao = database.downloadTaskDao(),
        processingTaskDao = database.processingTaskDao()
    )
    val libraryRepository: LibraryRepository = LibraryRepository(
        downloadTaskDao = database.downloadTaskDao(),
        storageManager = storageManager
    )
    val browserDataRepository: BrowserDataRepository = BrowserDataRepository(
        webHistoryDao = database.webHistoryDao(),
        bookmarkDao = database.bookmarkDao()
    )

    val inspectMediaUrlUseCase: InspectMediaUrlUseCase = InspectMediaUrlUseCase(providerRegistry)
    val enqueueMediaDownloadUseCase: EnqueueMediaDownloadUseCase = EnqueueMediaDownloadUseCase(downloadManager)
    val processLibraryMediaUseCase: ProcessLibraryMediaUseCase = ProcessLibraryMediaUseCase(
        router = processingRouter,
        preferencesRepository = preferencesRepository
    )
}
