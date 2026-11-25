package com.example.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.common.Formatters
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaType
import com.example.domain.models.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Scoped-storage compliant MediaStore manager for Klipz.
 * Writes completed files atomically via IS_PENDING into Downloads/Klipz/{Videos,Images,Audio}
 * (or private app storage when the user toggles Private Folder), avoiding broad storage permissions.
 */
class MediaStoreManager(private val context: Context) {

    companion object {
        const val ROOT_FOLDER = "Klipz"
        const val MIN_FREE_SPACE_BUFFER_BYTES = 25L * 1024L * 1024L // 25 MB safety margin
    }

    fun getTempDir(): File {
        val dir = File(context.cacheDir, "klipz_tmp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPrivateMediaDir(mediaType: MediaType): File {
        val dir = File(File(context.filesDir, "private_media"), mediaType.subfolder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createTempFile(taskId: String, extension: String): File {
        val safeExt = extension.filter { it.isLetterOrDigit() }.ifBlank { "bin" }
        return File(getTempDir(), "part_${taskId}.$safeExt")
    }

    fun relativePathFor(mediaType: MediaType): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/${mediaType.subfolder}"
    }

    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        val available = getAvailableStorageBytes()
        val needed = if (requiredBytes > 0L) requiredBytes + MIN_FREE_SPACE_BUFFER_BYTES else MIN_FREE_SPACE_BUFFER_BYTES
        return available >= needed
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun getStorageInfo(trackedLibraryBytes: Long): StorageInfo = withContext(Dispatchers.IO) {
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val total = stat?.totalBytes ?: 0L
        val available = stat?.availableBytes ?: 0L
        val used = (total - available).coerceAtLeast(0L)
        val tempBytes = directorySize(getTempDir())
        val privateBytes = directorySize(File(context.filesDir, "private_media"))
        StorageInfo(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            klipzLibraryBytes = maxOf(trackedLibraryBytes, privateBytes),
            tempCacheBytes = tempBytes
        )
    }

    suspend fun clearTempCache(): Long = withContext(Dispatchers.IO) {
        val dir = getTempDir()
        val freed = directorySize(dir)
        dir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("active_")) {
                file.deleteRecursively()
            }
        }
        freed
    }

    /**
     * Atomically publishes a verified temporary file to either Android MediaStore
     * (Downloads/Klipz/{Videos,Images,Audio}) or the app's private media folder.
     */
    suspend fun publishFile(
        sourceFile: File,
        title: String,
        mimeType: String,
        mediaType: MediaType,
        isPrivateFolder: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return@withContext Result.failure(IllegalStateException("Downloaded file is empty or missing."))
        }
        if (!hasSufficientStorage(sourceFile.length())) {
            return@withContext Result.failure(IllegalStateException("Insufficient storage space to save file."))
        }

        val cleanBase = UrlValidator.sanitizeFileName(title)
        val ext = Formatters.extensionForMimeType(mimeType)
        val displayName = if (cleanBase.endsWith(".$ext", ignoreCase = true)) cleanBase else "$cleanBase.$ext"

        if (isPrivateFolder || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext saveToPrivateOrAppExternal(sourceFile, displayName, mediaType)
        }

        val resolver = context.contentResolver
        val collection = when (mediaType) {
            MediaType.VIDEO -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.IMAGE -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.AUDIO -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(mediaType))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, values)
            ?: return@withContext saveToPrivateOrAppExternal(sourceFile, displayName, mediaType)

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: throw IllegalStateException("Unable to open MediaStore output stream.")

            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, publishValues, null, null)
            sourceFile.delete()
            Result.success(itemUri)
        } catch (e: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            Result.failure(e)
        }
    }

    private fun saveToPrivateOrAppExternal(
        sourceFile: File,
        displayName: String,
        mediaType: MediaType
    ): Result<Uri> {
        return runCatching {
            val targetDir = getPrivateMediaDir(mediaType)
            var dest = File(targetDir, displayName)
            var counter = 1
            val baseName = displayName.substringBeforeLast(".")
            val ext = displayName.substringAfterLast(".", "bin")
            while (dest.exists()) {
                dest = File(targetDir, "${baseName}_$counter.$ext")
                counter++
            }
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            sourceFile.delete()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        }
    }

    fun copyUriToTempFile(uriString: String, prefix: String = "proc", ext: String = "mp4"): File? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val temp = File(getTempDir(), "${prefix}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null
            if (temp.length() > 0L) temp else null
        }.getOrNull()
    }

    fun verifyUriExists(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).exists() } ?: false
                "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                else -> false
            }
        }.getOrDefault(false)
    }

    suspend fun deleteUri(uriString: String?): Boolean = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext false
        runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).delete() } ?: false
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
