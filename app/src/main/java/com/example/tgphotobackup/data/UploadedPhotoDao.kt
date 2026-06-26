package com.example.tgphotobackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UploadedPhotoDao {

    @Query("SELECT * FROM uploaded_photos WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): UploadedPhoto?

    @Query(
        "SELECT * FROM uploaded_photos " +
        "WHERE mediaId = :mediaId AND sizeBytes = :size AND dateModified = :dateModified LIMIT 1"
    )
    suspend fun findBySignature(mediaId: Long, size: Long, dateModified: Long): UploadedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: UploadedPhoto)

    @Query("SELECT COUNT(*) FROM uploaded_photos")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM uploaded_photos")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM uploaded_photos ORDER BY uploadedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<UploadedPhoto>

    @Query("SELECT * FROM uploaded_photos ORDER BY uploadedAt DESC")
    suspend fun getAll(): List<UploadedPhoto>

    @Query("SELECT DISTINCT bucketName FROM uploaded_photos WHERE bucketName != '' ORDER BY bucketName")
    suspend fun getDistinctBuckets(): List<String>

    @Query("SELECT * FROM uploaded_photos WHERE bucketName = :bucket ORDER BY uploadedAt DESC")
    suspend fun getByBucket(bucket: String): List<UploadedPhoto>

    @Query("DELETE FROM uploaded_photos WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)
}
