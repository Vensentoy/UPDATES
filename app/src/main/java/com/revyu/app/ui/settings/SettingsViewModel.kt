package com.revyu.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.AppSettingsStore
import com.revyu.app.core.preferences.ThemeMode
import com.revyu.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettingsStore: AppSettingsStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = appSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val hasApiKey: StateFlow<Boolean> = settingsRepository.hasApiKey
    val username: StateFlow<String> = settingsRepository.username
    val isLlccian: StateFlow<Boolean> = settingsRepository.isLlccian

    fun resyncLlccCalendar(schoolCalendarRepository: com.revyu.app.data.repository.SchoolCalendarRepository, onDone: () -> Unit) {
        viewModelScope.launch {
            schoolCalendarRepository.importParsedCalendar(com.revyu.app.core.util.schoolcalendar.LLCCCalendarData.getParseReport())
            onDone()
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepository.logout()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettingsStore.setThemeMode(mode) }
    }

    fun setAccentColor(accent: com.revyu.app.core.preferences.AccentColor, context: android.content.Context) {
        viewModelScope.launch {
            appSettingsStore.setAccentColor(accent)
            com.revyu.app.work.WorkScheduler.refreshWidgetsNow(context, force = true)
        }
    }

    fun setAutoStudySuggestions(enabled: Boolean) {
        viewModelScope.launch { appSettingsStore.setAutoStudySuggestions(enabled) }
    }

    fun setStudyBlockMinutes(minutes: Int) {
        viewModelScope.launch { appSettingsStore.setStudyBlockMinutes(minutes) }
    }

    fun setReviewBlockMinutes(minutes: Int) {
        viewModelScope.launch { appSettingsStore.setReviewBlockMinutes(minutes) }
    }

    fun setStartHour(hour: Int) {
        viewModelScope.launch { appSettingsStore.setPreferredStartHour(hour) }
    }

    fun setEndHour(hour: Int) {
        viewModelScope.launch { appSettingsStore.setPreferredEndHour(hour) }
    }

    fun setWeekendStudy(enabled: Boolean) {
        viewModelScope.launch { appSettingsStore.setWeekendStudy(enabled) }
    }

    fun setMaxMinutesPerDay(minutes: Int) {
        viewModelScope.launch { appSettingsStore.setMaxSuggestedMinutesPerDay(minutes) }
    }

    fun setWidgetUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsStore.setWidgetUpdatesEnabled(enabled) }
    }
}