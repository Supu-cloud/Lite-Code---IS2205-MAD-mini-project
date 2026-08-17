package com.texteditor.project.data

data class CompileResponse(
    val success: Boolean = false,
    val output: String = "",
    val error: String = ""
)
