package com.revyu.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.StudyLoadRepository
import com.revyu.app.data.repository.StudySetRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExamAttemptHistoryItem(
    val attempt: ExamAttemptEntity,
    val studySetId: String,
    val studySetTitle: String,
    val subjectName: String,
    val subjectAccentIndex: Int
)

data class ReviewerPdfHistoryItem(
    val studySet: StudySetEntity,
    val subjectName: String,
    val subjectAccentIndex: Int
)

class HistoryViewModel(
    private val studyLoadRepository: StudyLoadRepository,
    private val studySetRepository: StudySetRepository,
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    val imports: StateFlow<List<StudyLoadEntity>> = studyLoadRepository.observeImports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val examAttempts: StateFlow<List<ExamAttemptHistoryItem>> = combine(
        studySetRepository.observeAllSubmittedAttempts(),
        studySetRepository.observeAllStudySets(),
        subjectRepository.observeSubjectsWithSchedule()
    ) { attempts, studySets, subjectsWithSched ->
        val setMap = studySets.associateBy { it.id }
        val subjectMap = subjectsWithSched.map { it.subject }.associateBy { it.id }

        attempts.mapNotNull { attempt ->
            val set = setMap[attempt.studySetId] ?: return@mapNotNull null
            val subject = subjectMap[set.subjectId]
            ExamAttemptHistoryItem(
                attempt = attempt,
                studySetId = set.id,
                studySetTitle = set.title,
                subjectName = subject?.name ?: "Subject",
                subjectAccentIndex = subject?.accentIndex ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewerPdfs: StateFlow<List<ReviewerPdfHistoryItem>> = combine(
        studySetRepository.observeAllStudySets(),
        subjectRepository.observeSubjectsWithSchedule()
    ) { studySets, subjectsWithSched ->
        val subjectMap = subjectsWithSched.map { it.subject }.associateBy { it.id }

        studySets
            .filter { it.generationStatus == GenerationStatus.READY && !it.reviewerPdfPath.isNullOrBlank() }
            .sortedByDescending { it.createdAt }
            .map { set ->
                val subject = subjectMap[set.subjectId]
                ReviewerPdfHistoryItem(
                    studySet = set,
                    subjectName = subject?.name ?: "Subject",
                    subjectAccentIndex = subject?.accentIndex ?: 0
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteImport(studyLoad: StudyLoadEntity) {
        viewModelScope.launch { studyLoadRepository.deleteImport(studyLoad) }
    }
}
