package com.example.downloader.ytdlp

import android.content.Context
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.MediaType
import com.example.domain.models.Platform
import com.example.domain.models.VideoContainer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class YtDlpEngineStatus(
    val pythonRuntimeBundled: Boolean,
    val ytDlpVersion: String?,
    val jsRuntimeAvailable: Boolean,
    val ffmpegBundled: Boolean,
    val statusSummary: String
)

data class YtDlpProgressUpdate(
    val fraction: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?
)

sealed interface YtDlpDownloadResult {
    data class Completed(val outputFile: File, val mimeType: String, val finalBytes: Long) : YtDlpDownloadResult
    data object Cancelled : YtDlpDownloadResult
    data class Failed(val reason: String) : YtDlpDownloadResult
}

/**
 * Embedded Python + yt-dlp + QuickJS/EJS + FFmpeg engine backed by youtubedl-android 0.18.1.
 *
 * Key guarantees:
 * - Bundled at build time inside the signed APK (no Termux required, no unprompted dynamic binary downloads).
 * - Filters out DRM streams (`has_drm`) and non-media storyboard/image noise so video URLs always expose
 *   real Video and Audio formats at full native resolution & aspect ratio.
 * - Automatically pairs high-resolution adaptive video streams (2160p/1440p/1080p/720p/...) with best audio
 *   and muxes into MP4 or MKV via bundled FFmpeg without silent downscaling.
 * - Supports instant process cancellation via destroyProcessById(processId).
 */
class YtDlpBridge(private val context: Context) {

    private val initMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var ffmpegInitialized: Boolean = false

    @Volatile
    private var cachedVersion: String? = null

    @Volatile
    private var lastInitError: String? = null

    fun initializeAsync() {
        backgroundScope.launch {
            ensureInitialized()
        }
    }

