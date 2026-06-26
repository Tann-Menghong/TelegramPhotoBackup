package com.example.tgphotobackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_runs")
data class BackupRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long,
    val total: Int,
    val uploaded: Int,
    val skipped: Int,
    val failed: Int,
    val oversized: Int
)
