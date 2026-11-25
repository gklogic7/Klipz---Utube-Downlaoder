package com.example.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "Unknown"
        if (bytes < 1024L) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val unit = "KMGT"[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, unit)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "—"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatEta(etaSeconds: Long?): String {
        if (etaSeconds == null || etaSeconds <= 0L) return "—"
        val hours = etaSeconds / 3600
        val minutes = (etaSeconds % 3600) / 60
        val seconds = etaSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    fun formatDuration(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDate(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun formatDateTime(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun extensionForMimeType(mimeType: String, fallback: String = "mp4"): String {
        return when (mimeType.lowercase(Locale.US).substringBefore(";").trim()) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            "video/3gpp" -> "3gp"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac" -> "flac"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> fallback
        }
    }
}
