package com.revyu.app.ui.studyset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revyu.app.core.theme.PassGreen
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.MarginRuleCard

@Composable
fun QuestionsTab(questions: List<QuestionEntity>) {
    if (questions.isEmpty()) {
        EmptyState(title = "No questions yet", body = "This Study Set is still generating.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(questions, key = { it.id }) { question ->
            PracticeQuestionCard(question)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PracticeQuestionCard(question: QuestionEntity) {
    var revealed by remember(question.id) { mutableStateOf(false) }

    MarginRuleCard(onClick = { revealed = !revealed }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            if (question.options.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                question.options.forEach { option ->
                    Text(
                        "•  $option",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (revealed) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Answer: ${question.correctAnswers.joinToString(", ")}",
                    style = MaterialTheme.typography.titleSmall,
                    color = PassGreen
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap to reveal answer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
