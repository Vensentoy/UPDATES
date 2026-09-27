package com.revyu.app.ui.studyset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton

private val tabTitles = listOf("Reviewer", "Flashcards", "Questions")

@Composable
fun StudySetOverviewScreen(
    studySetId: String,
    onBack: () -> Unit,
    onStartExamMode: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: StudySetViewModel = viewModel(
        factory = viewModelFactory {
            StudySetViewModel(
                studySetId = studySetId,
                studySetRepository = container.studySetRepository,
                subjectRepository = container.subjectRepository,
                studyMaterialRepository = container.studyMaterialRepository,
                generationRepository = container.generationRepository
            )
        }
    )
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                state.studySet?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        val studySet = state.studySet
        when {
            state.isLoading -> LoadingState("Loading Study Set…")
            studySet != null && studySet.generationStatus != GenerationStatus.READY -> {
                Column(modifier = Modifier.padding(24.dp)) {
                    ErrorBanner(
                        message = studySet.generationError
                            ?: "Generation didn't finish — this can happen if the app closed or lost connection mid-generation.",
                        isRateLimit = false
                    )
                    viewModel.retryError?.let { err ->
                        Box(modifier = Modifier.padding(top = 12.dp)) { ErrorBanner(err) }
                    }
                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        PrimaryButton(
                            text = "Try again",
                            onClick = { viewModel.retryGeneration() },
                            modifier = Modifier.fillMaxWidth(),
                            loading = viewModel.isRetrying
                        )
                    }
                }
            }
            else -> {
                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ReviewerTab(studySet?.reviewerPdfPath, studySet?.title.orEmpty())
                        1 -> FlashcardsTab(state.flashcards)
                        2 -> QuestionsTab(state.questions)
                    }
                }

                if (studySet != null && studySet.generationStatus == GenerationStatus.READY) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        PrimaryButton(
                            text = if (studySet.examStatus == ExamStatus.COMPLETED) "Retake Exam Mode" else "Start Exam Mode",
                            onClick = onStartExamMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
