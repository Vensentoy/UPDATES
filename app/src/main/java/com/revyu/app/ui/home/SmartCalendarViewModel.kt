package com.revyu.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.calendar.CalendarEvent
import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.AppSettingsStore
import com.revyu.app.data.repository.CalendarEventRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DaySchedule(
    val day: DayOfWeek,
    val classes: List<CalendarEvent>,
    val suggestions: List<CalendarEvent>,
    val reminders: List<CalendarEvent> = emptyList()
) {
    val totalCount: Int get() = classes.size + suggestions.size + reminders.size
    val studyMinutes: Int get() = suggestions.sumOf { it.endMinute - it.startMinute }
}

data class SmartCalendarUiState(
    val isLoading: Boolean = true,
    val hasSchedule: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val selectedDay: DayOfWeek = LocalDate.now().dayOfWeek,
    val days: List<DaySchedule> = emptyList(),
    val settings: AppSettings = AppSettings()
)

class SmartCalendarViewModel(
    private val calendarEventRepository: CalendarEventRepository,
    private val appSettingsStore: AppSettingsStore,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now(clock).dayOfWeek)

    val uiState: StateFlow<SmartCalendarUiState> = combine(
        calendarEventRepository.observeAll(),
        appSettingsStore.settings,
        selectedDay
    ) { events, settings, day ->
        val today = LocalDate.now(clock)
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = DayOfWeek.values()
            .map { d ->
                val dateForDay = startOfWeek.plusDays((d.value - 1).toLong())
                DaySchedule(
                    day = d,
                    classes = events.filter { it.dayOfWeek == d && it.type == CalendarEventType.CLASS },
                    suggestions = events.filter {
                        it.dayOfWeek == d &&
                            (it.type == CalendarEventType.SUGGESTED_STUDY || it.type == CalendarEventType.REVIEW)
                    },
                    reminders = events.filter {
                        it.dayOfWeek == d &&
                            (it.type == CalendarEventType.REMINDER || it.type == CalendarEventType.EXAM) &&
                            (it.eventDate == null || it.eventDate == dateForDay)
                    }
                )
            }
        SmartCalendarUiState(
            isLoading = false,
            hasSchedule = events.isNotEmpty(),
            today = LocalDate.now(clock),
            selectedDay = day,
            days = days,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmartCalendarUiState())

    fun selectDay(day: DayOfWeek) {
        selectedDay.value = day
    }

    suspend fun regenerateSuggestions() {
        calendarEventRepository.regenerateSuggestions(uiState.value.settings.schedulerConfig())
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        calendarEventRepository.delete(event)
    }
}