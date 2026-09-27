package com.revyu.app.ui.vault

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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.data.local.entities.SubjectEntity
import com.revyu.app.data.repository.SubjectWithSchedule
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.subjectAccentColor
import com.revyu.app.ui.home.HomeUiState
import com.revyu.app.ui.home.HomeViewModel

@Composable
fun SemesterVaultSubjectsScreen(
    onBack: () -> Unit,
    onSubjectSelected: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { HomeViewModel(container.subjectRepository, container.smartCalendarRepository) }
    )
    val state by viewModel.uiState.collectAsState()

    SemesterVaultSubjectsContent(
        state = state,
        onBack = onBack,
        onSubjectSelected = onSubjectSelected
    )
}

@Composable
fun SemesterVaultSubjectsContent(
    state: HomeUiState,
    onBack: () -> Unit,
    onSubjectSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Semester Vault", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Pick a subject to build a comprehensive Midterm or Final Study Set from what you've already generated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (state.subjects.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "No subjects yet",
                body = "Import your Study Load and generate at least one Study Set first."
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.subjects, key = { it.subject.id }) { entry: SubjectWithSchedule ->
                MarginRuleCard(
                    accentColor = subjectAccentColor(entry.subject.accentIndex),
                    onClick = { onSubjectSelected(entry.subject.id) }
                ) {
                    Text(entry.subject.name, style = MaterialTheme.typography.titleMedium)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SemesterVaultSubjectsPreview() {
    RevyuTheme {
        SemesterVaultSubjectsContent(
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
            onBack = {},
            onSubjectSelected = {}
        )
    }
}
