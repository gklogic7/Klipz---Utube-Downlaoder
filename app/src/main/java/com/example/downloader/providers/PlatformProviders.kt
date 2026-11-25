package com.example.downloader.providers

import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.Platform
import com.example.downloader.ytdlp.YtDlpBridge

/**
 * YouTube adapter backed directly by the bundled embedded Python + yt-dlp + QuickJS/EJS runtime.
 * Fixes the cover-image-only bug: returns genuine Video (2160p..144p at full native ratio) and Audio formats,
 * never substituting a JPEG thumbnail as the sole available format.
 */
class YouTubeProvider(
    private val ytDlpBridge: YtDlpBridge
) : MediaProvider {

    override val providerName: String = "YouTube (yt-dlp + QuickJS/EJS)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) == Platform.YOUTUBE

    override suspend fun getMediaInfo(url: String): MediaInspectionResult {
        return ytDlpBridge.extractMetadata(url, Platform.YOUTUBE)
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String> {
        val selectorOrUrl = format.ytDlpFormatSelector ?: format.directUrl
        return if (!selectorOrUrl.isNullOrBlank()) {
            Result.success(selectorOrUrl)
        } else {
            Result.failure(IllegalStateException("No stream available for ${format.label}."))
        }
    }
}

/**
 * Facebook adapter using bundled embedded Python + yt-dlp first, falling back to public HTML5 video detection
 * without bypassing login walls or private posts, and never showing cover-only images for video links.
 */
class FacebookProvider(
    private val directProvider: DirectMediaProvider,
    private val ytDlpBridge: YtDlpBridge
) : MediaProvider {

    override val providerName: String = "Facebook (yt-dlp)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) == Platform.FACEBOOK

    override suspend fun getMediaInfo(url: String): MediaInspectionResult {
        val extracted = ytDlpBridge.extractMetadata(url, Platform.FACEBOOK)
        if (extracted is MediaInspectionResult.Success) return extracted
        val fallback = directProvider.getMediaInfo(url)
        if (fallback is MediaInspectionResult.Success && fallback.mediaInfo.formats.any { it.hasVideo || it.isAudioOnly }) {
            return MediaInspectionResult.Success(fallback.mediaInfo.copy(platform = Platform.FACEBOOK))
        }
        return extracted
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String> =
        directProvider.resolveAuthorizedDownload(media, format)
}

/**
 * Instagram adapter using bundled embedded Python + yt-dlp first while respecting private accounts.
 */
class InstagramProvider(
    private val directProvider: DirectMediaProvider,
    private val ytDlpBridge: YtDlpBridge
) : MediaProvider {

    override val providerName: String = "Instagram (yt-dlp)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) == Platform.INSTAGRAM

    override suspend fun getMediaInfo(url: String): MediaInspectionResult {
        val extracted = ytDlpBridge.extractMetadata(url, Platform.INSTAGRAM)
        if (extracted is MediaInspectionResult.Success) return extracted
        val fallback = directProvider.getMediaInfo(url)
        if (fallback is MediaInspectionResult.Success && fallback.mediaInfo.formats.isNotEmpty()) {
            return MediaInspectionResult.Success(fallback.mediaInfo.copy(platform = Platform.INSTAGRAM))
        }
        return extracted
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String> =
        directProvider.resolveAuthorizedDownload(media, format)
}

/**
 * Multi-platform video/audio adapter for TED, TikTok, Reddit, X, Dailymotion, SoundCloud, and Pinterest
 * backed by the bundled embedded Python + yt-dlp runtime.
 */
class GeneralYtDlpPlatformProvider(
    private val ytDlpBridge: YtDlpBridge
) : MediaProvider {

    private val supportedPlatforms = setOf(
        Platform.SOUNDCLOUD,
        Platform.PINTEREST,
        Platform.TIKTOK,
        Platform.DAILYMOTION,
        Platform.REDDIT,
        Platform.X,
        Platform.TED
    )

    override val providerName: String = "Supported Platforms (yt-dlp)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) in supportedPlatforms

    override suspend fun getMediaInfo(url: String): MediaInspectionResult {
        return ytDlpBridge.extractMetadata(url, UrlValidator.detectPlatform(url))
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String> {
        val selectorOrUrl = format.ytDlpFormatSelector ?: format.directUrl
        return if (!selectorOrUrl.isNullOrBlank()) {
            Result.success(selectorOrUrl)
        } else {
            Result.failure(IllegalStateException("No stream available for ${format.label}."))
        }
    }
}
