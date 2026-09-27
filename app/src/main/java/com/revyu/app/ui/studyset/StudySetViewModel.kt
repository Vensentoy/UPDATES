package com.revyu.app.ui.studyset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.RevyuResult
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.GenerationRepository
import com.revyu.app.data.repository.StudyMaterialRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudySetOverviewUiState(
    val studySet: StudySetEntity? = null,
    val flashcards: List<FlashcardEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val isLoading: Boolean = true
)

class StudySetViewModel(
    private val studySetId: String,
    private val studySetRepository: StudySetRepository,
    private val subjectRepository: SubjectRepository,
    private val studyMaterialRepository: StudyMaterialRepository,
    private val generationRepository: GenerationRepository
) : ViewModel() {

    val uiState: StateFlow<StudySetOverviewUiState> = combine(
        studySetRepository.observeById(studySetId),
        studySetRepository.observeFlashcards(studySetId),
        studySetRepository.observeQuestions(studySetId)
    ) { studySet, flashcards, questions ->
        StudySetOverviewUiState(
            studySet = studySet,
            flashcards = flashcards,
            questions = questions,
            isLoading = studySet == null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudySetOverviewUiState())

    var isRetrying by mutableStateOf(false)
        private set

    var retryError by mutableStateOf<String?>(null)
        private set

    /**
     * A failed generation is persisted as-is (see GenerationRepository.fail) so it stays
     * visible in the subject's Study Set history rather than silently vanishing. Without
     * this, backing out of the wizard's error state instead of tapping "Try again" left
     * that Study Set as a permanent dead end — visible in history, but with no way back.
     * Everything needed to retry (source material, reviewer/practice settings) already
     * lives on the StudySetEntity itself.
     */
    fun retryGeneration() {
        val studySet = uiState.value.studySet ?: return
        val materialId = studySet.sourceMaterialId
        if (materialId == null) {
            retryError = "The original uploaded material is no longer available, so this can't be regenerated. Create a new Study Set instead."
            return
        }

        isRetrying = true
        retryError = null
        viewModelScope.launch {
            val subject = subjectRepository.getSubject(studySet.subjectId)
            val material = studyMaterialRepository.getById(materialId)
            if (subject == null || material == null) {
                isRetrying = false
                retryError = "Couldn't find the subject or source material for this Study Set anymore."
                return@launch
            }

            when (val result = generationRepository.generateStudySet(studySet, subject.name, material.extractedText)) {
                is RevyuResult.Success -> { /* uiState reflects the READY status reactively */ }
                is RevyuResult.Error -> retryError = result.message
            }
            isRetrying = false
        }
    }
}
