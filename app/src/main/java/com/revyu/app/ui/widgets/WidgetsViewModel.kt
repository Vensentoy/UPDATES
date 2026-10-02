package com.revyu.app.ui.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.HomeWidgetKeys
import com.revyu.app.data.local.entities.HomeWidgetPrefEntity
import com.revyu.app.data.local.entities.defaultHomeWidgetPrefs
import com.revyu.app.data.repository.WidgetDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WidgetsViewModel(
    private val widgetDataRepository: WidgetDataRepository
) : ViewModel() {

    val prefs: StateFlow<List<HomeWidgetPrefEntity>> = widgetDataRepository.observeHomeWidgetPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultHomeWidgetPrefs())

    fun toggleEnabled(key: String) {
        val current = prefs.value
        viewModelScope.launch {
            widgetDataRepository.updateHomeWidgetPrefs(
                current.map {
                    if (it.key == key) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    /** Restore the default grid after the user has hidden everything. */
    fun resetDefaults() {
        viewModelScope.launch {
            widgetDataRepository.updateHomeWidgetPrefs(defaultHomeWidgetPrefs())
        }
    }
}