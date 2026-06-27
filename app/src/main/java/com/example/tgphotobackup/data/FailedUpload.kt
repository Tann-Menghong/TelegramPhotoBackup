package com.example.tgphotobackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "failed_uploads")
data class FailedUpload(
    @PrimaryKey val mediaId: Long,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String = "image/jpeg",
    val bucketName: String = "",
    val errorMessage: String,
    val failedAt: Long = System.currentTimeMillis()
)
