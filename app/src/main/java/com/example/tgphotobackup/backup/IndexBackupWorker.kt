package com.example.tgphotobackup.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.tgphotobackup.data.SettingsRepository
import kotlinx.coroutines.flow.first

class IndexBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext).settings.first()
        if (!settings.isProMax || !settings.autoIndexBackup) return Result.success()
        return IndexBackupManager.upload(applicationContext)
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
