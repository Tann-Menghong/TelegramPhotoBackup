package com.example.tgphotobackup.backup

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.tgphotobackup.MainActivity
import com.example.tgphotobackup.TgPhotoApp
import com.example.tgphotobackup.data.AppDatabase
import com.example.tgphotobackup.data.BackupRun
import com.example.tgphotobackup.data.SettingsRepository
import com.example.tgphotobackup.widget.WidgetUpdater
import kotlinx.coroutines.flow.first

class BackupWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0, 0, "Starting…", 0, 0))
        return try {
            val settings  = SettingsRepository(applicationContext).settings.first()
            val startedAt = System.currentTimeMillis()
            val summary   = BackupManager(applicationContext).backup(
                includeVideos = settings.includeVideos
            ) { done, total, name, speedBps, etaSec ->
                setForeground(foregroundInfo(done, total, name, speedBps, etaSec))
                setProgress(workDataOf(
                    KEY_DONE  to done,  KEY_TOTAL to total,
                    KEY_NAME  to name,
                    KEY_SPEED to speedBps, KEY_ETA to etaSec
                ))
            }

            AppDatabase.get(applicationContext).backupRunDao().insert(
                BackupRun(
                    startedAt  = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    total      = summary.total,
                    uploaded   = summary.uploaded,
                    skipped    = summary.skipped,
                    failed     = summary.failed,
                    oversized  = summary.oversized
                )
            )

            showCompletionNotification(summary.uploaded, summary.skipped, summary.failed, summary.oversized)

            // Update home-screen widget with fresh total count
            val totalBacked = AppDatabase.get(applicationContext).uploadedPhotoDao().count()
            WidgetUpdater.save(applicationContext, totalBacked)

            Result.success(workDataOf(
                KEY_UPLOADED  to summary.uploaded,
                KEY_SKIPPED   to summary.skipped,
                KEY_FAILED    to summary.failed,
                KEY_OVERSIZED to summary.total,
                KEY_TOTAL     to summary.total,
                KEY_ERROR     to summary.firstError
            ))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private fun foregroundInfo(
        done: Int, total: Int, name: String,
        speedBps: Long, etaSec: Long
    ): ForegroundInfo {
        val speedText = if (speedBps > 0) " · ${formatSpeed(speedBps)}" else ""
        val etaText   = if (etaSec > 0)   " · ${formatEta(etaSec)}"    else ""
        val text = if (total > 0) "$done/$total  $name$speedText$etaText" else "Preparing…"
        val notification = NotificationCompat.Builder(applicationContext, TgPhotoApp.BACKUP_CHANNEL_ID)
            .setContentTitle("Backing up to Telegram")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(NOTIF_ID, notification)
    }

    private fun showCompletionNotification(uploaded: Int, skipped: Int, failed: Int, oversized: Int) {
        val title = when {
            failed > 0 && uploaded == 0 -> "Backup failed"
            failed > 0 -> "Backup done with errors"
            uploaded == 0 -> "Nothing new to back up"
            else -> "Backup complete"
        }
        val text = buildString {
            if (uploaded > 0) append("$uploaded uploaded")
            if (skipped > 0)  append(if (isEmpty()) "$skipped skipped" else " · $skipped skipped")
            if (failed > 0)   append(if (isEmpty()) "$failed failed"   else " · $failed failed")
            if (oversized > 0) append(if (isEmpty()) "$oversized too large" else " · $oversized too large")
            if (isEmpty())    append("All files already backed up")
        }

        // Tap notification → open app
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, TgPhotoApp.BACKUP_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(
                if (failed > 0 && uploaded == 0) android.R.drawable.stat_notify_error
                else android.R.drawable.stat_sys_upload_done
            )
            .setPriority(
                if (failed > 0) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(DONE_NOTIF_ID, notif)
        } catch (_: SecurityException) {}
    }

    companion object {
        private const val NOTIF_ID      = 4242
        private const val DONE_NOTIF_ID = 4243
        const val KEY_DONE      = "done"
        const val KEY_TOTAL     = "total"
        const val KEY_NAME      = "name"
        const val KEY_SPEED     = "speed_bps"
        const val KEY_ETA       = "eta_sec"
        const val KEY_UPLOADED  = "uploaded"
        const val KEY_SKIPPED   = "skipped"
        const val KEY_FAILED    = "failed"
        const val KEY_OVERSIZED = "oversized"
        const val KEY_ERROR     = "first_error"

        fun formatSpeed(bps: Long): String = when {
            bps < 1024 * 1024 -> "${bps / 1024} KB/s"
            else -> "${"%.1f".format(bps / (1024.0 * 1024))} MB/s"
        }

        fun formatEta(sec: Long): String = when {
            sec < 60   -> "~${sec}s left"
            sec < 3600 -> "~${sec / 60}m left"
            else       -> "~${sec / 3600}h left"
        }
    }
}
