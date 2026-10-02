package com.revyu.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.revyu.app.core.theme.HighlighterYellow
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

@Composable
fun HomeScreen(
    onOpenSubject: (String) -> Unit,
    onTakeExamMode: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { HomeViewModel(container.subjectRepository, container.smartCalendarRepository) }
    )
    val state by viewModel.uiState.collectAsState()

    HomeContent(
        state = state,
        onOpenSubject = onOpenSubject,
        onTakeExamMode = onTakeExamMode,
        onOpenSettings = onOpenSettings,
        onOpenVault = onOpenVault
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenSubject: (String) -> Unit,
    onTakeExamMode: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Revyu", style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = onOpenVault) {
                    Icon(Icons.Filled.AutoStories, contentDescription = "Semester Vault")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Key, contentDescription = "API key settings")
                }
            }
        }

        if (state.subjects.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "No subjects yet",
                body = "Import your Study Load to get started."
            )
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
                        "Smart Calendar",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(state.pendingTasks, key = { "task_${it.studySet.id}" }) { task ->
                    PendingTaskCard(task = task, onClick = { onTakeExamMode(task.studySet.id) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item {
                Text(
                    "Your Subjects",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(state.subjects, key = { it.subject.id }) { entry ->
                SubjectFolderCard(entry = entry, onClick = { onOpenSubject(entry.subject.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PendingTaskCard(task: PendingReviewTask, onClick: () -> Unit) {
    MarginRuleCard(accentColor = HighlighterYellow, onClick = onClick) {
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
private fun SubjectFolderCard(entry: SubjectWithSchedule, onClick: () -> Unit) {
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RevyuTheme {
        HomeContent(
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
            onOpenSubject = {},
            onTakeExamMode = {},
            onOpenSettings = {},
            onOpenVault = {}
        )
    }
}
