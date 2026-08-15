package com.texteditor.project.ui

import androidx.lifecycle.ViewModel
import com.texteditor.project.data.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel : ViewModel() {
    private val _themeState = MutableStateFlow(AppTheme.SYSTEM)
    val themeState: StateFlow<AppTheme> = _themeState

    private val _serverUrlState = MutableStateFlow("http://192.168.1.100:5000/execute/kotlin")
    val serverUrlState: StateFlow<String> = _serverUrlState

    fun setTheme(theme: AppTheme) {
        _themeState.value = theme
    }
}
