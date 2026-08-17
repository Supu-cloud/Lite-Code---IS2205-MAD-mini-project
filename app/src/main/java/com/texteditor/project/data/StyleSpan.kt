package com.texteditor.project.data

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class StyleSpan(
    val start: Int,
    val end: Int,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val fontSize: Float? = null,
    val fontFamily: String? = null,
    val color: String? = null,
    val underline: Boolean = false
)
