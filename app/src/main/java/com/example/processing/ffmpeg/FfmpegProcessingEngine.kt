package com.example.processing.ffmpeg

import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ProcessingOperation
import com.example.processing.media3.MediaProcessingOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FfmpegCapabilities(
    val nativeBridgeLoaded: Boolean,
    val versionInfo: String,
    val supportedAbis: List<String>,
    val licenseMode: String
)

/**
 * Direct Android NDK + JNI bridge for custom LGPL FFmpeg native libraries
 * (arm64-v8a, armeabi-v7a, x86_64) — never uses deprecated/abandoned FFmpegKit.
 */
object FfmpegNativeBridge {

    private var loaded: Boolean = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("klipz_ffmpeg")
            loaded = true
        } catch (t: UnsatisfiedLinkError) {
            loaded = false
            loadError = t.message
        }
    }

    fun isAvailable(): Boolean = loaded

    fun getCapabilities(): FfmpegCapabilities {
        val version = if (loaded) {
            runCatching { nativeGetVersion() }.getOrDefault("NDK JNI Bridge Loaded")
        } else {
            "Not bundled in this build (compile with -PenableNativeFfmpeg=true via NDK/CI)"
        }
        return FfmpegCapabilities(
            nativeBridgeLoaded = loaded,
            versionInfo = version,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64"),
            licenseMode = "LGPL v2.1+ (no GPL/nonfree codecs enabled by default)"
        )
    }

    external fun nativeGetVersion(): String

    external fun nativeExecuteCommand(args: Array<String>): Int

    external fun nativeCancelActive(): Unit
}

class FfmpegProcessingEngine {

    fun isAvailable(): Boolean = FfmpegNativeBridge.isAvailable()

    suspend fun process(
        inputFile: File,
        outputFile: File,
        operation: ProcessingOperation,
        secondaryAudioFile: File? = null,
        onProgress: (Float) -> Unit
    ): MediaProcessingOutcome = withContext(Dispatchers.IO) {
        if (!FfmpegNativeBridge.isAvailable()) {
            return@withContext MediaProcessingOutcome.Failed(
                reason = "FFmpeg native library is not linked in this build variant. Use Media3 for MP4/AAC operations or build the NDK release workflow for MP3/advanced FFmpeg codecs.",
                engineUsed = ProcessingEngine.FFMPEG
            )
        }

        onProgress(0.15f)
        val command = buildCommand(inputFile, outputFile, operation, secondaryAudioFile)
        val exitCode = runCatching {
            FfmpegNativeBridge.nativeExecuteCommand(command.toTypedArray())
        }.getOrElse { err ->
            return@withContext MediaProcessingOutcome.Failed(
                reason = err.localizedMessage ?: "FFmpeg JNI execution failed.",
                engineUsed = ProcessingEngine.FFMPEG
            )
        }

        if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0L) {
            onProgress(1f)
            MediaProcessingOutcome.Success(outputFile, ProcessingEngine.FFMPEG)
        } else {
            MediaProcessingOutcome.Failed(
                reason = "FFmpeg exited with code $exitCode.",
                engineUsed = ProcessingEngine.FFMPEG
            )
        }
    }

    fun cancelActive() {
        if (FfmpegNativeBridge.isAvailable()) {
            runCatching { FfmpegNativeBridge.nativeCancelActive() }
        }
    }

    internal fun buildCommand(
        inputFile: File,
        outputFile: File,
        operation: ProcessingOperation,
        secondaryAudioFile: File?
    ): List<String> {
        return when (operation) {
            ProcessingOperation.EXTRACT_AUDIO, ProcessingOperation.CONVERT_AUDIO -> listOf(
                "ffmpeg", "-y", "-i", inputFile.absolutePath,
                "-vn", "-c:a", "libmp3lame", "-b:a", "192k",
                outputFile.absolutePath
            )
            ProcessingOperation.MERGE_AUDIO_VIDEO -> listOf(
                "ffmpeg", "-y",
                "-i", inputFile.absolutePath,
                "-i", (secondaryAudioFile ?: inputFile).absolutePath,
                "-c:v", "copy", "-c:a", "aac", "-shortest",
                outputFile.absolutePath
            )
            ProcessingOperation.COMPRESS_VIDEO -> listOf(
                "ffmpeg", "-y", "-i", inputFile.absolutePath,
                "-vf", "scale=-2:720", "-c:v", "mpeg4", "-q:v", "5", "-c:a", "aac",
                outputFile.absolutePath
            )
            ProcessingOperation.TRIM_VIDEO -> listOf(
                "ffmpeg", "-y", "-i", inputFile.absolutePath,
                "-c", "copy", outputFile.absolutePath
            )
            ProcessingOperation.CONVERT_VIDEO -> listOf(
                "ffmpeg", "-y", "-i", inputFile.absolutePath,
                "-c:v", "copy", "-c:a", "aac", "-movflags", "+faststart",
                outputFile.absolutePath
            )
        }
    }
}
