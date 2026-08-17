package com.texteditor.project.database.dao

import androidx.room.*
import com.texteditor.project.database.entity.FileVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface FileVersionDao {
    @Query("SELECT * FROM file_versions WHERE fileUri = :uri ORDER BY timestamp DESC")
    fun getVersionsForFile(uri: String): Flow<List<FileVersion>>

    @Insert
    suspend fun insertVersion(version: FileVersion)

    @Delete
    suspend fun deleteVersion(version: FileVersion)
}
