package com.example.tgphotobackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadedPhoto::class, BackupRun::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadedPhotoDao(): UploadedPhotoDao
    abstract fun backupRunDao(): BackupRunDao

    companion object {
        const val DB_NAME = "tg_photo_backup.db"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE uploaded_photos ADD COLUMN bucketName TEXT NOT NULL DEFAULT ''")
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
                .addMigrations(MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
            }

        fun resetInstance() { synchronized(this) { INSTANCE = null } }
    }
}
