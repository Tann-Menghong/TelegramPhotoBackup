package com.example.tgphotobackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tgphotobackup.backup.BackupScheduler
import com.example.tgphotobackup.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules the periodic backup job after a device reboot, because
 * WorkManager periodic work does NOT survive a reboot without this receiver.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsRepository(context).settings.first()
            if (settings.autoBackup && settings.isConfigured) {
                BackupScheduler.setPeriodic(
                    context,
                    enabled = true,
                    wifiOnly = settings.wifiOnly,
                    intervalHours = settings.autoBackupIntervalHours
                )
            }
        }
    }
}
