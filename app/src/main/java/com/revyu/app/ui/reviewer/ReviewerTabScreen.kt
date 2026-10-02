package com.revyu.app.ui.reviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.core.theme.FolderCoral
import com.revyu.app.core.theme.HighlighterYellow
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.PendingReviewTask
import com.revyu.app.data.repository.SubjectWithStudySets
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton
import com.revyu.app.ui.components.subjectAccentColor

@Composable
fun ReviewerTabScreen(
    onImportStudyLoad: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onCreateStudySet: (String) -> Unit,
    onStartExamMode: (String) -> Unit,
    onImportSchoolCalendar: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = com.revyu.app.core.preferences.AppSettings())
    val viewModel: ReviewerTabViewModel = viewModel(
        factory = viewModelFactory {
            ReviewerTabViewModel(
                subjectsWithStudySetsFlow = container.subjectRepository.observeSubjectsWithStudySets(),
                smartCalendarRepository = container.smartCalendarRepository
            )
        }
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            if (state.subjects.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    val first = state.subjects.firstOrNull()
                    if (first != null) onOpenSubject(first.subject.id)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Reviewer")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(padding)
        ) {
            com.revyu.app.ui.components.RevyuTopHeader(
                headerDrawableRes = com.revyu.app.R.drawable.reviewer_header,
                onOpenSettings = onOpenSettings,
                settings = settings
            )

            if (state.subjects.isEmpty() && !state.isLoading) {
                Column(modifier = Modifier.fillMaxSize()) {
                    EmptyState(
                        title = "Start with your study load",
                        body = "Import your LLCC Study Load PDF to build subjects, then generate AI reviewers, flashcards, and exams.",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        action = {
                            PrimaryButton(
                                text = "Import study load",
                                onClick = onImportStudyLoad,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.pendingTasks.isNotEmpty()) {
                    item {
                        Text(
                            "Exam pending",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(state.pendingTasks, key = { "task_${it.studySet.id}" }) { task ->
                        PendingExamCard(
                            task = task,
                            onStartExam = { onStartExamMode(task.studySet.id) }
                        )
                    }
                }

                item {
                    Text(
                        "Your subjects",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(state.subjects, key = { it.subject.id }) { entry ->
                    SubjectReviewCard(
                        entry = entry,
                        onTapSubject = { onOpenSubject(entry.subject.id) },
                        onCreateStudySet = { onCreateStudySet(entry.subject.id) },
                        onStartExam = {
                            val readySet = entry.studySets.firstOrNull {
                                it.generationStatus == GenerationStatus.READY &&
                                    it.examStatus != ExamStatus.COMPLETED
                            }
                            if (readySet != null) onStartExamMode(readySet.id)
                        }
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PendingExamCard(task: PendingReviewTask, onStartExam: () -> Unit) {
    MarginRuleCard(accentColor = HighlighterYellow, onClick = onStartExam) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.subject.name, style = MaterialTheme.typography.titleMedium)
            val whenText = when (task.daysUntilClass) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> "In ${task.daysUntilClass} days"
            }
            Text(
                "$whenText — Exam Mode not yet taken",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubjectReviewCard(
    entry: SubjectWithStudySets,
    onTapSubject: () -> Unit,
    onCreateStudySet: () -> Unit,
    onStartExam: () -> Unit
) {
    val accent = subjectAccentColor(entry.subject.accentIndex)
    val summary = StudySetStatusSummary.from(entry.studySets)
    val canStartExam = summary.ready > 0 && summary.examPending > 0
    MarginRuleCard(accentColor = accent, onClick = onTapSubject) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.subject.name, style = MaterialTheme.typography.titleMedium)
                    if (entry.schedule.isNotEmpty()) {
                        val schedText = entry.schedule.joinToString(" · ") {
                            "${DateTimeUtils.dayLabel(it.dayOfWeek)} ${DateTimeUtils.formatTime(it.startMinuteOfDay)}"
                        }
                        Text(
                            schedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (entry.subject.subjectCode != null) {
                    Text(
                        entry.subject.subjectCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (entry.studySets.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(summary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Create Reviewer",
                    onClick = onCreateStudySet,
                    modifier = Modifier.height(36.dp)
                )
                if (canStartExam) {
                    PrimaryButton(
                        text = "Start Exam",
                        onClick = onStartExam,
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(summary: StudySetStatusSummary) {
    val color = when {
        summary.failed > 0 -> FolderCoral
        summary.generating > 0 -> HighlighterYellow
        summary.ready > 0 -> PassGreen
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            summary.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}