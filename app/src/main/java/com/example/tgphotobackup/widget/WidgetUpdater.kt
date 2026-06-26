package com.example.tgphotobackup.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {

    private const val PREFS     = "tgbackup_widget"
    private const val KEY_LAST  = "last_backup_ms"
    private const val KEY_COUNT = "total_count"

    fun save(context: Context, totalCount: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST, System.currentTimeMillis())
            .putInt(KEY_COUNT, totalCount)
            .apply()
        CoroutineScope(Dispatchers.IO).launch {
            BackupWidget().updateAll(context)
        }
    }

    fun read(context: Context): Pair<Long, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getLong(KEY_LAST, 0L) to p.getInt(KEY_COUNT, 0)
    }
}
