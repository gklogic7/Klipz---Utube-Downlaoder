package com.example.downloader.providers

import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.Platform
import com.example.downloader.ytdlp.YtDlpBridge
import java.util.UUID

/**
 * YouTube adapter combining bundled embedded Python + yt-dlp (safe flags + cookieless InnerTube clients)
 * with a native Kotlin cookieless InnerTube (ANDROID_VR / IOS) fallback.
 * Never returns cover-image-only formats for YouTube videos.
 */
class YouTubeProvider(
    private val ytDlpBridge: YtDlpBridge,
    private val innerTubeFallback: YouTubeInnerTubeFallback
) : MediaProvider {

    override val providerName: String = "YouTube (yt-dlp + InnerTube)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) == Platform.YOUTUBE

    override suspend fun getMediaInfo(url: String): MediaInspectionResult =
        getMediaInfo(url, "yt_${UUID.randomUUID()}")

    override suspend fun getMediaInfo(url: String, processId: String): MediaInspectionResult {
        val videoId = UrlValidator.extractYouTubeVideoId(url)
            ?: return MediaInspectionResult.Error(
                message = "Open a specific YouTube video or Short before inspecting — this link points to a YouTube feed or channel page."
            )
        val canonical = "https://www.youtube.com/watch?v=$videoId"

        val primary = ytDlpBridge.extractMetadata(canonical, Platform.YOUTUBE, processId)
        if (primary is MediaInspectionResult.Success && primary.mediaInfo.formats.any { it.hasVideo || it.isAudioOnly }) {
            return primary
        }

        // Cookieless native InnerTube fallback (ANDROID_VR / IOS public clients)
        val fallback = innerTubeFallback.extractPublicVideo(canonical)
        if (fallback is MediaInspectionResult.Success && fallback.mediaInfo.formats.isNotEmpty()) {
            return fallback
        }

        return when {
            primary is MediaInspectionResult.Restricted -> primary
            fallback is MediaInspectionResult.Restricted -> fallback
            primary is MediaInspectionResult.Error -> {
                val combinedDetails = listOfNotNull(
                    primary.technicalDetails,
                    (fallback as? MediaInspectionResult.Error)?.technicalDetails?.let { "InnerTube fallback:\n$it" }
                ).joinToString("\n\n").takeIf { it.isNotBlank() }
                primary.copy(technicalDetails = combinedDetails)
            }
            else -> fallback
        }
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

    override suspend fun getMediaInfo(url: String): MediaInspectionResult =
        getMediaInfo(url, "fb_${UUID.randomUUID()}")

    override suspend fun getMediaInfo(url: String, processId: String): MediaInspectionResult {
        val canonical = UrlValidator.canonicalizeMediaUrl(url)
        val extracted = ytDlpBridge.extractMetadata(canonical, Platform.FACEBOOK, processId)
        if (extracted is MediaInspectionResult.Success) return extracted
        val fallback = directProvider.getMediaInfo(canonical)
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
 * Instagram adapter for public Reels and Posts without browser cookies.
 * Combines fixed bundled yt-dlp with native public /embed/captioned/ and GraphQL fallbacks.
 */
class InstagramProvider(
    private val directProvider: DirectMediaProvider,
    private val ytDlpBridge: YtDlpBridge,
    private val publicFallback: InstagramPublicFallback
) : MediaProvider {

    override val providerName: String = "Instagram (yt-dlp + Public Embed)"

    override fun canHandle(url: String): Boolean = UrlValidator.detectPlatform(url) == Platform.INSTAGRAM

    override suspend fun getMediaInfo(url: String): MediaInspectionResult =
        getMediaInfo(url, "ig_${UUID.randomUUID()}")

    override suspend fun getMediaInfo(url: String, processId: String): MediaInspectionResult {
        val shortcode = UrlValidator.extractInstagramShortcode(url)
            ?: return MediaInspectionResult.Error(
                message = "Open a specific public Instagram Reel or Post before inspecting — homepage feeds and profiles do not have a single downloadable video."
            )
        val canonical = UrlValidator.canonicalizeMediaUrl(url)

        // 1. Primary: Fixed bundled yt-dlp (no invalid --js-runtimes flag, cookieless)
        val extracted = ytDlpBridge.extractMetadata(canonical, Platform.INSTAGRAM, processId)
        if (extracted is MediaInspectionResult.Success && extracted.mediaInfo.formats.isNotEmpty()) {
            return extracted
        }

        // 2. Fallback: Native cookieless public /embed/captioned/ & public GraphQL (xdt_shortcode_media)
        val publicResult = publicFallback.extractPublicPostOrReel(canonical)
        if (publicResult is MediaInspectionResult.Success && publicResult.mediaInfo.formats.isNotEmpty()) {
            return publicResult
        }

        // 3. Fallback: Direct HTML5/OpenGraph video detection on public post page
        val directResult = directProvider.getMediaInfo(canonical)
        if (directResult is MediaInspectionResult.Success &&
            directResult.mediaInfo.formats.any { it.hasVideo || it.isAudioOnly }
        ) {
            return MediaInspectionResult.Success(directResult.mediaInfo.copy(platform = Platform.INSTAGRAM))
        }

        return when {
            publicResult is MediaInspectionResult.Restricted -> publicResult
            extracted is MediaInspectionResult.Restricted -> extracted
            extracted is MediaInspectionResult.Error -> {
                val hint = "Shortcode: $shortcode\n" + (extracted.technicalDetails.orEmpty())
                extracted.copy(technicalDetails = hint.trim())
            }
            else -> publicResult
        }
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

    override suspend fun getMediaInfo(url: String): MediaInspectionResult =
        getMediaInfo(url, "gen_${UUID.randomUUID()}")

    override suspend fun getMediaInfo(url: String, processId: String): MediaInspectionResult {
        val canonical = UrlValidator.canonicalizeMediaUrl(url)
        return ytDlpBridge.extractMetadata(canonical, UrlValidator.detectPlatform(canonical), processId)
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
