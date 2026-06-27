package com.example.tgphotobackup.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FailedUploadDao {
    @Query("SELECT * FROM failed_uploads ORDER BY failedAt DESC")
    suspend fun getAll(): List<FailedUpload>

    @Query("SELECT COUNT(*) FROM failed_uploads")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FailedUpload)

    @Delete
    suspend fun delete(item: FailedUpload)

    @Query("DELETE FROM failed_uploads WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM failed_uploads")
    suspend fun clear()
}
