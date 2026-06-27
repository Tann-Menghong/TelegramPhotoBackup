package com.example.tgphotobackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadedPhoto::class, BackupRun::class, FailedUpload::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadedPhotoDao(): UploadedPhotoDao
    abstract fun backupRunDao(): BackupRunDao
    abstract fun failedUploadDao(): FailedUploadDao

    companion object {
        const val DB_NAME = "tg_photo_backup.db"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE uploaded_photos ADD COLUMN bucketName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `failed_uploads` (" +
                        "`mediaId` INTEGER NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`mimeType` TEXT NOT NULL DEFAULT 'image/jpeg', " +
                        "`bucketName` TEXT NOT NULL DEFAULT '', " +
                        "`errorMessage` TEXT NOT NULL, " +
                        "`failedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`mediaId`))"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE uploaded_photos ADD COLUMN chunkGroup TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE uploaded_photos ADD COLUMN chunkIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE uploaded_photos ADD COLUMN totalChunks INTEGER NOT NULL DEFAULT 1")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { INSTANCE = it }
            }

        fun resetInstance() { synchronized(this) { INSTANCE = null } }
    }
}
