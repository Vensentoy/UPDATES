package com.revyu.app.ui.studyset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.repository.StudySetRepository
import kotlinx.coroutines.launch

class ExamModeViewModel(
    private val studySetId: String,
    private val studySetRepository: StudySetRepository
) : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set

    var questions by mutableStateOf<List<QuestionEntity>>(emptyList())
        private set

    var currentIndex by mutableIntStateOf(0)
        private set

    val answers = mutableStateMapOf<String, List<String>>()

    var isSubmitting by mutableStateOf(false)
        private set

    private var attempt: ExamAttemptEntity? = null

    init {
        viewModelScope.launch {
            questions = studySetRepository.getQuestionsOnce(studySetId)
            attempt = studySetRepository.startExamAttempt(studySetId)
            isLoading = false
        }
    }

    val currentQuestion: QuestionEntity? get() = questions.getOrNull(currentIndex)
    val isLastQuestion: Boolean get() = currentIndex == questions.lastIndex
    val answeredCount: Int get() = answers.keys.count { it in questions.map { q -> q.id } }

    fun setSingleAnswer(questionId: String, value: String) {
        answers[questionId] = listOf(value)
    }

    fun toggleMultiAnswer(questionId: String, value: String) {
        val current = answers[questionId].orEmpty()
        answers[questionId] = if (value in current) current - value else current + value
    }

    fun setTextAnswer(questionId: String, value: String) {
        answers[questionId] = listOf(value)
    }

    fun goNext() { if (currentIndex < questions.lastIndex) currentIndex++ }
    fun goPrevious() { if (currentIndex > 0) currentIndex-- }
    fun goTo(index: Int) { if (index in questions.indices) currentIndex = index }

    fun submit(onSubmitted: (String) -> Unit) {
        val current = attempt ?: return
        isSubmitting = true
        viewModelScope.launch {
            val updated = studySetRepository.submitExamAttempt(current, answers.toMap())
            isSubmitting = false
            onSubmitted(updated.id)
        }
    }
}
