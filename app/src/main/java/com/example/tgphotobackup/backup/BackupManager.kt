package com.example.tgphotobackup.backup

import android.content.Context
import com.example.tgphotobackup.data.AppDatabase
import com.example.tgphotobackup.data.FailedUpload
import com.example.tgphotobackup.data.SettingsRepository
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.telegram.TelegramClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class BackupSummary(
    val total: Int,
    val uploaded: Int,
    val skipped: Int,
    val failed: Int,
    val oversized: Int,
    val firstError: String? = null
)

class BackupManager(private val context: Context) {

    private val dao = AppDatabase.get(context).uploadedPhotoDao()
    private val failedDao = AppDatabase.get(context).failedUploadDao()
    private val settingsRepo = SettingsRepository(context)

    companion object {
        // Telegram Bot API upload limit
        private const val MAX_FILE_BYTES = 50L * 1024 * 1024
    }

    suspend fun backup(
        includeVideos: Boolean = true,
        onProgress: suspend (done: Int, total: Int, name: String, speedBps: Long, etaSec: Long) -> Unit
    ): BackupSummary {
        val settings = settingsRepo.settings.first()
        check(settings.isConfigured) { "Bot token / chat id not set" }

        val client = TelegramClient(settings.botToken)

        val mediaStoreMedia = MediaStoreScanner.queryAll(context, includeVideos)
            .filter { it.size > 0 }
            .filter { settings.includedAlbums.isEmpty() || it.bucketName in settings.includedAlbums }

        val safMedia = MediaStoreScanner
            .querySafFolders(context, settings.safFolderUris, includeVideos)
            .filter { it.size > 0 }

        val media = (mediaStoreMedia + safMedia)
            .distinctBy { it.uri }
            .sortedBy { it.dateModified }

        var uploaded   = 0
        var skipped    = 0
        var failed     = 0
        var oversized  = 0
        var firstError: String? = null
        val sessionHashes = HashSet<String>()

        var bytesUploaded = 0L
        val startMs = System.currentTimeMillis()

        media.forEachIndexed { index, photo ->
            currentCoroutineContext().ensureActive()

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
            val speedBps  = bytesUploaded * 1000L / elapsedMs
            val remaining = (media.size - index).toLong()
            val avgSize   = if (uploaded > 0) bytesUploaded / uploaded else 200_000L
            val etaSec    = if (speedBps > 0) avgSize * remaining / speedBps else 0L

            onProgress(index, media.size, photo.displayName, speedBps, etaSec)

            if (dao.findBySignature(photo.id, photo.size, photo.dateModified) != null) {
                runCatching { failedDao.deleteByMediaId(photo.id) }
                skipped++; return@forEachIndexed
            }
            if (photo.size > MAX_FILE_BYTES) {
                runCatching { failedDao.deleteByMediaId(photo.id) }
                oversized++; return@forEachIndexed
            }

            try {
                val hash = computeHash(photo.uri) ?: throw IllegalStateException("Cannot open ${photo.uri}")

                if (hash in sessionHashes || dao.findByHash(hash) != null) {
                    runCatching { failedDao.deleteByMediaId(photo.id) }
                    skipped++; return@forEachIndexed
                }

                val res = client.sendDocument(
                    chatId   = settings.chatId,
                    fileName = photo.displayName,
                    mime     = photo.mime,
                    length   = photo.size,
                    caption  = photo.displayName
                ) {
                    context.contentResolver.openInputStream(photo.uri)
                        ?: throw IllegalStateException("Cannot open ${photo.uri}")
                }
                dao.insert(UploadedPhoto(
                    contentHash  = hash,
                    mediaId      = photo.id,
                    displayName  = photo.displayName,
                    sizeBytes    = photo.size,
                    dateModified = photo.dateModified,
                    messageId    = res.messageId,
                    fileId       = res.fileId,
                    uploadedAt   = System.currentTimeMillis(),
                    mimeType     = photo.mime,
                    bucketName   = photo.bucketName
                ))

                sessionHashes.add(hash)
                bytesUploaded += photo.size
                uploaded++

                runCatching { failedDao.deleteByMediaId(photo.id) }
                ThumbnailCache.save(context, photo.id, photo.mime, hash)

                if (settings.autoDeleteAfterBackup) {
                    runCatching { context.contentResolver.delete(photo.uri, null, null) }
                }

            } catch (e: Exception) {
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                failed++
                runCatching {
                    failedDao.insert(FailedUpload(
                        mediaId      = photo.id,
                        displayName  = photo.displayName,
                        sizeBytes    = photo.size,
                        mimeType     = photo.mime,
                        bucketName   = photo.bucketName,
                        errorMessage = e.message ?: e.javaClass.simpleName
                    ))
                }
            }
        }

        // Remove stale failure records for files no longer on device (can never be retried)
        val scannedIds = media.map { it.id }.toSet()
        runCatching {
            failedDao.getAll().filter { it.mediaId !in scannedIds }
                .forEach { failedDao.deleteByMediaId(it.mediaId) }
        }

        onProgress(media.size, media.size, "Done", 0L, 0L)
        return BackupSummary(media.size, uploaded, skipped, failed, oversized, firstError)
    }

    private fun computeHash(uri: android.net.Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(65_536)
            var n = stream.read(buf)
            while (n >= 0) { md.update(buf, 0, n); n = stream.read(buf) }
            md.digest().joinToString("") { "%02x".format(it) }
        }

}
