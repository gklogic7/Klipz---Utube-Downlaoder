package com.example.downloader.engine

import android.net.Uri
import com.example.core.common.Formatters
import com.example.core.common.NetworkMonitor
import com.example.core.storage.MediaStoreManager
import com.example.domain.models.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class DownloadTransferRequest(
    val taskId: String,
    val downloadUrl: String,
    val title: String,
    val mimeType: String,
    val mediaType: MediaType,
    val isPrivateFolder: Boolean,
    val existingTempPath: String?,
    val expectedETag: String?,
    val expectedLastModified: String?,
    val wifiOnly: Boolean
)

sealed interface DownloadExecutionResult {
    data class Completed(val outputUri: Uri, val finalBytes: Long) : DownloadExecutionResult
    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val tempFilePath: String,
        val eTag: String?,
        val lastModified: String?,
        val supportsRange: Boolean
    ) : DownloadExecutionResult
    data class Cancelled(val cleanedUp: Boolean) : DownloadExecutionResult
    data class Failed(val reason: String, val recoverable: Boolean = true) : DownloadExecutionResult
}

/**
 * Standalone OkHttp streaming transfer engine with HTTP Range resume,
 * ETag/Last-Modified If-Range validation, rate limiting telemetry, and atomic MediaStore commit.
 */
