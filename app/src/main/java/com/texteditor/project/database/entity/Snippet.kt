package com.texteditor.project.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val code: String,
    val language: String // e.g., Kotlin, HTML, CSS, JS
)
