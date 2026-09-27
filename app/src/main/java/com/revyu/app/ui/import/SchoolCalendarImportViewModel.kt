package com.revyu.app.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revyu.app.core.util.FileTextExtractor
import com.revyu.app.core.util.schoolcalendar.OcrLine
import com.revyu.app.core.util.schoolcalendar.ParsedSchoolCalendarEvent
import com.revyu.app.core.util.schoolcalendar.SchoolCalendarOcr
import com.revyu.app.core.util.schoolcalendar.SchoolCalendarParser
import com.revyu.app.core.util.schoolcalendar.SchoolCalendarParseReport
import com.revyu.app.core.util.studyload.StudyLoadFileValidation
import com.revyu.app.core.util.studyload.displayFileName
import com.revyu.app.core.util.studyload.sizeOf
import com.revyu.app.data.repository.SchoolCalendarRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewableSchoolEvent(
    val original: ParsedSchoolCalendarEvent,
    var included: Boolean = true
) {
    var dateOverride: LocalDate? = null
    val date get() = dateOverride ?: original.date
    val title get() = original.title
    val hasTime get() = original.startMinute != null
    fun toParsed(): ParsedSchoolCalendarEvent? =
        if (included) original.copy(date = dateOverride ?: original.date) else null
}

sealed class SchoolCalendarImportUiState {
    data object Idle : SchoolCalendarImportUiState()
    data object Reading : SchoolCalendarImportUiState()
    data object Saving : SchoolCalendarImportUiState()
    data class Review(
        val fileName: String,
        val events: List<ReviewableSchoolEvent>,
        val warnings: List<String>
    ) : SchoolCalendarImportUiState()
    data class Error(val title: String, val message: String) : SchoolCalendarImportUiState()
}

/**
 * Import driver for the LLCC school calendar. Mirrors the study-load flow: pick a PDF,
 * extract rows (straight text when the PDF has one; on-device OCR when it's a photo scan),
 * let the user include/drop events, then persist through [SchoolCalendarRepository].
 *
 * Scans can be large (a 15 MB A4 page is normal), so the 5 MB study-load cap doesn't apply
 * here — calendar scans get their own, much higher limit.
 */
class SchoolCalendarImportViewModel(
    private val context: Context,
    private val extractor: FileTextExtractor,
    private val ocr: SchoolCalendarOcr,
    private val schoolCalendarRepository: SchoolCalendarRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SchoolCalendarImportUiState>(SchoolCalendarImportUiState.Idle)
    val state: StateFlow<SchoolCalendarImportUiState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) {
        val fileName = uri.displayFileName(context)
        if (!StudyLoadFileValidation.looksLikePdf(fileName)) {
            _state.value = SchoolCalendarImportUiState.Error(
                "PDF only",
                "Please upload a .pdf school calendar file."
            )
            return
        }
        val size = uri.sizeOf(context)
        if (size > MAX_FILE_BYTES) {
            _state.value = SchoolCalendarImportUiState.Error(
                "File too large",
                "That calendar is over ${MAX_FILE_MB} MB. Please choose a smaller one."
            )
            return
        }
        parse(uri, fileName)
    }

    private fun parse(uri: Uri, fileName: String) {
        _state.value = SchoolCalendarImportUiState.Reading
        viewModelScope.launch {
            val report = try {
                SchoolCalendarParser.parse(textOrOcr(uri, fileName))
            } catch (e: com.revyu.app.core.util.ExtractionException) {
                _state.value = SchoolCalendarImportUiState.Error(
                    "Couldn't read your calendar",
                    e.message ?: "No readable text was found in this PDF."
                )
                return@launch
            } catch (e: Exception) {
                _state.value = SchoolCalendarImportUiState.Error(
                    "Couldn't read your calendar",
                    "Something went wrong reading this PDF. Try a clearer copy."
                )
                return@launch
            }

            if (report.events.isEmpty()) {
                _state.value = SchoolCalendarImportUiState.Error(
                    "Nothing found",
                    "Couldn't find any dated events in $fileName. Try exporting the " +
                        "calendar as a clear PDF and importing again."
                )
                return@launch
            }

            _state.value = SchoolCalendarImportUiState.Review(
                fileName = fileName,
                events = report.events.map { ReviewableSchoolEvent(it) },
                warnings = report.warnings
            )
        }
    }

    /**
     * Prefers the real text layer (cheap and exact) and falls back to OCR for image scans.
     * A text PDF yields many lines; a photo scan yields ~none, which is the OCR trigger.
     */
    private suspend fun textOrOcr(uri: Uri, fileName: String): List<OcrLine> {
        if (!StudyLoadFileValidation.looksLikePdf(fileName)) {
            val extraction = extractor.extract(uri, fileName)
            return toTextLines(extraction.text)
        }
        return try {
            val textLines = toTextLines(extractor.extract(uri, fileName).text)
            if (textLines.size >= MIN_TEXT_LINES) textLines else throw com.revyu.app.core.util.ExtractionException("")
        } catch (_: Exception) {
            ocr.recognizePdf(uri).ifEmpty {
                throw com.revyu.app.core.util.ExtractionException(
                    "No readable text was found in this PDF."
                )
            }
        }
    }

    private fun toTextLines(text: String): List<OcrLine> =
        text.lines().mapNotNull { trimmed(it) }.map { OcrLine(it) }

    private fun trimmed(line: String): String? = line.trim().takeIf { it.isNotBlank() }

    fun toggleIncluded(index: Int) {
        val review = _state.value as? SchoolCalendarImportUiState.Review ?: return
        review.events[index].included = !review.events[index].included
        _state.value = review.copy(events = review.events.toList())
    }

    fun setDate(index: Int, date: LocalDate) {
        val review = _state.value as? SchoolCalendarImportUiState.Review ?: return
        review.events[index].dateOverride = date
        _state.value = review.copy(events = review.events.toList())
    }

    fun save(onDone: () -> Unit) {
        val review = _state.value as? SchoolCalendarImportUiState.Review ?: return
        val selected = review.events.mapNotNull { it.toParsed() }
        if (selected.isEmpty()) return

        _state.value = SchoolCalendarImportUiState.Saving
        viewModelScope.launch {
            try {
                schoolCalendarRepository.importParsedCalendar(
                    SchoolCalendarParseReport(events = selected, warnings = review.warnings)
                )
            } catch (e: Exception) {
                _state.value = SchoolCalendarImportUiState.Error(
                    "Import failed",
                    "Couldn't save this calendar. Try again."
                )
                return@launch
            }
            _state.value = SchoolCalendarImportUiState.Idle
            onDone()
        }
    }

    fun dismissError() {
        _state.value = SchoolCalendarImportUiState.Idle
    }

    private companion object {
        const val MAX_FILE_MB = 30
        const val MAX_FILE_BYTES: Long = 30L * 1024L * 1024L
        const val MIN_TEXT_LINES = 3
    }
}