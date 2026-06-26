package com.example.tgphotobackup.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val UNIQUE_ONE_TIME = "backup_now"
    const val UNIQUE_PERIODIC = "backup_periodic"

    private fun constraints(
        wifiOnly: Boolean,
        requiresCharging: Boolean = false
    ) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(requiresCharging)
        .build()

    fun runNow(context: Context, wifiOnly: Boolean) {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints(wifiOnly, requiresCharging = false))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_ONE_TIME, ExistingWorkPolicy.KEEP, req)
    }

    fun setPeriodic(
        context: Context,
        enabled: Boolean,
        wifiOnly: Boolean,
        intervalHours: Int = 12,
        requiresCharging: Boolean = false
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(UNIQUE_PERIODIC); return }
        val req = PeriodicWorkRequestBuilder<BackupWorker>(
            intervalHours.toLong().coerceAtLeast(1), TimeUnit.HOURS
        )
            .setConstraints(constraints(wifiOnly, requiresCharging))
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
