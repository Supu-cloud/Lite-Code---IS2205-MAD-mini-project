package com.texteditor.project.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texteditor.project.data.AppTheme
import com.texteditor.project.util.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val themePreferences: ThemePreferences) : ViewModel() {
    val themeState: StateFlow<AppTheme> = themePreferences.themeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM
        )

    val serverUrlState: StateFlow<String> = themePreferences.serverUrlFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "http://192.168.1.100:5000/execute/kotlin"
        )

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            themePreferences.saveTheme(theme)
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            themePreferences.saveServerUrl(url)
        }
    }
}
