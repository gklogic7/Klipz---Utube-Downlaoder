package com.example.downloader.engine

data class DownloadProgress(
    val taskId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val supportsRange: Boolean
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Exponential moving average calculator for smooth speed and ETA telemetry.
 */
class TransferRateTracker {
    private var lastSampleTimeMs: Long = 0L
    private var lastSampleBytes: Long = 0L
    private var smoothedBytesPerSec: Double = 0.0

    fun reset(initialBytes: Long, nowMs: Long = System.currentTimeMillis()) {
        lastSampleTimeMs = nowMs
        lastSampleBytes = initialBytes
        smoothedBytesPerSec = 0.0
    }

    fun sample(currentBytes: Long, totalBytes: Long, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long?> {
        val elapsedMs = nowMs - lastSampleTimeMs
        if (elapsedMs < 350L) {
            val speed = smoothedBytesPerSec.toLong()
            return speed to computeEta(currentBytes, totalBytes, speed)
        }
        val deltaBytes = (currentBytes - lastSampleBytes).coerceAtLeast(0L)
        val instantRate = (deltaBytes * 1000.0) / elapsedMs.toDouble()
        smoothedBytesPerSec = if (smoothedBytesPerSec <= 0.0) {
            instantRate
        } else {
            (0.35 * instantRate) + (0.65 * smoothedBytesPerSec)
        }
        lastSampleTimeMs = nowMs
        lastSampleBytes = currentBytes
        val speed = smoothedBytesPerSec.toLong()
        return speed to computeEta(currentBytes, totalBytes, speed)
    }

    private fun computeEta(downloaded: Long, total: Long, speedBps: Long): Long? {
        if (total <= 0L || speedBps <= 0L || downloaded >= total) return null
        return ((total - downloaded) / speedBps).coerceAtLeast(1L)
    }
}
