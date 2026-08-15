package com.texteditor.project.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel : ViewModel() {
    private val _serverUrlState = MutableStateFlow("http://192.168.1.100:5000/execute/kotlin")
    val serverUrlState: StateFlow<String> = _serverUrlState
}
