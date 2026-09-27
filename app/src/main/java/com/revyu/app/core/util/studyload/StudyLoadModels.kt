package com.revyu.app.core.util.studyload

import java.time.DayOfWeek

/** One weekly class session for a subject (e.g. "Monday 07:00-09:00 in CAS-RM5A/COT-RM2"). */
data class ParsedClassSession(
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val room: String? = null
)

/** A single subject parsed from a study-load document. A subject may have many sessions. */
data class ParsedStudyLoadSubject(
    val subjectCode: String? = null,
    val catalogCode: String? = null,
    val name: String,
    val units: Int? = null,
    val sessions: List<ParsedClassSession> = emptyList()
)

/** The structured result of parsing an official study-load PDF. */
data class ParsedStudyLoad(
    val institution: String? = null,
    val semester: String? = null,
    val academicYear: String? = null,
    val course: String? = null,
    val yearLevel: String? = null,
    val section: String? = null,
    val studentName: String? = null,
    val subjects: List<ParsedStudyLoadSubject> = emptyList()
) {
    val totalUnits: Int get() = subjects.sumOf { it.units ?: 0 }
    val totalSessions: Int get() = subjects.sumOf { it.sessions.size }
}

/** A parse that succeeded but may carry non-fatal confidence warnings for the review step. */
data class StudyLoadParseReport(
    val studyLoad: ParsedStudyLoad,
    val warnings: List<String> = emptyList()
)

/** Whether an extracted document looks like the study load format this parser understands. */
sealed class StudyLoadRecognition {
    data object Recognized : StudyLoadRecognition()
    data class NotRecognized(val reason: String) : StudyLoadRecognition()
}