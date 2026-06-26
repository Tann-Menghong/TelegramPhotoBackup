package com.example.tgphotobackup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tgphotobackup.backup.BackupWorker
import com.example.tgphotobackup.data.BackupRun
import com.example.tgphotobackup.ui.UpdateState
import com.example.tgphotobackup.ui.GalleryScreen
import com.example.tgphotobackup.ui.HistoryScreen
import com.example.tgphotobackup.ui.MainViewModel
import com.example.tgphotobackup.ui.PhotoDetailScreen
import com.example.tgphotobackup.ui.TgBackupTheme
import com.example.tgphotobackup.ui.formatBytes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()

            // Theme override from settings: 0=system, 1=light, 2=dark
            val isDark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            TgBackupTheme(darkTheme = isDark) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !isDark
                            isAppearanceLightNavigationBars = !isDark
                        }
                    }
                }

                var showSettings       by remember { mutableStateOf(false) }
                var selectedTab        by remember { mutableIntStateOf(0) }
                var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
                val allPhotos          by vm.allBackedUpPhotos.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val connResult by vm.connectionResult.collectAsState()

                LaunchedEffect(connResult) {
                    connResult?.takeIf { it.isNotBlank() }?.let {
                        scope.launch { snackbarHostState.showSnackbar(it) }
                        vm.clearConnectionResult()
                    }
                }

                val tabs = listOf(
                    "Home"    to Icons.Default.Home,
                    "Gallery" to Icons.Default.PhotoLibrary,
                    "History" to Icons.Default.History
                )

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (!showSettings) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(when (selectedTab) {
                                        1 -> "Gallery"; 2 -> "History"; else -> "TG Backup"
                                    }, style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold)
                                },
                                actions = {
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(Icons.Default.Settings, "Settings")
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background)
                            )
                        }
                    },
                    bottomBar = {
                        if (!showSettings) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp
                            ) {
                                tabs.forEachIndexed { i, (label, icon) ->
                                    NavigationBarItem(
                                        selected = selectedTab == i,
                                        onClick  = { selectedTab = i },
                                        icon  = { Icon(icon, label) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when {
                            showSettings -> SettingsScreen(vm) { showSettings = false }
                            selectedTab == 1 -> GalleryScreen(vm) { idx -> selectedPhotoIndex = idx }
                            selectedTab == 2 -> HistoryScreen(vm)
                            else -> HomeScreen(vm)
                        }
                        selectedPhotoIndex?.let { idx ->
                            if (allPhotos.isNotEmpty()) {
                                PhotoDetailScreen(
                                    photos = allPhotos,
                                    initialIndex = idx,
                                    vm = vm,
                                    onBack = { selectedPhotoIndex = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val context       = LocalContext.current
    val settings      by vm.settings.collectAsState()
    val status        by vm.status.collectAsState()
    val stats         by vm.stats.collectAsState()
    val localFreeUris by vm.localFreeUris.collectAsState()
    val verifyResult  by vm.verifyResult.collectAsState()
    val runs          by vm.backupRuns.collectAsState()
    val duplicates    by vm.duplicates.collectAsState()
    val updateState   by vm.updateState.collectAsState()

    // Show "just updated" banner once after version changes
    val currentVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrDefault("")
    }
    val prefs = remember { context.getSharedPreferences("app_meta", android.content.Context.MODE_PRIVATE) }
    var showWhatsNew by remember {
        val seen = prefs.getString("whats_new_seen", "")
        mutableStateOf(seen != currentVersion)
    }

    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
        if (hasPermission) vm.refreshStats(hasPermission = true)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.refreshStats(hasPermission = true)
    }

    val freeUpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.refreshStats() }

    // For Android 8+: navigate to "allow installs from unknown sources" before downloading
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After user returns from the settings screen, retry download
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()) {
            vm.downloadAndInstall(context)
        }
    }

    val pm = context.getSystemService(PowerManager::class.java)
    val needsBatteryOpt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !pm.isIgnoringBatteryOptimizations(context.packageName)

    val progress = if (stats.totalOnDevice > 0)
        stats.totalBackedUp.toFloat() / stats.totalOnDevice else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Hero card ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(Modifier.padding(24.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Backed up",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text("${stats.totalBackedUp}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("of ${stats.totalOnDevice} files · ${formatBytes(stats.backedUpBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(2.dp))
                    val lastLabel = when {
                        stats.lastBackupTime > 0 -> "Last: " + SimpleDateFormat(
                            "MMM d, HH:mm", Locale.getDefault()).format(Date(stats.lastBackupTime))
                        settings.isConfigured -> "No backup yet"
                        else -> "Configure in Settings"
                    }
                    Text(lastLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
                ArcProgress(progress = progress, modifier = Modifier.size(96.dp),
                    primaryColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    label = "${(progress * 100).toInt()}%")
            }
        }

        // ── What's New (shown once after each update) ──────────
        if (showWhatsNew) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Updated to v$currentVersion",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Dark/Light theme now works · Home screen widget · Bug fixes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = {
                        prefs.edit().putString("whats_new_seen", currentVersion).apply()
                        showWhatsNew = false
                    }) {
                        Icon(Icons.Default.Close, "Dismiss",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Alerts ─────────────────────────────────────────────
        if (!hasPermission) {
            AlertBanner(Icons.Default.Lock, "Photo access needed",
                "Tap to grant permission and start backing up.",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer, "Grant") {
                permissionLauncher.launch(requiredPermissions())
            }
        }
        if (needsBatteryOpt) {
            AlertBanner(Icons.Default.BatteryAlert, "Unrestricted battery needed",
                "Allow unrestricted battery use for background backups.",
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer, "Fix") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                }
            }
        }
        if (!settings.isConfigured) {
            AlertBanner(Icons.Default.Warning, "Bot not configured",
                "Open Settings and add your Telegram bot token and channel ID.",
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant, null) {}
        }
        if (duplicates.isNotEmpty()) {
            AlertBanner(Icons.Default.ContentCopy,
                "${duplicates.size} duplicate group${if (duplicates.size > 1) "s" else ""} found",
                "${duplicates.sumOf { it.size }} photos share the same content hash. " +
                "Use Gallery → long-press to select and clean up.",
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer, null) {}
        }

        // ── App update ─────────────────────────────────────────
        if (updateState.available || updateState.downloading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (updateState.downloading) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Downloading update…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("${(updateState.progress * 100).toInt()}% downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                } else {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.SystemUpdate, "Update",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Update available — v${updateState.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Tap Install to download and update",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()) {
                                installPermLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            } else {
                                vm.downloadAndInstall(context)
                            }
                        }) { Text("Install") }
                    }
                }
            }
        }

        // ── Upload progress ────────────────────────────────────
        AnimatedVisibility(status.running,
            enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Uploading…", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            if (status.currentName.isNotBlank()) {
                                Text(status.currentName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${status.done}/${status.total}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { vm.cancelBackup() }) {
                                Icon(Icons.Default.Stop, "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    val speed = status.speedBytesPerSec; val eta = status.etaSeconds
                    if (speed > 0 || eta > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (speed > 0) Text(BackupWorker.formatSpeed(speed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (eta > 0) Text(BackupWorker.formatEta(eta),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── Result / error banners ─────────────────────────────
        status.lastError?.let { err ->
            AlertBanner(Icons.Default.Warning, "Upload failed", err,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer, null) {}
        }
        status.lastResult?.takeIf { !status.running }?.let { result ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Text(result, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Verify progress ────────────────────────────────────
        if (verifyResult.running) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Verifying ${verifyResult.checked} files…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Primary action ─────────────────────────────────────
        Button(
            onClick  = { vm.runBackup() },
            enabled  = hasPermission && settings.isConfigured && !status.running,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CloudUpload, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back up now", style = MaterialTheme.typography.labelLarge)
        }

        // ── Free up space ──────────────────────────────────────
        if (stats.storageToFree > 0) {
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val request = MediaStore.createDeleteRequest(
                            context.contentResolver, localFreeUris)
                        freeUpLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } else {
                        vm.deleteLocalCopies()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Free up ${formatBytes(stats.storageToFree)} of local storage",
                    style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Secondary buttons ──────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick  = { vm.verifyBackup() },
                enabled  = settings.isConfigured && !status.running && !verifyResult.running,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Verify", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick  = { vm.uploadIndex() },
                enabled  = settings.isConfigured && !status.running,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save index", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Weekly upload chart ────────────────────────────────
        if (runs.isNotEmpty()) {
            WeeklyChart(runs)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Weekly Upload Chart ──────────────────────────────────────────────────────

@Composable
private fun WeeklyChart(runs: List<BackupRun>) {
    val dayFmt  = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val keyFmt  = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val todayKey = keyFmt.format(Date())

    // Last 7 days oldest → newest
    val days = remember {
        (6 downTo 0).reversed().map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            Pair(keyFmt.format(cal.time), dayFmt.format(cal.time))
        }
    }
    val uploadsByDay = remember(runs) {
        days.map { (dateKey, _) ->
            runs.filter { keyFmt.format(Date(it.finishedAt)) == dateKey }
                .sumOf { it.uploaded }
        }
    }
    val maxCount = uploadsByDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    val totalWeek = uploadsByDay.sum()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("$totalWeek files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            val primary      = MaterialTheme.colorScheme.primary
            val surfaceVar   = MaterialTheme.colorScheme.surfaceVariant
            val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, (dateKey, dayLabel) ->
                    val isToday   = dateKey == todayKey
                    val barFrac   = uploadsByDay[i].toFloat() / maxCount
                    val hasUploads = uploadsByDay[i] > 0

                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)) {
                        // Count label above bar
                        if (hasUploads) {
                            Text("${uploadsByDay[i]}", fontSize = 9.sp,
                                color = primary, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }
                        Spacer(Modifier.height(2.dp))

                        // Bar
                        Box(Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(56.dp),
                            contentAlignment = Alignment.BottomCenter) {
                            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                                val barH = size.height * (if (hasUploads) barFrac.coerceAtLeast(0.08f) else 0.04f)
                                drawRoundRect(
                                    color = if (hasUploads) primary else surfaceVar,
                                    topLeft = Offset(0f, size.height - barH),
                                    size = Size(size.width, barH),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(dayLabel, fontSize = 9.sp,
                            color = if (isToday) primary else onSurfaceVar,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings   by vm.settings.collectAsState()
    val connResult by vm.connectionResult.collectAsState()
    val isTesting  by vm.isTestingConnection.collectAsState()
    val allPhotos  by vm.allBackedUpPhotos.collectAsState()

    // Known albums from backed-up photos
    val availableAlbums = remember(allPhotos) {
        allPhotos.map { it.bucketName.ifBlank { "Other" } }.distinct().sorted()
    }

    val intervalOptions = listOf(6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Every 24 hours")
    val themeOptions    = listOf(0 to "System default", 1 to "Light", 2 to "Dark")

    var token              by remember(settings.botToken)               { mutableStateOf(settings.botToken) }
    var chatId             by remember(settings.chatId)                 { mutableStateOf(settings.chatId) }
    var wifiOnly           by remember(settings.wifiOnly)               { mutableStateOf(settings.wifiOnly) }
    var autoBackup         by remember(settings.autoBackup)             { mutableStateOf(settings.autoBackup) }
    var includeVideos      by remember(settings.includeVideos)          { mutableStateOf(settings.includeVideos) }
    var charging           by remember(settings.requiresCharging)       { mutableStateOf(settings.requiresCharging) }
    var autoDelete         by remember(settings.autoDeleteAfterBackup)  { mutableStateOf(settings.autoDeleteAfterBackup) }
    var themeMode          by remember(settings.themeMode)              { mutableIntStateOf(settings.themeMode) }
    var includedAlbums     by remember(settings.includedAlbums)         { mutableStateOf(settings.includedAlbums) }
    var intervalHours      by remember(settings.autoBackupIntervalHours){ mutableIntStateOf(settings.autoBackupIntervalHours) }
    var updateUrl          by remember(settings.updateUrl)              { mutableStateOf(settings.updateUrl) }
    var showIntervalMenu   by remember { mutableStateOf(false) }
    var showThemeMenu      by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Telegram ──────────────────────────────────────
            SettingsSection("Telegram") {
                OutlinedTextField(token, { token = it; vm.clearConnectionResult() },
                    label = { Text("Bot token") },
                    placeholder = { Text("123456:ABC-DEF…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(chatId, { chatId = it },
                    label = { Text("Channel / group ID") },
                    placeholder = { Text("-1001234567890") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { vm.testConnection(token) }, enabled = !isTesting,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    ) { Text(if (isTesting) "Testing…" else "Test token") }
                    FilledTonalButton(
                        onClick = { vm.testChannel(token, chatId) }, enabled = !isTesting,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    ) { Text(if (isTesting) "Testing…" else "Test channel") }
                }
                connResult?.let { msg ->
                    val ok = msg.startsWith("Token OK") || msg.startsWith("Channel OK")
                    Text(msg, style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }

            // ── Backup ────────────────────────────────────────
            SettingsSection("Backup") {
                ToggleRow(Icons.Default.WifiOff, "Wi-Fi only",
                    "Don't upload on mobile data", wifiOnly) { wifiOnly = it }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                ToggleRow(Icons.Default.CloudUpload, "Auto backup",
                    "Run in background automatically", autoBackup) { autoBackup = it }
                AnimatedVisibility(autoBackup) {
                    Column {
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Interval", style = MaterialTheme.typography.bodyMedium)
                            Box {
                                TextButton(onClick = { showIntervalMenu = true }) {
                                    Text(intervalOptions.find { it.first == intervalHours }?.second
                                        ?: "Every 12 hours", color = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(showIntervalMenu, { showIntervalMenu = false }) {
                                    intervalOptions.forEach { (h, label) ->
                                        DropdownMenuItem(text = { Text(label) },
                                            onClick = { intervalHours = h; showIntervalMenu = false })
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        ToggleRow(Icons.Default.BatteryChargingFull, "Only when charging",
                            "Auto backup runs only while plugged in", charging) { charging = it }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                ToggleRow(Icons.Default.PhotoLibrary, "Include videos",
                    "Also back up video files", includeVideos) { includeVideos = it }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                ToggleRow(Icons.Default.AutoDelete, "Auto-delete after backup",
                    "Remove local copy once safely uploaded", autoDelete) { autoDelete = it }
            }

            // ── Albums to back up ──────────────────────────────
            if (availableAlbums.isNotEmpty()) {
                SettingsSection("Albums to back up") {
                    Text(
                        if (includedAlbums.isEmpty()) "Backing up all albums (default)"
                        else "${includedAlbums.size} of ${availableAlbums.size} albums selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                    )
                    // "All albums" shortcut
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = includedAlbums.isEmpty(),
                            onCheckedChange = { if (it) includedAlbums = emptySet() }
                        )
                        Column {
                            Text("All albums", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text("Back up everything (recommended)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    availableAlbums.forEach { album ->
                        val checked = album in includedAlbums
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    includedAlbums = if (on) includedAlbums + album
                                                    else includedAlbums - album
                                }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Folder, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text(album, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // ── Appearance ─────────────────────────────────────
            SettingsSection("Appearance") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Theme", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text(themeOptions.find { it.first == themeMode }?.second ?: "System default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        TextButton(onClick = { showThemeMenu = true }) {
                            Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(showThemeMenu, { showThemeMenu = false }) {
                            themeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) },
                                    onClick = { themeMode = mode; showThemeMenu = false })
                            }
                        }
                    }
                }
            }

            // ── Updates ───────────────────────────────────────
            val updateState by vm.updateState.collectAsState()
            SettingsSection("App Updates") {
                OutlinedTextField(updateUrl, { updateUrl = it },
                    label = { Text("Update URL") },
                    placeholder = { Text("https://github.com/user/repo") },
                    supportingText = {
                        Text("Paste your GitHub repo URL, or a JSON URL with " +
                            "{versionName, apkUrl}")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { vm.checkForUpdates(manual = true) },
                        enabled = updateUrl.isNotBlank() &&
                            !updateState.checking && !updateState.downloading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (updateState.checking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Check now")
                        }
                    }
                }
            }

            // ── Save ──────────────────────────────────────────
            Button(
                onClick = {
                    vm.save(token, chatId, wifiOnly, autoBackup, includeVideos,
                        intervalHours = intervalHours, requiresCharging = charging,
                        autoDeleteAfterBackup = autoDelete, themeMode = themeMode,
                        includedAlbums = includedAlbums, updateUrl = updateUrl)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Save settings", style = MaterialTheme.typography.labelLarge) }

            val ctx = LocalContext.current
            val versionName = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }.getOrDefault("—")
            Text("TG Photo Backup v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArcProgress(
    progress: Float, modifier: Modifier = Modifier,
    primaryColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color,
    label: String
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.width * 0.09f
            val inset  = stroke / 2f
            val arcSz  = Size(size.width - stroke, size.height - stroke)
            val tl     = Offset(inset, inset)
            drawArc(trackColor, 135f, 270f, false, tl, arcSz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f)
                drawArc(primaryColor, 135f, 270f * progress.coerceIn(0f, 1f), false, tl, arcSz,
                    style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun AlertBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    actionLabel: String?, onAction: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = containerColor) {
        Row(Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = contentColor,
                    fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f))
            }
            if (actionLabel != null) {
                TextButton(onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, description: String,
    checked: Boolean, onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
}
