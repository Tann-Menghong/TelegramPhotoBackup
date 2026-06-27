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

    // Count unique files only (exclude non-primary chunks)
    @Query("SELECT COUNT(*) FROM uploaded_photos WHERE chunkGroup IS NULL OR chunkIndex = 0")
    suspend fun count(): Int

    // Sum sizes of primary entries only (chunk 0 stores the full file size)
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM uploaded_photos WHERE chunkGroup IS NULL OR chunkIndex = 0")
    suspend fun totalBytes(): Long

    // Gallery / History: show primary entries only
    @Query("SELECT * FROM uploaded_photos WHERE (chunkGroup IS NULL OR chunkIndex = 0) ORDER BY uploadedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<UploadedPhoto>

    @Query("SELECT * FROM uploaded_photos WHERE chunkGroup IS NULL OR chunkIndex = 0 ORDER BY uploadedAt DESC")
    suspend fun getAll(): List<UploadedPhoto>

    @Query("SELECT DISTINCT bucketName FROM uploaded_photos WHERE bucketName != '' ORDER BY bucketName")
    suspend fun getDistinctBuckets(): List<String>

    @Query("SELECT * FROM uploaded_photos WHERE bucketName = :bucket AND (chunkGroup IS NULL OR chunkIndex = 0) ORDER BY uploadedAt DESC")
    suspend fun getByBucket(bucket: String): List<UploadedPhoto>

    @Query("DELETE FROM uploaded_photos WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM uploaded_photos WHERE contentHash = :hash")
    suspend fun deleteByHash(hash: String)

    // Retrieve all chunks of a multi-part file in order
    @Query("SELECT * FROM uploaded_photos WHERE chunkGroup = :group ORDER BY chunkIndex ASC")
    suspend fun findChunks(group: String): List<UploadedPhoto>
}
