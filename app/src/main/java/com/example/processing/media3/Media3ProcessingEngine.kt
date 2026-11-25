package com.example.processing.media3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.models.ProcessingOperation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class Media3ProcessingRequest(
    val inputFile: File,
    val outputFile: File,
    val operation: ProcessingOperation,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val targetAudioMime: String = MimeTypes.AUDIO_AAC
)

sealed interface MediaProcessingOutcome {
    data class Success(val outputFile: File, val engineUsed: com.example.domain.models.ProcessingEngine) : MediaProcessingOutcome
    data class Failed(val reason: String, val engineUsed: com.example.domain.models.ProcessingEngine) : MediaProcessingOutcome
    data object Cancelled : MediaProcessingOutcome
}

/**
 * Primary hardware-accelerated processing engine powered by Android Media3 Transformer.
 * Supports MP4 export/conversion, video compression, trimming, and AAC/M4A audio extraction.
 */
class Media3ProcessingEngine(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun process(
        request: Media3ProcessingRequest,
        onProgress: (Float) -> Unit
    ): MediaProcessingOutcome = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            try {
                if (request.outputFile.exists()) request.outputFile.delete()
                request.outputFile.parentFile?.mkdirs()

                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(Uri.fromFile(request.inputFile))

                if (request.operation == ProcessingOperation.TRIM_VIDEO && request.trimEndMs != null && request.trimEndMs > request.trimStartMs) {
                    mediaItemBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(request.trimStartMs.coerceAtLeast(0L))
                            .setEndPositionMs(request.trimEndMs)
                            .build()
                    )
                }

                val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                if (request.operation == ProcessingOperation.EXTRACT_AUDIO || request.operation == ProcessingOperation.CONVERT_AUDIO) {
                    editedBuilder.setRemoveVideo(true)
                }

                val transformerBuilder = Transformer.Builder(context)
                    .setAudioMimeType(request.targetAudioMime)

                if (request.operation == ProcessingOperation.CONVERT_VIDEO || request.operation == ProcessingOperation.COMPRESS_VIDEO) {
                    transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H264)
                }

                val progressHolder = ProgressHolder()
                var progressPollingRunnable: Runnable? = null
                var transformerRef: Transformer? = null

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressPollingRunnable?.let { mainHandler.removeCallbacks(it) }
                        onProgress(1f)
                        if (continuation.isActive) {
                            continuation.resume(
                                MediaProcessingOutcome.Success(
                                    outputFile = request.outputFile,
                                    engineUsed = com.example.domain.models.ProcessingEngine.MEDIA3
                                )
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressPollingRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (continuation.isActive) {
                            continuation.resume(
                                MediaProcessingOutcome.Failed(
                                    reason = exportException.localizedMessage ?: "Media3 Transformer export failed.",
                                    engineUsed = com.example.domain.models.ProcessingEngine.MEDIA3
                                )
                            )
                        }
                    }
                }

                val transformer = transformerBuilder.addListener(listener).build()
                transformerRef = transformer

                progressPollingRunnable = object : Runnable {
                    override fun run() {
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                        }
                        if (continuation.isActive) {
                            mainHandler.postDelayed(this, 250L)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        progressPollingRunnable?.let { mainHandler.removeCallbacks(it) }
                        runCatching { transformerRef?.cancel() }
                        runCatching { request.outputFile.delete() }
                    }
                }

                transformer.start(editedBuilder.build(), request.outputFile.absolutePath)
                mainHandler.postDelayed(progressPollingRunnable, 200L)
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(
                        MediaProcessingOutcome.Failed(
                            reason = e.localizedMessage ?: "Failed to initialize Media3 Transformer.",
                            engineUsed = com.example.domain.models.ProcessingEngine.MEDIA3
                        )
                    )
                }
            }
        }
    }
}