class DownloadEngine(
    private val httpClient: OkHttpClient,
    private val storageManager: MediaStoreManager,
    private val networkMonitor: NetworkMonitor
) {

    suspend fun execute(
        request: DownloadTransferRequest,
        isPauseRequested: () -> Boolean,
        isCancelRequested: () -> Boolean,
        onProgress: suspend (DownloadProgress, String?, String?, String) -> Unit
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        if (!networkMonitor.isConnected()) {
            return@withContext DownloadExecutionResult.Failed("No internet connection available.", recoverable = true)
        }
        if (request.wifiOnly && !networkMonitor.isOnWifiOrUnmetered()) {
            return@withContext DownloadExecutionResult.Failed(
                "Wi-Fi only mode is enabled. Waiting for Wi-Fi connection.",
                recoverable = true
            )
        }

        val ext = Formatters.extensionForMimeType(request.mimeType)
        val tempFile = request.existingTempPath?.let { File(it) }?.takeIf { it.exists() }
            ?: storageManager.createTempFile(request.taskId, ext)

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val canAttemptResume = existingBytes > 0L && (!request.expectedETag.isNullOrBlank() || !request.expectedLastModified.isNullOrBlank())

        val httpRequestBuilder = Request.Builder().url(request.downloadUrl)
        if (canAttemptResume) {
            httpRequestBuilder.header("Range", "bytes=$existingBytes-")
            val validator = request.expectedETag ?: request.expectedLastModified
            if (!validator.isNullOrBlank()) {
                httpRequestBuilder.header("If-Range", validator)
            }
        }

        val call = httpClient.newCall(httpRequestBuilder.build())
        try {
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    return@withContext when (response.code) {
                        416 -> {
                            // Range not satisfiable; discard stale partial and retry clean
                            tempFile.delete()
                            DownloadExecutionResult.Failed("Partial file out of sync (HTTP 416). Retry to restart download.")
                        }
                        401, 403 -> DownloadExecutionResult.Failed(
                            "Access denied (HTTP ${response.code}). Media link may have expired or requires authorization.",
                            recoverable = false
                        )
                        404 -> DownloadExecutionResult.Failed("Media file not found on server (HTTP 404).", recoverable = false)
                        429 -> DownloadExecutionResult.Failed("Server rate limit reached (HTTP 429). Please retry shortly.", recoverable = true)
                        else -> DownloadExecutionResult.Failed("Transfer failed with HTTP ${response.code}.")
                    }
                }

                val isResumed = response.code == 206
                val acceptRangesHeader = response.header("Accept-Ranges")
                val supportsRange = isResumed || acceptRangesHeader.equals("bytes", ignoreCase = true)
                val eTag = response.header("ETag") ?: request.expectedETag
                val lastModified = response.header("Last-Modified") ?: request.expectedLastModified

                val body = response.body
                    ?: return@withContext DownloadExecutionResult.Failed("Server returned an empty response body.")

                val contentLength = body.contentLength()
                val startOffset = if (isResumed) existingBytes else 0L
                val totalBytes = when {
                    isResumed -> parseTotalFromContentRange(response.header("Content-Range"))
                        ?: if (contentLength > 0L) startOffset + contentLength else -1L
                    contentLength > 0L -> contentLength
                    else -> -1L
                }

                if (totalBytes > 0L && !storageManager.hasSufficientStorage(totalBytes - startOffset)) {
                    return@withContext DownloadExecutionResult.Failed(
                        "Insufficient device storage available for this download.",
                        recoverable = false
                    )
                }

                if (!isResumed && tempFile.exists()) {
                    tempFile.delete()
                }
                tempFile.parentFile?.mkdirs()

                val rateTracker = TransferRateTracker().apply { reset(startOffset) }
                var downloaded = startOffset
                var lastEmitTime = 0L

                FileOutputStream(tempFile, isResumed).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()

                            if (isCancelRequested()) {
                                call.cancel()
                                output.flush()
                                tempFile.delete()
                                return@withContext DownloadExecutionResult.Cancelled(cleanedUp = true)
                            }
                            if (isPauseRequested()) {
                                call.cancel()
                                output.flush()
                                return@withContext DownloadExecutionResult.Paused(
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes.coerceAtLeast(downloaded),
                                    tempFilePath = tempFile.absolutePath,
                                    eTag = eTag,
                                    lastModified = lastModified,
                                    supportsRange = supportsRange
                                )
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 350L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                val (speed, eta) = rateTracker.sample(downloaded, totalBytes, now)
                                onProgress(
                                    DownloadProgress(
                                        taskId = request.taskId,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes.coerceAtLeast(0L),
                                        speedBytesPerSec = speed,
                                        etaSeconds = eta,
                                        supportsRange = supportsRange
                                    ),
                                    eTag,
                                    lastModified,
                                    tempFile.absolutePath
                                )
                                lastEmitTime = now
                            }
                        }
                        output.flush()
                    }
                }

                // Publish atomically to MediaStore or Private Folder
                val publishResult = storageManager.publishFile(
                    sourceFile = tempFile,
                    title = request.title,
                    mimeType = request.mimeType,
                    mediaType = request.mediaType,
                    isPrivateFolder = request.isPrivateFolder
                )

                publishResult.fold(
                    onSuccess = { uri ->
                        DownloadExecutionResult.Completed(outputUri = uri, finalBytes = downloaded)
                    },
                    onFailure = { err ->
                        DownloadExecutionResult.Failed(err.localizedMessage ?: "Failed to save downloaded media.")
                    }
                )
            }
        } catch (e: CancellationException) {
            call.cancel()
            if (isCancelRequested()) {
                tempFile.delete()
                DownloadExecutionResult.Cancelled(cleanedUp = true)
            } else {
                DownloadExecutionResult.Paused(
                    downloadedBytes = if (tempFile.exists()) tempFile.length() else existingBytes,
                    totalBytes = 0L,
                    tempFilePath = tempFile.absolutePath,
                    eTag = request.expectedETag,
                    lastModified = request.expectedLastModified,
                    supportsRange = true
                )
            }
        } catch (e: IOException) {
            if (isPauseRequested()) {
                DownloadExecutionResult.Paused(
                    downloadedBytes = if (tempFile.exists()) tempFile.length() else existingBytes,
                    totalBytes = 0L,
                    tempFilePath = tempFile.absolutePath,
                    eTag = request.expectedETag,
                    lastModified = request.expectedLastModified,
                    supportsRange = true
                )
            } else if (isCancelRequested()) {
                tempFile.delete()
                DownloadExecutionResult.Cancelled(cleanedUp = true)
            } else {
                DownloadExecutionResult.Failed("Network interrupted: ${e.localizedMessage ?: "connection lost"}")
            }
        } catch (e: Exception) {
            DownloadExecutionResult.Failed(e.localizedMessage ?: "Unexpected download error.")
        }
    }

    internal fun parseTotalFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        return header.substringAfter("/", "").trim().toLongOrNull()?.takeIf { it > 0L }
    }
}
