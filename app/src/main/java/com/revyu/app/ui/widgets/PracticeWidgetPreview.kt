package com.revyu.app.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.core.preferences.PracticeWidgetPins
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.core.widget.DailyFlashcardData
import com.revyu.app.core.widget.DailyQuestionData
import com.revyu.app.core.widget.ExamCountdownRow
import com.revyu.app.core.widget.PinOption
import com.revyu.app.core.widget.PracticeReadyRow
import com.revyu.app.core.widget.RecentScoreRow
import com.revyu.app.data.local.entities.HomeWidgetKeys
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.di.LocalAppContainer
import kotlinx.coroutines.launch

/** Interactive, full-width previews for the practice Home widgets. */
@Composable
fun PracticeWidgetPreview(
    key: String,
    openStudySet: (String) -> Unit = {},
    openExam: (String) -> Unit = {}
) {
    when (key) {
        HomeWidgetKeys.FLASHCARD -> FlashcardOfDayPreview(openStudySet)
        HomeWidgetKeys.QUIZ -> QuestionOfDayPreview(openExam)
        HomeWidgetKeys.PRACTICE_SCORES -> RecentScoresPreview(openStudySet)
        HomeWidgetKeys.EXAM_COUNTDOWN -> NextExamPreview()
        HomeWidgetKeys.PRACTICE_READY -> PracticeReadyPreview(openStudySet)
    }
}

