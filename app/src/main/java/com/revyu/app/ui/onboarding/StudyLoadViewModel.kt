package com.revyu.app.ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.FileTextExtractor
import com.revyu.app.core.util.StudyLoadParser
import com.revyu.app.core.util.UriUtils
import com.revyu.app.data.repository.SettingsRepository
import com.revyu.app.data.repository.SubjectRepository
import kotlinx.coroutines.launch
import java.util.UUID

data class EditableSubjectRow(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var scheduleText: String
)

sealed class StudyLoadUiState {
    data object Picking : StudyLoadUiState()
    data object Parsing : StudyLoadUiState()
    data class Reviewing(val rows: List<EditableSubjectRow>) : StudyLoadUiState()
    data object Saving : StudyLoadUiState()
    data class Error(val message: String) : StudyLoadUiState()
}

class StudyLoadViewModel(
    private val context: Context,
    private val extractor: FileTextExtractor,
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var state by mutableStateOf<StudyLoadUiState>(StudyLoadUiState.Picking)
        private set

    private val rows = mutableStateListOf<EditableSubjectRow>()

    fun onFilePicked(uri: Uri) {
        val fileName = UriUtils.displayName(context, uri)
        state = StudyLoadUiState.Parsing
        viewModelScope.launch {
            try {
                val extraction = extractor.extract(uri, fileName)
                val parsed = StudyLoadParser.parse(extraction.text)
                if (parsed.isEmpty()) {
                    state = StudyLoadUiState.Error(
                        "Couldn't find any subject schedules in $fileName. You can still add subjects manually below."
                    )
                    rows.clear()
                    rows.add(EditableSubjectRow(name = "", scheduleText = ""))
                    state = StudyLoadUiState.Reviewing(rows.toList())
                    return@launch
                }
                rows.clear()
                rows.addAll(
                    parsed.map {
                        EditableSubjectRow(
                            name = it.name,
                            scheduleText = StudyLoadParser.blocksToEditableText(it.blocks)
                        )
                    }
                )
                state = StudyLoadUiState.Reviewing(rows.toList())
            } catch (e: Exception) {
                state = StudyLoadUiState.Error(e.message ?: "Couldn't read that file. Try a different one, or add subjects manually.")
            }
        }
    }

    fun startManualEntry() {
        rows.clear()
        rows.add(EditableSubjectRow(name = "", scheduleText = ""))
        state = StudyLoadUiState.Reviewing(rows.toList())
    }

    fun updateRowName(id: String, name: String) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) {
            rows[index] = rows[index].copy(name = name)
            state = StudyLoadUiState.Reviewing(rows.toList())
        }
    }

    fun updateRowSchedule(id: String, scheduleText: String) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) {
            rows[index] = rows[index].copy(scheduleText = scheduleText)
            state = StudyLoadUiState.Reviewing(rows.toList())
        }
    }

    fun addRow() {
        rows.add(EditableSubjectRow(name = "", scheduleText = ""))
        state = StudyLoadUiState.Reviewing(rows.toList())
    }

    fun removeRow(id: String) {
        rows.removeAll { it.id == id }
        state = StudyLoadUiState.Reviewing(rows.toList())
    }

    fun retry() {
        state = StudyLoadUiState.Picking
    }

    fun confirm(onDone: () -> Unit) {
        val validRows = rows.filter { it.name.isNotBlank() }
        viewModelScope.launch {
            state = StudyLoadUiState.Saving
            val parsedSubjects = validRows.map { row ->
                com.revyu.app.core.util.ParsedSubject(
                    name = row.name.trim(),
                    blocks = StudyLoadParser.parseEditableText(row.scheduleText)
                )
            }
            subjectRepository.importStudyLoad(parsedSubjects)
            settingsRepository.markStudyLoadImported()
            onDone()
        }
    }
}
