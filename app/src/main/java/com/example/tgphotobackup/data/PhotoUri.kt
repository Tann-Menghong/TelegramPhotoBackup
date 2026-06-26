package com.example.tgphotobackup.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * Reconstructs the on-device content URI from the stored mediaId and mimeType.
 * Returns null if we can't determine the collection (shouldn't happen in practice).
 */
fun UploadedPhoto.contentUri(): Uri {
    val base = if (mimeType.startsWith("video/")) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    return ContentUris.withAppendedId(base, mediaId)
}

fun UploadedPhoto.isVideo(): Boolean = mimeType.startsWith("video/")
