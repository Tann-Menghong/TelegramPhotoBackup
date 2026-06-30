package com.example.tgphotobackup.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.tgphotobackup.backup.BackupScheduler
import com.example.tgphotobackup.backup.BackupWorker
import com.example.tgphotobackup.backup.MediaStoreScanner
import com.example.tgphotobackup.backup.NetworkChecker
import com.example.tgphotobackup.backup.EncryptionManager
import com.example.tgphotobackup.data.AppDatabase
import com.example.tgphotobackup.data.AppSettings
import com.example.tgphotobackup.data.BackupRun
import com.example.tgphotobackup.data.FailedUpload
import com.example.tgphotobackup.data.LicenseType
import com.example.tgphotobackup.data.ProManager
import com.example.tgphotobackup.data.SettingsRepository
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.data.contentUri
import com.example.tgphotobackup.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── State types ─────────────────────────────────────────────────────────────

data class BackupStatus(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val lastResult: String? = null,
    val lastError: String? = null
)

data class LibraryStats(
    val totalOnDevice: Int = 0,
    val totalBackedUp: Int = 0,
    val backedUpBytes: Long = 0,
    val lastBackupTime: Long = 0,
    val storageToFree: Long = 0
)

data class VerifyResult(
    val checked: Int = 0,
    val broken: Int = 0,
    val running: Boolean = false
)

