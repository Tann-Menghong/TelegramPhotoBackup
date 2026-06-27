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
    private val failedDao = AppDatabase.get(context).failedUploadDao()
    private val settingsRepo = SettingsRepository(context)

    companion object {
        // 19 MB per chunk — fits within both the 50 MB upload AND 20 MB download Bot API limits
        const val CHUNK_SIZE = 19L * 1024 * 1024
        // Absolute ceiling — files larger than this are reported as oversized and skipped
        private const val ABSOLUTE_MAX = 4L * 1024 * 1024 * 1024
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
                skipped++; return@forEachIndexed
            }
            if (photo.size > ABSOLUTE_MAX) { oversized++; return@forEachIndexed }

            try {
                val hash = computeHash(photo.uri) ?: throw IllegalStateException("Cannot open ${photo.uri}")

                if (hash in sessionHashes || dao.findByHash(hash) != null) {
                    skipped++; return@forEachIndexed
                }

                if (photo.size <= CHUNK_SIZE) {
                    // ── Single-file upload ──────────────────────────────
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
                } else {
                    // ── Chunked upload for large files ──────────────────
                    val totalChunks = ((photo.size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                    // Collect all results before inserting to DB — if any chunk fails we insert nothing
                    data class ChunkResult(val index: Int, val chunkSize: Long, val res: TelegramClient.SendResult)
                    val chunkResults = mutableListOf<ChunkResult>()

                    for (chunkIdx in 0 until totalChunks) {
                        currentCoroutineContext().ensureActive()
                        val chunkStart = chunkIdx.toLong() * CHUNK_SIZE
                        val chunkSize  = minOf(CHUNK_SIZE, photo.size - chunkStart)
                        val chunkName  = "${photo.displayName} [${chunkIdx + 1}/$totalChunks]"

                        onProgress(index, media.size, chunkName, speedBps, etaSec)

                        val res = client.sendDocument(
                            chatId   = settings.chatId,
                            fileName = chunkName,
                            mime     = photo.mime,
                            length   = chunkSize,
                            caption  = chunkName
                        ) {
                            val stream = context.contentResolver.openInputStream(photo.uri)
                                ?: throw IllegalStateException("Cannot open ${photo.uri}")
                            stream.skip(chunkStart)
                            LimitedInputStream(stream, chunkSize)
                        }
                        chunkResults.add(ChunkResult(chunkIdx, chunkSize, res))
                    }

                    // All chunks uploaded — commit to DB atomically
                    val now = System.currentTimeMillis()
                    for (cr in chunkResults) {
                        dao.insert(UploadedPhoto(
                            // Chunk 0 uses the real hash (dedup anchor); others get a unique derived key
                            contentHash  = if (cr.index == 0) hash else "${hash}_c${cr.index}",
                            mediaId      = if (cr.index == 0) photo.id else 0L,
                            displayName  = photo.displayName,
                            // Chunk 0 stores full file size so Gallery shows the right value
                            sizeBytes    = if (cr.index == 0) photo.size else 0L,
                            dateModified = photo.dateModified,
                            messageId    = cr.res.messageId,
                            fileId       = cr.res.fileId,
                            uploadedAt   = now,
                            mimeType     = photo.mime,
                            bucketName   = photo.bucketName,
                            chunkGroup   = hash,
                            chunkIndex   = cr.index,
                            totalChunks  = totalChunks
                        ))
                    }
                }

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

    /** Reads at most [limit] bytes from [source], then signals EOF. */
    private class LimitedInputStream(
        private val source: InputStream,
        private val limit: Long
    ) : InputStream() {
        private var remaining = limit
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = source.read()
            if (b >= 0) remaining--
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
        override fun close() = source.close()
    }
}
