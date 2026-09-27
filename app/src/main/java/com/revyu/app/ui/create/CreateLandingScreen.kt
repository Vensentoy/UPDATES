package com.revyu.app.ui.create

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.theme.FolderCoral
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.data.local.entities.SubjectEntity
import com.revyu.app.data.repository.PendingReviewTask
import com.revyu.app.data.repository.SubjectWithSchedule
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.subjectAccentColor
import com.revyu.app.ui.home.HomeUiState
import com.revyu.app.ui.home.HomeViewModel

@Composable
fun CreateLandingScreen(
    onImportStudyLoad: () -> Unit,
    onImportSchoolCalendar: () -> Unit = {},
    onOpenSubject: (String) -> Unit,
    onTakeExamMode: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            HomeViewModel(container.subjectRepository, container.smartCalendarRepository)
        }
    )
    val state by viewModel.uiState.collectAsState()

    CreateLandingContent(
        state = state,
        onImportStudyLoad = onImportStudyLoad,
        onOpenSubject = onOpenSubject,
        onTakeExamMode = onTakeExamMode
    )
}

@Composable
fun CreateLandingContent(
    state: HomeUiState,
    onImportStudyLoad: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onTakeExamMode: (String) -> Unit,
    onImportSchoolCalendar: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Text(
            "Create",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        if (state.subjects.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "Start with your study load",
                body = "Import your LLCC Study Load PDF to build everything from it — subjects, schedules, and study suggestions.",
                action = {
                    com.revyu.app.ui.components.PrimaryButton(
                        text = "Import study load",
                        onClick = onImportStudyLoad,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                }
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ImportCard(
                    count = state.subjects.size,
                    onClick = onImportStudyLoad,
                    note = "Smart Calendar reads your PDF and plans your week automatically."
                )
            }

            if (state.pendingTasks.isNotEmpty()) {
                item { SectionTitle("Exam Mode pending") }
                items(state.pendingTasks, key = { "task_${it.studySet.id}" }) { task ->
                    PendingExamCard(task = task, onClick = { onTakeExamMode(task.studySet.id) })
                }
            }

            item { SectionTitle("Your subjects") }
            items(state.subjects, key = { it.subject.id }) { entry ->
                SubjectRow(entry = entry, onClick = { onOpenSubject(entry.subject.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ImportCard(count: Int, note: String, onClick: () -> Unit, title: String? = null) {
    MarginRuleCard(
        accentColor = if (count == 0) FolderCoral else PassGreen,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title ?: (if (count == 0) "Import your study load" else "Import another study load"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PendingExamCard(task: PendingReviewTask, onClick: () -> Unit) {
    MarginRuleCard(accentColor = androidx.compose.ui.graphics.Color(0xFFF4C430), onClick = onClick) {
        Column {
            Text(task.subject.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            val whenText = when (task.daysUntilClass) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> "In ${task.daysUntilClass} days"
            }
            Text(
                "$whenText — Exam Mode not taken yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubjectRow(entry: SubjectWithSchedule, onClick: () -> Unit) {
    val accent = subjectAccentColor(entry.subject.accentIndex)
    MarginRuleCard(accentColor = accent, onClick = onClick) {
        Column {
            Text(entry.subject.name, style = MaterialTheme.typography.titleMedium)
            if (entry.schedule.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                val summary = entry.schedule.joinToString(" · ") {
                    "${DateTimeUtils.dayLabel(it.dayOfWeek)} ${DateTimeUtils.formatTime(it.startMinuteOfDay)}"
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.subject.subjectCode != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.subject.subjectCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateLandingScreenPreview() {
    RevyuTheme {
        CreateLandingContent(
            state = HomeUiState(
                subjects = listOf(
                    SubjectWithSchedule(
                        subject = SubjectEntity(id = "1", name = "Computer Science 101", subjectCode = "CS101", accentIndex = 0),
                        schedule = emptyList()
                    ),
                    SubjectWithSchedule(
                        subject = SubjectEntity(id = "2", name = "Mathematics 201", subjectCode = "MATH201", accentIndex = 1),
                        schedule = emptyList()
                    )
                ),
                isLoading = false
            ),
            onImportStudyLoad = {},
            onImportSchoolCalendar = {},
            onOpenSubject = {},
            onTakeExamMode = {}
        )
    }
}