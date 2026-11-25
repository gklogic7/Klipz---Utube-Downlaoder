package com.example

import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.Platform
import com.example.domain.models.ProcessingOperation
import com.example.domain.models.VideoContainer
import com.example.downloader.engine.DownloadQueue
import com.example.downloader.engine.TransferRateTracker
import com.example.downloader.providers.DirectMediaProvider
import com.example.downloader.ytdlp.YtDlpBridge
import com.example.processing.ffmpeg.FfmpegProcessingEngine
import com.example.processing.validation.OutputValidator
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KlipzCoreAndQueueUnitTest {

    @Test
    fun `url validator accepts valid http and https and rejects malformed or localhost`() {
        assertTrue(UrlValidator.isValidHttpUrl("https://example.com/video.mp4"))
        assertTrue(UrlValidator.isValidHttpUrl("http://cdn.example.org/file.jpg"))
        assertTrue(UrlValidator.isSecureHttps("https://example.com/clip.mp4"))
        assertFalse(UrlValidator.isSecureHttps("http://example.com/clip.mp4"))
        assertFalse(UrlValidator.isValidHttpUrl("ftp://example.com/clip.mp4"))
        assertFalse(UrlValidator.isValidHttpUrl("http://localhost:8080/test.mp4"))
        assertFalse(UrlValidator.isValidHttpUrl("not a url"))
    }

    @Test
    fun `url validator detects supported platforms and sanitizes filenames`() {
        assertEquals(Platform.YOUTUBE, UrlValidator.detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, UrlValidator.detectPlatform("https://m.youtube.com/watch?v=abc123"))
        assertEquals(Platform.FACEBOOK, UrlValidator.detectPlatform("https://www.facebook.com/watch/?v=12345"))
        assertEquals(Platform.INSTAGRAM, UrlValidator.detectPlatform("https://www.instagram.com/reel/XYZ/"))
        assertEquals(Platform.DIRECT, UrlValidator.detectPlatform("https://media.example.com/sample/video.mp4"))
        assertEquals(Platform.TED, UrlValidator.detectPlatform("https://www.ted.com/talks/sample"))

        val safe = UrlValidator.sanitizeFileName("My:Clip/2026*?\"<>|Test.mp4")
        assertFalse(safe.contains(":"))
        assertFalse(safe.contains("/"))
        assertFalse(safe.contains("*"))

        val redacted = UrlValidator.redactForLog("https://cdn.example.com/v.mp4?token=secret123&sig=abc")
        assertEquals("https://cdn.example.com/v.mp4", redacted)
        assertFalse(redacted.contains("secret123"))
    }

    @Test
    fun `direct media provider extracts real html5 video without image clutter or invented resolutions`() {
        val provider = DirectMediaProvider(OkHttpClient())
        val html = """
            <html>
              <head>
                <title>Sample Public Lecture</title>
                <meta property="og:image" content="https://cdn.example.com/thumb.jpg" />
              </head>
              <body>
                <video controls>
                  <source src="https://cdn.example.com/lecture.mp4" type="video/mp4" />
                </video>
              </body>
            </html>
        """.trimIndent()

        val result = provider.inspectHtmlForAuthorizedMedia("https://example.com/lecture", html)
        assertTrue(result is MediaInspectionResult.Success)
        val info = (result as MediaInspectionResult.Success).mediaInfo
        assertEquals("Sample Public Lecture", info.title)
        // Video stream found — og:image must NOT pollute available formats as an image-only download
        assertEquals(1, info.formats.size)
        assertTrue(info.formats.all { it.hasVideo })
        assertEquals("https://cdn.example.com/lecture.mp4", info.formats.first().directUrl)
        // Must not fabricate unavailable resolutions like 2160p/1440p
        assertFalse(info.formats.any { it.resolution == "2160p" })
    }

    @Test
    fun `direct media provider still extracts og image on genuine image-only non-video pages`() {
        val provider = DirectMediaProvider(OkHttpClient())
        val html = """
            <html>
              <head>
                <title>Public Photo Gallery</title>
                <meta property="og:image" content="https://cdn.example.com/photo_full.jpg" />
              </head>
              <body><p>Photo article</p></body>
            </html>
        """.trimIndent()

        val result = provider.inspectHtmlForAuthorizedMedia("https://example.com/gallery/photo", html)
        assertTrue(result is MediaInspectionResult.Success)
        val info = (result as MediaInspectionResult.Success).mediaInfo
        assertEquals(1, info.formats.size)
        assertEquals("https://cdn.example.com/photo_full.jpg", info.formats.first().directUrl)
        assertEquals("image/jpeg", info.formats.first().mimeType)
    }

    @Test
    fun `ytdlp parser extracts full youtube resolutions audio formats and filters storyboards and drm`() {
        val sampleJson = """
            {
              "title": "Demo 4K YouTube Video",
              "thumbnail": "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
              "duration": 242,
              "uploader": "Demo Channel",
              "formats": [
                {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none"},
                {"format_id": "img0", "ext": "jpg", "vcodec": "none", "acodec": "none"},
                {"format_id": "drm99", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "avc1", "acodec": "none", "has_drm": true},
                {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 128, "filesize": 3872000},
                {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus", "abr": 160, "filesize": 4840000},
                {"format_id": "18", "ext": "mp4", "width": 640, "height": 360, "fps": 30, "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "filesize": 13400000, "url": "https://rr1.googlevideo.com/videoplayback?id=18"},
                {"format_id": "137", "ext": "mp4", "width": 1920, "height": 1080, "fps": 30, "vcodec": "avc1.640028", "acodec": "none", "filesize": 78000000},
                {"format_id": "315", "ext": "webm", "width": 3840, "height": 2160, "fps": 60, "vcodec": "vp09.00.51", "acodec": "none", "filesize": 168000000}
              ]
            }
        """.trimIndent()

        // Using a null-safe pure-JVM check of YtDlpBridge parsing via helper
        val bridge = createPureJvmYtDlpBridge()
        val result = bridge.parseYtDlpJson(
            rawJson = sampleJson,
            sourceUrl = "https://www.youtube.com/watch?v=abc123",
            platform = Platform.YOUTUBE
        )
        assertTrue(result is MediaInspectionResult.Success)
        val info = (result as MediaInspectionResult.Success).mediaInfo

        // Must expose Audio M4A, Audio MP3, 2160p60, 1080p, 360p — zero image/storyboard/DRM formats
        assertTrue(info.formats.any { it.isAudioOnly && it.container == "M4A" })
        assertTrue(info.formats.any { it.isAudioOnly && it.container == "MP3" })
        val uhd = info.formats.firstOrNull { it.resolution == "2160p60" }
        assertNotNull(uhd)
        assertEquals(3840, uhd!!.widthPx)
        assertEquals(2160, uhd.heightPx)
        assertEquals("16:9", uhd.aspectRatioLabel)
        assertTrue(uhd.requiresFfmpegMerge)
        assertTrue(uhd.isYtDlpManaged)
        assertNotNull(uhd.ytDlpFormatSelector)
        assertTrue(uhd.ytDlpFormatSelector!!.startsWith("315+140"))

        assertTrue(info.formats.any { it.resolution == "1080p" && it.widthPx == 1920 && it.heightPx == 1080 })
        assertTrue(info.formats.any { it.resolution == "360p" && it.widthPx == 640 && it.heightPx == 360 })
        assertFalse(info.formats.any { it.mimeType.startsWith("image/") })
        assertFalse(info.formats.any { it.formatId.contains("drm99") })
    }

    @Test
    fun `ytdlp resolution tier and aspect ratio preserve vertical youtube shorts and ultrawide ratios`() {
        val bridge = createPureJvmYtDlpBridge()
        // Vertical 1080x1920 YouTube Short -> 1080p, 9:16
        assertEquals(1080, bridge.computeResolutionTier(1080, 1920))
        assertEquals("9:16", bridge.computeAspectRatioLabel(1080, 1920))
        // Standard 4K 16:9
        assertEquals(2160, bridge.computeResolutionTier(3840, 2160))
        assertEquals("16:9", bridge.computeAspectRatioLabel(3840, 2160))
        // Ultrawide 21:9
        assertEquals("21:9", bridge.computeAspectRatioLabel(2560, 1080))
        // Square 1:1
        assertEquals("1:1", bridge.computeAspectRatioLabel(1080, 1080))
    }

    @Test
    fun `video container enum maps mp4 and mkv mime types and extensions accurately`() {
        assertEquals("mp4", VideoContainer.MP4.extension)
        assertEquals("video/mp4", VideoContainer.MP4.mimeType)
        assertEquals("mkv", VideoContainer.MKV.extension)
        assertEquals("video/x-matroska", VideoContainer.MKV.mimeType)
        assertEquals("mkv", Formatters.extensionForMimeType(VideoContainer.MKV.mimeType))
        assertEquals(VideoContainer.MKV, VideoContainer.fromName("mkv"))
        assertEquals(VideoContainer.MP4, VideoContainer.fromName("unknown"))
    }

    @Test
    fun `download queue enforces concurrency limit and priority ordering`() {
        val queue = DownloadQueue(initialMaxConcurrent = 2)
        assertTrue(queue.enqueue("low_task", priority = 0))
        assertTrue(queue.enqueue("high_task", priority = 5))
        assertTrue(queue.enqueue("mid_task", priority = 2))
        // Duplicate enqueue is ignored
        assertFalse(queue.enqueue("high_task", priority = 5))

        assertEquals("high_task", queue.pollNextRunnable())
        assertEquals("mid_task", queue.pollNextRunnable())
        // Concurrency cap reached (2 active)
        assertNull(queue.pollNextRunnable())
        assertEquals(2, queue.activeCount())
        assertEquals(1, queue.pendingCount())

        queue.markFinished("high_task")
        assertEquals("low_task", queue.pollNextRunnable())
    }

    @Test
    fun `transfer rate tracker calculates realistic speed and eta`() {
        val tracker = TransferRateTracker()
        tracker.reset(initialBytes = 0L, nowMs = 1_000L)
        val (speed, eta) = tracker.sample(
            currentBytes = 1_000_000L,
            totalBytes = 5_000_000L,
            nowMs = 2_000L
        )
        assertEquals(1_000_000L, speed)
        assertNotNull(eta)
        assertEquals(4L, eta)
        assertEquals("1.0 MB/s", Formatters.formatSpeed(1_048_576L))
    }

    @Test
    fun `ffmpeg command builder produces valid lgpl commands and output validator rejects empty files`() {
        val engine = FfmpegProcessingEngine()
        val input = File("/tmp/in.mp4")
        val output = File("/tmp/out.mp3")
        val cmd = engine.buildCommand(input, output, ProcessingOperation.EXTRACT_AUDIO, null)
        assertTrue(cmd.contains("-vn"))
        assertTrue(cmd.contains(input.absolutePath))
        assertTrue(cmd.contains(output.absolutePath))

        val validator = OutputValidator()
        val missing = validator.validate(File("/tmp/does_not_exist_klipz_test.mp4"))
        assertFalse(missing.isValid)

        val emptyTmp = File.createTempFile("klipz_empty_", ".mp4")
        try {
            val emptyResult = validator.validate(emptyTmp)
            assertFalse(emptyResult.isValid)
        } finally {
            emptyTmp.delete()
        }
    }

    @Test
    fun `formatters handle bytes duration and mime extensions cleanly`() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("04:02", Formatters.formatDuration(242_000L))
        assertEquals("mp4", Formatters.extensionForMimeType("video/mp4"))
        assertEquals("mkv", Formatters.extensionForMimeType("video/x-matroska"))
        assertEquals("m4a", Formatters.extensionForMimeType("audio/mp4"))
        assertEquals("jpg", Formatters.extensionForMimeType("image/jpeg"))
    }

    @Test
    fun `url validator extracts urls from arbitrary share text and normalizes bare domains`() {
        assertEquals(
            "https://media.example.com/clip.mp4",
            UrlValidator.extractFirstUrl("Check out https://media.example.com/clip.mp4.")
        )
        assertEquals("https://example.com/video", UrlValidator.normalizeUrl("example.com/video"))
        assertNull(UrlValidator.extractFirstUrl("Just plain notes with no link"))
    }

    private fun createPureJvmYtDlpBridge(): YtDlpBridge {
        // YtDlpBridge.parseYtDlpJson and tier/aspect helpers do not touch Android Context
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(theUnsafe, YtDlpBridge::class.java) as YtDlpBridge
    }
}
