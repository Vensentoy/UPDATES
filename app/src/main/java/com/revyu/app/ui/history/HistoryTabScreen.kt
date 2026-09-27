package com.revyu.app.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryTabScreen(onOpenSettings: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = com.revyu.app.core.preferences.AppSettings())
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory { HistoryViewModel(container.studyLoadRepository) }
    )
    val imports by viewModel.imports.collectAsState()

    HistoryTabContent(
        imports = imports,
        settings = settings,
        onDeleteImport = { viewModel.deleteImport(it) },
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun HistoryTabContent(
    imports: List<StudyLoadEntity>,
    settings: com.revyu.app.core.preferences.AppSettings = com.revyu.app.core.preferences.AppSettings(),
    onDeleteImport: (StudyLoadEntity) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        com.revyu.app.ui.components.RevyuTopHeader(
            headerDrawableRes = com.revyu.app.R.drawable.history_header,
            onOpenSettings = onOpenSettings,
            settings = settings
        )

        if (imports.isEmpty()) {
            EmptyState(
                title = "No imports yet",
                body = "Study loads you import appear here with what was in them."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(imports, key = { it.id }) { entry ->
                ImportRow(entry = entry, onDelete = { onDeleteImport(entry) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ImportRow(entry: StudyLoadEntity, onDelete: () -> Unit) {
    MarginRuleCard(accentColor = MaterialTheme.colorScheme.tertiary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.fileName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                val whenImported = entry.importedAt
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US))
                Text(
                    whenImported,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(entry.subjectCount)
                        append(" subjects")
                        if (entry.unitTotal > 0) append(" · ${entry.unitTotal} units")
                        entry.semester?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete import history",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryTabPreview() {
    RevyuTheme {
        HistoryTabContent(
            imports = listOf(
                StudyLoadEntity(
                    id = "1",
                    fileName = "LLCC_StudyLoad_AY2024.pdf",
                    importedAt = Instant.now(),
                    subjectCount = 6,
                    unitTotal = 18,
                    semester = "1st Semester 2024-2025"
                )
            ),
            onDeleteImport = {}
        )
    }
}