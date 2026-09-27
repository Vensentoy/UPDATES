package com.revyu.app.ui.widgets

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revyu.app.R
import com.revyu.app.core.theme.FolderCoral
import com.revyu.app.core.theme.HighlighterYellow
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.data.local.entities.HomeWidgetKeys
import com.revyu.app.data.local.entities.HomeWidgetPrefEntity
import com.revyu.app.data.local.entities.practiceWidgetKeys
import com.revyu.app.di.LocalAppContainer

data class WidgetKindInfo(
    val key: String,
    val label: String,
    val description: String
)

val WidgetKinds = listOf(
    WidgetKindInfo(HomeWidgetKeys.TODAY_SCHEDULE, "Today schedule", "Today's classes + study sessions"),
    WidgetKindInfo(HomeWidgetKeys.NEXT_CLASS, "Next class", "Your next remaining class and when"),
    WidgetKindInfo(HomeWidgetKeys.STUDY_PLAN, "Study plan", "Suggested review sessions this week"),
    WidgetKindInfo(HomeWidgetKeys.FLASHCARD, "Flashcard of the day", "One flashcard, rotated daily — tap to flip"),
    WidgetKindInfo(HomeWidgetKeys.QUIZ, "Question of the day", "One practice question from your sets"),
    WidgetKindInfo(HomeWidgetKeys.WEEK_OVERVIEW, "Week overview", "7-day dot-strip of your week"),
    WidgetKindInfo(HomeWidgetKeys.STUDY_MINUTES, "Study minutes", "Minutes of focused study planned today"),
    WidgetKindInfo(HomeWidgetKeys.SUBJECTS, "Subjects", "Quick count of your subjects"),
    WidgetKindInfo(HomeWidgetKeys.PRACTICE_SCORES, "Recent practice", "Your latest exam-mode scores"),
    WidgetKindInfo(HomeWidgetKeys.EXAM_COUNTDOWN, "Next exam", "Days until your next calendar exam"),
    WidgetKindInfo(HomeWidgetKeys.PRACTICE_READY, "Ready to practice", "Study sets you haven't quizzed yet")
)

fun kindInfoFor(key: String): WidgetKindInfo = WidgetKinds.first { it.key == key }

@Composable
fun infoKeyColor(key: String): Color = when (key) {
    HomeWidgetKeys.NEXT_CLASS -> PassGreen
    HomeWidgetKeys.STUDY_PLAN -> HighlighterYellow
    HomeWidgetKeys.WEEK_OVERVIEW -> FolderCoral
    HomeWidgetKeys.FLASHCARD -> FolderCoral
    HomeWidgetKeys.QUIZ -> PassGreen
    HomeWidgetKeys.PRACTICE_SCORES -> HighlighterYellow
    HomeWidgetKeys.EXAM_COUNTDOWN -> MaterialTheme.colorScheme.error
    HomeWidgetKeys.PRACTICE_READY -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/**
 * Card for one Home-grid widget. Shows widget title, description, and preview/toggle.
 */
@Composable
fun HomeWidgetCard(
    pref: HomeWidgetPrefEntity,
    showToggle: Boolean = false,
    onToggle: () -> Unit = {},
    showPreview: Boolean = true,
    fullWidthPreview: Boolean = false,
    pinContent: (@Composable () -> Unit)? = null,
    preview: @Composable () -> Unit = {}
) {
    val info = kindInfoFor(pref.key)
    com.revyu.app.ui.components.MarginRuleCard(
        accentColor = if (pref.enabled) infoKeyColor(pref.key) else MaterialTheme.colorScheme.outline
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showToggle) {
                    Switch(checked = pref.enabled, onCheckedChange = { onToggle() })
                }
            }
            if (pref.enabled) {
                pinContent?.invoke()
                if (showPreview) {
                    if (fullWidthPreview) {
                        preview()
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { preview() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The live, reorderable Home grid, rendered on the Smart Calendar (Home) tab. Uses its own
 * [WidgetsViewModel] scoped to the calling destination; the Widgets tab owns a second
 * instance — both write the same persisted prefs, so order/toggles stay in sync.
 */
@Composable
fun HomeGridSection(
    openStudySet: (String) -> Unit = {},
    openExam: (String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val viewModel: WidgetsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.revyu.app.di.viewModelFactory { WidgetsViewModel(container.widgetDataRepository) }
    )
    val prefs by viewModel.prefs.collectAsState()
    val enabled = prefs.filter { it.enabled }
    if (enabled.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LauncherAddWidgetsRow()
        Text(
            "Your widgets",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
        enabled.forEach { pref ->
            val isPractice = pref.key in practiceWidgetKeys
            HomeWidgetCard(
                pref = pref,
                fullWidthPreview = isPractice,
                preview = {
                    if (isPractice) {
                        PracticeWidgetPreview(key = pref.key, openStudySet = openStudySet, openExam = openExam)
                    } else {
                        WidgetPreview(key = pref.key)
                    }
                }
            )
        }
    }
}

/** One-tap "Add to home screen" chips for every launcher widget, on the Home tab. */
@Composable
private fun LauncherAddWidgetsRow() {
    val context = LocalContext.current
    val widgets = listOf(
        context.getString(R.string.practice_add_schedule) to
            ComponentName(context.packageName, "com.revyu.app.widget.RevyuWidgetProvider"),
        context.getString(R.string.practice_add_flashcard) to
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.FlashcardWidgetProvider"),
        context.getString(R.string.practice_add_question) to
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.QuestionWidgetProvider"),
        context.getString(R.string.practice_add_qna) to
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.QnAWidgetProvider"),
        context.getString(R.string.practice_add_exam) to
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.ExamWidgetProvider")
    )
    Column {
        Text(
            "Add to home screen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(widgets) { (label, component) ->
                SuggestionChip(
                    onClick = {
                        WidgetPinController.pin(
                            context,
                            component,
                            fallbackMessage = "Long-press a free spot on your home screen, then Widgets \u2192 $label."
                        )
                    },
                    label = { Text(label) }
                )
            }
        }
    }
}

/** Small live-data preview rendered inside each Home-grid card. */
@Composable
fun WidgetPreview(key: String) {
    val container = LocalAppContainer.current
    var text by remember(key) { mutableStateOf("…") }
    LaunchedEffect(key) {
        text = when (key) {
            HomeWidgetKeys.TODAY_SCHEDULE -> {
                val data = container.widgetDataRepository.todayScheduleData()
                if (data.rows.isEmpty()) data.emptyText
                else data.rows.take(2).joinToString(" | ") { "${it.timeText} ${it.title}" }
            }
            HomeWidgetKeys.NEXT_CLASS -> {
                val d = container.widgetDataRepository.nextClassData()
                if (d.hasNext) "${d.title} · ${d.dayLabel}" else com.revyu.app.core.util.MotivationalMessages.noClassMessage()
            }
            HomeWidgetKeys.STUDY_PLAN -> {
                val d = container.widgetDataRepository.studyMinutesData()
                "${d.plannedToday} min suggested today"
            }
            HomeWidgetKeys.WEEK_OVERVIEW -> {
                val d = container.widgetDataRepository.weekData()
                d.days.joinToString(" ") { cell -> "${cell.label}:${cell.eventCount}" }
            }
            HomeWidgetKeys.STUDY_MINUTES -> {
                val d = container.widgetDataRepository.studyMinutesData()
                "${d.plannedToday} min planned · ${d.completedToday} done"
            }
            else -> {
                val count = container.subjectRepository.countSubjects()
                "$count subjects"
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(220.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}