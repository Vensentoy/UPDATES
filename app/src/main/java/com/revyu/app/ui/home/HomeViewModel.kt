package com.revyu.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.repository.PendingReviewTask
import com.revyu.app.data.repository.SmartCalendarRepository
import com.revyu.app.data.repository.SubjectRepository
import com.revyu.app.data.repository.SubjectWithSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val subjects: List<SubjectWithSchedule> = emptyList(),
    val pendingTasks: List<PendingReviewTask> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    subjectRepository: SubjectRepository,
    smartCalendarRepository: SmartCalendarRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        subjectRepository.observeSubjectsWithSchedule(),
        smartCalendarRepository.observePendingTasks()
    ) { subjects, tasks ->
        HomeUiState(subjects = subjects, pendingTasks = tasks, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
