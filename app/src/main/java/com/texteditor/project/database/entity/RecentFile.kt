package com.texteditor.project.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val uri: String,
    val fileName: String,
    val lastAccessed: Long,
    val mode: String // KOTLIN, WEB, TEXT
)
