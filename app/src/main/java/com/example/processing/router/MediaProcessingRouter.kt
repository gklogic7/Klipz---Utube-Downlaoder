package com.example.processing.router

import android.net.Uri
import com.example.core.database.ProcessingTaskDao
import com.example.core.database.ProcessingTaskEntity
import com.example.core.storage.MediaStoreManager
import com.example.domain.models.MediaType
import com.example.domain.models.ProcessingEngine
import com.example.domain.models.ProcessingOperation
import com.example.domain.models.ProcessingStatus
import com.example.processing.ffmpeg.FfmpegProcessingEngine
import com.example.processing.media3.Media3ProcessingEngine
import com.example.processing.media3.Media3ProcessingRequest
import com.example.processing.media3.MediaProcessingOutcome
import com.example.processing.validation.MediaInspector
import com.example.processing.validation.MediaStreamInspection
import com.example.processing.validation.OutputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RoutingDecision(
    val engine: ProcessingEngine,
    val requiresConversion: Boolean,
    val targetMimeType: String,
    val targetExtension: String,
    val reason: String
)

/**
 * Automatic hybrid Media3 + FFmpeg processing router.
 *
 * Decision rules:
 * 1. Inspects input container & codecs via MediaExtractor + MediaCodecList.
 * 2. Preserves original quality whenever no conversion is needed.
 * 3. Selects Media3 Transformer for hardware-accelerated MP4/AAC/trim/compress operations.
 * 4. Routes MP3 encoding, stream merging, or unsupported containers to FFmpeg JNI.
 * 5. Validates every output file before atomically saving to MediaStore.
 */
