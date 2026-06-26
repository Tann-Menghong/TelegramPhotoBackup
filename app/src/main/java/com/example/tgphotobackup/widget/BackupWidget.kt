package com.example.tgphotobackup.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.tgphotobackup.MainActivity
import com.example.tgphotobackup.backup.BackupWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BackupWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (lastMs, count) = WidgetUpdater.read(context)
        provideContent {
            GlanceTheme {
                WidgetBody(lastMs, count)
            }
        }
    }
}

@Composable
private fun WidgetBody(lastMs: Long, count: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        // ── Title ─────────────────────────────────────────────
        Text(
            text = "TG Backup",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(GlanceModifier.height(8.dp))

        // ── Photo count ───────────────────────────────────────
        Text(
            text = "$count photos",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )

        // ── Last backup time ──────────────────────────────────
        Text(
            text = if (lastMs > 0) "Last: ${relativeTime(lastMs)}" else "Never backed up",
            style = TextStyle(
                color = GlanceTheme.colors.secondary,
                fontSize = 11.sp
            )
        )

        Spacer(GlanceModifier.defaultWeight())

        // ── Action buttons ────────────────────────────────────
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            androidx.glance.Button(
                text = "Open",
                onClick = actionStartActivity<MainActivity>(),
                modifier = GlanceModifier.defaultWeight().height(36.dp)
            )
            Spacer(GlanceModifier.height(1.dp).padding(horizontal = 4.dp))
            androidx.glance.Button(
                text = "Backup Now",
                onClick = actionRunCallback<BackupNowAction>(),
                modifier = GlanceModifier.defaultWeight().height(36.dp)
            )
        }
    }
}

private fun relativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1)   -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1)    -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

class BackupNowAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_backup",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackupWorker>().build()
        )
    }
}
