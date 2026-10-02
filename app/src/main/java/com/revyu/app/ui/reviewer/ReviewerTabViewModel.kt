package com.revyu.app.ui.reviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.repository.PendingReviewTask
import com.revyu.app.data.repository.SmartCalendarRepository
import com.revyu.app.data.repository.SubjectWithStudySets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ReviewerTabViewModel(
    subjectsWithStudySetsFlow: kotlinx.coroutines.flow.Flow<List<SubjectWithStudySets>>,
    smartCalendarRepository: SmartCalendarRepository
) : ViewModel() {

    data class UiState(
        val subjects: List<SubjectWithStudySets> = emptyList(),
        val pendingTasks: List<PendingReviewTask> = emptyList(),
        val isLoading: Boolean = true
    )

    val uiState: StateFlow<UiState> = combine(
        subjectsWithStudySetsFlow,
        smartCalendarRepository.observePendingTasks()
    ) { subjects, pendingTasks ->
        UiState(
            subjects = subjects,
            pendingTasks = pendingTasks,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState(isLoading = true))
}

/** Status summary for one subject's study sets, shown on the Reviewer tab cards. */
data class StudySetStatusSummary(
    val ready: Int,
    val generating: Int,
    val failed: Int,
    val examCompleted: Int,
    val examPending: Int
) {
    companion object {
        fun from(studySets: List<StudySetEntity>): StudySetStatusSummary {
            var ready = 0; var generating = 0; var failed = 0; var examCompleted = 0; var examPending = 0
            for (s in studySets) {
                when (s.generationStatus) {
                    GenerationStatus.READY -> {
                        ready++
                        if (s.examStatus == ExamStatus.COMPLETED) examCompleted++
                        else examPending++
                    }
                    GenerationStatus.GENERATING -> generating++
                    else -> failed++
                }
            }
            return StudySetStatusSummary(ready, generating, failed, examCompleted, examPending)
        }
    }

    fun label(): String = buildList {
        if (ready > 0) add("$ready ready")
        if (generating > 0) add("$generating generating")
        if (failed > 0) add("$failed failed")
        if (examPending > 0) add("$examPending exam pending")
        if (examCompleted > 0) add("$examCompleted exam done")
    }.joinToString(" · ").ifEmpty { "No study sets" }
}