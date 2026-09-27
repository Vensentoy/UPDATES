package com.revyu.app.ui.studyset

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun ExamModeScreen(
    studySetId: String,
    onSubmitted: (String) -> Unit,
    onExit: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ExamModeViewModel = viewModel(
        factory = viewModelFactory { ExamModeViewModel(studySetId, container.studySetRepository) }
    )
    var showUnansweredWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        if (viewModel.isLoading) {
            LoadingState("Preparing your exam…")
            return@Column
        }
        if (viewModel.questions.isEmpty()) {
            Text("No questions available for this Study Set.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Back", onClick = onExit, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        val progress = (viewModel.currentIndex + 1).toFloat() / viewModel.questions.size
        Text(
            "Question ${viewModel.currentIndex + 1} of ${viewModel.questions.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        val question = viewModel.currentQuestion
        if (question != null) {
            QuestionPrompt(question)
            Spacer(Modifier.height(16.dp))
            AnswerInput(question = question, viewModel = viewModel)
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                text = "Previous",
                onClick = { viewModel.goPrevious() },
                modifier = Modifier.weight(1f),
                enabled = viewModel.currentIndex > 0
            )
            if (viewModel.isLastQuestion) {
                PrimaryButton(
                    text = "Submit",
                    onClick = {
                        if (viewModel.answeredCount < viewModel.questions.size) {
                            showUnansweredWarning = true
                        } else {
                            viewModel.submit(onSubmitted)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    loading = viewModel.isSubmitting
                )
            } else {
                PrimaryButton(
                    text = "Next",
                    onClick = { viewModel.goNext() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showUnansweredWarning) {
        val unanswered = viewModel.questions.size - viewModel.answeredCount
        AlertDialog(
            onDismissRequest = { showUnansweredWarning = false },
            title = { Text("$unanswered question${if (unanswered == 1) "" else "s"} unanswered") },
            text = { Text("You can still go back and finish them, or submit as-is — unanswered questions are simply marked incorrect.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnansweredWarning = false
                    viewModel.submit(onSubmitted)
                }) { Text("Submit anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showUnansweredWarning = false }) { Text("Go back") }
            }
        )
    }
}

@Composable
private fun QuestionPrompt(question: QuestionEntity) {
    Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun AnswerInput(question: QuestionEntity, viewModel: ExamModeViewModel) {
    val currentAnswer = viewModel.answers[question.id].orEmpty()

    when (question.type) {
        QuestionType.SINGLE_CHOICE -> {
            Column(Modifier.selectableGroup()) {
                question.options.forEach { option ->
                    OptionRow(
                        text = option,
                        selected = currentAnswer.contains(option),
                        onClick = { viewModel.setSingleAnswer(question.id, option) },
                        isCheckbox = false
                    )
                }
            }
        }
        QuestionType.MULTIPLE_CHOICE -> {
            Column {
                question.options.forEach { option ->
                    OptionRow(
                        text = option,
                        selected = currentAnswer.contains(option),
                        onClick = { viewModel.toggleMultiAnswer(question.id, option) },
                        isCheckbox = true
                    )
                }
            }
        }
        QuestionType.TRUE_FALSE -> {
            Column(Modifier.selectableGroup()) {
                listOf("True", "False").forEach { option ->
                    OptionRow(
                        text = option,
                        selected = currentAnswer.contains(option),
                        onClick = { viewModel.setSingleAnswer(question.id, option) },
                        isCheckbox = false
                    )
                }
            }
        }
        QuestionType.IDENTIFICATION, QuestionType.SHORT_ANSWER -> {
            OutlinedTextField(
                value = currentAnswer.firstOrNull() ?: "",
                onValueChange = { viewModel.setTextAnswer(question.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your answer") },
                singleLine = question.type == QuestionType.IDENTIFICATION
            )
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit, isCheckbox: Boolean) {
    val modifier = if (isCheckbox) {
        Modifier.fillMaxWidth().toggleable(value = selected, onValueChange = { onClick() }, role = Role.Checkbox)
    } else {
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    }
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCheckbox) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
