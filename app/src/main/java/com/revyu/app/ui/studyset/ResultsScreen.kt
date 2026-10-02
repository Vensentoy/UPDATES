package com.revyu.app.ui.studyset

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.CatExpression
import com.revyu.app.core.preferences.getCatDrawableRes
import com.revyu.app.core.theme.ErrorRust
import com.revyu.app.core.theme.Paper
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton

@Composable
fun ResultsScreen(
    studySetId: String,
    attemptId: String,
    onDone: () -> Unit
) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = AppSettings())
    val viewModel: ResultsViewModel = viewModel(
        factory = viewModelFactory { ResultsViewModel(studySetId, attemptId, container.studySetRepository) }
    )
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 20.dp)
    ) {
        if (state.isLoading || state.attempt == null) {
            LoadingState("Grading your exam…")
            return@Column
        }

        val attempt = state.attempt
        val isPassed = attempt.scorePercentage >= 75f
        val catDrawable = settings.accentColor.getCatDrawableRes(
            if (isPassed) CatExpression.CHEER else CatExpression.STUDY
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Results", style = MaterialTheme.typography.headlineMedium)
            Text("🐾", style = MaterialTheme.typography.headlineSmall)
        }

        // Compact results box that doesn't fill vertical height
        MarginRuleCard(accentColor = if (isPassed) PassGreen else ErrorRust) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${attempt.scorePercentage.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        color = (if (isPassed) PassGreen else ErrorRust).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            viewModel.performanceLabel(attempt.scorePercentage),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPassed) PassGreen else ErrorRust,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatColumn("Total", attempt.totalQuestions.toString())
                    StatColumn("Correct", attempt.correctCount.toString())
                    StatColumn("Incorrect", attempt.incorrectCount.toString())
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mascot cheer/study cat positioned below the results rectangle box
        Image(
            painter = painterResource(id = catDrawable),
            contentDescription = "Revyu Mascot",
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(Paper, RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(16.dp))

        val incorrect = state.reviews.filter { !it.wasCorrect }
        if (incorrect.isNotEmpty()) {
            Text(
                "Review these",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(incorrect, key = { it.question.id }) { review ->
                    MarginRuleCard(accentColor = ErrorRust) {
                        Column {
                            Text(review.question.prompt, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Your answer: ${review.givenAnswer.joinToString(", ").ifBlank { "(no answer)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Correct answer: ${review.question.correctAnswers.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PassGreen
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text(
                "Perfect score — every question correct!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
