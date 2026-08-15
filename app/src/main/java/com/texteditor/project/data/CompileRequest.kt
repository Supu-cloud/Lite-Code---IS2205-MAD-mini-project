package com.texteditor.project.data

data class CompileRequest(
    val code: String,
    val stdin: String,
    val ext: String = "kt"
)
