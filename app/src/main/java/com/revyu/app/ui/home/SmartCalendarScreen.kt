package com.revyu.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.calendar.CalendarEvent
import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.theme.FolderCoral
import com.revyu.app.core.theme.HighlighterYellow
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.widgets.HomeGridSection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SmartCalendarScreen(
    onImportStudyLoad: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenStudySet: (String) -> Unit = {},
    onStartExamMode: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val viewModel: SmartCalendarViewModel = viewModel(
        factory = viewModelFactory {
            SmartCalendarViewModel(
                calendarEventRepository = container.calendarEventRepository,
                appSettingsStore = container.appSettingsStore
            )
        }
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var regenerating by remember { mutableStateOf(false) }

    SmartCalendarContent(
        state = state,
        regenerating = regenerating,
        onRegenerate = {
            regenerating = true
            scope.launch {
                viewModel.regenerateSuggestions()
                regenerating = false
            }
        },
        onSelectDay = viewModel::selectDay,
        onDeleteEvent = { event -> scope.launch { viewModel.deleteEvent(event) } },
        onImportStudyLoad = onImportStudyLoad,
        onOpenSubject = onOpenSubject,
        onOpenStudySet = onOpenStudySet,
        onStartExamMode = onStartExamMode,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun SmartCalendarContent(
    state: SmartCalendarUiState,
    regenerating: Boolean,
    onRegenerate: () -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    onImportStudyLoad: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenStudySet: (String) -> Unit,
    onStartExamMode: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        com.revyu.app.ui.components.RevyuTopHeader(
            headerDrawableRes = com.revyu.app.R.drawable.revyu_header,
            onOpenSettings = onOpenSettings,
            settings = state.settings
        )

        val startOfWeek = state.today.minusDays((state.today.dayOfWeek.value - 1).toLong())
        val selectedDate = startOfWeek.plusDays((state.selectedDay.value - 1).toLong())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.hasSchedule) {
                IconButton(onClick = onRegenerate, enabled = !regenerating) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerate study suggestions")
                }
            }
        }

        WeekStrip(
            selectedDay = state.selectedDay,
            days = state.days,
            todayDate = state.today,
            onSelect = onSelectDay,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        if (!state.hasSchedule && !state.isLoading) {
            EmptyState(
                title = "No schedule yet",
                body = "Import your LLCC Study Load PDF so Revyu can lay out your week and suggest study slots.",
                modifier = Modifier.padding(horizontal = 24.dp),
                action = {
                    PrimaryButton(
                        text = "Import study load",
                        onClick = onImportStudyLoad,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
            return@Column
        }

        if (state.isLoading) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        val selected = state.days.firstOrNull { it.day == state.selectedDay }
        val classes = selected?.classes ?: emptyList()
        val suggestions = selected?.suggestions ?: emptyList()
        val reminders = selected?.reminders ?: emptyList()

        val isWeekend = com.revyu.app.core.util.MotivationalMessages.isWeekend(selectedDate)
        val noClassText = com.revyu.app.core.util.MotivationalMessages.noClassMessage(selectedDate)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedDayTitle(state.selectedDay, state.today),
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (selected != null && selected.studyMinutes > 0) {
                        Text(
                            "${selected.studyMinutes} min suggested",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (classes.isEmpty() && suggestions.isEmpty() && reminders.isEmpty()) {
                item {
                    EmptyState(
                        title = if (isWeekend) "Happy Weekend!" else "No Classes Today",
                        body = noClassText
                    )
                }
            } else if (classes.isEmpty() && suggestions.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = noClassText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            if (reminders.isNotEmpty()) {
                item {
                    SectionLabel("School Events & Reminders")
                }
                items(reminders.size, key = { index -> "rem_${reminders[index].id}_$index" }) { index ->
                    val event = reminders[index]
                    EventCard(
                        event = event,
                        accent = HighlighterYellow,
                        onClick = { event.subjectId?.let(onOpenSubject) }
                    )
                }
            }

            if (classes.isNotEmpty()) {
                item {
                    SectionLabel("Classes")
                }
                items(classes.size, key = { index -> "cls_${classes[index].id}_$index" }) { index ->
                    val event = classes[index]
                    EventCard(event, accent = MaterialTheme.colorScheme.primary, onClick = {
                        event.subjectId?.let(onOpenSubject)
                    })
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    SectionLabel("Suggested study")
                }
                items(suggestions.size, key = { index -> "sug_${suggestions[index].id}_$index" }) { index ->
                    val event = suggestions[index]
                    EventCard(
                        event = event,
                        accent = if (event.type == CalendarEventType.REVIEW) HighlighterYellow else PassGreen,
                        onClick = { event.subjectId?.let(onOpenSubject) },
                        onDelete = { onDeleteEvent(event) }
                    )
                }
                item {
                    Text(
                        "Suggestions regenerate automatically when your schedule or settings change.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                HomeGridSection(openStudySet = onOpenStudySet, openExam = onStartExamMode)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WeekStrip(
    selectedDay: DayOfWeek,
    days: List<DaySchedule>,
    todayDate: LocalDate,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    val startOfWeek = todayDate.minusDays((todayDate.dayOfWeek.value - 1).toLong())

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
    ) {
        items(DayOfWeek.values().toList(), key = { it.value }) { day ->
            val dayDate = startOfWeek.plusDays((day.value - 1).toLong())
            val isSelected = day == selectedDay
            val isToday = day == todayDate.dayOfWeek
            Surface(
                modifier = Modifier
                    .width(60.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else if (isToday) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelect(day) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, Locale.US),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${dayDate.dayOfMonth}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    accent: Color,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    MarginRuleCard(accentColor = accent, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.subjectName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(DateTimeUtils.formatTimeRange(event.startMinute, event.endMinute))
                        if (!event.room.isNullOrBlank()) append(" · ${event.room}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.type == CalendarEventType.SUGGESTED_STUDY ||
                event.type == CalendarEventType.REVIEW
            ) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Text("✕", style = MaterialTheme.typography.titleSmall, color = FolderCoral)
                    }
                }
            }
        }
    }
}

private fun selectedDayTitle(day: DayOfWeek, today: java.time.LocalDate): String {
    val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val date = startOfWeek.plusDays((day.value - 1).toLong())
    val dateStr = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    return when (day) {
        today.dayOfWeek -> "Today · ${day.getDisplayName(TextStyle.FULL, Locale.US)}, $dateStr"
        today.plusDays(1).dayOfWeek -> "Tomorrow · ${day.getDisplayName(TextStyle.FULL, Locale.US)}, $dateStr"
        else -> "${day.getDisplayName(TextStyle.FULL, Locale.US)}, $dateStr"
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartCalendarPreview() {
    RevyuTheme {
        SmartCalendarContent(
            state = SmartCalendarUiState(
                hasSchedule = true,
                isLoading = false,
                selectedDay = DayOfWeek.MONDAY,
                today = LocalDate.now(),
                days = listOf(
                    DaySchedule(
                        day = DayOfWeek.MONDAY,
                        classes = listOf(
                            CalendarEvent(
                                id = "1",
                                subjectName = "Computer Science 101",
                                dayOfWeek = DayOfWeek.MONDAY,
                                startMinute = 540,
                                endMinute = 630,
                                type = CalendarEventType.CLASS
                            )
                        ),
                        suggestions = emptyList()
                    )
                )
            ),
            regenerating = false,
            onRegenerate = {},
            onSelectDay = {},
            onDeleteEvent = {},
            onImportStudyLoad = {},
            onOpenSubject = {},
            onOpenStudySet = {},
            onStartExamMode = {}
        )
    }
}