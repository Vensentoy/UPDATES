package com.revyu.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class ApiKeySetupViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    var apiKeyInput by mutableStateOf("")
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onInputChange(value: String) {
        apiKeyInput = value
        errorMessage = null
    }

    fun save(onSaved: () -> Unit) {
        val trimmed = apiKeyInput.trim()
        if (trimmed.length < 20 || !trimmed.startsWith("sk-or-")) {
            errorMessage = "That doesn't look like an OpenRouter key — it should start with \"sk-or-\"."
            return
        }
        viewModelScope.launch {
            isSaving = true
            settingsRepository.saveApiKey(trimmed)
            isSaving = false
            onSaved()
        }
    }
}
