package com.revyu.app.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.FileTextExtractor
import com.revyu.app.core.util.studyload.ParsedClassSession
import com.revyu.app.core.util.studyload.ParsedStudyLoad
import com.revyu.app.core.util.studyload.ParsedStudyLoadSubject
import com.revyu.app.core.util.studyload.StudyLoadFileValidation
import com.revyu.app.core.util.studyload.StudyLoadParseReport
import com.revyu.app.core.util.studyload.StudyLoadParserRegistry
import com.revyu.app.core.util.studyload.displayFileName
import com.revyu.app.core.util.studyload.sizeOf
import com.revyu.app.data.repository.StudyLoadRepository
import com.revyu.app.data.repository.StudyLoadImportResult
import com.revyu.app.core.preferences.AppSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewableSubject(
    val original: ParsedStudyLoadSubject,
    var name: String,
    var units: Int?,
    var included: Boolean = true
) {
    val sessions: List<ParsedClassSession> get() = original.sessions
    fun toParsed(): ParsedStudyLoadSubject? =
        if (included && name.isNotBlank()) {
            original.copy(name = name.trim(), units = units)
        } else null
}

sealed class StudyLoadImportUiState {
    data object Idle : StudyLoadImportUiState()
    data object Parsing : StudyLoadImportUiState()
    data object Saving : StudyLoadImportUiState()
    data class Review(
        val fileName: String,
        val metadata: ParsedStudyLoad,
        val subjects: List<ReviewableSubject>,
        val warnings: List<String>
    ) : StudyLoadImportUiState()
    data class Duplicate(val fileName: String) : StudyLoadImportUiState()
    data class Error(val title: String, val message: String) : StudyLoadImportUiState()
}

class StudyLoadImportViewModel(
    private val context: Context,
    private val extractor: FileTextExtractor,
    private val studyLoadRepository: StudyLoadRepository,
    private val appSettingsStore: AppSettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow<StudyLoadImportUiState>(StudyLoadImportUiState.Idle)
    val state: StateFlow<StudyLoadImportUiState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) {
        val fileName = uri.displayFileName(context)
        if (!StudyLoadFileValidation.looksLikePdf(fileName)) {
            _state.value = StudyLoadImportUiState.Error("PDF only", StudyLoadFileValidation.NOT_PDF_MESSAGE)
            return
        }
        val size = uri.sizeOf(context)
        when (val sizeResult = StudyLoadFileValidation.checkSize(size)) {
            is StudyLoadFileValidation.SizeResult.TooLarge -> {
                _state.value = StudyLoadImportUiState.Error(
                    StudyLoadFileValidation.FILE_TOO_LARGE_TITLE,
                    StudyLoadFileValidation.FILE_TOO_LARGE_MESSAGE
                )
            }
            StudyLoadFileValidation.SizeResult.Ok -> parse(uri, fileName)
        }
    }

    private fun parse(uri: Uri, fileName: String) {
        _state.value = StudyLoadImportUiState.Parsing
        viewModelScope.launch {
            try {
                val existing = studyLoadRepository.getStudyLoadByFileName(fileName)
                if (existing != null) {
                    _state.value = StudyLoadImportUiState.Duplicate(fileName)
                    return@launch
                }
                val extraction = extractor.extract(uri, fileName)
                val report = StudyLoadParserRegistry.parse(extraction.text)
                if (report == null) {
                    _state.value = StudyLoadImportUiState.Error(
                        "Not recognized",
                        if (extraction.text.isNotBlank()) {
                            StudyLoadFileValidation.NOT_LLCC_MESSAGE + "\n\nHere's what the file contained:\n" +
                                StudyLoadFileValidation.previewOf(extraction.text)
                        } else {
                            StudyLoadFileValidation.NOT_LLCC_MESSAGE
                        }
                    )
                    return@launch
                }
                val subjects = report.studyLoad.subjects.map {
                    ReviewableSubject(original = it, name = it.name, units = it.units)
                }
                if (subjects.isEmpty()) {
                    _state.value = StudyLoadImportUiState.Error(
                        "Nothing found",
                        "Couldn't find any subject schedules in $fileName."
                    )
                    return@launch
                }
                _state.value = StudyLoadImportUiState.Review(
                    fileName = fileName,
                    metadata = report.studyLoad,
                    subjects = subjects,
                    warnings = report.warnings
                )
            } catch (e: Exception) {
                _state.value = StudyLoadImportUiState.Error(
                    "Couldn't read PDF",
                    StudyLoadFileValidation.UNREADABLE_MESSAGE
                )
            }
        }
    }

    fun setName(index: Int, name: String) {
        val review = _state.value as? StudyLoadImportUiState.Review ?: return
        review.subjects[index].name = name
        _state.value = review.copy(subjects = review.subjects.toList())
    }

    fun setUnits(index: Int, units: Int?) {
        val review = _state.value as? StudyLoadImportUiState.Review ?: return
        review.subjects[index].units = units
        _state.value = review.copy(subjects = review.subjects.toList())
    }

    fun toggleIncluded(index: Int) {
        val review = _state.value as? StudyLoadImportUiState.Review ?: return
        review.subjects[index].included = !review.subjects[index].included
        _state.value = review.copy(subjects = review.subjects.toList())
    }

    fun save(onDone: (StudyLoadImportResult) -> Unit) {
        val review = _state.value as? StudyLoadImportUiState.Review ?: return
        val selected = review.subjects.mapNotNull { it.toParsed() }
        if (selected.isEmpty()) return

        _state.value = StudyLoadImportUiState.Saving
        viewModelScope.launch {
            val settings = appSettingsStore.settings.first()
            val editedLoad = review.metadata.copy(subjects = selected)
            val result = try {
                studyLoadRepository.importParsedStudyLoad(
                    report = StudyLoadParseReport(studyLoad = editedLoad, warnings = review.warnings),
                    fileName = review.fileName,
                    config = settings.schedulerConfig()
                )
            } catch (e: Exception) {
                _state.value = StudyLoadImportUiState.Error(
                    "Import failed",
                    "Couldn't save this study load. Try again."
                )
                return@launch
            }
            _state.value = StudyLoadImportUiState.Idle
            onDone(result)
        }
    }

    fun dismissError() {
        _state.value = StudyLoadImportUiState.Idle
    }
}