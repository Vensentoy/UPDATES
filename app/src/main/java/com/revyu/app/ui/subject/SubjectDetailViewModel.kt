package com.revyu.app.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.SubjectEntity
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubjectDetailUiState(
    val subject: SubjectEntity? = null,
    val studySets: List<StudySetEntity> = emptyList(),
    val isLoading: Boolean = true
)

class SubjectDetailViewModel(
    private val subjectId: String,
    private val subjectRepository: SubjectRepository,
    private val studySetRepository: StudySetRepository
) : ViewModel() {

    private val subjectFlow = MutableStateFlow<SubjectEntity?>(null)

    val uiState: StateFlow<SubjectDetailUiState> = combine(
        subjectFlow,
        studySetRepository.observeForSubject(subjectId)
    ) { subject, sets ->
        SubjectDetailUiState(subject = subject, studySets = sets, isLoading = subject == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubjectDetailUiState())

    init {
        viewModelScope.launch {
            subjectFlow.value = subjectRepository.getSubject(subjectId)
        }
    }

    fun deleteStudySet(studySet: StudySetEntity) {
        viewModelScope.launch {
            studySetRepository.deleteStudySet(studySet)
        }
    }

    /** Fire-and-forget: the subject and everything under it cascades on delete, and the caller navigates away immediately. */
    fun deleteSubject(onDeleted: () -> Unit) {
        val subject = subjectFlow.value ?: return
        viewModelScope.launch {
            subjectRepository.deleteSubject(subject)
        }
        onDeleted()
    }
}
