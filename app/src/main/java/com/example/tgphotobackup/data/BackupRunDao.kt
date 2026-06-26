package com.example.tgphotobackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BackupRunDao {
    @Insert
    suspend fun insert(run: BackupRun): Long

    @Query("SELECT * FROM backup_runs ORDER BY startedAt DESC LIMIT 20")
    suspend fun recent(): List<BackupRun>
}
