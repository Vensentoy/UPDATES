package com.revyu.app.ui.history

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.theme.ErrorRust
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.RevyuTopHeader
import com.revyu.app.ui.components.SecondaryButton
import com.revyu.app.ui.components.subjectAccentColor
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryTab(val label: String) {
    EXAMS("Exams"),
    REVIEWERS("PDF Reviewers"),
    IMPORTS("Study Loads")
}

@Composable
fun HistoryTabScreen(
    onOpenSettings: () -> Unit = {},
    onOpenResults: (String, String) -> Unit = { _, _ -> },
    onOpenStudySet: (String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = AppSettings())
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            HistoryViewModel(
                studyLoadRepository = container.studyLoadRepository,
                studySetRepository = container.studySetRepository,
                subjectRepository = container.subjectRepository
            )
        }
    )
    val imports by viewModel.imports.collectAsState()
    val examAttempts by viewModel.examAttempts.collectAsState()
    val reviewerPdfs by viewModel.reviewerPdfs.collectAsState()

    HistoryTabContent(
        imports = imports,
        examAttempts = examAttempts,
        reviewerPdfs = reviewerPdfs,
        settings = settings,
        onDeleteImport = { viewModel.deleteImport(it) },
        onOpenSettings = onOpenSettings,
        onOpenResults = onOpenResults,
        onOpenStudySet = onOpenStudySet
    )
}

@Composable
fun HistoryTabContent(
    imports: List<StudyLoadEntity>,
    examAttempts: List<ExamAttemptHistoryItem> = emptyList(),
    reviewerPdfs: List<ReviewerPdfHistoryItem> = emptyList(),
    settings: AppSettings = AppSettings(),
    onDeleteImport: (StudyLoadEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenResults: (String, String) -> Unit = { _, _ -> },
    onOpenStudySet: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(HistoryTab.EXAMS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        RevyuTopHeader(
            headerDrawableRes = com.revyu.app.R.drawable.history_header,
            onOpenSettings = onOpenSettings,
            settings = settings
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryTab.entries.forEach { tab ->
                val count = when (tab) {
                    HistoryTab.EXAMS -> examAttempts.size
                    HistoryTab.REVIEWERS -> reviewerPdfs.size
                    HistoryTab.IMPORTS -> imports.size
                }
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text("${tab.label} ($count)") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            HistoryTab.EXAMS -> ExamHistoryList(
                attempts = examAttempts,
                onOpenResults = onOpenResults
            )
            HistoryTab.REVIEWERS -> ReviewerPdfHistoryList(
                reviewers = reviewerPdfs,
                onOpenStudySet = onOpenStudySet
            )
            HistoryTab.IMPORTS -> StudyLoadHistoryList(
                imports = imports,
                onDeleteImport = onDeleteImport
            )
        }
    }
}

@Composable
private fun ExamHistoryList(
    attempts: List<ExamAttemptHistoryItem>,
    onOpenResults: (String, String) -> Unit
) {
    if (attempts.isEmpty()) {
        EmptyState(
            title = "No exam attempts yet",
            body = "Complete an Exam Mode to save your score history and review mistakes here."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(attempts, key = { it.attempt.id }) { item ->
            ExamAttemptRow(
                item = item,
                onClick = { onOpenResults(item.studySetId, item.attempt.id) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExamAttemptRow(
    item: ExamAttemptHistoryItem,
    onClick: () -> Unit
) {
    val isPassed = item.attempt.scorePercentage >= 75f
    val accent = subjectAccentColor(item.subjectAccentIndex)
    val formatter = remember(item.attempt.id) {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US).withZone(ZoneId.systemDefault())
    }

    MarginRuleCard(accentColor = accent, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.subjectName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        item.studySetTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    color = (if (isPassed) PassGreen else ErrorRust).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${item.attempt.scorePercentage.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPassed) PassGreen else ErrorRust,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateText = item.attempt.submittedAt?.let { formatter.format(it) } ?: ""
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${item.attempt.correctCount}/${item.attempt.totalQuestions} Correct",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            SecondaryButton(
                text = "Analyze Results & Mistakes",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
    }
}

@Composable
private fun ReviewerPdfHistoryList(
    reviewers: List<ReviewerPdfHistoryItem>,
    onOpenStudySet: (String) -> Unit
) {
    val context = LocalContext.current

    if (reviewers.isEmpty()) {
        EmptyState(
            title = "No PDF reviewers yet",
            body = "Your generated AI reviewers will appear here so you can review in-app or share them anytime."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reviewers, key = { it.studySet.id }) { item ->
            ReviewerPdfRow(
                item = item,
                onOpen = { onOpenStudySet(item.studySet.id) },
                onShare = {
                    item.studySet.reviewerPdfPath?.let { path ->
                        sharePdf(context, path, item.studySet.title)
                    }
                }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReviewerPdfRow(
    item: ReviewerPdfHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val accent = subjectAccentColor(item.subjectAccentIndex)
    val formatter = remember(item.studySet.id) {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US).withZone(ZoneId.systemDefault())
    }

    MarginRuleCard(accentColor = accent, onClick = onOpen) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                item.subjectName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.studySet.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Generated ${formatter.format(item.studySet.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Review in App",
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(36.dp)
                )
                SecondaryButton(
                    text = "Share PDF",
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(36.dp)
                )
            }
        }
    }
}

@Composable
private fun StudyLoadHistoryList(
    imports: List<StudyLoadEntity>,
    onDeleteImport: (StudyLoadEntity) -> Unit
) {
    if (imports.isEmpty()) {
        EmptyState(
            title = "No imports yet",
            body = "Study loads you import appear here with what was in them."
        )
        return
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

@Composable
private fun ImportRow(entry: StudyLoadEntity, onDelete: () -> Unit) {
    MarginRuleCard(accentColor = MaterialTheme.colorScheme.primary) {
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

private fun pdfUri(context: Context, pdfPath: String) =
    FileProvider.getUriForFile(context, "com.revyu.app.fileprovider", File(pdfPath))

private fun sharePdf(context: Context, pdfPath: String, title: String) {
    val uri = pdfUri(context, pdfPath)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share Reviewer").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
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
                    importedAt = java.time.Instant.now(),
                    subjectCount = 6,
                    unitTotal = 18,
                    semester = "1st Semester 2024-2025"
                )
            )
        )
    }
}
