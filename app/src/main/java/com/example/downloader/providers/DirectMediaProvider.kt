package com.example.downloader.providers

import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.MediaType
import com.example.domain.models.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Live OkHttp provider for direct video, image, and audio URLs plus public HTML5/OpenGraph
 * direct media tags (<video>, <audio>, <meta property="og:video">, <meta property="og:image">).
 * Never invents unavailable resolutions: only returns formats actually observed from headers/markup.
 */
class DirectMediaProvider(
    private val httpClient: OkHttpClient
) : MediaProvider {

    override val providerName: String = "Direct Media & HTML5"

    override fun canHandle(url: String): Boolean = UrlValidator.isValidHttpUrl(url)

    override suspend fun getMediaInfo(url: String): MediaInspectionResult = withContext(Dispatchers.IO) {
        if (!UrlValidator.isValidHttpUrl(url)) {
            return@withContext MediaInspectionResult.Error("Invalid URL. Please enter a valid http:// or https:// address.")
        }

        try {
            val headReq = Request.Builder().url(url).head().build()
            val headResponse = runCatching { httpClient.newCall(headReq).execute() }.getOrNull()

            if (headResponse != null) {
                headResponse.use { resp ->
                    if (resp.isSuccessful) {
                        val contentType = resp.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase(Locale.US).orEmpty()
                        val contentLength = resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }

                        if (isDirectMediaMime(contentType)) {
                            return@withContext buildDirectFileResult(url, contentType, contentLength)
                        }
                    } else if (resp.code == 401 || resp.code == 403) {
                        return@withContext MediaInspectionResult.Restricted(
                            mediaInfo = null,
                            reason = "Access denied (HTTP ${resp.code}).",
                            guidance = "This URL requires authorization or blocks automated access."
                        )
                    }
                }
            }

            // Fallback GET inspection (avoid Range header on HTML pages to prevent 416 or truncated video tags)
            val getReq = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .build()

            httpClient.newCall(getReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    return@withContext if (resp.code == 401 || resp.code == 403) {
                        MediaInspectionResult.Restricted(
                            mediaInfo = null,
                            reason = "Authorization required (HTTP ${resp.code}).",
                            guidance = "Klipz does not bypass private-content or authentication restrictions."
                        )
                    } else {
                        MediaInspectionResult.Error("Server returned HTTP ${resp.code} while inspecting URL.")
                    }
                }

                val contentType = resp.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase(Locale.US).orEmpty()
                val totalLength = parseTotalSize(resp.header("Content-Range"), resp.header("Content-Length"))

                if (isDirectMediaMime(contentType)) {
                    return@withContext buildDirectFileResult(url, contentType, totalLength)
                }

                if (contentType.contains("html") || contentType.contains("xml") || contentType.isEmpty()) {
                    val htmlSnippet = resp.body?.charStream()?.use { reader ->
                        val buffer = CharArray(512 * 1024)
                        val count = reader.read(buffer)
                        if (count > 0) String(buffer, 0, count) else ""
                    }.orEmpty()
                    return@withContext inspectHtmlForAuthorizedMedia(url, htmlSnippet)
                }

                MediaInspectionResult.Error("Unsupported content type ($contentType). Provide a direct video, audio, or image URL.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaInspectionResult.Error("Network error while analyzing link: ${e.localizedMessage ?: "connection failed"}")
        }
    }

    override suspend fun getFormats(media: MediaInfo): List<MediaFormat> = media.formats

    override suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String> {
        val target = format.directUrl
        return if (!target.isNullOrBlank() && UrlValidator.isValidHttpUrl(target)) {
            Result.success(target)
        } else {
            Result.failure(IllegalStateException("No direct authorized media stream URL available for ${format.label}."))
        }
    }

    private fun buildDirectFileResult(
        url: String,
        mimeType: String,
        contentLength: Long?
    ): MediaInspectionResult.Success {
        val mediaType = mediaTypeFromMime(mimeType)
        val ext = Formatters.extensionForMimeType(mimeType)
        val rawName = runCatching {
            URI(url).path?.substringAfterLast("/")?.substringBeforeLast(".")
        }.getOrNull().orEmpty().ifBlank { "Klipz ${mediaType.displayName}" }

        val format = MediaFormat(
            formatId = "direct_original",
            label = "Original",
            resolution = when (mediaType) {
                MediaType.AUDIO -> "Audio"
                MediaType.IMAGE -> "Original Image"
                MediaType.VIDEO -> "Original"
            },
            codec = codecHintFromMime(mimeType),
            container = ext.uppercase(Locale.US),
            mimeType = mimeType,
            fileSizeBytes = contentLength,
            isAudioOnly = mediaType == MediaType.AUDIO,
            hasAudio = mediaType != MediaType.IMAGE,
            hasVideo = mediaType == MediaType.VIDEO,
            directUrl = url
        )

        return MediaInspectionResult.Success(
            MediaInfo(
                id = UUID.randomUUID().toString(),
                title = UrlValidator.sanitizeFileName(rawName, "Media File"),
                sourceUrl = url,
                platform = UrlValidator.detectPlatform(url),
                mediaType = mediaType,
                thumbnailUrl = if (mediaType == MediaType.IMAGE) url else null,
                durationMs = null,
                formats = listOf(format)
            )
        )
    }

    internal fun inspectHtmlForAuthorizedMedia(pageUrl: String, html: String): MediaInspectionResult {
        val title = extractMeta(html, "og:title")
            ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching { URI(pageUrl).host }.getOrNull()
            ?: "Web Media"

        val ogImage = resolveRelativeUrl(pageUrl, extractMeta(html, "og:image"))
        val ogVideo = resolveRelativeUrl(pageUrl, extractMeta(html, "og:video:secure_url") ?: extractMeta(html, "og:video"))
        val ogAudio = resolveRelativeUrl(pageUrl, extractMeta(html, "og:audio:secure_url") ?: extractMeta(html, "og:audio"))

        val discoveredFormats = mutableListOf<MediaFormat>()

        if (ogVideo != null && UrlValidator.looksLikeDirectMediaUrl(ogVideo)) {
            val mime = guessMimeFromUrl(ogVideo, "video/mp4")
            discoveredFormats.add(
                MediaFormat(
                    formatId = "og_video",
                    label = "Original",
                    resolution = "Original",
                    codec = codecHintFromMime(mime),
                    container = Formatters.extensionForMimeType(mime).uppercase(Locale.US),
                    mimeType = mime,
                    isAudioOnly = false,
                    hasAudio = true,
                    hasVideo = true,
                    directUrl = ogVideo
                )
            )
        }

        val sourceRegex = Regex(
            """<(?:video|audio|source)[^>]+src\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        sourceRegex.findAll(html).take(6).forEachIndexed { idx, match ->
            val resolved = resolveRelativeUrl(pageUrl, match.groupValues[1])
            if (resolved != null && UrlValidator.looksLikeDirectMediaUrl(resolved) && discoveredFormats.none { it.directUrl == resolved }) {
                val mime = guessMimeFromUrl(resolved, "video/mp4")
                val isAudio = mime.startsWith("audio/")
                discoveredFormats.add(
                    MediaFormat(
                        formatId = "html_stream_$idx",
                        label = if (isAudio) "Audio" else "Original",
                        resolution = if (isAudio) "Audio" else "Original",
                        codec = codecHintFromMime(mime),
                        container = Formatters.extensionForMimeType(mime).uppercase(Locale.US),
                        mimeType = mime,
                        isAudioOnly = isAudio,
                        hasAudio = true,
                        hasVideo = !isAudio,
                        directUrl = resolved
                    )
                )
            }
        }

        if (ogAudio != null && UrlValidator.looksLikeDirectMediaUrl(ogAudio) && discoveredFormats.none { it.directUrl == ogAudio }) {
            val mime = guessMimeFromUrl(ogAudio, "audio/mpeg")
            discoveredFormats.add(
                MediaFormat(
                    formatId = "og_audio",
                    label = "Audio",
                    resolution = "Audio",
                    codec = codecHintFromMime(mime),
                    container = Formatters.extensionForMimeType(mime).uppercase(Locale.US),
                    mimeType = mime,
                    isAudioOnly = true,
                    hasAudio = true,
                    hasVideo = false,
                    directUrl = ogAudio
                )
            )
        }

        val platform = UrlValidator.detectPlatform(pageUrl)
        val isVideoPlatform = platform in setOf(
            Platform.YOUTUBE, Platform.FACEBOOK, Platform.TIKTOK, Platform.DAILYMOTION, Platform.TED
        )

        // Only expose og:image as a downloadable image when no video/audio exists and it is not a video platform
        if (discoveredFormats.isEmpty() && !isVideoPlatform && ogImage != null && UrlValidator.isValidHttpUrl(ogImage)) {
            val mime = guessMimeFromUrl(ogImage, "image/jpeg")
            discoveredFormats.add(
                MediaFormat(
                    formatId = "og_image",
                    label = "Original Image",
                    resolution = "Original Image",
                    codec = codecHintFromMime(mime),
                    container = Formatters.extensionForMimeType(mime, "jpg").uppercase(Locale.US),
                    mimeType = mime,
                    isAudioOnly = false,
                    hasAudio = false,
                    hasVideo = false,
                    directUrl = ogImage
                )
            )
        }

        val primaryType = when {
            discoveredFormats.any { it.hasVideo } -> MediaType.VIDEO
            discoveredFormats.any { it.isAudioOnly } -> MediaType.AUDIO
            else -> MediaType.IMAGE
        }

        val info = MediaInfo(
            id = UUID.randomUUID().toString(),
            title = UrlValidator.sanitizeFileName(decodeHtmlEntities(title), "Web Media"),
            sourceUrl = pageUrl,
            platform = platform,
            mediaType = primaryType,
            thumbnailUrl = ogImage,
            formats = discoveredFormats,
            restrictionNotice = if (discoveredFormats.isEmpty()) {
                "This webpage does not expose a direct downloadable media URL in public HTML markup."
            } else {
                null
            }
        )

        return if (discoveredFormats.isNotEmpty()) {
            MediaInspectionResult.Success(info)
        } else {
            MediaInspectionResult.Restricted(
                mediaInfo = info,
                reason = "No direct media stream found on this page.",
                guidance = "Open the page in Klipz Browser to inspect public HTML5 video/image elements, or paste a direct media URL (.mp4, .m4a, .mp3, .jpg, .png, .webp)."
            )
        }
    }

    private fun extractMeta(html: String, property: String): String? {
        val pattern1 = Regex(
            """<meta[^>]+(?:property|name)\s*=\s*["']${Regex.escape(property)}["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val pattern2 = Regex(
            """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']${Regex.escape(property)}["']""",
            RegexOption.IGNORE_CASE
        )
        return pattern1.find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)
            ?: pattern2.find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        return runCatching {
            val resolved = URI(baseUrl).resolve(candidate.trim()).toString()
            if (UrlValidator.isValidHttpUrl(resolved)) resolved else null
        }.getOrNull()
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun isDirectMediaMime(mime: String): Boolean {
        return mime.startsWith("video/") ||
            mime.startsWith("audio/") ||
            mime.startsWith("image/")
    }

    private fun mediaTypeFromMime(mime: String): MediaType = when {
        mime.startsWith("video/") -> MediaType.VIDEO
        mime.startsWith("audio/") -> MediaType.AUDIO
        else -> MediaType.IMAGE
    }

    private fun codecHintFromMime(mime: String): String = when (mime) {
        "video/mp4" -> "MP4 / H.264"
        "video/webm" -> "WEBM / VP9"
        "video/x-matroska" -> "MKV"
        "audio/mpeg" -> "MP3"
        "audio/mp4", "audio/aac" -> "AAC"
        "audio/ogg", "audio/opus" -> "OPUS"
        "image/jpeg" -> "JPEG"
        "image/png" -> "PNG"
        "image/webp" -> "WEBP"
        "image/gif" -> "GIF"
        else -> mime.substringAfter("/").uppercase(Locale.US)
    }

    private fun guessMimeFromUrl(url: String, fallback: String): String {
        val path = runCatching { URI(url).path?.lowercase(Locale.US) }.getOrNull().orEmpty()
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") || path.endsWith(".aac") -> "audio/mp4"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            else -> fallback
        }
    }

    private fun parseTotalSize(contentRange: String?, contentLength: String?): Long? {
        if (!contentRange.isNullOrBlank()) {
            val total = contentRange.substringAfter("/", "").trim().toLongOrNull()
            if (total != null && total > 0L) return total
        }
        return contentLength?.toLongOrNull()?.takeIf { it > 0L }
    }
}
