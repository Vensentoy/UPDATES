package com.revyu.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.data.repository.StudyLoadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val studyLoadRepository: StudyLoadRepository
) : ViewModel() {

    val imports: StateFlow<List<StudyLoadEntity>> = studyLoadRepository.observeImports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteImport(studyLoad: StudyLoadEntity) {
        viewModelScope.launch { studyLoadRepository.deleteImport(studyLoad) }
    }
}