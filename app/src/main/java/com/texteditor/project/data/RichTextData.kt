package com.texteditor.project.data

data class RichTextData(
    val content: String,
    val styleSpans: List<StyleSpan> = emptyList(),
    val baseColorHex: String = "",
    val defaultFontSize: Float = 16f,
    val defaultFontFamily: String = "Default"
)