class MediaProcessingRouter(
    private val media3Engine: Media3ProcessingEngine,
    private val ffmpegEngine: FfmpegProcessingEngine,
    private val inspector: MediaInspector,
    private val validator: OutputValidator,
    private val storageManager: MediaStoreManager,
    private val processingTaskDao: ProcessingTaskDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun decideEngine(
        inspection: MediaStreamInspection,
        operation: ProcessingOperation,
        preference: ProcessingEngine,
        preserveOriginalQuality: Boolean,
        preferMp3Audio: Boolean = false
    ): RoutingDecision {
        val media3SupportedInput = inspection.containerExtension in setOf("mp4", "m4v", "mov", "3gp", "webm", "m4a", "aac")
        val videoDecoderOk = inspection.videoMime?.let { inspector.isDecoderSupported(it) } ?: true
        val h264EncoderOk = inspector.isEncoderSupported("video/avc")
        val aacEncoderOk = inspector.isEncoderSupported("audio/mp4a-latm")

        // Never needlessly re-encode when preserveOriginalQuality is on and file already matches MP4 H.264/AAC
        if (preserveOriginalQuality &&
            operation == ProcessingOperation.CONVERT_VIDEO &&
            inspection.containerExtension == "mp4" &&
            inspection.videoMime == "video/avc"
        ) {
            return RoutingDecision(
                engine = ProcessingEngine.MEDIA3,
                requiresConversion = false,
                targetMimeType = "video/mp4",
                targetExtension = "mp4",
                reason = "Source is already H.264 MP4; original quality preserved."
            )
        }

        val media3Capable = when (operation) {
            ProcessingOperation.CONVERT_VIDEO -> media3SupportedInput && videoDecoderOk && h264EncoderOk
            ProcessingOperation.COMPRESS_VIDEO -> media3SupportedInput && videoDecoderOk && h264EncoderOk
            ProcessingOperation.TRIM_VIDEO -> media3SupportedInput && videoDecoderOk
            ProcessingOperation.EXTRACT_AUDIO -> !preferMp3Audio && media3SupportedInput && aacEncoderOk
            ProcessingOperation.CONVERT_AUDIO -> !preferMp3Audio && media3SupportedInput && aacEncoderOk
            ProcessingOperation.MERGE_AUDIO_VIDEO -> false
        }

        val chosen = when {
            operation == ProcessingOperation.MERGE_AUDIO_VIDEO -> ProcessingEngine.FFMPEG
            preferMp3Audio && (operation == ProcessingOperation.EXTRACT_AUDIO || operation == ProcessingOperation.CONVERT_AUDIO) -> ProcessingEngine.FFMPEG
            preference == ProcessingEngine.FFMPEG && ffmpegEngine.isAvailable() -> ProcessingEngine.FFMPEG
            preference == ProcessingEngine.MEDIA3 && media3Capable -> ProcessingEngine.MEDIA3
            media3Capable -> ProcessingEngine.MEDIA3
            else -> ProcessingEngine.FFMPEG
        }

        val (targetMime, targetExt) = when (operation) {
            ProcessingOperation.EXTRACT_AUDIO, ProcessingOperation.CONVERT_AUDIO -> {
                if (chosen == ProcessingEngine.FFMPEG && preferMp3Audio) {
                    "audio/mpeg" to "mp3"
                } else {
                    "audio/mp4" to "m4a"
                }
            }
            else -> "video/mp4" to "mp4"
        }

        val reason = when (chosen) {
            ProcessingEngine.MEDIA3 -> "Selected Media3 Transformer for hardware-accelerated ${operation.displayName}."
            ProcessingEngine.FFMPEG -> "Selected FFmpeg NDK engine for advanced codec/container handling."
            ProcessingEngine.AUTO -> "Automatic routing."
        }

        return RoutingDecision(
            engine = chosen,
            requiresConversion = true,
            targetMimeType = targetMime,
            targetExtension = targetExt,
            reason = reason
        )
    }

    fun enqueueProcessing(
        downloadId: String,
        sourceUri: String,
        title: String,
        operation: ProcessingOperation,
        preference: ProcessingEngine,
        preserveOriginalQuality: Boolean,
        preferMp3Audio: Boolean = false,
        trimStartMs: Long = 0L,
        trimEndMs: Long? = null,
        isPrivateFolder: Boolean = false
    ): String {
        val taskId = UUID.randomUUID().toString()
        val job = scope.launch {
            runProcessingTask(
                taskId = taskId,
                downloadId = downloadId,
                sourceUri = sourceUri,
                title = title,
                operation = operation,
                preference = preference,
                preserveOriginalQuality = preserveOriginalQuality,
                preferMp3Audio = preferMp3Audio,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                isPrivateFolder = isPrivateFolder
            )
        }
        activeJobs[taskId] = job
        return taskId
    }

    fun cancelProcessing(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        ffmpegEngine.cancelActive()
        scope.launch {
            processingTaskDao.updateProgress(
                id = taskId,
                status = ProcessingStatus.CANCELLED.name,
                progress = 0f,
                outputPath = null,
                errorMessage = "Cancelled by user"
            )
        }
    }

    private suspend fun runProcessingTask(
        taskId: String,
        downloadId: String,
        sourceUri: String,
        title: String,
        operation: ProcessingOperation,
        preference: ProcessingEngine,
        preserveOriginalQuality: Boolean,
        preferMp3Audio: Boolean,
        trimStartMs: Long,
        trimEndMs: Long?,
        isPrivateFolder: Boolean
    ) {
        val inputTemp = storageManager.copyUriToTempFile(sourceUri, prefix = "in_$taskId")
        if (inputTemp == null) {
            processingTaskDao.upsert(
                ProcessingTaskEntity(
                    id = taskId,
                    downloadId = downloadId,
                    title = title,
                    selectedEngine = preference.name,
                    operation = operation.name,
                    status = ProcessingStatus.FAILED.name,
                    inputPath = sourceUri,
                    outputPath = null,
                    progress = 0f,
                    errorMessage = "Unable to read source media file for processing.",
                    createdAt = System.currentTimeMillis()
                )
            )
            return
        }

        val inspection = inspector.inspect(inputTemp)
        val decision = decideEngine(
            inspection = inspection,
            operation = operation,
            preference = preference,
            preserveOriginalQuality = preserveOriginalQuality,
            preferMp3Audio = preferMp3Audio
        )

        val entity = ProcessingTaskEntity(
            id = taskId,
            downloadId = downloadId,
            title = title,
            selectedEngine = decision.engine.name,
            operation = operation.name,
            status = ProcessingStatus.RUNNING.name,
            inputPath = inputTemp.absolutePath,
            outputPath = null,
            progress = 0.05f,
            errorMessage = null,
            createdAt = System.currentTimeMillis()
        )
        processingTaskDao.upsert(entity)

        val outputTemp = File(storageManager.getTempDir(), "out_${taskId}.${decision.targetExtension}")

        try {
            if (!decision.requiresConversion) {
                inputTemp.copyTo(outputTemp, overwrite = true)
                publishAndFinish(taskId, outputTemp, title, operation, decision, isPrivateFolder)
                return
            }

            val outcome = when (decision.engine) {
                ProcessingEngine.MEDIA3, ProcessingEngine.AUTO -> {
                    media3Engine.process(
                        Media3ProcessingRequest(
                            inputFile = inputTemp,
                            outputFile = outputTemp,
                            operation = operation,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs
                        ),
                        onProgress = { progress ->
                            scope.launch {
                                processingTaskDao.updateProgress(
                                    id = taskId,
                                    status = ProcessingStatus.RUNNING.name,
                                    progress = progress,
                                    outputPath = null,
                                    errorMessage = null
                                )
                            }
                        }
                    )
                }
                ProcessingEngine.FFMPEG -> {
                    ffmpegEngine.process(
                        inputFile = inputTemp,
                        outputFile = outputTemp,
                        operation = operation,
                        onProgress = { progress ->
                            scope.launch {
                                processingTaskDao.updateProgress(
                                    id = taskId,
                                    status = ProcessingStatus.RUNNING.name,
                                    progress = progress,
                                    outputPath = null,
                                    errorMessage = null
                                )
                            }
                        }
                    )
                }
            }

            when (outcome) {
                is MediaProcessingOutcome.Success -> {
                    publishAndFinish(taskId, outcome.outputFile, title, operation, decision, isPrivateFolder)
                }
                is MediaProcessingOutcome.Failed -> {
                    processingTaskDao.updateProgress(
                        id = taskId,
                        status = ProcessingStatus.FAILED.name,
                        progress = 0f,
                        outputPath = null,
                        errorMessage = outcome.reason
                    )
                }
                is MediaProcessingOutcome.Cancelled -> {
                    processingTaskDao.updateProgress(
                        id = taskId,
                        status = ProcessingStatus.CANCELLED.name,
                        progress = 0f,
                        outputPath = null,
                        errorMessage = "Cancelled"
                    )
                }
            }
        } catch (e: CancellationException) {
            processingTaskDao.updateProgress(
                id = taskId,
                status = ProcessingStatus.CANCELLED.name,
                progress = 0f,
                outputPath = null,
                errorMessage = "Cancelled by user"
            )
            throw e
        } catch (e: Exception) {
            processingTaskDao.updateProgress(
                id = taskId,
                status = ProcessingStatus.FAILED.name,
                progress = 0f,
                outputPath = null,
                errorMessage = e.localizedMessage ?: "Processing error"
            )
        } finally {
            runCatching { inputTemp.delete() }
            runCatching { if (outputTemp.exists()) outputTemp.delete() }
            activeJobs.remove(taskId)
        }
    }

    private suspend fun publishAndFinish(
        taskId: String,
        outputFile: File,
        title: String,
        operation: ProcessingOperation,
        decision: RoutingDecision,
        isPrivateFolder: Boolean
    ) {
        val validation = validator.validate(outputFile, expectAudioOrVideo = true)
        if (!validation.isValid) {
            processingTaskDao.updateProgress(
                id = taskId,
                status = ProcessingStatus.FAILED.name,
                progress = 0f,
                outputPath = null,
                errorMessage = validation.reason ?: "Processed output failed validation."
            )
            return
        }

        val outMediaType = if (decision.targetMimeType.startsWith("audio/")) MediaType.AUDIO else MediaType.VIDEO
        val suffix = when (operation) {
            ProcessingOperation.EXTRACT_AUDIO -> "_audio"
            ProcessingOperation.COMPRESS_VIDEO -> "_compressed"
            ProcessingOperation.TRIM_VIDEO -> "_trimmed"
            ProcessingOperation.CONVERT_AUDIO -> "_converted"
            ProcessingOperation.CONVERT_VIDEO -> "_mp4"
            ProcessingOperation.MERGE_AUDIO_VIDEO -> "_merged"
        }

        val published: Result<Uri> = storageManager.publishFile(
            sourceFile = outputFile,
            title = "$title$suffix",
            mimeType = decision.targetMimeType,
            mediaType = outMediaType,
            isPrivateFolder = isPrivateFolder
        )

        published.fold(
            onSuccess = { uri ->
                processingTaskDao.updateProgress(
                    id = taskId,
                    status = ProcessingStatus.COMPLETED.name,
                    progress = 1f,
                    outputPath = uri.toString(),
                    errorMessage = null
                )
            },
            onFailure = { err ->
                processingTaskDao.updateProgress(
                    id = taskId,
                    status = ProcessingStatus.FAILED.name,
                    progress = 0f,
                    outputPath = null,
                    errorMessage = err.localizedMessage ?: "Failed to save processed file."
                )
            }
        )
    }
}