/** Shared full-width card body for practice widgets. */
@Composable
private fun PracticeSurface(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PracticeSectionHeader(subjectName: String, setTitle: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            subjectName,
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
        Text(
            setTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlashcardOfDayPreview(openStudySet: (String) -> Unit) {
    val container = LocalAppContainer.current
    val pins by container.appSettingsStore.practiceWidgetPins.collectAsState(initial = PracticeWidgetPins())
    var data by remember { mutableStateOf<DailyFlashcardData?>(null) }
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(pins.flashcardStudySetId) {
        data = container.widgetDataRepository.dailyFlashcard(pins.flashcardStudySetId)
        flipped = false
    }

    val card = data
    PracticeSurface(onClick = { flipped = !flipped }) {
        if (card == null) {
            Text(
                "No flashcards yet — generate a study set to get a daily card.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            PracticeSectionHeader(card.subjectName, card.setTitle, com.revyu.app.core.theme.FolderCoral)
            Text(
                if (flipped) card.back else card.front,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (flipped) "Tap to show front" else "Tap to reveal answer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { openStudySet(card.studySetId) }, modifier = Modifier.align(Alignment.End)) {
                Text("Open flashcards")
            }
        }
    }
}

@Composable
private fun QuestionOfDayPreview(openExam: (String) -> Unit) {
    val container = LocalAppContainer.current
    val pins by container.appSettingsStore.practiceWidgetPins.collectAsState(initial = PracticeWidgetPins())
    var data by remember { mutableStateOf<DailyQuestionData?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pins.questionStudySetId) {
        data = container.widgetDataRepository.dailyQuestion(pins.questionStudySetId)
        selected = null
    }

    val question = data
    PracticeSurface {
        if (question == null) {
            Text(
                "No practice questions yet — generate a study set to get a daily question.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            PracticeSectionHeader(question.subjectName, question.setTitle, com.revyu.app.core.theme.PassGreen)
            Text(question.prompt, style = MaterialTheme.typography.titleMedium)

            val options = if (question.options.isNotEmpty()) question.options
            else if (question.type == QuestionType.TRUE_FALSE) listOf("True", "False")
            else emptyList()

            if (options.isNotEmpty()) {
                options.forEach { option ->
                    val isSelected = selected == option
                    val correct = option in question.correctAnswers
                    val verdict = when {
                        !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
                        correct -> com.revyu.app.core.theme.PassGreen
                        else -> MaterialTheme.colorScheme.error
                    }
                    Surface(
                        color = if (!isSelected) MaterialTheme.colorScheme.surface
                        else verdict.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = option }
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (selected != null && selected !in question.correctAnswers) {
                    Text(
                        "Correct answer: ${question.correctAnswers.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    if (selected == null) "Tap to reveal the answer" else "Answer: ${question.correctAnswers.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = selected ?: "" }
                )
            }

            TextButton(onClick = { openExam(question.studySetId) }, modifier = Modifier.align(Alignment.End)) {
                Text("Practice mode")
            }
        }
    }
}

@Composable
private fun RecentScoresPreview(openStudySet: (String) -> Unit) {
    val container = LocalAppContainer.current
    var rows by remember { mutableStateOf<List<RecentScoreRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        rows = container.widgetDataRepository.recentPracticeScores(limit = 5)
    }

    PracticeSurface {
        if (rows.isEmpty()) {
            Text(
                "No practice scores yet — finish an exam mode to see results here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openStudySet(row.studySetId) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${row.subjectName} · ${row.setTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${row.scorePercentage.toInt()}% · ${if (row.daysAgo == 0L) "Today" else "${row.daysAgo}d ago"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NextExamPreview() {
    val container = LocalAppContainer.current
    var countdown by remember { mutableStateOf<ExamCountdownRow?>(null) }
    LaunchedEffect(Unit) {
        countdown = container.widgetDataRepository.nextExamCountdown()
    }

    PracticeSurface {
        val next = countdown
        if (next == null) {
            Text(
                "No exams on your calendar yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                if (next.daysUntil == 0) "Today"
                else if (next.daysUntil == 1) "Tomorrow"
                else "${next.daysUntil} days",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                if (next.atMinute != null) "${next.title} · ${DateTimeUtils.formatTime(next.atMinute)}"
                else next.title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PracticeReadyPreview(openStudySet: (String) -> Unit) {
    val container = LocalAppContainer.current
    var rows by remember { mutableStateOf<List<PracticeReadyRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        rows = container.widgetDataRepository.practiceReadySubjects()
    }

    PracticeSurface {
        if (rows.isEmpty()) {
            Text(
                "All your study sets have been practiced. Nice!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openStudySet(row.firstStudySetId) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.subjectName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${row.setCount} set${if (row.setCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "Choose study set" pin row + dialog for the flashcard and question widgets. */
@Composable
fun PinnedSetPicker(widgetKey: String) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val pins by container.appSettingsStore.practiceWidgetPins.collectAsState(initial = PracticeWidgetPins())
    var options by remember { mutableStateOf<List<PinOption>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { options = container.widgetDataRepository.pinOptions() }

    val current = when (widgetKey) {
        HomeWidgetKeys.FLASHCARD -> pins.flashcardStudySetId
        else -> pins.questionStudySetId
    }

    fun select(studySetId: String?) {
        scope.launch {
            when (widgetKey) {
                HomeWidgetKeys.FLASHCARD -> container.appSettingsStore.setFlashcardPin(studySetId)
                else -> container.appSettingsStore.setQuestionPin(studySetId)
            }
        }
        open = false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Pinned:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { open = true }) {
            Text(
                options.firstOrNull { it.studySetId == current }
                    ?.let { "${it.subjectName} · ${it.setTitle}" }
                    ?: "All study sets"
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose a study set") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item(key = "all") {
                        Text(
                            "All study sets",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select(null) }
                                .padding(vertical = 10.dp)
                        )
                    }
                    items(options, key = { it.studySetId }) { option ->
                        Text(
                            "${option.subjectName} · ${option.setTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select(option.studySetId) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

@Preview(showBackground = true, name = "Flashcard Widget Preview")
@Composable
private fun FlashcardWidgetSamplePreview() {
    RevyuTheme {
        PracticeSurface {
            PracticeSectionHeader("COMPUTER SCIENCE", "CS101 · Flashcards", com.revyu.app.core.theme.FolderCoral)
            Text(
                "What is the time complexity of binary search?",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Tap to reveal answer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {}, modifier = Modifier.align(Alignment.End)) {
                Text("Open flashcards")
            }
        }
    }
}

@Preview(showBackground = true, name = "Question Widget Preview")
@Composable
private fun QuestionWidgetSamplePreview() {
    RevyuTheme {
        PracticeSurface {
            PracticeSectionHeader("MATHEMATICS", "MATH201 · Daily Question", com.revyu.app.core.theme.PassGreen)
            Text("What is the derivative of sin(x)?", style = MaterialTheme.typography.titleMedium)

            val options = listOf("cos(x)", "-cos(x)", "tan(x)", "-sin(x)")
            options.forEachIndexed { index, option ->
                val isSelected = index == 0
                Surface(
                    color = if (!isSelected) MaterialTheme.colorScheme.surface
                    else com.revyu.app.core.theme.PassGreen.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            TextButton(onClick = {}, modifier = Modifier.align(Alignment.End)) {
                Text("Practice mode")
            }
        }
    }
}

@Preview(showBackground = true, name = "Next Exam Widget Preview")
@Composable
private fun NextExamWidgetSamplePreview() {
    RevyuTheme {
        PracticeSurface {
            Text(
                "10 days",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                "Final Examinations · Industrial Organization and Management",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}