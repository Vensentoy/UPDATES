package com.revyu.app.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revyu.app.core.util.ExtractionStatus
import com.revyu.app.data.local.entities.StudyMaterialEntity
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.InfoBanner
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun UploadMaterialScreen(
    viewModel: CreateStudySetViewModel,
    onNext: () -> Unit,
    onOpenExistingStudySet: (String) -> Unit = {}
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadMaterial(it) }
    }
    val isOnline by viewModel.isOnline.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            WizardStepHeader(step = 1, total = 4, title = "Upload study material")
            Spacer(Modifier.height(4.dp))
            Text(
                "PDF, DOCX, PPTX, XLSX, TXT, or MD for ${viewModel.subjectName}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (!isOnline) {
                                InfoBanner(message = "You are offline. Files are read on your device. PowerPoint files use the built-in reader instead of the conversion server.")
                Spacer(Modifier.height(12.dp))
            }

            viewModel.uploadWarning?.let { warning ->
                InfoBanner(message = warning)
                Spacer(Modifier.height(12.dp))
            }

            when (val upload = viewModel.uploadState) {
                is UploadUiState.Uploading -> LoadingState(
                    when (viewModel.uploadStatus) {
                        ExtractionStatus.Reading -> "Reading your file…"
                        ExtractionStatus.WakingServer -> "Waking up the conversion server. This can take up to a minute…"
                        ExtractionStatus.Converting -> "Converting your PowerPoint…"
                    }
                )
                is UploadUiState.ExistingStudySetsFound -> {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.dismissUploadState()
                        },
                        title = { Text("Existing Study Sets") },
                        text = {
                            Text("You already have ${upload.count} ${if (upload.count == 1) "Study Set" else "Study Sets"} from this file.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.dismissUploadState()
                                onNext()
                            }) {
                                Text("Create a new variation", maxLines = 1)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                onOpenExistingStudySet(upload.latestStudySetId)
                            }) {
                                Text("Open latest", maxLines = 1)
                            }
                        }
                    )

                    PrimaryButton(
                        text = "Choose file",
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    viewModel.selectedMaterial?.let { selected ->
                        Spacer(Modifier.height(16.dp))
                        Text("Selected", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        MaterialRow(material = selected, isSelected = true, onClick = {})
                    }
                }
                is UploadUiState.Failed -> {
                    ErrorBanner(upload.message)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(text = "Choose a different file", onClick = { filePicker.launch(arrayOf("*/*")) })
                }
                is UploadUiState.Idle -> {
                    PrimaryButton(
                        text = "Choose file",
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    viewModel.selectedMaterial?.let { selected ->
                        Spacer(Modifier.height(16.dp))
                        Text("Selected", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        MaterialRow(material = selected, isSelected = true, onClick = {})
                    }

                    val others = viewModel.existingMaterials.filter { it.id != viewModel.selectedMaterial?.id }
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Or reuse a previous upload", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        // Fixed height instead of weight(1f, fill = false) inside a LazyColumn:
                        // nesting a weighted LazyColumn inside a now-scrollable outer Column is
                        // an invalid combination (unbounded-height scrollable in unbounded-height
                        // scrollable) and would crash or silently mis-measure. spacedBy handles
                        // item spacing; the column just grows with its content.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            others.forEach { material ->
                                MaterialRow(
                                    material = material,
                                    isSelected = false,
                                    onClick = { viewModel.selectExistingMaterial(material) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.selectedMaterial != null
        )
    }
}

@Composable
private fun MaterialRow(material: StudyMaterialEntity, isSelected: Boolean, onClick: () -> Unit) {
    MarginRuleCard(onClick = onClick) {
        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(material.fileName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isSelected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(
                "${material.characterCount} characters extracted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WizardStepHeader(step: Int, total: Int, title: String) {
    Text(
        "STEP $step OF $total",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Text(title, style = MaterialTheme.typography.headlineMedium)
}
