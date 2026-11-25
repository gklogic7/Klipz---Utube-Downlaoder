package com.example.downloader.providers

import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult
import com.example.domain.models.MediaType
import com.example.domain.models.Platform
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Native Kotlin cookieless YouTube InnerTube (`ANDROID_VR` / `IOS`) fallback extractor.
 * Provides fast, public, non-DRM Video & Audio extraction at full native resolutions and aspect ratios
 * whenever yt-dlp encounters a web JS challenge or temporary client issue on device.
 */
class YouTubeInnerTubeFallback(
    private val httpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private data class InnerTubeClient(
        val clientName: String,
        val clientVersion: String,
        val clientIdHeader: String,
        val userAgent: String,
        val osName: String,
        val osVersion: String,
        val deviceModel: String
    )

    private val publicClients = listOf(
        InnerTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            clientIdHeader = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceModel = "Quest 3"
        ),
        InnerTubeClient(
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientIdHeader = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iOS",
            osVersion = "18.3.2.22D82",
            deviceModel = "iPhone16,2"
        )
    )

    suspend fun extractPublicVideo(url: String): MediaInspectionResult = withContext(Dispatchers.IO) {
        val videoId = UrlValidator.extractYouTubeVideoId(url)
            ?: return@withContext MediaInspectionResult.Error(
                message = "Open a specific YouTube video or Short URL (not a homepage or channel feed) to inspect formats."
            )
        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val failures = mutableListOf<String>()

        for (client in publicClients) {
            try {
                val payload = """
                    {
                      "videoId": "$videoId",
                      "contentCheckOk": true,
                      "racyCheckOk": true,
                      "context": {
                        "client": {
                          "clientName": "${client.clientName}",
                          "clientVersion": "${client.clientVersion}",
                          "osName": "${client.osName}",
                          "osVersion": "${client.osVersion}",
                          "deviceModel": "${client.deviceModel}",
                          "hl": "en",
                          "gl": "US"
                        }
                      }
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("User-Agent", client.userAgent)
                    .header("X-YouTube-Client-Name", client.clientIdHeader)
                    .header("X-YouTube-Client-Version", client.clientVersion)
                    .header("Origin", "https://www.youtube.com")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        failures.add("${client.clientName}: HTTP ${resp.code}")
                        return@use
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) {
                        failures.add("${client.clientName}: empty body")
                        return@use
                    }
                    val parsed = parsePlayerJson(canonicalUrl, body)
                    if (parsed is MediaInspectionResult.Success) {
                        return@withContext parsed
                    }
                    if (parsed is MediaInspectionResult.Restricted) {
                        return@withContext parsed
                    }
                    if (parsed is MediaInspectionResult.Error) {
                        failures.add("${client.clientName}: ${parsed.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add("${client.clientName}: ${e.localizedMessage ?: "network error"}")
            }
        }

        MediaInspectionResult.Error(
            message = "Could not extract public video streams from YouTube.",
            technicalDetails = failures.joinToString("\n").takeIf { it.isNotBlank() }
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parsePlayerJson(sourceUrl: String, rawJson: String): MediaInspectionResult {
        val adapter = Moshi.Builder().build().adapter(Map::class.java)
        val root = (adapter.fromJson(rawJson) as? Map<String, Any?>)
            ?: return MediaInspectionResult.Error("Malformed YouTube player response.")

        val playability = root["playabilityStatus"] as? Map<String, Any?>
        val status = playability?.get("status")?.toString().orEmpty()
        if (status.isNotBlank() && !status.equals("OK", ignoreCase = true)) {
            val reason = playability?.get("reason")?.toString()?.takeIf { it.isNotBlank() }
                ?: "YouTube playback status: $status"
            return if (status.equals("LOGIN_REQUIRED", ignoreCase = true) ||
                status.equals("UNPLAYABLE", ignoreCase = true)
            ) {
                MediaInspectionResult.Restricted(
                    mediaInfo = null,
                    reason = reason,
                    guidance = "This video requires sign-in, age verification, or is restricted. Klipz only extracts public videos without cookies."
                )
            } else {
                MediaInspectionResult.Error(reason)
            }
        }

        val details = root["videoDetails"] as? Map<String, Any?>
        val title = details?.get("title")?.toString()?.takeIf { it.isNotBlank() } ?: "YouTube Video"
        val author = details?.get("author")?.toString()?.takeIf { it.isNotBlank() }
        val durationSec = details?.get("lengthSeconds")?.toString()?.toLongOrNull()
        val durationMs = durationSec?.takeIf { it > 0L }?.times(1000L)
        val thumbList = ((details?.get("thumbnail") as? Map<String, Any?>)?.get("thumbnails") as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
        val thumbnailUrl = thumbList?.lastOrNull()?.get("url")?.toString()
            ?.takeIf { UrlValidator.isValidHttpUrl(it) }

        val streamingData = root["streamingData"] as? Map<String, Any?>
            ?: return MediaInspectionResult.Error("No streamingData returned by YouTube.")

        val combinedRaw = mutableListOf<Map<String, Any?>>()
        (streamingData["formats"] as? List<*>)?.mapNotNullTo(combinedRaw) { it as? Map<String, Any?> }
        (streamingData["adaptiveFormats"] as? List<*>)?.mapNotNullTo(combinedRaw) { it as? Map<String, Any?> }

        val parsedStreams = mutableListOf<RawInnerTubeStream>()
        for (item in combinedRaw) {
            val directUrl = item["url"]?.toString()?.takeIf { UrlValidator.isValidHttpUrl(it) } ?: continue
            val rawMime = item["mimeType"]?.toString().orEmpty()
            val mimeBase = rawMime.substringBefore(";").trim().lowercase(Locale.US)
            val codecs = Regex("""codecs="([^"]+)"""").find(rawMime)?.groupValues?.getOrNull(1).orEmpty()
            val itag = (item["itag"] as? Number)?.toInt() ?: continue
            val width = (item["width"] as? Number)?.toInt() ?: 0
            val height = (item["height"] as? Number)?.toInt() ?: 0
            val fps = (item["fps"] as? Number)?.toInt()?.takeIf { it > 0 }
            val contentLength = item["contentLength"]?.toString()?.toLongOrNull()?.takeIf { it > 0L }
            val bitrate = (item["bitrate"] as? Number)?.toInt() ?: (item["averageBitrate"] as? Number)?.toInt()
            val hasVideo = mimeBase.startsWith("video/")
            val hasAudio = mimeBase.startsWith("audio/") ||
                (hasVideo && (item["audioQuality"] != null || codecs.contains(",")))
            if (!hasVideo && !hasAudio) continue

            val approxBytes = contentLength ?: if (bitrate != null && bitrate > 0 && durationSec != null && durationSec > 0L) {
                (bitrate.toLong() / 8L) * durationSec
            } else null

            parsedStreams.add(
                RawInnerTubeStream(
                    itag = itag,
                    width = width,
                    height = height,
                    fps = fps,
                    mimeBase = mimeBase,
                    codecs = codecs,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    sizeBytes = approxBytes,
                    bitrateKbps = bitrate?.div(1000),
                    directUrl = directUrl
                )
            )
        }

        if (parsedStreams.isEmpty()) {
            return MediaInspectionResult.Error("No direct public YouTube video or audio streams available.")
        }

        val audioOnly = parsedStreams.filter { !it.hasVideo && it.hasAudio }
        val bestAudio = audioOnly
            .filter { it.mimeBase.contains("mp4") || it.codecs.contains("mp4a") }
            .maxByOrNull { it.bitrateKbps ?: 0 }
            ?: audioOnly.maxByOrNull { it.bitrateKbps ?: 0 }

        val outFormats = mutableListOf<MediaFormat>()

        if (bestAudio != null) {
            outFormats.add(
                MediaFormat(
                    formatId = "yt_it_audio_${bestAudio.itag}",
                    label = "Audio M4A",
                    resolution = "Audio M4A",
                    codec = if (bestAudio.codecs.contains("opus", true)) "OPUS" else "AAC",
                    container = if (bestAudio.mimeBase.contains("webm")) "WEBM" else "M4A",
                    mimeType = bestAudio.mimeBase,
                    fileSizeBytes = bestAudio.sizeBytes,
                    bitrateKbps = bestAudio.bitrateKbps,
                    isAudioOnly = true,
                    hasAudio = true,
                    hasVideo = false,
                    directUrl = bestAudio.directUrl,
                    ytDlpFormatSelector = "${bestAudio.itag}/bestaudio/best",
                    isYtDlpManaged = false,
                    requiresFfmpegMerge = false
                )
            )
        }

        // Group videos by native resolution tier (shorter side for Shorts)
        val grouped = parsedStreams.filter { it.hasVideo }.groupBy { s ->
            computeTier(s.width, s.height)
        }

        grouped.entries.sortedByDescending { it.key }.forEach { (tierPx, candidates) ->
            // Prefer muxed direct stream if at same tier and no yt-dlp needed, otherwise highest quality adaptive
            val muxed = candidates.filter { it.hasAudio }.maxByOrNull { it.bitrateKbps ?: 0 }
            val best = muxed ?: candidates.maxWithOrNull(
                compareBy<RawInnerTubeStream> { it.fps ?: 0 }
                    .thenBy { if (it.mimeBase.contains("mp4")) 2 else 1 }
                    .thenBy { it.bitrateKbps ?: 0 }
            ) ?: return@forEach

            val needsMerge = !best.hasAudio
            val tierLabel = if (tierPx > 0) "${tierPx}p" else "Original"
            val label = if (best.fps != null && best.fps >= 50 && tierPx >= 720) "${tierLabel}${best.fps}" else tierLabel
            val aspect = computeAspect(best.width, best.height)
            val totalSize = if (needsMerge && best.sizeBytes != null && bestAudio?.sizeBytes != null) {
                best.sizeBytes + bestAudio.sizeBytes
            } else best.sizeBytes

            val selector = if (needsMerge) {
                val audioId = bestAudio?.itag?.toString() ?: "bestaudio"
                "${best.itag}+$audioId/bestvideo[height=${best.height}]+bestaudio/best[height<=${best.height}]"
            } else {
                null
            }

            outFormats.add(
                MediaFormat(
                    formatId = "yt_it_${tierPx}_${best.itag}",
                    label = label,
                    resolution = label,
                    widthPx = best.width,
                    heightPx = best.height,
                    fps = best.fps,
                    aspectRatioLabel = aspect,
                    codec = cleanCodec(best.codecs, best.mimeBase),
                    container = if (needsMerge) "MP4/MKV" else if (best.mimeBase.contains("webm")) "WEBM" else "MP4",
                    mimeType = if (needsMerge) "video/mp4" else best.mimeBase,
                    fileSizeBytes = totalSize,
                    bitrateKbps = best.bitrateKbps,
                    isAudioOnly = false,
                    hasAudio = true,
                    hasVideo = true,
                    directUrl = if (!needsMerge) best.directUrl else best.directUrl,
                    ytDlpFormatSelector = selector,
                    isYtDlpManaged = needsMerge,
                    requiresFfmpegMerge = needsMerge
                )
            )
        }

        return MediaInspectionResult.Success(
            MediaInfo(
                id = UUID.randomUUID().toString(),
                title = UrlValidator.sanitizeFileName(title, "YouTube Video"),
                sourceUrl = sourceUrl,
                platform = Platform.YOUTUBE,
                mediaType = if (outFormats.all { it.isAudioOnly }) MediaType.AUDIO else MediaType.VIDEO,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                author = author,
                formats = outFormats,
                supportsDirectDownload = outFormats.isNotEmpty()
            )
        )
    }

    private fun computeTier(width: Int, height: Int): Int {
        if (width <= 0 && height <= 0) return 0
        if (width <= 0) return height
        if (height <= 0) return width
        val raw = minOf(width, height)
        val standards = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
        return standards.minByOrNull { abs(it - raw) }?.takeIf { abs(it - raw) <= 40 } ?: raw
    }

    private fun computeAspect(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val r = width.toDouble() / height.toDouble()
        return when {
            abs(r - 16.0 / 9.0) < 0.06 -> "16:9"
            abs(r - 9.0 / 16.0) < 0.06 -> "9:16"
            abs(r - 4.0 / 3.0) < 0.06 -> "4:3"
            abs(r - 21.0 / 9.0) < 0.10 -> "21:9"
            abs(r - 1.0) < 0.05 -> "1:1"
            else -> "${width}:${height}"
        }
    }

    private fun cleanCodec(codecs: String, mimeBase: String): String {
        val c = codecs.lowercase(Locale.US)
        return when {
            c.contains("avc1") -> "H.264 + AAC"
            c.contains("vp09") || c.contains("vp9") -> "VP9 + AAC"
            c.contains("av01") -> "AV1 + AAC"
            mimeBase.contains("webm") -> "WEBM"
            else -> "MP4"
        }
    }

    private data class RawInnerTubeStream(
        val itag: Int,
        val width: Int,
        val height: Int,
        val fps: Int?,
        val mimeBase: String,
        val codecs: String,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val sizeBytes: Long?,
        val bitrateKbps: Int?,
        val directUrl: String
    )
}

/**
 * Native Kotlin cookieless public Instagram Reel & Post extractor.
 * Queries Instagram's public `/embed/captioned/` page and public GraphQL endpoint (`xdt_shortcode_media`)
 * without browser cookies or login credentials.
 */
class InstagramPublicFallback(
    private val httpClient: OkHttpClient
) {
    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val formMediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

    suspend fun extractPublicPostOrReel(url: String): MediaInspectionResult = withContext(Dispatchers.IO) {
        val shortcode = UrlValidator.extractInstagramShortcode(url)
            ?: return@withContext MediaInspectionResult.Error(
                message = "Open a specific Instagram Reel or Post URL (not a profile or home feed) to inspect formats."
            )
        val canonicalUrl = UrlValidator.canonicalizeMediaUrl(url)
        val failures = mutableListOf<String>()

        // Strategy 1: Public /embed/captioned/ endpoint (no cookies needed for public Reels/Posts)
        val embedUrls = listOf(
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
            "https://www.instagram.com/reel/$shortcode/embed/captioned/"
        )
        for (embedUrl in embedUrls) {
            try {
                val req = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", browserUserAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        val parsed = parseEmbedHtml(canonicalUrl, html)
                        if (parsed is MediaInspectionResult.Success) {
                            return@withContext parsed
                        }
                    } else {
                        failures.add("Embed HTTP ${resp.code}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add("Embed: ${e.localizedMessage ?: "network error"}")
            }
        }

        // Strategy 2: Public cookieless GraphQL query (xdt_shortcode_media)
        val docIds = listOf("24368985919464652", "8845758582119845", "10015901848480474")
        for (docId in docIds) {
            try {
                val varsEncoded = URLEncoder.encode("""{"shortcode":"$shortcode"}""", "UTF-8")
                val bodyStr = "av=0&__d=www&__user=0&__a=1&variables=$varsEncoded&doc_id=$docId"
                val req = Request.Builder()
                    .url("https://www.instagram.com/graphql/query/")
                    .header("User-Agent", browserUserAgent)
                    .header("Accept", "*/*")
                    .header("Referer", "https://www.instagram.com/")
                    .header("X-IG-App-ID", "936619743392459")
                    .post(bodyStr.toRequestBody(formMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = resp.body?.string().orEmpty()
                        val parsed = parseGraphQlJson(canonicalUrl, json)
                        if (parsed is MediaInspectionResult.Success) {
                            return@withContext parsed
                        }
                    } else {
                        failures.add("GraphQL($docId) HTTP ${resp.code}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add("GraphQL: ${e.localizedMessage ?: "network error"}")
            }
        }

        MediaInspectionResult.Restricted(
            mediaInfo = null,
            reason = "Instagram did not expose a public stream for this link.",
            guidance = "Make sure the Reel or Post is public (not a private account or story), or update yt-dlp in Settings and retry."
        )
    }

    internal fun parseEmbedHtml(sourceUrl: String, html: String): MediaInspectionResult {
        if (html.isBlank()) return MediaInspectionResult.Error("Empty Instagram embed page.")

        val videoUrlRegex = Regex("""(?:\\"|")video_url(?:\\"|")\s*:\s*(?:\\"|")(https?[^"\\]+(?:\\.[^"\\]*)*)(?:\\"|")""")
        val displayUrlRegex = Regex("""(?:\\"|")display_url(?:\\"|")\s*:\s*(?:\\"|")(https?[^"\\]+(?:\\.[^"\\]*)*)(?:\\"|")""")
        val titleRegex = Regex("""<div class="Caption"[^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        val videoUrls = videoUrlRegex.findAll(html)
            .map { unescapeJsonUrl(it.groupValues[1]) }
            .filter { UrlValidator.isValidHttpUrl(it) }
            .distinct()
            .toList()

        val displayUrl = displayUrlRegex.find(html)
            ?.groupValues?.getOrNull(1)
            ?.let(::unescapeJsonUrl)
            ?.takeIf { UrlValidator.isValidHttpUrl(it) }

        val rawCaption = titleRegex.find(html)?.groupValues?.getOrNull(1)
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(80)
            ?.takeIf { it.isNotBlank() }

        val formats = mutableListOf<MediaFormat>()
        videoUrls.forEachIndexed { idx, vUrl ->
            formats.add(
                MediaFormat(
                    formatId = "ig_embed_video_$idx",
                    label = if (videoUrls.size > 1) "Reel Video ${idx + 1}" else "Original HD",
                    resolution = if (videoUrls.size > 1) "Video ${idx + 1}" else "Original",
                    codec = "H.264 + AAC",
                    container = "MP4",
                    mimeType = "video/mp4",
                    isAudioOnly = false,
                    hasAudio = true,
                    hasVideo = true,
                    directUrl = vUrl,
                    isYtDlpManaged = false
                )
            )
        }

        // Only expose image when this is genuinely an Instagram photo post (no video found)
        if (formats.isEmpty() && displayUrl != null && !sourceUrl.contains("/reel/", ignoreCase = true)) {
            formats.add(
                MediaFormat(
                    formatId = "ig_embed_image_0",
                    label = "Original Photo",
                    resolution = "Original Image",
                    codec = "JPEG",
                    container = "JPG",
                    mimeType = "image/jpeg",
                    isAudioOnly = false,
                    hasAudio = false,
                    hasVideo = false,
                    directUrl = displayUrl,
                    isYtDlpManaged = false
                )
            )
        }

        if (formats.isEmpty()) {
            return MediaInspectionResult.Error("No public video found in Instagram embed response.")
        }

        return MediaInspectionResult.Success(
            MediaInfo(
                id = UUID.randomUUID().toString(),
                title = UrlValidator.sanitizeFileName(rawCaption ?: "Instagram Reel", "Instagram Reel"),
                sourceUrl = sourceUrl,
                platform = Platform.INSTAGRAM,
                mediaType = if (formats.any { it.hasVideo }) MediaType.VIDEO else MediaType.IMAGE,
                thumbnailUrl = displayUrl,
                formats = formats,
                supportsDirectDownload = true
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseGraphQlJson(sourceUrl: String, rawJson: String): MediaInspectionResult {
        val adapter = Moshi.Builder().build().adapter(Map::class.java)
        val root = runCatching { adapter.fromJson(rawJson) as? Map<String, Any?> }.getOrNull()
            ?: return MediaInspectionResult.Error("Malformed Instagram GraphQL JSON.")
        val data = root["data"] as? Map<String, Any?> ?: return MediaInspectionResult.Error("Missing GraphQL data.")
        val media = (data["xdt_shortcode_media"] as? Map<String, Any?>)
            ?: (data["shortcode_media"] as? Map<String, Any?>)
            ?: return MediaInspectionResult.Error("No public Instagram media in GraphQL response.")

        val captionEdges = ((media["edge_media_to_caption"] as? Map<String, Any?>)?.get("edges") as? List<*>)
        val caption = (((captionEdges?.firstOrNull() as? Map<String, Any?>)?.get("node") as? Map<String, Any?>)
            ?.get("text")?.toString())?.trim()?.take(80)?.takeIf { it.isNotBlank() }

        val ownerName = (media["owner"] as? Map<String, Any?>)?.get("username")?.toString()
        val thumb = media["display_url"]?.toString()?.takeIf { UrlValidator.isValidHttpUrl(it) }
        val durationMs = (media["video_duration"] as? Number)?.toDouble()?.takeIf { it > 0.0 }?.times(1000.0)?.toLong()
        val dims = media["dimensions"] as? Map<String, Any?>
        val width = (dims?.get("width") as? Number)?.toInt() ?: 0
        val height = (dims?.get("height") as? Number)?.toInt() ?: 0

        val formats = mutableListOf<MediaFormat>()
        val mainVideoUrl = media["video_url"]?.toString()?.takeIf { UrlValidator.isValidHttpUrl(it) }
        if (mainVideoUrl != null) {
            val tier = if (width > 0 && height > 0) "${minOf(width, height)}p" else "Original"
            formats.add(
                MediaFormat(
                    formatId = "ig_gql_video_main",
                    label = tier,
                    resolution = tier,
                    widthPx = width,
                    heightPx = height,
                    aspectRatioLabel = computeIgAspect(width, height),
                    codec = "H.264 + AAC",
                    container = "MP4",
                    mimeType = "video/mp4",
                    isAudioOnly = false,
                    hasAudio = true,
                    hasVideo = true,
                    directUrl = mainVideoUrl
                )
            )
        }

        // Check carousel sidecar videos if present
        val sidecarEdges = ((media["edge_sidecar_to_children"] as? Map<String, Any?>)?.get("edges") as? List<*>)
        sidecarEdges?.forEachIndexed { idx, edge ->
            val node = (edge as? Map<String, Any?>)?.get("node") as? Map<String, Any?> ?: return@forEachIndexed
            val vUrl = node["video_url"]?.toString()?.takeIf { UrlValidator.isValidHttpUrl(it) }
            if (vUrl != null && formats.none { it.directUrl == vUrl }) {
                formats.add(
                    MediaFormat(
                        formatId = "ig_gql_clip_${idx + 1}",
                        label = "Clip ${idx + 1}",
                        resolution = "Clip ${idx + 1}",
                        codec = "H.264 + AAC",
                        container = "MP4",
                        mimeType = "video/mp4",
                        isAudioOnly = false,
                        hasAudio = true,
                        hasVideo = true,
                        directUrl = vUrl
                    )
                )
            }
        }

        if (formats.isEmpty() && thumb != null && !sourceUrl.contains("/reel/", ignoreCase = true)) {
            formats.add(
                MediaFormat(
                    formatId = "ig_gql_photo",
                    label = "Original Photo",
                    resolution = "Original Image",
                    widthPx = width,
                    heightPx = height,
                    codec = "JPEG",
                    container = "JPG",
                    mimeType = "image/jpeg",
                    isAudioOnly = false,
                    hasAudio = false,
                    hasVideo = false,
                    directUrl = thumb
                )
            )
        }

        if (formats.isEmpty()) {
            return MediaInspectionResult.Error("No public video found in Instagram GraphQL payload.")
        }

        return MediaInspectionResult.Success(
            MediaInfo(
                id = UUID.randomUUID().toString(),
                title = UrlValidator.sanitizeFileName(caption ?: ownerName?.let { "$it — Instagram" } ?: "Instagram Reel"),
                sourceUrl = sourceUrl,
                platform = Platform.INSTAGRAM,
                mediaType = if (formats.any { it.hasVideo }) MediaType.VIDEO else MediaType.IMAGE,
                thumbnailUrl = thumb,
                durationMs = durationMs,
                author = ownerName,
                formats = formats,
                supportsDirectDownload = true
            )
        )
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw
            .replace("\\\\/", "/")
            .replace("\\/", "/")
            .replace("\\\\u0026", "&")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .trim()
    }

    private fun computeIgAspect(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val r = width.toDouble() / height.toDouble()
        return when {
            abs(r - 9.0 / 16.0) < 0.08 -> "9:16"
            abs(r - 16.0 / 9.0) < 0.08 -> "16:9"
            abs(r - 4.0 / 5.0) < 0.08 -> "4:5"
            abs(r - 1.0) < 0.05 -> "1:1"
            else -> null
        }
    }
}
