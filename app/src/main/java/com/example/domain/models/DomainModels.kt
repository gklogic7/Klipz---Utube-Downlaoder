package com.example.domain.models

enum class Platform(val displayName: String, val hostHints: List<String>) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "music.youtube.com")),
    FACEBOOK("Facebook", listOf("facebook.com", "fb.watch", "fb.com")),
    INSTAGRAM("Instagram", listOf("instagram.com", "instagr.am")),
    DIRECT("Direct URL", emptyList()),
    SOUNDCLOUD("SoundCloud", listOf("soundcloud.com")),
    PINTEREST("Pinterest", listOf("pinterest.com", "pin.it")),
    TIKTOK("TikTok", listOf("tiktok.com")),
    DAILYMOTION("Dailymotion", listOf("dailymotion.com", "dai.ly")),
    REDDIT("Reddit", listOf("reddit.com", "redd.it", "v.redd.it")),
    X("X.com", listOf("x.com", "twitter.com")),
    TED("TED Talks", listOf("ted.com")),
    WEB("Web", emptyList());

    companion object {
        fun fromName(raw: String): Platform =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DIRECT
    }
}

enum class MediaType(val displayName: String, val subfolder: String) {
    VIDEO("Video", "Videos"),
    IMAGE("Image", "Images"),
    AUDIO("Audio", "Audio");

    companion object {
        fun fromName(raw: String): MediaType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIDEO
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING || this == PAUSED || this == PROCESSING

    companion object {
        fun fromName(raw: String): DownloadStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUEUED
    }
}

enum class ProcessingEngine(val displayName: String) {
    AUTO("Automatic (Media3 first)"),
    MEDIA3("Media3 Transformer"),
    FFMPEG("FFmpeg (NDK/JNI)");

    companion object {
        fun fromName(raw: String): ProcessingEngine =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

enum class ProcessingOperation(val displayName: String, val description: String) {
    CONVERT_VIDEO("Convert to MP4", "Re-package or transcode to universal H.264/AAC MP4"),
    COMPRESS_VIDEO("Compress Video", "Reduce file size to 720p hardware-encoded MP4"),
    EXTRACT_AUDIO("Extract Audio", "Extract clean audio track (M4A via Media3 or MP3 via FFmpeg)"),
    MERGE_AUDIO_VIDEO("Merge Audio & Video", "Mux separate audio and video streams into MP4"),
    TRIM_VIDEO("Trim Video", "Trim start and end range without quality loss where possible"),
    CONVERT_AUDIO("Convert Audio", "Convert audio format (AAC/M4A or MP3)");

    companion object {
        fun fromName(raw: String): ProcessingOperation =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CONVERT_VIDEO
    }
}

enum class ProcessingStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromName(raw: String): ProcessingStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUEUED
    }
}

enum class ThemeMode(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System Default");

    companion object {
        fun fromName(raw: String): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

enum class LayoutDensity(val displayName: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact");

    companion object {
        fun fromName(raw: String): LayoutDensity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: COMFORTABLE
    }
}

enum class VideoContainer(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String
) {
    MP4("mp4", "video/mp4", "MP4", "Universal compatibility"),
    MKV("mkv", "video/x-matroska", "MKV", "Lossless remux (VP9/AV1/Opus)");

    companion object {
        fun fromName(raw: String): VideoContainer =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MP4
    }
}

data class MediaFormat(
    val formatId: String,
    val label: String,
    val resolution: String,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fps: Int? = null,
    val aspectRatioLabel: String? = null,
    val codec: String,
    val container: String,
    val mimeType: String,
    val fileSizeBytes: Long? = null,
    val bitrateKbps: Int? = null,
    val isAudioOnly: Boolean = false,
    val hasAudio: Boolean = true,
    val hasVideo: Boolean = true,
    val directUrl: String? = null,
    val ytDlpFormatSelector: String? = null,
    val isYtDlpManaged: Boolean = false,
    val requiresFfmpegMerge: Boolean = false
)

data class MediaInfo(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val platform: Platform,
    val mediaType: MediaType,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val author: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    val restrictionNotice: String? = null,
    val supportsDirectDownload: Boolean = formats.any { !it.directUrl.isNullOrBlank() || it.isYtDlpManaged }
)

sealed interface MediaInspectionResult {
    data class Success(val mediaInfo: MediaInfo) : MediaInspectionResult
    data class Restricted(
        val mediaInfo: MediaInfo?,
        val reason: String,
        val guidance: String
    ) : MediaInspectionResult
    data class Error(val message: String, val recoverable: Boolean = true) : MediaInspectionResult
}

data class DownloadTask(
    val id: String,
    val mediaId: String,
    val formatId: String,
    val title: String,
    val sourceUrl: String,
    val platform: Platform,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val fileFormat: String,
    val mimeType: String,
    val status: DownloadStatus,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val supportsRange: Boolean,
    val outputUri: String?,
    val isPrivateFolder: Boolean,
    val outputContainer: VideoContainer = VideoContainer.MP4,
    val isYtDlpManaged: Boolean = false,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?
)

data class ProcessingTask(
    val id: String,
    val downloadId: String,
    val title: String,
    val selectedEngine: ProcessingEngine,
    val operation: ProcessingOperation,
    val status: ProcessingStatus,
    val inputPath: String,
    val outputPath: String?,
    val progress: Float,
    val errorMessage: String?,
    val createdAt: Long
)

data class LibraryFile(
    val id: String,
    val taskId: String?,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String,
    val fileFormat: String,
    val qualityLabel: String,
    val platform: Platform,
    val sizeBytes: Long,
    val durationMs: Long?,
    val contentUri: String,
    val thumbnailUri: String?,
    val dateAdded: Long,
    val isPrivateFolder: Boolean,
    val existsOnDisk: Boolean = true
)

data class WebHistoryItem(
    val id: Long = 0,
    val url: String,
    val title: String,
    val platform: Platform,
    val visitedAt: Long
)

data class BookmarkItem(
    val id: Long = 0,
    val url: String,
    val title: String,
    val platform: Platform,
    val createdAt: Long
)

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val klipzLibraryBytes: Long,
    val tempCacheBytes: Long
)

data class PlatformShortcut(
    val id: String,
    val label: String,
    val platform: Platform,
    val launchUrl: String,
    val isDirectPrompt: Boolean = false
)

enum class LibrarySortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name A–Z"),
    SIZE_DESC("Largest First")
}
