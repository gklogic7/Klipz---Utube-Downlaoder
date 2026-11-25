package com.example.core.common

import android.content.Intent
import com.example.domain.models.Platform
import java.net.URI
import java.util.Locale

object UrlValidator {

    private val urlRegex = Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)

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
