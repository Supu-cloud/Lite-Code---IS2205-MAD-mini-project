package com.texteditor.project.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.texteditor.project.database.dao.FileVersionDao
import com.texteditor.project.database.dao.RecentFileDao
import com.texteditor.project.database.dao.SnippetDao
import com.texteditor.project.database.entity.FileVersion
import com.texteditor.project.database.entity.RecentFile
import com.texteditor.project.database.entity.Snippet

@Database(entities = [RecentFile::class, Snippet::class, FileVersion::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun snippetDao(): SnippetDao
    abstract fun fileVersionDao(): FileVersionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "litecode_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
