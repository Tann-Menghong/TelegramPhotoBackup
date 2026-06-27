package com.example.tgphotobackup.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uploaded_photos")
data class UploadedPhoto(
    @PrimaryKey val contentHash: String,
    val mediaId: Long,
    val displayName: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val messageId: Long,
    val fileId: String,
    val uploadedAt: Long,
    val mimeType: String = "image/jpeg",
    val bucketName: String = "",
    @ColumnInfo(defaultValue = "NULL") val chunkGroup: String? = null,
    @ColumnInfo(defaultValue = "0")    val chunkIndex: Int = 0,
    @ColumnInfo(defaultValue = "1")    val totalChunks: Int = 1
)
