package com.texteditor.project.database.dao

import androidx.room.*
import com.texteditor.project.database.entity.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY title ASC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE language = :lang ORDER BY title ASC")
    fun getSnippetsByLanguage(lang: String): Flow<List<Snippet>>

    @Upsert
    suspend fun upsertSnippet(snippet: Snippet)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)
}
