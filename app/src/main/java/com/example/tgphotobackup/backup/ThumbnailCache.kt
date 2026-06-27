package com.example.tgphotobackup.backup

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.File

object ThumbnailCache {
    private fun dir(context: Context) = File(context.filesDir, "thumbs").also { it.mkdirs() }
    fun file(context: Context, hash: String) = File(dir(context), "$hash.jpg")

    fun save(context: Context, mediaId: Long, mime: String, hash: String) {
        runCatching {
            val f = file(context, hash)
            if (f.exists()) return
            val isVideo = mime.startsWith("video/")
            val thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = if (isVideo)
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                else
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
                context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
            } else {
                @Suppress("DEPRECATION")
                if (isVideo)
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, null)
                else
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null)
            } ?: return
            f.outputStream().use { out -> thumb.compress(Bitmap.CompressFormat.JPEG, 80, out) }
        }
    }

    fun exists(context: Context, hash: String) = file(context, hash).exists()

    fun delete(context: Context, hash: String) = runCatching { file(context, hash).delete() }
}
