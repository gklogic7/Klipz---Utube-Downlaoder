package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        MediaFormatEntity::class,
        DownloadTaskEntity::class,
        ProcessingTaskEntity::class,
        WebHistoryEntity::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KlipzDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun processingTaskDao(): ProcessingTaskDao
    abstract fun webHistoryDao(): WebHistoryDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val DATABASE_NAME = "klipz_local.db"

        fun create(context: Context): KlipzDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KlipzDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
        }
    }
}
