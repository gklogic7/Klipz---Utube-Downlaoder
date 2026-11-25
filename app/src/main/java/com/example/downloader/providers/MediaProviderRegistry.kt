package com.example.downloader.providers

import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.MediaType
import com.example.downloader.ytdlp.YtDlpBridge
import java.util.UUID

/**
 * Coordinates provider lookup across bundled yt-dlp adapters, direct HTTP media inspection,
 * and non-DRM HTML5 streams observed live inside the embedded Klipz Browser session.
 */
class MediaProviderRegistry(
    private val providers: List<MediaProvider>,
    private val fallbackDirectProvider: DirectMediaProvider,
    private val ytDlpBridge: YtDlpBridge? = null
) {

    fun selectProvider(url: String): MediaProvider {
        return providers.firstOrNull { it.canHandle(url) } ?: fallbackDirectProvider
    }

    suspend fun inspect(
        url: String,
        browserDetectedFormats: List<MediaFormat> = emptyList(),
        pageTitleHint: String? = null,
        processId: String = "inspect_${UUID.randomUUID()}"
    ): MediaInspectionResult {
        val normalized = UrlValidator.normalizeUrl(url)
        if (!UrlValidator.isValidHttpUrl(normalized)) {
            return MediaInspectionResult.Error("Please enter a valid http:// or https:// media URL.")
        }

        val provider = selectProvider(normalized)
        var result = provider.getMediaInfo(normalized)

        // If DirectMediaProvider didn't find video/audio on a generic webpage, try bundled yt-dlp extractor
        if (provider == fallbackDirectProvider &&
            !UrlValidator.looksLikeDirectMediaUrl(normalized) &&
            ytDlpBridge != null
        ) {
            val hasPlayableAV = (result as? MediaInspectionResult.Success)
                ?.mediaInfo?.formats?.any { it.hasVideo || it.isAudioOnly } == true
            if (!hasPlayableAV) {
                val ytResult = ytDlpBridge.extractMetadata(
                    url = normalized,
                    platform = UrlValidator.detectPlatform(normalized),
                    processId = processId
                )
                if (ytResult is MediaInspectionResult.Success && ytResult.mediaInfo.formats.isNotEmpty()) {
                    result = ytResult
                }
            }
        }

        // Only merge video/audio streams from browser sniffer (skip random webpage images when video/audio exists)
        val playableBrowserStreams = browserDetectedFormats.filter { it.hasVideo || it.isAudioOnly }
        val usableBrowserStreams = if (playableBrowserStreams.isNotEmpty()) playableBrowserStreams else browserDetectedFormats

        if (usableBrowserStreams.isEmpty()) {
            return result
        }

        return when (result) {
            is MediaInspectionResult.Success -> {
                val existingHasAV = result.mediaInfo.formats.any { it.hasVideo || it.isAudioOnly }
                val extra = if (existingHasAV) playableBrowserStreams else usableBrowserStreams
                val merged = (result.mediaInfo.formats + extra)
                    .distinctBy { it.ytDlpFormatSelector ?: it.directUrl ?: it.formatId }
                val primaryType = when {
                    merged.any { it.hasVideo } -> MediaType.VIDEO
                    merged.any { it.isAudioOnly } -> MediaType.AUDIO
                    else -> result.mediaInfo.mediaType
                }
                MediaInspectionResult.Success(
                    result.mediaInfo.copy(
                        mediaType = primaryType,
                        formats = merged,
                        restrictionNotice = if (merged.any { it.hasVideo || it.isAudioOnly }) null else result.mediaInfo.restrictionNotice
                    )
                )
            }
            is MediaInspectionResult.Restricted -> {
                if (playableBrowserStreams.isEmpty()) return result
                val base = result.mediaInfo ?: MediaInfo(
                    id = UUID.randomUUID().toString(),
                    title = UrlValidator.sanitizeFileName(pageTitleHint ?: "Browser Media"),
                    sourceUrl = normalized,
                    platform = UrlValidator.detectPlatform(normalized),
                    mediaType = if (playableBrowserStreams.any { it.hasVideo }) MediaType.VIDEO else MediaType.AUDIO
                )
                MediaInspectionResult.Success(
                    base.copy(
                        formats = playableBrowserStreams.distinctBy { it.directUrl ?: it.formatId },
                        restrictionNotice = null
                    )
                )
            }
            is MediaInspectionResult.Error -> {
                if (playableBrowserStreams.isEmpty()) return result
                MediaInspectionResult.Success(
                    MediaInfo(
                        id = UUID.randomUUID().toString(),
                        title = UrlValidator.sanitizeFileName(pageTitleHint ?: "Detected Media"),
                        sourceUrl = normalized,
                        platform = UrlValidator.detectPlatform(normalized),
                        mediaType = if (playableBrowserStreams.any { it.hasVideo }) MediaType.VIDEO else MediaType.AUDIO,
                        formats = playableBrowserStreams.distinctBy { it.directUrl ?: it.formatId }
                    )
                )
            }
        }
    }
}
