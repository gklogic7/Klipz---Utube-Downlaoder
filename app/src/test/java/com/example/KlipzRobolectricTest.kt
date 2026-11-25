package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.UrlValidator
import com.example.core.database.DownloadTaskEntity
import com.example.core.database.KlipzDatabase
import com.example.core.database.MediaEntity
import com.example.core.database.MediaFormatEntity
import com.example.core.storage.MediaStoreManager
import com.example.domain.models.DownloadStatus
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.MediaType
import com.example.domain.models.Platform
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ProcessingOperation
import com.example.downloader.engine.DownloadRecovery
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.processing.ffmpeg.FfmpegProcessingEngine
import com.example.processing.media3.Media3ProcessingEngine
import com.example.processing.router.MediaProcessingRouter
import com.example.processing.validation.MediaInspector
import com.example.processing.validation.MediaStreamInspection
import com.example.processing.validation.OutputValidator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KlipzRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: KlipzDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KlipzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name resource is Klipz and share intent extracts url safely`() {
        assertEquals("Klipz", context.getString(R.string.app_name))

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Watch this clip: https://example.com/media/demo.mp4 thanks!")
        }
        assertEquals("https://example.com/media/demo.mp4", UrlValidator.extractSharedUrl(shareIntent))
    }

    @Test
    fun `room database persists media formats and recovers interrupted downloads`() = runTest {
        val now = System.currentTimeMillis()
        db.mediaDao().insertMedia(
            MediaEntity(
                id = "m1",
                title = "Demo Video",
                sourceUrl = "https://example.com/demo.mp4",
                platform = Platform.DIRECT.name,
                mediaType = MediaType.VIDEO.name,
                thumbnail = null,
                duration = 120_000L,
                createdAt = now
            )
        )
        db.mediaDao().insertFormats(
            listOf(
                MediaFormatEntity(
                    mediaId = "m1",
                    formatId = "fmt_1080",
                    resolution = "1080p",
                    codec = "H.264",
                    mimeType = "video/mp4",
                    fileSize = 15_000_000L
                )
            )
        )
        assertEquals(1, db.mediaDao().getFormatsForMedia("m1").size)

        db.downloadTaskDao().upsertTask(
            DownloadTaskEntity(
                id = "t1",
                mediaId = "m1",
                formatId = "fmt_1080",
                title = "Demo Video",
                sourceUrl = "https://example.com/demo.mp4",
                platform = Platform.DIRECT.name,
                mediaType = MediaType.VIDEO.name,
                thumbnailUrl = null,
                qualityLabel = "1080p",
                fileFormat = "MP4",
                mimeType = "video/mp4",
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0.4f,
                downloadedBytes = 6_000_000L,
                totalBytes = 15_000_000L,
                speedBytesPerSec = 500_000L,
                etaSeconds = 18L,
                eTag = "\"abc\"",
                lastModified = null,
                supportsRange = true,
                tempFilePath = null,
                outputUri = null,
                isPrivateFolder = false,
                priority = 0,
                createdAt = now,
                completedAt = null,
                errorMessage = null
            )
        )

        val duplicate = db.downloadTaskDao().findDuplicateTask("https://example.com/demo.mp4", "fmt_1080")
        assertNotNull(duplicate)

        val recovery = DownloadRecovery(db.downloadTaskDao())
        val recoveredCount = recovery.recoverInterruptedTasks()
        assertEquals(1, recoveredCount)
        assertEquals(DownloadStatus.PAUSED.name, db.downloadTaskDao().getTaskById("t1")?.status)
    }

    @Test
    fun `mediastore manager maps relative folders cleanly`() {
        val storage = MediaStoreManager(context)
        assertTrue(storage.relativePathFor(MediaType.VIDEO).endsWith("Klipz/Videos"))
        assertTrue(storage.relativePathFor(MediaType.IMAGE).endsWith("Klipz/Images"))
        assertTrue(storage.relativePathFor(MediaType.AUDIO).endsWith("Klipz/Audio"))
    }

    @Test
    fun `ytdlp bridge parses real json formats and blocks drm streams`() {
        val bridge = YtDlpBridge(context)
        val drmJson = """{"title":"Protected Movie","_has_drm":true,"formats":[]}"""
        val drmResult = bridge.parseYtDlpJson(drmJson, "https://example.com/drm", Platform.WEB)
        assertTrue(drmResult is MediaInspectionResult.Restricted)

        val validJson = """
            {
              "title": "Open Talk",
              "duration": 95.0,
              "formats": [
                {"format_id":"18","height":360,"vcodec":"avc1","acodec":"mp4a","ext":"mp4","filesize":5000000,"url":"https://cdn.example.com/360.mp4"},
                {"format_id":"140","height":0,"vcodec":"none","acodec":"mp4a","ext":"m4a","filesize":1200000,"url":"https://cdn.example.com/audio.m4a"}
              ]
            }
        """.trimIndent()
        val parsed = bridge.parseYtDlpJson(validJson, "https://example.com/talk", Platform.TED)
        assertTrue(parsed is MediaInspectionResult.Success)
        val formats = (parsed as MediaInspectionResult.Success).mediaInfo.formats
        assertEquals(2, formats.size)
        assertTrue(formats.any { it.resolution == "360p" })
        assertTrue(formats.any { it.isAudioOnly })
    }

    @Test
    fun `processing router preserves original quality and routes mp3 and merge to ffmpeg`() {
        val router = MediaProcessingRouter(
            media3Engine = Media3ProcessingEngine(context),
            ffmpegEngine = FfmpegProcessingEngine(),
            inspector = MediaInspector(),
            validator = OutputValidator(),
            storageManager = MediaStoreManager(context),
            processingTaskDao = db.processingTaskDao()
        )
        val mp4Sample = MediaStreamInspection(
            filePath = "/tmp/sample.mp4",
            containerExtension = "mp4",
            hasVideo = true,
            hasAudio = true,
            videoMime = "video/avc",
            audioMime = "audio/mp4a-latm",
            width = 1920,
            height = 1080,
            durationUs = 10_000_000L,
            sizeBytes = 5_000_000L
        )

        val preserve = router.decideEngine(
            inspection = mp4Sample,
            operation = ProcessingOperation.CONVERT_VIDEO,
            preference = ProcessingEngine.AUTO,
            preserveOriginalQuality = true
        )
        assertFalse(preserve.requiresConversion)

        val mp3Decision = router.decideEngine(
            inspection = mp4Sample,
            operation = ProcessingOperation.EXTRACT_AUDIO,
            preference = ProcessingEngine.AUTO,
            preserveOriginalQuality = false,
            preferMp3Audio = true
        )
        assertEquals(ProcessingEngine.FFMPEG, mp3Decision.engine)
        assertEquals("mp3", mp3Decision.targetExtension)

        val mergeDecision = router.decideEngine(
            inspection = mp4Sample,
            operation = ProcessingOperation.MERGE_AUDIO_VIDEO,
            preference = ProcessingEngine.AUTO,
            preserveOriginalQuality = false
        )
        assertEquals(ProcessingEngine.FFMPEG, mergeDecision.engine)
    }
}
