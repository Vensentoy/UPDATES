package com.revyu.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun StudyLoadScreen(onImported: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: StudyLoadViewModel = viewModel(
        factory = viewModelFactory {
            StudyLoadViewModel(context, container.fileTextExtractor, container.subjectRepository, container.settingsRepository)
        }
    )

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 24.dp)
    ) {
        Text("Your Study Load", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Upload the file with your subjects and schedule. We'll turn each subject into a folder for your Study Sets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when (val state = viewModel.state) {
            is StudyLoadUiState.Picking -> {
                EmptyState(
                    title = "No file selected yet",
                    body = "PDF, DOCX, TXT, or MD — anything listing your subjects with their days and times.",
                    action = {
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = "Choose file",
                            onClick = { filePicker.launch(arrayOf("*/*")) }
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.startManualEntry() }) {
                            Text("Or add subjects manually")
                        }
                    }
                )
            }

            is StudyLoadUiState.Parsing -> {
                LoadingState("Reading your Study Load…")
            }

            is StudyLoadUiState.Error -> {
                ErrorBanner(state.message)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(text = "Try another file", onClick = { viewModel.retry() })
                    SecondaryButton(text = "Add manually", onClick = { viewModel.startManualEntry() })
                }
            }

            is StudyLoadUiState.Reviewing -> {
                Text(
                    "Check that these look right — you can edit anything before saving.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        SubjectRowEditor(
                            row = row,
                            onNameChange = { viewModel.updateRowName(row.id, it) },
                            onScheduleChange = { viewModel.updateRowSchedule(row.id, it) },
                            onRemove = { viewModel.removeRow(row.id) }
                        )
                    }
                    item {
                        TextButton(onClick = { viewModel.addRow() }) {
                            Text("+ Add another subject")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Save and continue",
                    onClick = { viewModel.confirm(onImported) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is StudyLoadUiState.Saving -> {
                LoadingState("Setting up your subjects…")
            }
        }
    }
}

@Composable
private fun SubjectRowEditor(
    row: EditableSubjectRow,
    onNameChange: (String) -> Unit,
    onScheduleChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
                label = { Text("Subject name") },
                singleLine = true
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove subject")
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = row.scheduleText,
            onValueChange = onScheduleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Schedule") },
            placeholder = { Text("MWF 9:00 AM-10:00 AM") },
            singleLine = true
        )
    }
}
