package com.example.tgphotobackup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.data.isVideo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(vm: MainViewModel) {
    val photos by vm.allBackedUpPhotos.collectAsState()
    val runs   by vm.backupRuns.collectAsState()
    val stats  by vm.stats.collectAsState()

    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No statistics yet — back up some photos first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        return
    }

    val videoCount = remember(photos) { photos.count { it.isVideo() } }
    val photoCount = photos.size - videoCount

    val topAlbums = remember(photos) {
        photos.groupBy { it.bucketName.ifBlank { "Other" } }
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }
            .take(5)
    }

    val largest = remember(photos) {
        photos.sortedByDescending { it.sizeBytes }.take(5)
    }

    val freed = (stats.backedUpBytes - stats.storageToFree).coerceAtLeast(0L)

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Overview cards ────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewCard(Modifier.weight(1f), "Total backed up",
                "${stats.totalBackedUp}", formatBytes(stats.backedUpBytes))
            OverviewCard(Modifier.weight(1f), "Storage freed",
                formatBytes(freed), "removed locally")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconStatCard(Modifier.weight(1f), Icons.Default.Image, "Photos", "$photoCount")
            IconStatCard(Modifier.weight(1f), Icons.Default.Movie, "Videos", "$videoCount")
        }

        // ── Top albums ────────────────────────────────────────
        if (topAlbums.isNotEmpty()) {
            StatsCard("Top albums") {
                val max = topAlbums.first().second.coerceAtLeast(1)
                topAlbums.forEach { (name, count) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("$count", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { count.toFloat() / max },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // ── Monthly uploads chart ─────────────────────────────
        StatsCard("Last 6 months") {
            MonthlyChart(runs)
        }

        // ── Largest files ─────────────────────────────────────
        StatsCard("Largest files") {
            largest.forEach { photo ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(photo.displayName, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(photo.bucketName.ifBlank { "Other" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatBytes(photo.sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverviewCard(modifier: Modifier, label: String, value: String, sub: String) {
    Card(modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun IconStatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
                         label: String, value: String) {
    Card(modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MonthlyChart(runs: List<com.example.tgphotobackup.data.BackupRun>) {
    val monthFmt = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val keyFmt   = remember { SimpleDateFormat("yyyyMM", Locale.getDefault()) }

    val months = remember {
        (5 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -offset)
            Triple(keyFmt.format(cal.time), monthFmt.format(cal.time), cal.time)
        }
    }
    val uploadsByMonth = remember(runs) {
        months.map { (key, _, _) ->
            runs.filter { keyFmt.format(Date(it.finishedAt)) == key }.sumOf { it.uploaded }
        }
    }
    val maxCount = uploadsByMonth.maxOrNull()?.coerceAtLeast(1) ?: 1

    val primary    = MaterialTheme.colorScheme.primary
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant
    val onSurface  = MaterialTheme.colorScheme.onSurfaceVariant

    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom) {
        months.forEachIndexed { i, (_, label, _) ->
            val frac = uploadsByMonth[i].toFloat() / maxCount
            val has = uploadsByMonth[i] > 0
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)) {
                if (has) {
                    Text("${uploadsByMonth[i]}", fontSize = 9.sp, color = primary,
                        style = MaterialTheme.typography.labelSmall)
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(64.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                        val barH = size.height * (if (has) frac.coerceAtLeast(0.08f) else 0.04f)
                        drawRoundRect(
                            color = if (has) primary else surfaceVar,
                            topLeft = Offset(0f, size.height - barH),
                            size = Size(size.width, barH),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 9.sp, color = onSurface,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
