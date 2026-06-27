package com.example.tgphotobackup.backup

import android.content.Context
import com.example.tgphotobackup.data.AppDatabase
import com.example.tgphotobackup.data.SettingsRepository
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.telegram.TelegramClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.InputStream
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
    private val settingsRepo = SettingsRepository(context)

    suspend fun backup(
        includeVideos: Boolean = true,
        onProgress: suspend (done: Int, total: Int, name: String, speedBps: Long, etaSec: Long) -> Unit
    ): BackupSummary {
        val settings = settingsRepo.settings.first()
        check(settings.isConfigured) { "Bot token / chat id not set" }

        val client   = TelegramClient(settings.botToken)
        val maxBytes = settings.maxFileSizeBytes

        // Filter by selected albums (empty set = all albums)
        val media = MediaStoreScanner.queryAll(context, includeVideos)
            .filter { it.size > 0 }
            .filter {
                settings.includedAlbums.isEmpty() || it.bucketName in settings.includedAlbums
            }
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
                skipped++; return@forEachIndexed
            }
            if (photo.size > maxBytes) { oversized++; return@forEachIndexed }

            try {
                // Compute hash before uploading so duplicates are caught without sending the file.
                val hash = context.contentResolver.openInputStream(photo.uri)?.use { stream ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(65_536)
                    var n = stream.read(buf)
                    while (n >= 0) { md.update(buf, 0, n); n = stream.read(buf) }
                    md.digest().joinToString("") { "%02x".format(it) }
                } ?: throw IllegalStateException("Cannot open ${photo.uri}")

                if (hash in sessionHashes || dao.findByHash(hash) != null) {
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

                sessionHashes.add(hash)
                dao.insert(
                    UploadedPhoto(
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
                    )
                )
                bytesUploaded += photo.size
                uploaded++

                // Auto-delete local copy after successful upload
                if (settings.autoDeleteAfterBackup) {
                    runCatching { context.contentResolver.delete(photo.uri, null, null) }
                }

            } catch (e: Exception) {
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                failed++
            }
        }

        onProgress(media.size, media.size, "Done", 0L, 0L)
        return BackupSummary(media.size, uploaded, skipped, failed, oversized, firstError)
    }
}
