package com.texteditor.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.texteditor.project.ui.AppInfoScreen
import com.texteditor.project.ui.EditorScreen
import com.texteditor.project.ui.SplashScreen
import com.texteditor.project.ui.ThemeViewModel
import com.texteditor.project.ui.theme.Text_EditorTheme
import com.texteditor.project.util.ThemePreferences

class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(ThemePreferences(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val appTheme by themeViewModel.themeState.collectAsState()

            Text_EditorTheme(appTheme = appTheme) {
                var currentScreen by remember { mutableStateOf("splash") }

                when (currentScreen) {
                    "splash" -> SplashScreen(onAnimationFinished = { currentScreen = "editor" })
                    "editor" -> EditorScreen(
                        themeViewModel = themeViewModel,
                        onShowInfo = { currentScreen = "info" }
                    )
                    "info" -> AppInfoScreen(onBack = { currentScreen = "editor" })
                }
            }
        }
    }
}
