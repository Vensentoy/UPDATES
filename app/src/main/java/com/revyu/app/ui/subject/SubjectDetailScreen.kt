package com.revyu.app.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.core.theme.HighlighterYellow
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.StudySetKind
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.subjectAccentColor
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@Composable
fun SubjectDetailScreen(
    subjectId: String,
    onBack: () -> Unit,
    onCreateStudySet: () -> Unit,
    onOpenStudySet: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: SubjectDetailViewModel = viewModel(
        factory = viewModelFactory {
            SubjectDetailViewModel(subjectId, container.subjectRepository, container.studySetRepository)
        }
    )
    val state by viewModel.uiState.collectAsState()

    var showDeleteSubjectDialog by remember { mutableStateOf(false) }
    var studySetPendingDelete by remember { mutableStateOf<StudySetEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateStudySet) {
                Icon(Icons.Filled.Add, contentDescription = "Create Study Set")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    state.subject?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                if (state.subject != null) {
                    IconButton(onClick = { showDeleteSubjectDialog = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete subject")
                    }
                }
            }

            if (state.studySets.isEmpty() && !state.isLoading) {
                EmptyState(
                    title = "No Study Sets yet",
                    body = "Tap + to upload material and generate your first reviewer, flashcards, and questions."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.studySets, key = { it.id }) { studySet ->
                        StudySetCard(
                            studySet = studySet,
                            accentIndex = state.subject?.accentIndex ?: 0,
                            onClick = { onOpenStudySet(studySet.id) },
                            onDeleteClick = { studySetPendingDelete = studySet }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) } // clears the FAB
                }
            }
        }
    }

    if (showDeleteSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSubjectDialog = false },
            title = { Text("Delete ${state.subject?.name.orEmpty()}?") },
            text = { Text("This removes the subject and every Study Set under it — reviewers, flashcards, questions, and results. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSubjectDialog = false
                    viewModel.deleteSubject(onDeleted = onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSubjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    studySetPendingDelete?.let { studySet ->
        AlertDialog(
            onDismissRequest = { studySetPendingDelete = null },
            title = { Text("Delete this Study Set?") },
            text = { Text("\"${studySet.title}\" and its reviewer, flashcards, questions, and results will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStudySet(studySet)
                    studySetPendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { studySetPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudySetCard(
    studySet: StudySetEntity,
    accentIndex: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formatter = remember(studySet.id) {
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
    }
    MarginRuleCard(accentColor = subjectAccentColor(accentIndex), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                if (studySet.kind != StudySetKind.REGULAR) {
                    Box(
                        modifier = Modifier
                            .background(HighlighterYellow.copy(alpha = 0.22f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (studySet.kind == StudySetKind.MIDTERM_VAULT) "SEMESTER VAULT · MIDTERM" else "SEMESTER VAULT · FINAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(studySet.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatter.format(studySet.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    statusLabel(studySet),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (studySet.kind != StudySetKind.REGULAR && studySet.sourceStudySetIds.isNotEmpty()) {
                    Text(
                        "Built from ${studySet.sourceStudySetIds.size} Study Set${if (studySet.sourceStudySetIds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.height(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete Study Set",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusLabel(studySet: StudySetEntity): String = when (studySet.generationStatus) {
    GenerationStatus.PENDING -> "Waiting to generate"
    GenerationStatus.GENERATING -> "Generating…"
    GenerationStatus.FAILED -> "Generation failed"
    GenerationStatus.READY -> when (studySet.examStatus) {
        ExamStatus.NOT_TAKEN -> "Exam Mode: Not taken"
        ExamStatus.IN_PROGRESS -> "Exam Mode: In progress"
        ExamStatus.COMPLETED -> "Exam Mode: Completed"
    }
}