data class UpdateState(
    val available: Boolean = false,
    val versionName: String = "",
    val apkUrl: String = "",
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val dao          = AppDatabase.get(app).uploadedPhotoDao()
    private val failedDao    = AppDatabase.get(app).failedUploadDao()
    private val workManager  = WorkManager.getInstance(app)

    val settings = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings()
    )

    val isPro: kotlinx.coroutines.flow.StateFlow<Boolean> = settings
        .map { it.isPro }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isProMax: kotlinx.coroutines.flow.StateFlow<Boolean> = settings
        .map { it.isProMax }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val proExpiresAt: kotlinx.coroutines.flow.StateFlow<String> = settings
        .map { it.proExpiresAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _stats = MutableStateFlow(LibraryStats())
    val stats = _stats.asStateFlow()

    private val _recentUploads = MutableStateFlow<List<UploadedPhoto>>(emptyList())
    val recentUploads = _recentUploads.asStateFlow()

    private val _allBackedUpPhotos = MutableStateFlow<List<UploadedPhoto>>(emptyList())
    val allBackedUpPhotos = _allBackedUpPhotos.asStateFlow()

    private val _backupRuns = MutableStateFlow<List<BackupRun>>(emptyList())
    val backupRuns = _backupRuns.asStateFlow()

    private val _localFreeUris = MutableStateFlow<List<Uri>>(emptyList())
    val localFreeUris = _localFreeUris.asStateFlow()

    private val _connectionResult = MutableStateFlow<String?>(null)
    val connectionResult = _connectionResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection = _isTestingConnection.asStateFlow()

    private val _restoreStatus = MutableStateFlow<String?>(null)
    val restoreStatus = _restoreStatus.asStateFlow()

    private val _verifyResult = MutableStateFlow(VerifyResult())
    val verifyResult = _verifyResult.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState = _updateState.asStateFlow()

    private val _shareStatus = MutableStateFlow<String?>(null)
    val shareStatus = _shareStatus.asStateFlow()

    private val _failedUploads = MutableStateFlow<List<FailedUpload>>(emptyList())
    val failedUploads = _failedUploads.asStateFlow()

    val failedCount: kotlinx.coroutines.flow.StateFlow<Int> =
        failedDao.countFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _bulkRestoreStatus = MutableStateFlow<String?>(null)
    val bulkRestoreStatus = _bulkRestoreStatus.asStateFlow()

    // Rate-limiting state for license key entry
    private var licenseAttempts = 0
    private var licenseLockUntil = 0L
    private val _licenseLockoutSeconds = MutableStateFlow(0L)
    val licenseLockoutSeconds = _licenseLockoutSeconds.asStateFlow()

    // Duplicate groups: photos sharing the same contentHash
    val duplicates: kotlinx.coroutines.flow.StateFlow<List<List<UploadedPhoto>>> =
        _allBackedUpPhotos
            .map { photos ->
                photos.groupBy { it.contentHash }
                      .values
                      .filter { it.size > 1 }
                      .toList()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workStatus = workManager
        .getWorkInfosForUniqueWorkFlow(BackupScheduler.UNIQUE_ONE_TIME)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status = workStatus
        .map { it.toStatus() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupStatus())

    init {
        refreshStats()
        viewModelScope.launch {
            workStatus
                .filter { it?.state == WorkInfo.State.SUCCEEDED }
                .distinctUntilChanged()
                .collect { refreshStats() }
        }
        // Auto-check for updates once settings are loaded
        viewModelScope.launch {
            settings.filter { it.updateUrl.isNotBlank() }.first()
            checkForUpdates(manual = false)
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    fun refreshStats(hasPermission: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val app     = getApplication<Application>()
            val backed  = dao.getAll()
            val onDevice = if (hasPermission) MediaStoreScanner.queryAll(app).size else 0

            val freeUris = mutableListOf<Uri>()
            var freeBytes = 0L
            backed.forEach { photo ->
                val uri = photo.contentUri()
                val exists = runCatching {
                    app.contentResolver.query(uri,
                        arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                        ?.use { it.count > 0 } ?: false
                }.getOrDefault(false)
                if (exists) { freeUris.add(uri); freeBytes += photo.sizeBytes }
            }
            _localFreeUris.value = freeUris

            val runs = AppDatabase.get(app).backupRunDao().recent()
            _recentUploads.value     = dao.recent()
            _allBackedUpPhotos.value = backed
            _backupRuns.value        = runs
            _failedUploads.value     = failedDao.getAll()
            _stats.value = LibraryStats(
                totalOnDevice  = onDevice,
                totalBackedUp  = backed.size,
                backedUpBytes  = dao.totalBytes(),
                lastBackupTime = runs.firstOrNull()?.finishedAt ?: 0L,
                storageToFree  = freeBytes
            )
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun save(
        botToken: String, chatId: String,
        wifiOnly: Boolean, autoBackup: Boolean,
        includeVideos: Boolean,
        maxFileSizeMb: Int = 50,
        intervalHours: Int = 12,
        requiresCharging: Boolean = false,
        autoDeleteAfterBackup: Boolean = false,
        themeMode: Int = 0,
        includedAlbums: Set<String> = emptySet(),
        updateUrl: String = "",
        biometricLock: Boolean = false,
        safFolderUris: Set<String> = emptySet(),
        encryptBackup: Boolean = false,
        autoIndexBackup: Boolean = false
    ) {
        viewModelScope.launch {
            settingsRepo.save(
                botToken, chatId, wifiOnly, autoBackup, includeVideos,
                maxFileSizeMb, intervalHours, requiresCharging,
                autoDeleteAfterBackup, themeMode, includedAlbums, updateUrl,
                biometricLock, safFolderUris, encryptBackup, autoIndexBackup
            )
            BackupScheduler.setPeriodic(getApplication(), autoBackup, wifiOnly,
                intervalHours, requiresCharging)
            BackupScheduler.setAutoIndex(getApplication(), autoIndexBackup)
        }
    }

    // ── Backup control ────────────────────────────────────────────────────────

    fun runBackup() {
        val s = settings.value
        if (!s.isConfigured)                                    { _connectionResult.value = "Set the bot token and channel ID first."; return }
        if (!NetworkChecker.isConnected(getApplication()))      { _connectionResult.value = "No internet connection."; return }
        if (s.wifiOnly && !NetworkChecker.isWifi(getApplication())) { _connectionResult.value = "Wi-Fi only mode is on — connect to Wi-Fi first."; return }
        BackupScheduler.runNow(getApplication(), s.wifiOnly)
    }

    fun cancelBackup() = workManager.cancelUniqueWork(BackupScheduler.UNIQUE_ONE_TIME)

    // ── Connection tests ──────────────────────────────────────────────────────

    fun testConnection(botToken: String) {
        if (botToken.isBlank()) { _connectionResult.value = "Enter a bot token first."; return }
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionResult.value   = "Checking token…"
            val result = withContext(Dispatchers.IO) { TelegramClient(botToken).getMe() }
            _connectionResult.value = result.fold(
                onSuccess = { "Token OK — bot is @$it" },
                onFailure = { "Token error: ${it.message}" }
            )
            _isTestingConnection.value = false
        }
    }

    fun testChannel(botToken: String, chatId: String) {
        if (botToken.isBlank() || chatId.isBlank()) {
            _connectionResult.value = "Fill in both bot token and channel ID first."; return
        }
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionResult.value   = "Sending test message…"
            val result = withContext(Dispatchers.IO) {
                TelegramClient(botToken).sendTestMessage(chatId)
            }
            _connectionResult.value = result.fold(
                onSuccess = { "Channel OK — test message sent!" },
                onFailure = { "Channel error: ${it.message}" }
            )
            _isTestingConnection.value = false
        }
    }

    fun clearConnectionResult() { _connectionResult.value = null }

    // ── Pro unlock ────────────────────────────────────────────────────────────

    fun unlockPro(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < licenseLockUntil) {
            _licenseLockoutSeconds.value = (licenseLockUntil - now) / 1000
            return false
        }
        val type = ProManager.validate(key)
        if (type !is LicenseType.ProMonthly || !ProManager.isProActive(type)) {
            recordFailedAttempt(now)
            return false
        }
        clearLockout()
        viewModelScope.launch { settingsRepo.savePro(key) }
        return true
    }

    fun unlockProMax(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < licenseLockUntil) {
            _licenseLockoutSeconds.value = (licenseLockUntil - now) / 1000
            return false
        }
        val type = ProManager.validate(key)
        if (!ProManager.isProMaxActive(type)) {
            recordFailedAttempt(now)
            return false
        }
        clearLockout()
        viewModelScope.launch { settingsRepo.saveProMax(key) }
        return true
    }

    private fun recordFailedAttempt(now: Long) {
        licenseAttempts++
        if (licenseAttempts >= 5) {
            // 15 s × extra attempts, capped at 5 minutes
            val delay = minOf(licenseAttempts * 15_000L, 300_000L)
            licenseLockUntil = now + delay
            _licenseLockoutSeconds.value = delay / 1000
        }
    }

    private fun clearLockout() {
        licenseAttempts = 0; licenseLockUntil = 0L; _licenseLockoutSeconds.value = 0L
    }

    // ── Export backup report (Pro Max) ────────────────────────────────────────

    fun exportReport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val photos = _allBackedUpPhotos.value
            val runs   = _backupRuns.value
            val fmt    = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val sb     = StringBuilder()
            sb.appendLine("TG Backup Report — ${fmt.format(java.util.Date())}")
            sb.appendLine("Total: ${photos.size} files · ${formatBytes(photos.sumOf { it.sizeBytes })}")
            sb.appendLine()
            sb.appendLine("=== BACKUP RUNS (${runs.size}) ===")
            runs.forEach { run ->
                sb.appendLine("${fmt.format(java.util.Date(run.finishedAt))} — " +
                    "${run.uploaded} uploaded / ${run.skipped} skipped / ${run.failed} failed")
            }
            sb.appendLine()
            sb.appendLine("=== FILES (${photos.size}) ===")
            val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            photos.forEach { p ->
                sb.appendLine("${dayFmt.format(java.util.Date(p.uploadedAt))} | " +
                    "${p.displayName} | ${formatBytes(p.sizeBytes)} | ${p.bucketName}")
            }
            val file = java.io.File(context.cacheDir, "backup_report.txt")
            file.writeText(sb.toString())
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Export Backup Report").also {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // ── Index backup ──────────────────────────────────────────────────────────

    fun uploadIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            _connectionResult.value = "Uploading index…"
            val result = com.example.tgphotobackup.backup.IndexBackupManager.upload(getApplication())
            _connectionResult.value = result.fold(
                onSuccess = { "Index uploaded successfully." },
                onFailure = { "Index upload failed: ${it.message}" }
            )
        }
    }

    // ── Restore from Telegram ─────────────────────────────────────────────────

    fun restorePhoto(photo: UploadedPhoto) {
        _restoreStatus.value = "Downloading…"
        viewModelScope.launch(Dispatchers.IO) {
            val s      = settingsRepo.settings.first()
            val client = TelegramClient(s.botToken)

            val data: ByteArray = if (photo.totalChunks > 1 && photo.chunkGroup != null) {
                // Chunked file — download and concatenate all parts
                val chunks = dao.findChunks(photo.chunkGroup).sortedBy { it.chunkIndex }
                if (chunks.size < photo.totalChunks) {
                    _restoreStatus.value = "Restore failed: some chunks are missing"
                    return@launch
                }
                val buf = java.io.ByteArrayOutputStream()
                for (chunk in chunks) {
                    _restoreStatus.value = "Downloading part ${chunk.chunkIndex + 1}/${photo.totalChunks}…"
                    val fp = client.getFilePath(chunk.fileId).getOrElse {
                        _restoreStatus.value = "Restore failed: ${it.message}"
                        return@launch
                    }
                    val bytes = client.downloadBytes(fp).getOrElse {
                        _restoreStatus.value = "Download failed: ${it.message}"
                        return@launch
                    }
                    buf.write(bytes)
                }
                buf.toByteArray()
            } else {
                // Single file
                val filePath = client.getFilePath(photo.fileId).getOrElse {
                    _restoreStatus.value = "Restore failed: ${it.message}"
                    return@launch
                }
                client.downloadBytes(filePath).getOrElse {
                    _restoreStatus.value = "Download failed: ${it.message}"
                    return@launch
                }
            }

            val decKey = EncryptionManager.deriveKey(s.botToken)
            val actualData = if (EncryptionManager.isEncryptedName(photo.displayName))
                runCatching { EncryptionManager.decrypt(data, decKey) }.getOrDefault(data)
            else data
            val displayName = EncryptionManager.decryptedName(photo.displayName)
            val saved = saveToGallery(getApplication(), displayName, photo.mimeType, actualData)
            _restoreStatus.value = if (saved) "Saved '$displayName' to gallery ✓"
                                   else "Failed to save to gallery"
        }
    }

    fun clearRestoreStatus() { _restoreStatus.value = null }

    // ── Retry queue (failed uploads) ───────────────────────────────────────────

    /** Re-runs the backup; succeeded files clear their failure record automatically. */
    fun retryFailed() = runBackup()

    fun clearAllFailed() {
        viewModelScope.launch(Dispatchers.IO) {
            failedDao.clear()
            _failedUploads.value = emptyList()
        }
    }

    // ── Bulk restore ───────────────────────────────────────────────────────────

    fun bulkRestore(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val photos = dao.getAll()
            if (photos.isEmpty()) { _bulkRestoreStatus.value = "Nothing to restore"; return@launch }
            val s = settingsRepo.settings.first()
            val client = TelegramClient(s.botToken)
            var restored = 0; var failed = 0
            val decKey = EncryptionManager.deriveKey(s.botToken)
            photos.forEachIndexed { i, photo ->
                _bulkRestoreStatus.value = "Restoring ${i + 1}/${photos.size}…"
                runCatching {
                    val filePath = client.getFilePath(photo.fileId).getOrThrow()
                    var data = client.downloadBytes(filePath).getOrThrow()
                    if (EncryptionManager.isEncryptedName(photo.displayName)) {
                        data = runCatching { EncryptionManager.decrypt(data, decKey) }.getOrDefault(data)
                    }
                    val name = EncryptionManager.decryptedName(photo.displayName)
                    if (saveToGallery(context, name, photo.mimeType, data)) restored++ else failed++
                }.onFailure { failed++ }
            }
            _bulkRestoreStatus.value = "Restored $restored files" +
                if (failed > 0) " · $failed failed" else ""
            refreshStats()
        }
    }

    fun clearBulkRestoreStatus() { _bulkRestoreStatus.value = null }

    // ── One-tap duplicate cleaner ──────────────────────────────────────────────

    fun deleteAllDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val groups = _allBackedUpPhotos.value
                .groupBy { it.contentHash }
                .values.filter { it.size > 1 }
            var freed = 0L
            groups.forEach { group ->
                // keep the first (oldest uploaded), delete the rest from device and DB
                group.drop(1).forEach { photo ->
                    runCatching { app.contentResolver.delete(photo.contentUri(), null, null) }
                    dao.deleteByMediaId(photo.mediaId)
                    freed += photo.sizeBytes
                }
            }
            if (freed > 0) _connectionResult.value = "Removed duplicates, freed ${formatBytes(freed)}"
            refreshStats()
        }
    }

    // ── Share (works even when local file is deleted) ──────────────────────────

    fun sharePhoto(photo: UploadedPhoto, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _shareStatus.value = "Preparing…"
            val uri = downloadToShareCache(context, photo)
            if (uri != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = photo.mimeType
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                _shareStatus.value = null
            } else {
                _shareStatus.value = "Download failed — check bot token & connection"
            }
        }
    }

    fun sharePhotos(photos: List<UploadedPhoto>, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = arrayListOf<Uri>()
            photos.forEachIndexed { i, photo ->
                _shareStatus.value = "Downloading ${i + 1}/${photos.size}…"
                downloadToShareCache(context, photo)?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share ${uris.size} files"))
                _shareStatus.value = null
            } else {
                _shareStatus.value = "Download failed — check bot token & connection"
            }
        }
    }

    fun clearShareStatus() { _shareStatus.value = null }

    // ── Play video (works even when local file is deleted) ─────────────────────

    private val _playStatus = MutableStateFlow<String?>(null)
    val playStatus = _playStatus.asStateFlow()
    private var playJob: Job? = null

    fun playVideo(photo: UploadedPhoto, context: android.content.Context) {
        playJob?.cancel()
        _playStatus.value = null
        playJob = viewModelScope.launch(Dispatchers.IO) {
            _playStatus.value = "Preparing video…"
            val uri = downloadToShareCache(context, photo)
            if (uri != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, photo.mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                _playStatus.value = null
            } else {
                _playStatus.value = "Download failed — check bot token & connection"
            }
        }
    }

    fun clearPlayStatus() { playJob?.cancel(); _playStatus.value = null }

    private suspend fun downloadToShareCache(
        context: android.content.Context,
        photo: UploadedPhoto
    ): Uri? {
        // Use the on-device file directly if it still exists
        val localUri = photo.contentUri()
        val localExists = runCatching {
            context.contentResolver.query(
                localUri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (localExists) return localUri

        // File was deleted — download from Telegram into cache
        val s = settingsRepo.settings.first()
        if (s.botToken.isBlank()) return null
        val client = TelegramClient(s.botToken)

        val shareDir = java.io.File(context.cacheDir, "share").also { it.mkdirs() }
        val file = java.io.File(shareDir, photo.displayName)

        if (photo.totalChunks > 1 && photo.chunkGroup != null) {
            // Chunked file — download each part and concatenate
            val chunks = AppDatabase.get(context).uploadedPhotoDao()
                .findChunks(photo.chunkGroup)
                .sortedBy { it.chunkIndex }
            if (chunks.size < photo.totalChunks) return null

            file.outputStream().buffered().use { out ->
                for (chunk in chunks) {
                    _shareStatus.value = "Downloading part ${chunk.chunkIndex + 1}/${photo.totalChunks}…"
                    val filePath = client.getFilePath(chunk.fileId).getOrElse { return null }
                    val bytes    = client.downloadBytes(filePath).getOrElse { return null }
                    out.write(bytes)
                }
            }
        } else {
            // Single file
            val filePath = client.getFilePath(photo.fileId).getOrElse { return null }
            val data     = client.downloadBytes(filePath).getOrElse { return null }
            file.writeBytes(data)
        }

        // Decrypt in-place if the file was encrypted
        if (EncryptionManager.isEncryptedName(photo.displayName)) {
            val decKey = EncryptionManager.deriveKey(s.botToken)
            val decName = EncryptionManager.decryptedName(photo.displayName)
            val decFile = java.io.File(shareDir, decName)
            runCatching {
                val decBytes = EncryptionManager.decrypt(file.readBytes(), decKey)
                decFile.writeBytes(decBytes)
                file.delete()
                return androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", decFile)
            }
        }

        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
    }

    // ── Free up space ─────────────────────────────────────────────────────────

    fun deleteLocalCopies() {
        viewModelScope.launch(Dispatchers.IO) {
            var freed = 0L
            _localFreeUris.value.forEach { uri ->
                runCatching {
                    val deleted = getApplication<Application>().contentResolver.delete(uri, null, null)
                    if (deleted > 0) {
                        val photo = _allBackedUpPhotos.value.find { it.contentUri() == uri }
                        freed += photo?.sizeBytes ?: 0L
                    }
                }
            }
            _connectionResult.value = if (freed > 0)
                "Freed ${formatBytes(freed)} of local storage"
            else
                "Nothing to remove (files may need system confirmation on this Android version)"
            refreshStats()
        }
    }

    fun batchDeleteLocal(hashes: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val photos = _allBackedUpPhotos.value.filter { it.contentHash in hashes }
            var freed = 0L
            photos.forEach { photo ->
                runCatching {
                    val del = app.contentResolver.delete(photo.contentUri(), null, null)
                    if (del > 0) freed += photo.sizeBytes
                }
            }
            if (freed > 0) _connectionResult.value = "Freed ${formatBytes(freed)}"
            refreshStats()
        }
    }

    fun deletePhotosFromBackup(hashes: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsRepo.settings.first()
            val app = getApplication<Application>()
            val client = TelegramClient(s.botToken)
            val photos = _allBackedUpPhotos.value.filter { it.contentHash in hashes }
            var deleted = 0
            photos.forEach { photo ->
                if (s.chatId.isNotBlank()) runCatching { client.deleteMessage(s.chatId, photo.messageId) }
                runCatching { dao.deleteByHash(photo.contentHash) }
                runCatching { app.contentResolver.delete(photo.contentUri(), null, null) }
                deleted++
            }
            _connectionResult.value = "Deleted $deleted photo${if (deleted != 1) "s" else ""} from backup"
            refreshStats()
        }
    }

    // ── Backup verification ───────────────────────────────────────────────────

    fun verifyBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val s      = settingsRepo.settings.first()
            val photos = dao.getAll()
            if (photos.isEmpty()) { _connectionResult.value = "Nothing backed up yet."; return@launch }

            _verifyResult.value = VerifyResult(running = true)
            val client = TelegramClient(s.botToken)
            var checked = 0; var broken = 0

            photos.forEach { photo ->
                val ok = client.fileExists(photo.fileId)
                checked++
                if (!ok) broken++
                _verifyResult.value = VerifyResult(checked, broken, running = true)
            }
            _verifyResult.value = VerifyResult(checked, broken, running = false)
            _connectionResult.value = if (broken == 0)
                "All $checked files verified ✓"
            else
                "$broken/$checked files are missing from Telegram!"
        }
    }

    // ── Self-update ───────────────────────────────────────────────────────────

    fun checkForUpdates(manual: Boolean = true) {
        val url = settings.value.updateUrl
        if (url.isBlank()) {
            if (manual) _connectionResult.value = "Enter an update URL in Settings → Updates first."
            return
        }
        if (_updateState.value.checking || _updateState.value.downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState(checking = true)
            val app = getApplication<Application>()
            val currentVersion = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
            }.getOrDefault("0")

            runCatching {
                val client = okhttp3.OkHttpClient.Builder()
                    .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val apiUrl = resolveApiUrl(url)
                val response = client.newCall(
                    okhttp3.Request.Builder().url(apiUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "TGPhotoBackup/$currentVersion")
                        .build()
                ).execute()

                if (response.code == 404) {
                    _updateState.value = UpdateState()
                    if (manual) _connectionResult.value =
                        "No releases on GitHub yet. Create a release and upload the APK first."
                    return@runCatching
                }
                if (!response.isSuccessful) {
                    throw Exception("GitHub returned HTTP ${response.code}")
                }

                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = org.json.JSONObject(body)
                val (remoteVersion, apkUrl) = if (isGithub(url)) {
                    val tag   = json.getString("tag_name").trimStart('v')
                    val assets = json.getJSONArray("assets")
                    val apk   = (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name").endsWith(".apk") }
                        ?: throw Exception("No APK asset found in this release")
                    tag to apk.getString("browser_download_url")
                } else {
                    val ver = json.optString("versionName",
                        json.optInt("versionCode", 0).toString())
                    ver to json.getString("apkUrl")
                }

                if (isNewer(remoteVersion, currentVersion)) {
                    _updateState.value = UpdateState(
                        available = true, versionName = remoteVersion, apkUrl = apkUrl)
                } else {
                    _updateState.value = UpdateState()
                    if (manual) _connectionResult.value =
                        "Already on latest version (v$currentVersion)"
                }
            }.onFailure {
                _updateState.value = UpdateState()
                if (manual) _connectionResult.value = "Update check failed: ${it.message}"
            }
        }
    }

    fun downloadAndInstall(ctx: Context) {
        val state = _updateState.value
        if (!state.available || state.apkUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = state.copy(downloading = true, progress = 0f)
            runCatching {
                val client = okhttp3.OkHttpClient.Builder()
                    .callTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                    .build()
                val response = client.newCall(
                    okhttp3.Request.Builder().url(state.apkUrl).build()
                ).execute()
                val body      = response.body ?: throw Exception("Empty response")
                val totalLen  = body.contentLength()
                val updateDir = java.io.File(ctx.cacheDir, "updates").also { it.mkdirs() }
                val apkFile   = java.io.File(updateDir, "update.apk")

                apkFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(8192)
                        var downloaded = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            if (totalLen > 0)
                                _updateState.value = _updateState.value.copy(
                                    progress = downloaded.toFloat() / totalLen)
                        }
                    }
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.provider", apkFile)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                _updateState.value = UpdateState()   // reset after install prompt shown
            }.onFailure {
                _updateState.value = state.copy(downloading = false, progress = 0f)
                _connectionResult.value = "Download failed: ${it.message}"
            }
        }
    }

    fun dismissUpdate() { _updateState.value = UpdateState() }

    private fun isGithub(url: String) = url.contains("github.com")

    private fun resolveApiUrl(url: String): String {
        if (!isGithub(url)) return url
        val m = Regex("github\\.com/([^/]+)/([^/#?]+)").find(url) ?: return url
        val (owner, repo) = m.destructured
        return "https://api.github.com/repos/$owner/${repo.trimEnd('/')}/releases/latest"
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trimStart('v').split(".")
            .mapNotNull { it.toIntOrNull() }
        val r = parts(remote); val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val diff = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun WorkInfo?.toStatus(): BackupStatus {
        if (this == null) return BackupStatus()
        val running  = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
        val data     = if (state.isFinished) outputData else progress
        val uploaded = outputData.getInt(BackupWorker.KEY_UPLOADED, 0)
        val skipped  = outputData.getInt(BackupWorker.KEY_SKIPPED, 0)
        val failed   = outputData.getInt(BackupWorker.KEY_FAILED, 0)
        val firstErr = outputData.getString(BackupWorker.KEY_ERROR)
        return BackupStatus(
            running          = running,
            done             = data.getInt(BackupWorker.KEY_DONE, 0),
            total            = data.getInt(BackupWorker.KEY_TOTAL, 0),
            currentName      = data.getString(BackupWorker.KEY_NAME).orEmpty(),
            speedBytesPerSec = data.getLong(BackupWorker.KEY_SPEED, 0),
            etaSeconds       = data.getLong(BackupWorker.KEY_ETA, 0),
            lastResult = if (state == WorkInfo.State.SUCCEEDED) buildString {
                append("$uploaded uploaded · $skipped skipped")
                if (failed > 0) append(" · $failed failed")
                if (firstErr != null) append("\nError: $firstErr")
            } else null,
            lastError = when {
                state == WorkInfo.State.FAILED -> "Backup failed. Check token & channel ID."
                state == WorkInfo.State.SUCCEEDED && failed > 0 && uploaded == 0 ->
                    firstErr?.let { "All uploads failed: $it" }
                        ?: "All files failed — is the bot admin in your channel?"
                else -> null
            }
        )
    }
}

// ─── Standalone helpers ───────────────────────────────────────────────────────

private fun saveToGallery(ctx: Context, name: String, mime: String, data: ByteArray): Boolean {
    val isVideo  = mime.startsWith("video/")
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else         MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else         MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) "Movies/TGBackup" else "Pictures/TGBackup")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = ctx.contentResolver.insert(collection, values) ?: return false
    return runCatching {
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(data) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, clear, null, null)
        }
        true
    }.getOrElse { ctx.contentResolver.delete(uri, null, null); false }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024            -> "$bytes B"
    bytes < 1024 * 1024     -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else                    -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}
