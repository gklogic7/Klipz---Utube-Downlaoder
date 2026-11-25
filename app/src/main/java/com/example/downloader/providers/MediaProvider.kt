package com.example.downloader.providers

import com.example.domain.models.MediaFormat
import com.example.domain.models.MediaInfo
import com.example.domain.models.MediaInspectionResult

/**
 * Modular provider contract for authorized media metadata and stream discovery.
 */
interface MediaProvider {
    val providerName: String

    fun canHandle(url: String): Boolean

    suspend fun getMediaInfo(url: String): MediaInspectionResult

    suspend fun getMediaInfo(url: String, processId: String): MediaInspectionResult = getMediaInfo(url)

    suspend fun getFormats(media: MediaInfo): List<MediaFormat>

    suspend fun resolveAuthorizedDownload(media: MediaInfo, format: MediaFormat): Result<String>
}
