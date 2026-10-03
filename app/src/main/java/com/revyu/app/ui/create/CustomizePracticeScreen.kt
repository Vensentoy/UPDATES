package com.revyu.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revyu.app.data.local.entities.DifficultyMix
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.ui.components.ChipGroup
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizePracticeScreen(
    viewModel: CreateStudySetViewModel,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    var hasTappedGenerate by remember { mutableStateOf(false) }

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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            WizardStepHeader(step = 3, total = 4, title = "Customize practice questions")
            Spacer(Modifier.height(20.dp))

            SettingLabel("Language")
            Text(
                "English (Taglish support is coming later)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            SettingLabel("Flashcards — ${viewModel.flashcardCount}")
            Slider(
                value = viewModel.flashcardCount.toFloat(),
                onValueChange = { viewModel.applyFlashcardCount(((it + 2.5f) / 5).toInt() * 5) },
                valueRange = 10f..60f,
                steps = 9
            )

            Spacer(Modifier.height(20.dp))
            SettingLabel("Question bank size — ${viewModel.maxQuestions}")
            Slider(
                value = viewModel.maxQuestions.toFloat(),
                onValueChange = { viewModel.applyMaxQuestions(((it + 2.5f) / 5).toInt() * 5) },
                valueRange = 10f..60f,
                steps = 9
            )

            Spacer(Modifier.height(20.dp))
            SettingLabel("Difficulty")
            ChipGroup {
                DifficultyMix.entries.forEach { mix ->
                    FilterChip(
                        selected = viewModel.difficultyMix == mix,
                        onClick = { viewModel.applyDifficultyMix(mix) },
                        label = {
                            Text(
                                mix.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingLabel("Question types")
            ChipGroup {
                QuestionType.entries.forEach { type ->
                    FilterChip(
                        selected = type in viewModel.selectedQuestionTypes,
                        onClick = { viewModel.toggleQuestionType(type) },
                        label = {
                            Text(
                                type.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "Generate Study Set",
                onClick = {
                    if (!hasTappedGenerate) {
                        hasTappedGenerate = true
                        onGenerate()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = viewModel.selectedQuestionTypes.isNotEmpty() && !hasTappedGenerate
            )
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
}
