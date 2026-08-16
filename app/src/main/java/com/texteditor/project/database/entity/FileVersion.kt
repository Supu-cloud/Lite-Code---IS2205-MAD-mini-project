package com.texteditor.project.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_versions")
data class FileVersion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val versionName: String,
    val timestamp: Long,
    val deltaJson: String // Unified diff/patch stored as JSON string
)
