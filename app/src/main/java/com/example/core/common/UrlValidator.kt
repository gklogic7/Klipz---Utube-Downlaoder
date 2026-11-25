package com.example.core.common

import android.content.Intent
import com.example.domain.models.Platform
import java.net.URI
import java.util.Locale

object UrlValidator {

    private val urlRegex = Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)

    private val youtubeWatchIdRegex = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
    private val youtubePathIdRegex = Regex(
        """(?:youtu\.be/|youtube\.com/(?:shorts|live|embed|v)/)([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE
    )
    private val instagramShortcodeRegex = Regex(
        """(?:instagram\.com|instagr\.am)/(?:[^/?#]+/)?(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val trackingParams = setOf(
        "igsh", "igshid", "si", "feature", "pp", "fbclid", "gclid",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
    )

    fun isValidHttpUrl(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US)
            val host = uri.host
            (scheme == "https" || scheme == "http") &&
                !host.isNullOrBlank() &&
                host.contains(".") &&
                !host.equals("localhost", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun isSecureHttps(input: String?): Boolean {
        if (!isValidHttpUrl(input)) return false
        return try {
            URI(input!!.trim()).scheme.equals("https", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> trimmed
        }
    }

    /**
     * Canonicalizes YouTube and Instagram links by stripping noisy share/tracking parameters
     * (?si=, ?igsh=, etc.) and normalizing /shorts/, /reels/, and youtu.be/ variants.
     */
    fun canonicalizeMediaUrl(rawUrl: String): String {
        val normalized = normalizeUrl(rawUrl)
        if (!isValidHttpUrl(normalized)) return normalized

        val platform = detectPlatform(normalized)
        if (platform == Platform.YOUTUBE) {
            val videoId = extractYouTubeVideoId(normalized)
            if (videoId != null) {
                return "https://www.youtube.com/watch?v=$videoId"
            }
        }
        if (platform == Platform.INSTAGRAM) {
            val shortcode = extractInstagramShortcode(normalized)
            if (shortcode != null) {
                val lower = normalized.lowercase(Locale.US)
                val segment = if (lower.contains("/reel/") || lower.contains("/reels/")) "reel" else "p"
                return "https://www.instagram.com/$segment/$shortcode/"
            }
        }

        return stripTrackingQueryParams(normalized)
    }

    fun extractYouTubeVideoId(url: String): String? {
        val trimmed = url.trim()
        youtubeWatchIdRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        youtubePathIdRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun extractInstagramShortcode(url: String): String? {
        return instagramShortcodeRegex.find(url.trim())?.groupValues?.getOrNull(1)
    }

    private fun stripTrackingQueryParams(url: String): String {
        return runCatching {
            val uri = URI(url)
            val rawQuery = uri.rawQuery ?: return url
            val kept = rawQuery.split("&")
                .filter { part ->
                    val key = part.substringBefore("=").lowercase(Locale.US)
                    key.isNotBlank() && key !in trackingParams
                }
            val rebuiltQuery = kept.takeIf { it.isNotEmpty() }?.joinToString("&")
            URI(uri.scheme, uri.authority, uri.path, rebuiltQuery, null).toString()
        }.getOrDefault(url)
    }

    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text.trim())?.value ?: return null
        val cleaned = match.trimEnd('.', ',', ';', ')', ']', '}')
        return if (isValidHttpUrl(cleaned)) cleaned else null
    }

    fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return extractFirstUrl(sharedText)
    }

    fun detectPlatform(url: String): Platform {
        val host = try {
            URI(url.trim()).host?.lowercase(Locale.US)?.removePrefix("www.")?.removePrefix("m.") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (host.isEmpty()) return Platform.DIRECT
        for (platform in Platform.entries) {
            if (platform.hostHints.any { hint -> host == hint || host.endsWith(".$hint") }) {
                return platform
            }
        }
        return if (looksLikeDirectMediaUrl(url)) Platform.DIRECT else Platform.WEB
    }

    fun looksLikeDirectMediaUrl(url: String): Boolean {
        val path = try {
            URI(url.trim()).path?.lowercase(Locale.US) ?: ""
        } catch (_: Exception) {
            url.lowercase(Locale.US)
        }
        val mediaExtensions = listOf(
            ".mp4", ".webm", ".mkv", ".mov", ".m4v", ".3gp",
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac",
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
        )
        return mediaExtensions.any { path.endsWith(it) }
    }

    fun sanitizeFileName(raw: String, fallback: String = "klipz_media"): String {
        val stripped = raw
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
            .take(96)
        return if (stripped.isBlank()) fallback else stripped
    }

    /** Redacts query tokens/credentials before any diagnostic logging. */
    fun redactForLog(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (_: Exception) {
            "[redacted-url]"
        }
    }
}
