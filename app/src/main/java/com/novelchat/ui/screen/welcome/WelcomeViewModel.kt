package com.novelchat.ui.screen.welcome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.novelchat.ui.viewmodel.SetupFlowPolicy

class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = (application as NovelChatApp).settingsRepository

    private val _isApiConfigured = MutableStateFlow<Boolean?>(null)
    val isApiConfigured: StateFlow<Boolean?> = _isApiConfigured.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepo.apiBaseUrl,
                settingsRepo.apiKey,
                settingsRepo.model
            ) { url, key, model ->
                SetupFlowPolicy.canContinue(url, key, model)
            }.catch {
                emit(false)
            }.collect { configured ->
                _isApiConfigured.value = configured
            }
        }
    }
}
