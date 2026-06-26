package com.example.tgphotobackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TgPhotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BACKUP_CHANNEL_ID,
                "Photo Backup",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows progress while photos are uploaded to Telegram" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val BACKUP_CHANNEL_ID = "backup_progress"
    }
}
