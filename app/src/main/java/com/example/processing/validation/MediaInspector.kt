package com.example.processing.validation

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

data class MediaStreamInspection(
    val filePath: String,
    val containerExtension: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val videoMime: String?,
    val audioMime: String?,
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val sizeBytes: Long
)

data class OutputValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val sizeBytes: Long = 0L,
    val trackCount: Int = 0
)

/**
 * Inspects local media files using Android MediaExtractor & MediaCodecList
 * so MediaProcessingRouter makes real hardware/codec-aware decisions.
 */
class MediaInspector {

    fun inspect(file: File): MediaStreamInspection {
        val ext = file.extension.lowercase()
        val extractor = MediaExtractor()
        var hasVideo = false
        var hasAudio = false
        var videoMime: String? = null
        var audioMime: String? = null
        var width = 0
        var height = 0
        var durationUs = 0L

        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    videoMime = mime
                    width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                    height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioMime = mime
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
        } catch (_: Exception) {
            // Unrecognized by Android MediaExtractor (candidate for FFmpeg)
        } finally {
            runCatching { extractor.release() }
        }

        return MediaStreamInspection(
            filePath = file.absolutePath,
            containerExtension = ext,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            videoMime = videoMime,
            audioMime = audioMime,
            width = width,
            height = height,
            durationUs = durationUs,
            sizeBytes = if (file.exists()) file.length() else 0L
        )
    }

    fun isEncoderSupported(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    fun isDecoderSupported(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }
}

class OutputValidator {

    fun validate(outputFile: File, expectAudioOrVideo: Boolean = true): OutputValidationResult {
        if (!outputFile.exists()) {
            return OutputValidationResult(isValid = false, reason = "Output file was not created.")
        }
        val bytes = outputFile.length()
        if (bytes <= 0L) {
            return OutputValidationResult(isValid = false, reason = "Output file is empty (0 bytes).")
        }
        if (!expectAudioOrVideo) {
            return OutputValidationResult(isValid = true, sizeBytes = bytes)
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(outputFile.absolutePath)
            val tracks = extractor.trackCount
            if (tracks <= 0) {
                OutputValidationResult(isValid = false, reason = "Output contains no valid media tracks.", sizeBytes = bytes)
            } else {
                OutputValidationResult(isValid = true, sizeBytes = bytes, trackCount = tracks)
            }
        } catch (e: Exception) {
            OutputValidationResult(
                isValid = false,
                reason = "Output media container failed validation: ${e.localizedMessage ?: "corrupt container"}",
                sizeBytes = bytes
            )
        } finally {
            runCatching { extractor.release() }
        }
    }
}
