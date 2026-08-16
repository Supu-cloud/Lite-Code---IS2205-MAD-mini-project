package com.texteditor.project.database.dao

import androidx.room.*
import com.texteditor.project.database.entity.RecentFile
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessed DESC LIMIT 20")
    fun getAllRecentFiles(): Flow<List<RecentFile>>

    @Upsert
    suspend fun upsertRecentFile(file: RecentFile)

    @Delete
    suspend fun deleteRecentFile(file: RecentFile)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}
