package com.revyu.app.ui.studyset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.repository.StudySetRepository
import kotlinx.coroutines.launch

data class QuestionReview(
    val question: QuestionEntity,
    val givenAnswer: List<String>,
    val wasCorrect: Boolean
)

data class ResultsUiState(
    val attempt: ExamAttemptEntity? = null,
    val reviews: List<QuestionReview> = emptyList(),
    val isLoading: Boolean = true
)

class ResultsViewModel(
    private val studySetId: String,
    private val attemptId: String,
    private val studySetRepository: StudySetRepository
) : ViewModel() {

    var uiState by mutableStateOf(ResultsUiState())
        private set

    init {
        viewModelScope.launch {
            val attempt = studySetRepository.getExamAttempt(attemptId)
            val questions = studySetRepository.getQuestionsOnce(studySetId)
            val answers = attempt?.let { studySetRepository.decodeAnswers(it.answersJson) } ?: emptyMap()

            val reviews = questions.map { question ->
                val given = answers[question.id].orEmpty()
                val expected = question.correctAnswers.map { it.trim().lowercase() }.toSet()
                val givenNormalized = given.map { it.trim().lowercase() }.toSet()
                QuestionReview(question, given, givenNormalized == expected)
            }

            uiState = ResultsUiState(attempt = attempt, reviews = reviews, isLoading = false)
        }
    }

    fun performanceLabel(percentage: Float): String = studySetRepository.performanceLabel(percentage)
}
