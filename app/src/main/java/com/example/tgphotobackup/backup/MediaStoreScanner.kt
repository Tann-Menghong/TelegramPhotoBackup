package com.example.tgphotobackup.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

data class LocalPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val mime: String,
    val mediaType: MediaType,
    val bucketName: String = ""
)

enum class MediaType { IMAGE, VIDEO }

object MediaStoreScanner {

    fun queryAll(context: Context, includeVideos: Boolean = true): List<LocalPhoto> {
        val images = queryImages(context)
        return if (includeVideos) images + queryVideos(context) else images
    }

    /**
     * Enumerates image/video files from the given SAF tree URIs (folders the user
     * granted access to via OpenDocumentTree). Returns them as [LocalPhoto] so they
     * flow through the same backup pipeline as MediaStore media.
     */
    fun querySafFolders(
        context: Context,
        uris: Set<String>,
        includeVideos: Boolean
    ): List<LocalPhoto> {
        if (uris.isEmpty()) return emptyList()
        val out = mutableListOf<LocalPhoto>()
        uris.forEach { uriString ->
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return@runCatching
                val folderName = tree.name ?: ""
                collectSaf(tree, folderName, includeVideos, out)
            }
        }
        return out
    }

    private fun collectSaf(
        dir: DocumentFile,
        bucketName: String,
        includeVideos: Boolean,
        out: MutableList<LocalPhoto>
    ) {
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) {
                collectSaf(doc, doc.name ?: bucketName, includeVideos, out)
            } else if (doc.isFile) {
                val mime = doc.type ?: guessMime(doc.name)
                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")
                if (!isImage && !isVideo) return@forEach
                if (isVideo && !includeVideos) return@forEach
                val name = doc.name ?: "saf_${doc.uri.hashCode()}"
                out += LocalPhoto(
                    id           = doc.uri.toString().hashCode().toLong(),
                    uri          = doc.uri,
                    displayName  = name,
                    size         = doc.length(),
                    dateModified = doc.lastModified() / 1000L,
                    mime         = mime,
                    mediaType    = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    bucketName   = bucketName
                )
            }
        }
    }

    private fun guessMime(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: ""
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"         -> "image/png"
            "gif"         -> "image/gif"
            "webp"        -> "image/webp"
            "heic"        -> "image/heic"
            "mp4"         -> "video/mp4"
            "mkv"         -> "video/x-matroska"
            "mov"         -> "video/quicktime"
            "3gp"         -> "video/3gpp"
            else          -> "application/octet-stream"
        }
    }

    fun queryImages(context: Context): List<LocalPhoto> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return query(context, collection, MediaType.IMAGE)
    }

    fun queryVideos(context: Context): List<LocalPhoto> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return query(context, collection, MediaType.VIDEO)
    }

    private fun query(context: Context, collection: Uri, type: MediaType): List<LocalPhoto> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val out = mutableListOf<LocalPhoto>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol     = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol   = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol   = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol   = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol   = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val bucketCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += LocalPhoto(
                    id           = id,
                    uri          = ContentUris.withAppendedId(collection, id),
                    displayName  = c.getString(nameCol) ?: "media_$id",
                    size         = c.getLong(sizeCol),
                    dateModified = c.getLong(dateCol),
                    mime         = c.getString(mimeCol) ?: "application/octet-stream",
                    mediaType    = type,
                    bucketName   = if (bucketCol >= 0) c.getString(bucketCol) ?: "" else ""
                )
            }
        }
        return out
    }
}