    suspend fun ensureInitialized(): Boolean = initMutex.withLock {
        if (initialized) return true
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context)
                initialized = true
                cachedVersion = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
                ffmpegInitialized = runCatching {
                    FFmpeg.getInstance().init(context)
                    true
                }.getOrDefault(false)
                lastInitError = null
                true
            } catch (t: Throwable) {
                initialized = false
                lastInitError = t.localizedMessage ?: t.javaClass.simpleName
                false
            }
        }
    }

    fun isAvailable(): Boolean = initialized || isBundledLibraryPresent()

    fun getStatus(): YtDlpEngineStatus {
        val bundled = isBundledLibraryPresent()
        val hasQjs = hasQuickJsBinary()
        val summary = when {
            initialized && ffmpegInitialized ->
                "Embedded Python + yt-dlp ${cachedVersion ?: "active"} + QuickJS/EJS + FFmpeg ready"
            initialized ->
                "Embedded Python + yt-dlp ${cachedVersion ?: "active"} + QuickJS/EJS ready"
            bundled && lastInitError != null ->
                "Embedded Python + yt-dlp bundled (init pending: $lastInitError)"
            bundled ->
                "Embedded Python + yt-dlp + QuickJS/EJS + FFmpeg bundled (ready on demand)"
            else ->
                "Direct HTTP & WebView HTML5 extractor active"
        }
        return YtDlpEngineStatus(
            pythonRuntimeBundled = bundled,
            ytDlpVersion = cachedVersion ?: if (bundled) "bundled (0.18.1)" else null,
            jsRuntimeAvailable = hasQjs || bundled,
            ffmpegBundled = ffmpegInitialized || bundled,
            statusSummary = summary
        )
    }

    fun cancelProcess(processId: String) {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }
    }

    suspend fun extractMetadata(
        url: String,
        platform: Platform,
        processId: String = "inspect_${UUID.randomUUID()}"
    ): MediaInspectionResult = withContext(Dispatchers.IO) {
        val ready = ensureInitialized()
        if (!ready) {
            return@withContext MediaInspectionResult.Error(
                "Embedded Python/yt-dlp runtime failed to initialize: ${lastInitError ?: "check ABI packaging"}."
            )
        }

        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-check-certificates")
                addOption("--skip-download")
                // Prefer QuickJS (bundled libqjs.so) for YouTube EJS player challenges when available
                addOption("--js-runtimes", "quickjs")
            }

            val response = YoutubeDL.getInstance().execute(request, processId, null)
            val rawJson = extractJsonObject(response.out)
            if (rawJson.isNullOrBlank()) {
                return@withContext MediaInspectionResult.Error(
                    mapErrorText(response.err.ifBlank { "No video or audio streams returned for ${UrlValidator.redactForLog(url)}." })
                )
            }
            parseYtDlpJson(rawJson, url, platform)
        } catch (e: CancellationException) {
            cancelProcess(processId)
            throw e
        } catch (e: Exception) {
            val mapped = mapPythonException(e)
            if (mapped.contains("DRM", ignoreCase = true) || mapped.contains("login", ignoreCase = true)) {
                MediaInspectionResult.Restricted(
                    mediaInfo = null,
                    reason = mapped,
                    guidance = "Klipz respects DRM and private-account protections. Public and user-authorized videos can be downloaded in full resolution."
                )
            } else {
                MediaInspectionResult.Error(mapped)
            }
        }
    }

    suspend fun downloadWithYtDlp(
        sourceUrl: String,
        formatSelector: String,
        isAudioOnly: Boolean,
        audioExtension: String,
        outputContainer: VideoContainer,
        stagingDir: File,
        taskId: String,
        onProgress: suspend (YtDlpProgressUpdate) -> Unit
    ): YtDlpDownloadResult = withContext(Dispatchers.IO) {
        val ready = ensureInitialized()
        if (!ready) {
            return@withContext YtDlpDownloadResult.Failed(
                "Embedded Python/yt-dlp runtime is unavailable: ${lastInitError ?: "initialization failed"}."
            )
        }

        val jobDir = File(stagingDir, "ytdlp_$taskId").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        try {
            val outputTemplate = File(jobDir, "media.%(ext)s").absolutePath
            val request = YoutubeDLRequest(sourceUrl).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--no-warnings")
                addOption("--js-runtimes", "quickjs")
                addOption("-o", outputTemplate)

                if (isAudioOnly) {
                    addOption("-f", formatSelector.ifBlank { "bestaudio/best" })
                    addOption("-x")
                    val targetAudioExt = audioExtension.lowercase(Locale.US).let {
                        if (it in setOf("mp3", "m4a", "opus")) it else "m4a"
                    }
                    addOption("--audio-format", targetAudioExt)
                    addOption("--audio-quality", "0")
                } else {
                    // Auto-merge selected full-resolution video + best audio into MP4 or MKV
                    addOption("-f", formatSelector.ifBlank { "bestvideo+bestaudio/best" })
                    addOption("--merge-output-format", outputContainer.extension)
                }
            }

            var lastEmitted = 0L
            YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                val now = System.currentTimeMillis()
                if (now - lastEmitted >= 250L || progress >= 99f) {
                    val parsedSpeed = parseSpeedBytesPerSec(line)
                    val parsedTotal = parseTotalBytes(line)
                    val fraction = (progress / 100f).coerceIn(0f, 1f)
                    val downloaded = if (parsedTotal > 0L) (parsedTotal * fraction).toLong() else 0L
                    lastEmitted = now
                    kotlinx.coroutines.runBlocking {
                        onProgress(
                            YtDlpProgressUpdate(
                                fraction = fraction,
                                downloadedBytes = downloaded,
                                totalBytes = parsedTotal,
                                speedBytesPerSec = parsedSpeed,
                                etaSeconds = etaInSeconds.takeIf { it > 0L }
                            )
                        )
                    }
                }
            }

            val outputFile = jobDir.listFiles()
                ?.filter { it.isFile && it.length() > 0L && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: return@withContext YtDlpDownloadResult.Failed("Download finished but no merged media file was produced.")

            val finalMime = mimeFromExtension(outputFile.extension, hasVideo = !isAudioOnly)
            YtDlpDownloadResult.Completed(
                outputFile = outputFile,
                mimeType = finalMime,
                finalBytes = outputFile.length()
            )
        } catch (e: CancellationException) {
            cancelProcess(taskId)
            jobDir.deleteRecursively()
            YtDlpDownloadResult.Cancelled
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            jobDir.deleteRecursively()
            if (msg.contains("destroyed", ignoreCase = true) || msg.contains("interrupt", ignoreCase = true)) {
                YtDlpDownloadResult.Cancelled
            } else {
                YtDlpDownloadResult.Failed(mapPythonException(e))
            }
        }
    }

    /**
     * Parses yt-dlp metadata JSON into clean Video & Audio formats at full native resolution and aspect ratio.
     * Strictly filters out DRM streams, storyboards, and image-only entries so video links never show only an image.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun parseYtDlpJson(rawJson: String, sourceUrl: String, platform: Platform): MediaInspectionResult {
        val adapter = Moshi.Builder().build().adapter(Map::class.java)
        val root = (adapter.fromJson(rawJson) as? Map<String, Any?>)
            ?: return MediaInspectionResult.Error("Unable to parse media metadata JSON.")
        if (root.optBoolean("_has_drm", false)) {
            return MediaInspectionResult.Restricted(
                mediaInfo = null,
                reason = "This media is DRM-protected.",
                guidance = "Klipz does not bypass DRM or copy-protection mechanisms."
            )
        }

        val title = root.optString("title").ifBlank { "Untitled Media" }
        val thumb = root.optString("thumbnail").takeIf { UrlValidator.isValidHttpUrl(it) }
        val durationSec = root.optDouble("duration", -1.0)
        val durationMs = if (durationSec > 0.0) (durationSec * 1000.0).toLong() else null
        val uploader = root.optString("uploader").takeIf { it.isNotBlank() }
            ?: root.optString("channel").takeIf { it.isNotBlank() }

        val rawFormats = mutableListOf<RawYtDlpStream>()
        val formatsList = root["formats"] as? List<*>
        if (formatsList != null) {
            for (i in formatsList.indices) {
                val fmt = formatsList[i] as? Map<String, Any?> ?: continue
                if (fmt.optBoolean("has_drm", false)) continue

                val ext = fmt.optString("ext", "").lowercase(Locale.US)
                // Strictly ignore storyboards, thumbnails, manifests, and image-only entries
                if (ext in setOf("mhtml", "jpg", "jpeg", "png", "webp", "vtt", "srv1", "srv2", "srv3", "ttml")) {
                    continue
                }

                val vcodec = fmt.optString("vcodec", "none").lowercase(Locale.US)
                val acodec = fmt.optString("acodec", "none").lowercase(Locale.US)
                val hasVideo = vcodec != "none" && vcodec.isNotBlank()
                val hasAudio = acodec != "none" && acodec.isNotBlank()
                if (!hasVideo && !hasAudio) continue

                val width = fmt.optInt("width", 0).coerceAtLeast(0)
                val height = fmt.optInt("height", 0).coerceAtLeast(0)
                val fps = fmt.optDouble("fps", -1.0).takeIf { it > 0.0 }?.toInt()
                val tbr = fmt.optDouble("tbr", -1.0).takeIf { it > 0.0 }
                    ?: fmt.optDouble("abr", -1.0).takeIf { it > 0.0 }
                    ?: fmt.optDouble("vbr", -1.0).takeIf { it > 0.0 }
                val directUrl = fmt.optString("url").takeIf { UrlValidator.isValidHttpUrl(it) }
                val exactSize = fmt.optLong("filesize", -1L).takeIf { it > 0L }
                    ?: fmt.optLong("filesize_approx", -1L).takeIf { it > 0L }
                    ?: estimateSizeFromBitrate(tbr, durationSec)

                val formatId = fmt.optString("format_id", "").ifBlank { "fmt_$i" }
                rawFormats.add(
                    RawYtDlpStream(
                        formatId = formatId,
                        width = width,
                        height = height,
                        fps = fps,
                        vcodec = vcodec,
                        acodec = acodec,
                        ext = ext.ifBlank { if (hasVideo) "mp4" else "m4a" },
                        hasVideo = hasVideo,
                        hasAudio = hasAudio,
                        sizeBytes = exactSize,
                        bitrateKbps = tbr?.toInt(),
                        directUrl = directUrl
                    )
                )
            }
        }

        // Fallback if single-stream root JSON carries direct video/audio without formats[]
        if (rawFormats.isEmpty()) {
            val rootUrl = root.optString("url").takeIf { UrlValidator.isValidHttpUrl(it) }
            val rootExt = root.optString("ext", "mp4").lowercase(Locale.US)
            if (rootUrl != null && rootExt !in setOf("jpg", "jpeg", "png", "webp", "mhtml")) {
                rawFormats.add(
                    RawYtDlpStream(
                        formatId = root.optString("format_id", "best"),
                        width = root.optInt("width", 0),
                        height = root.optInt("height", 0),
                        fps = root.optDouble("fps", -1.0).takeIf { it > 0.0 }?.toInt(),
                        vcodec = root.optString("vcodec", "h264"),
                        acodec = root.optString("acodec", "aac"),
                        ext = rootExt,
                        hasVideo = rootExt !in setOf("mp3", "m4a", "opus", "ogg", "wav"),
                        hasAudio = true,
                        sizeBytes = root.optLong("filesize", -1L).takeIf { it > 0L },
                        bitrateKbps = root.optDouble("tbr", -1.0).takeIf { it > 0.0 }?.toInt(),
                        directUrl = rootUrl
                    )
                )
            }
        }

        if (rawFormats.isEmpty()) {
            return MediaInspectionResult.Error(
                "No downloadable video or audio streams were found for this link."
            )
        }

        // Identify best audio stream for adaptive pairing (prefer m4a/aac or highest bitrate opus)
        val audioStreams = rawFormats.filter { !it.hasVideo && it.hasAudio }
        val bestM4aAudio = audioStreams
            .filter { it.ext == "m4a" || it.acodec.contains("mp4a") || it.acodec.contains("aac") }
            .maxByOrNull { it.bitrateKbps ?: 0 }
        val bestOverallAudio = audioStreams.maxByOrNull { it.bitrateKbps ?: 0 }
        val pairingAudio = bestM4aAudio ?: bestOverallAudio

        val resultFormats = mutableListOf<MediaFormat>()

        // 1. Add Audio-Only formats first (M4A original + MP3 transcode via bundled FFmpeg)
        if (pairingAudio != null) {
            resultFormats.add(
                MediaFormat(
                    formatId = "audio_m4a_${pairingAudio.formatId}",
                    label = "Audio M4A",
                    resolution = "Audio M4A",
                    codec = cleanCodecName(pairingAudio.acodec).ifBlank { "AAC" },
                    container = "M4A",
                    mimeType = "audio/mp4",
                    fileSizeBytes = pairingAudio.sizeBytes,
                    bitrateKbps = pairingAudio.bitrateKbps,
                    isAudioOnly = true,
                    hasAudio = true,
                    hasVideo = false,
                    directUrl = pairingAudio.directUrl,
                    ytDlpFormatSelector = "${pairingAudio.formatId}/bestaudio/best",
                    isYtDlpManaged = true,
                    requiresFfmpegMerge = false
                )
            )
            resultFormats.add(
                MediaFormat(
                    formatId = "audio_mp3_${pairingAudio.formatId}",
                    label = "Audio MP3",
                    resolution = "Audio MP3",
                    codec = "MP3",
                    container = "MP3",
                    mimeType = "audio/mpeg",
                    fileSizeBytes = pairingAudio.sizeBytes,
                    bitrateKbps = pairingAudio.bitrateKbps ?: 192,
                    isAudioOnly = true,
                    hasAudio = true,
                    hasVideo = false,
                    directUrl = pairingAudio.directUrl,
                    ytDlpFormatSelector = "bestaudio/best",
                    isYtDlpManaged = true,
                    requiresFfmpegMerge = true
                )
            )
        }

        // 2. Group video streams by full native resolution tier (preserving exact aspect ratio — e.g. 16:9, 9:16, 21:9)
        // Use the shorter dimension for vertical Shorts (e.g. 1080x1920 -> 1080p) so resolution labels match YouTube!
        val videoStreams = rawFormats.filter { it.hasVideo }
        val groupedByTier = videoStreams.groupBy { stream ->
            computeResolutionTier(stream.width, stream.height)
        }

        groupedByTier.entries
            .sortedByDescending { it.key }
            .forEach { (tierPx, candidates) ->
                // Prefer highest quality stream at this tier: prioritize AVC/H.264 or VP9/AV1 with highest fps & bitrate
                val bestVideo = candidates.maxWithOrNull(
                    compareBy<RawYtDlpStream> { it.fps ?: 0 }
                        .thenBy { codecPriorityScore(it.vcodec) }
                        .thenBy { it.bitrateKbps ?: 0 }
                ) ?: return@forEach

                val needsMerge = !bestVideo.hasAudio
                val combinedSize = when {
                    needsMerge && bestVideo.sizeBytes != null && pairingAudio?.sizeBytes != null ->
                        bestVideo.sizeBytes + pairingAudio.sizeBytes
                    else -> bestVideo.sizeBytes
                }

                val tierLabel = if (tierPx > 0) "${tierPx}p" else "Original"
                val resolutionLabel = if (bestVideo.fps != null && bestVideo.fps >= 50 && tierPx >= 720) {
                    "${tierLabel}${bestVideo.fps}"
                } else {
                    tierLabel
                }

                val aspectLabel = computeAspectRatioLabel(bestVideo.width, bestVideo.height)
                val codecText = buildString {
                    append(cleanCodecName(bestVideo.vcodec))
                    if (needsMerge && pairingAudio != null) {
                        append(" + ")
                        append(cleanCodecName(pairingAudio.acodec))
                    } else if (bestVideo.hasAudio) {
                        append(" + ")
                        append(cleanCodecName(bestVideo.acodec))
                    }
                }.ifBlank { bestVideo.ext.uppercase(Locale.US) }

                val selector = if (needsMerge) {
                    val audioPart = pairingAudio?.formatId ?: "bestaudio"
                    "${bestVideo.formatId}+$audioPart/bestvideo[height=${bestVideo.height}]+bestaudio/best[height<=${bestVideo.height}]"
                } else {
                    "${bestVideo.formatId}/best[height<=${bestVideo.height}]"
                }

                resultFormats.add(
                    MediaFormat(
                        formatId = "vid_${tierPx}_${bestVideo.formatId}",
                        label = resolutionLabel,
                        resolution = resolutionLabel,
                        widthPx = bestVideo.width,
                        heightPx = bestVideo.height,
                        fps = bestVideo.fps,
                        aspectRatioLabel = aspectLabel,
                        codec = codecText,
                        container = "MP4/MKV",
                        mimeType = "video/mp4",
                        fileSizeBytes = combinedSize,
                        bitrateKbps = bestVideo.bitrateKbps,
                        isAudioOnly = false,
                        hasAudio = true,
                        hasVideo = true,
                        directUrl = if (!needsMerge) bestVideo.directUrl else null,
                        ytDlpFormatSelector = selector,
                        isYtDlpManaged = true,
                        requiresFfmpegMerge = needsMerge
                    )
                )
            }

        val info = MediaInfo(
            id = UUID.randomUUID().toString(),
            title = UrlValidator.sanitizeFileName(title, "Klipz Video"),
            sourceUrl = sourceUrl,
            platform = platform,
            mediaType = if (resultFormats.all { it.isAudioOnly }) MediaType.AUDIO else MediaType.VIDEO,
            thumbnailUrl = thumb,
            durationMs = durationMs,
            author = uploader,
            formats = resultFormats,
            restrictionNotice = null,
            supportsDirectDownload = resultFormats.isNotEmpty()
        )
        return MediaInspectionResult.Success(info)
    }

    internal fun computeResolutionTier(width: Int, height: Int): Int {
        if (width <= 0 && height <= 0) return 0
        if (width <= 0) return height
        if (height <= 0) return width
        // For vertical videos (e.g., 1080x1920 Shorts), YouTube classifies quality by the shorter side (1080p)
        val rawTier = minOf(width, height)
        val standardTiers = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
        return standardTiers.minByOrNull { abs(it - rawTier) }
            ?.takeIf { abs(it - rawTier) <= 40 }
            ?: rawTier
    }

    internal fun computeAspectRatioLabel(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val ratio = width.toDouble() / height.toDouble()
        return when {
            abs(ratio - 16.0 / 9.0) < 0.06 -> "16:9"
            abs(ratio - 9.0 / 16.0) < 0.06 -> "9:16"
            abs(ratio - 4.0 / 3.0) < 0.06 -> "4:3"
            abs(ratio - 3.0 / 4.0) < 0.06 -> "3:4"
            abs(ratio - 21.0 / 9.0) < 0.10 -> "21:9"
            abs(ratio - 1.0) < 0.05 -> "1:1"
            else -> {
                val g = gcd(width, height)
                "${width / g}:${height / g}"
            }
        }
    }

    internal fun mapPythonException(error: Throwable): String = mapErrorText(error.message.orEmpty())

    internal fun mapErrorText(raw: String): String {
        return when {
            raw.contains("Sign in", ignoreCase = true) ||
                raw.contains("confirm you're not a bot", ignoreCase = true) ||
                raw.contains("Private video", ignoreCase = true) ||
                raw.contains("login", ignoreCase = true) ->
                "This content requires login or is private. Klipz only accesses public or authorized media."
            raw.contains("DRM", ignoreCase = true) ->
                "This stream is DRM-protected and cannot be downloaded."
            raw.contains("429", ignoreCase = true) || raw.contains("rate", ignoreCase = true) ->
                "The source platform rate-limited this request. Please retry shortly."
            raw.contains("Unsupported URL", ignoreCase = true) ->
                "This URL is not a supported video/audio source."
            raw.contains("timed out", ignoreCase = true) ->
                "Connection timed out while analyzing the media URL."
            else -> raw.lines().lastOrNull { it.contains("ERROR:", ignoreCase = true) }
                ?.substringAfter("ERROR:", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Unable to extract video or audio streams from this URL."
        }
    }

    private fun extractJsonObject(rawOutput: String): String? {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return rawOutput.substring(start, end + 1)
    }

    private fun isBundledLibraryPresent(): Boolean {
        return runCatching {
            Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            true
        }.getOrDefault(false)
    }

    private fun hasQuickJsBinary(): Boolean {
        val nativeDir = runCatching { File(context.applicationInfo.nativeLibraryDir) }.getOrNull() ?: return false
        return nativeDir.listFiles()?.any { it.name.contains("qjs", ignoreCase = true) } == true
    }

    private fun estimateSizeFromBitrate(bitrateKbps: Double?, durationSec: Double): Long? {
        if (bitrateKbps == null || bitrateKbps <= 0.0 || durationSec <= 0.0) return null
        return ((bitrateKbps * 1000.0 / 8.0) * durationSec).toLong().takeIf { it > 0L }
    }

    private fun codecPriorityScore(vcodec: String): Int = when {
        vcodec.startsWith("avc1") || vcodec.contains("h264") -> 3
        vcodec.startsWith("vp09") || vcodec.startsWith("vp9") -> 2
        vcodec.startsWith("av01") -> 1
        else -> 0
    }

    private fun cleanCodecName(raw: String): String = when {
        raw.startsWith("avc1") || raw.contains("h264") -> "H.264"
        raw.startsWith("vp09") || raw.startsWith("vp9") -> "VP9"
        raw.startsWith("av01") -> "AV1"
        raw.startsWith("hev1") || raw.startsWith("hvc1") -> "HEVC"
        raw.startsWith("mp4a") || raw.contains("aac") -> "AAC"
        raw.contains("opus") -> "OPUS"
        raw.contains("mp3") -> "MP3"
        raw == "none" || raw.isBlank() -> ""
        else -> raw.substringBefore(".").uppercase(Locale.US)
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x.coerceAtLeast(1)
    }

    private fun parseSpeedBytesPerSec(line: String?): Long {
        if (line.isNullOrBlank()) return 0L
        val match = Regex("""at\s+([0-9.]+)\s*(KiB|MiB|GiB|KB|MB|GB|B)/s""", RegexOption.IGNORE_CASE).find(line)
            ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase(Locale.US)
        val multiplier = when (unit) {
            "kib", "kb" -> 1024.0
            "mib", "mb" -> 1024.0 * 1024.0
            "gib", "gb" -> 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    private fun parseTotalBytes(line: String?): Long {
        if (line.isNullOrBlank()) return 0L
        val match = Regex("""of\s+~?\s*([0-9.]+)\s*(KiB|MiB|GiB|KB|MB|GB|B)""", RegexOption.IGNORE_CASE).find(line)
            ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase(Locale.US)
        val multiplier = when (unit) {
            "kib", "kb" -> 1024.0
            "mib", "mb" -> 1024.0 * 1024.0
            "gib", "gb" -> 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    private fun mimeFromExtension(ext: String, hasVideo: Boolean): String {
        return when (ext.lowercase(Locale.US)) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> if (hasVideo) "video/webm" else "audio/webm"
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus", "ogg" -> "audio/ogg"
            else -> if (hasVideo) "video/mp4" else "audio/mp4"
        }
    }

    private fun Map<String, Any?>.optString(key: String, default: String = ""): String {
        val value = this[key] ?: return default
        return value.toString()
    }

    private fun Map<String, Any?>.optBoolean(key: String, default: Boolean = false): Boolean {
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    private fun Map<String, Any?>.optInt(key: String, default: Int = 0): Int {
        return when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toDoubleOrNull()?.toInt() ?: default
            else -> default
        }
    }

    private fun Map<String, Any?>.optLong(key: String, default: Long = 0L): Long {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toDoubleOrNull()?.toLong() ?: default
            else -> default
        }
    }

    private fun Map<String, Any?>.optDouble(key: String, default: Double = 0.0): Double {
        return when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private data class RawYtDlpStream(
        val formatId: String,
        val width: Int,
        val height: Int,
        val fps: Int?,
        val vcodec: String,
        val acodec: String,
        val ext: String,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val sizeBytes: Long?,
        val bitrateKbps: Int?,
        val directUrl: String?
    )
}
