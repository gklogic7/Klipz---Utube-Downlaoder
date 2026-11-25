package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormats(formats: List<MediaFormatEntity>)

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): MediaEntity?

    @Query("SELECT * FROM media_formats WHERE mediaId = :mediaId")
    suspend fun getFormatsForMedia(mediaId: String): List<MediaFormatEntity>

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'PROCESSING') ORDER BY priority DESC, createdAt ASC")
    fun observeActiveTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun observeCompletedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('FAILED', 'CANCELLED') ORDER BY createdAt DESC")
    fun observeFailedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE sourceUrl = :sourceUrl AND formatId = :formatId AND status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'COMPLETED') LIMIT 1")
    suspend fun findDuplicateTask(sourceUrl: String, formatId: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN ('DOWNLOADING', 'PROCESSING')")
    suspend fun getInterruptedTasks(): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            progress = :progress,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            etaSeconds = :etaSeconds,
            eTag = COALESCE(:eTag, eTag),
            lastModified = COALESCE(:lastModified, lastModified),
            supportsRange = :supportsRange,
            tempFilePath = COALESCE(:tempFilePath, tempFilePath),
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long?,
        eTag: String?,
        lastModified: String?,
        supportsRange: Boolean,
        tempFilePath: String?,
        errorMessage: String?
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'COMPLETED',
            progress = 1.0,
            downloadedBytes = :finalBytes,
            totalBytes = :finalBytes,
            speedBytesPerSec = 0,
            etaSeconds = 0,
            outputUri = :outputUri,
            tempFilePath = NULL,
            completedAt = :completedAt,
            errorMessage = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: String,
        finalBytes: Long,
        outputUri: String,
        completedAt: Long
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            speedBytesPerSec = 0,
            etaSeconds = NULL,
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: String, status: String, errorMessage: String? = null)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_tasks WHERE status IN ('FAILED', 'CANCELLED')")
    suspend fun clearFailedTasks()
}

@Dao
interface ProcessingTaskDao {
    @Query("SELECT * FROM processing_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProcessingTaskEntity>>

    @Query("SELECT * FROM processing_tasks WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ProcessingTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ProcessingTaskEntity)

    @Query("UPDATE processing_tasks SET status = :status, progress = :progress, outputPath = COALESCE(:outputPath, outputPath), errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Float,
        outputPath: String?,
        errorMessage: String?
    )

    @Query("DELETE FROM processing_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WebHistoryDao {
    @Query("SELECT * FROM web_history ORDER BY visitedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<WebHistoryEntity>>

    @Query("SELECT COUNT(*) FROM web_history")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WebHistoryEntity)

    @Query("DELETE FROM web_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM web_history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
