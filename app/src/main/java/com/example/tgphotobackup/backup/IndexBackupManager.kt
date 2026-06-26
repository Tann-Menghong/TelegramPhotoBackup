package com.example.tgphotobackup.backup

import android.content.Context
import com.example.tgphotobackup.data.AppDatabase
import com.example.tgphotobackup.data.SettingsRepository
import com.example.tgphotobackup.telegram.TelegramClient
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Exports the local Room database (which maps every backed-up photo to its
 * Telegram file_id) to the Telegram channel so the index survives a phone wipe.
 *
 * Uploads the file as "tg_backup_index.db" — a document message in the channel.
 */
object IndexBackupManager {

    suspend fun upload(context: Context): Result<Unit> {
        val settings = SettingsRepository(context).settings.first()
        if (!settings.isConfigured) return Result.failure(IllegalStateException("Not configured"))

        // Close the database cleanly before copying
        AppDatabase.get(context).close()
        AppDatabase.resetInstance()

        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        if (!dbFile.exists()) return Result.failure(IllegalStateException("DB file not found"))

        val client = TelegramClient(settings.botToken)
        return try {
            client.sendDocument(
                chatId = settings.chatId,
                fileName = "tg_backup_index_${System.currentTimeMillis()}.db",
                mime = "application/octet-stream",
                length = dbFile.length()
            ) { dbFile.inputStream() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Re-open database
            AppDatabase.get(context)
        }
    }

    /** Save a backup copy of the DB to a local safe location. */
    fun localBackup(context: Context) {
        val src = context.getDatabasePath(AppDatabase.DB_NAME)
        val dest = File(context.filesDir, "index_backup.db")
        if (src.exists()) src.copyTo(dest, overwrite = true)
    }
}
