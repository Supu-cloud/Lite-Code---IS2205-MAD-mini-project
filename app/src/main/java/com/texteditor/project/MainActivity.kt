package com.texteditor.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.texteditor.project.ui.EditorScreen
import com.texteditor.project.ui.ThemeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ThemeViewModel = viewModel()
            EditorScreen(themeViewModel = viewModel, onShowInfo = {})
        }
    }
}
