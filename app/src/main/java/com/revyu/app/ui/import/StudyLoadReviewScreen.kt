package com.revyu.app.ui.import

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.revyu.app.core.navigation.RevyuDestinations
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.core.util.studyload.StudyLoadFileValidation
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLoadReviewScreen(
    navController: NavHostController,
    onImported: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: StudyLoadImportViewModel = rememberImportViewModel(navController)
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Review & save") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        val review = state as? StudyLoadImportUiState.Review
        if (review == null) {
            Text(
                "No import in progress.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        if (review.warnings.isNotEmpty()) {
            ErrorBanner(
                message = buildString {
                    append(StudyLoadFileValidation.PARTIAL_PARSE_MESSAGE)
                    append(" ")
                    append(review.warnings.joinToString(" "))
                },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(review.subjects, key = { _, s -> s.original.subjectCode ?: s.original.name }) { index, subject ->
                ReviewSubjectCard(
                    subject = subject,
                    onChangeName = { viewModel.setName(index, it) },
                    onChangeUnits = { viewModel.setUnits(index, it) },
                    onToggleIncluded = { viewModel.toggleIncluded(index) }
                )
            }

            val selectedCount = review.subjects.count { it.included }
            item {
                PrimaryButton(
                    text = "Save $selectedCount subject(s)",
                    onClick = { viewModel.save { onImported() } },
                    enabled = selectedCount > 0,
                    loading = state is StudyLoadImportUiState.Saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReviewSubjectCard(
    subject: ReviewableSubject,
    onChangeName: (String) -> Unit,
    onChangeUnits: (Int?) -> Unit,
    onToggleIncluded: () -> Unit
) {
    val accent = if (subject.included) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    MarginRuleCard(accentColor = accent) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(checked = subject.included, onCheckedChange = { onToggleIncluded() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subject.original.subjectCode ?: "Subject",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (subject.original.catalogCode != null) {
                        Text(
                            subject.original.catalogCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (subject.original.units != null) {
                    Text(
                        "${subject.original.units} units",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = subject.name,
                onValueChange = onChangeName,
                label = { Text("Subject name") },
                singleLine = true,
                enabled = subject.included,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            if (subject.sessions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                subject.sessions.forEach { session ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            DateTimeUtils.dayLabel(session.day.value, short = false),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            DateTimeUtils.formatTimeRange(session.startMinute, session.endMinute) +
                                (session.room?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}