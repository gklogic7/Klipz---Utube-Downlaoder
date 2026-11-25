package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [Index(value = ["sourceUrl"]), Index(value = ["createdAt"])]
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val platform: String,
    val mediaType: String,
    val thumbnail: String?,
    val duration: Long?,
    val createdAt: Long
)

@Entity(
    tableName = "media_formats",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"])]
)
data class MediaFormatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val formatId: String,
    val resolution: String,
    val codec: String,
    val mimeType: String,
    val fileSize: Long?
)

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val formatId: String,
    val title: String,
    val sourceUrl: String,
    val platform: String,
    val mediaType: String,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val fileFormat: String,
    val mimeType: String,
    val status: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val eTag: String?,
    val lastModified: String?,
    val supportsRange: Boolean,
    val tempFilePath: String?,
    val outputUri: String?,
    val isPrivateFolder: Boolean,
    val ytDlpFormatSelector: String? = null,
    val outputContainer: String = "MP4",
    val priority: Int,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?
)

@Entity(
    tableName = "processing_tasks",
    indices = [Index(value = ["downloadId"]), Index(value = ["status"])]
)
data class ProcessingTaskEntity(
    @PrimaryKey val id: String,
    val downloadId: String,
    val title: String,
    val selectedEngine: String,
    val operation: String,
    val status: String,
    val inputPath: String,
    val outputPath: String?,
    val progress: Float,
    val errorMessage: String?,
    val createdAt: Long
)

@Entity(
    tableName = "web_history",
    indices = [Index(value = ["visitedAt"])]
)
data class WebHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val platform: String,
    val visitedAt: Long
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val platform: String,
    val createdAt: Long
)
