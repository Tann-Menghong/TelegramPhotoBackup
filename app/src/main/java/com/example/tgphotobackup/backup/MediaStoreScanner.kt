package com.example.tgphotobackup.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

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
