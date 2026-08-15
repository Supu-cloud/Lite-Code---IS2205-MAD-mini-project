package com.texteditor.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun EditorScreen(themeViewModel: ThemeViewModel, onShowInfo: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "LiteCode Project Skeleton - Ready for Module Integration")
    }
}
