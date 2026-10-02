package com.revyu.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun SemesterVaultConfigureScreen(
    viewModel: SemesterVaultViewModel,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        Text(viewModel.subjectName, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))

        if (viewModel.isLoadingSources) {
            LoadingState("Checking what's available…")
            return@Column
        }

        if (viewModel.eligibleStudySets.isEmpty()) {
            EmptyState(
                title = "Nothing to build from yet",
                body = "Generate at least one regular Study Set for ${viewModel.subjectName} first — Semester Vault synthesizes from those."
            )
            Spacer(Modifier.weight(1f))
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Text(
            "Which exam is this for?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultExamType.entries.forEach { type ->
                FilterChip(
                    selected = viewModel.examType == type,
                    onClick = { viewModel.applyExamType(type) },
                    label = { Text(type.label) }
                )
            }
        }

        Text(
            "Covers these Study Sets",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            "Uncheck anything not covered by this exam.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(viewModel.eligibleStudySets, key = { it.id }) { studySet ->
                val checked = studySet.id in viewModel.selectedStudySetIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            onValueChange = { viewModel.toggleStudySet(studySet.id) },
                            role = Role.Checkbox
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                    Text(studySet.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "Generate",
                onClick = onGenerate,
                modifier = Modifier.weight(1f),
                enabled = viewModel.selectedStudySetIds.isNotEmpty()
            )
        }
    }
}
