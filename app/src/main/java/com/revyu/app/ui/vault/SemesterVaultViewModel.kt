package com.revyu.app.ui.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.RevyuResult
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.StudySetKind
import com.revyu.app.data.repository.GenerationRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class VaultExamType(val label: String, val kind: StudySetKind, val titlePrefix: String) {
    MIDTERM("Midterm Exam", StudySetKind.MIDTERM_VAULT, "Midterm"),
    FINAL("Final Exam", StudySetKind.FINAL_VAULT, "Final")
}

sealed class VaultGenerationUiState {
    data object Idle : VaultGenerationUiState()
    data object InProgress : VaultGenerationUiState()
    data class Success(val studySetId: String) : VaultGenerationUiState()
    data class Failed(val message: String, val isRateLimit: Boolean, val isMissingApiKey: Boolean = false) : VaultGenerationUiState()
}

class SemesterVaultViewModel(
    val subjectId: String,
    private val subjectRepository: SubjectRepository,
    private val studySetRepository: StudySetRepository,
    private val generationRepository: GenerationRepository
) : ViewModel() {

    var subjectName by mutableStateOf("")
        private set

    var eligibleStudySets by mutableStateOf<List<StudySetEntity>>(emptyList())
        private set

    var selectedStudySetIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var examType by mutableStateOf(VaultExamType.MIDTERM)
        private set

    var isLoadingSources by mutableStateOf(true)
        private set

    var generationState by mutableStateOf<VaultGenerationUiState>(VaultGenerationUiState.Idle)
        private set

    init {
        viewModelScope.launch {
            subjectRepository.getSubject(subjectId)?.let { subjectName = it.name }
            val eligible = studySetRepository.getVaultEligibleStudySets(subjectId)
            eligibleStudySets = eligible
            selectedStudySetIds = eligible.map { it.id }.toSet() // default: include everything available
            isLoadingSources = false
        }
    }

    fun applyExamType(type: VaultExamType) { examType = type }

    fun toggleStudySet(id: String) {
        selectedStudySetIds = if (id in selectedStudySetIds) selectedStudySetIds - id else selectedStudySetIds + id
    }

    fun startGeneration() {
        val selected = eligibleStudySets.filter { it.id in selectedStudySetIds }
        if (selected.isEmpty()) {
            generationState = VaultGenerationUiState.Failed("Select at least one Study Set to build this from.", false)
            return
        }

        generationState = VaultGenerationUiState.InProgress
        viewModelScope.launch {
            val dateLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()).format(java.time.LocalDate.now())
            val studySet = StudySetEntity(
                subjectId = subjectId,
                sourceMaterialId = null,
                title = "${examType.titlePrefix} Study Set — $dateLabel",
                kind = examType.kind,
                sourceStudySetIds = selected.map { it.id },
                reviewerColumns = 2,
                reviewerFontStyle = ReviewerFontStyle.SERIF,
                reviewerFontSizeSp = 12,
                reviewerMargins = ReviewerMargins.NORMAL,
                questionLanguage = "English",
                maxQuestions = 25,
                questionTypes = listOf(
                    QuestionType.SINGLE_CHOICE,
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.TRUE_FALSE,
                    QuestionType.IDENTIFICATION
                )
            )
            studySetRepository.insertStudySet(studySet)

            val aggregatedText = studySetRepository.aggregateContentForVault(selected)
            when (val result = generationRepository.generateStudySet(studySet, subjectName, aggregatedText, examType.label)) {
                is RevyuResult.Success -> generationState = VaultGenerationUiState.Success(studySet.id)
                is RevyuResult.Error -> generationState = VaultGenerationUiState.Failed(result.message, result.isRateLimit, result.isMissingApiKey)
            }
        }
    }

    fun retryGeneration() = startGeneration()
}
