package com.revyu.app.core.util.schoolcalendar

import java.time.LocalDate

/** One OCR text block with its on-page position (keeps grid scans dateable). */
data class OcrLine(
    val text: String,
    val x: Int = 0,
    val y: Int = 0,
    /** Word-level boxes from ML Kit, kept so the grid extractor can date cells. */
    val elements: List<OcrElement> = emptyList()
)

/** Where a parsed event's date came from — drives grouping and confidence in review. */
enum class EventKind {
    /** MonthName + day / numeric / ISO spelled out next to the title. */
    EXPLICIT_DATE,
    /** Matched a known academic vocabulary keyword, dated by the nearest day cell. */
    KEYWORD,
    /** No keyword, no explicit date — best-effort nearest-day, flagged in review. */
    CATCH_ALL
}

/** One dated, non-recurring school-calendar entry. */
data class ParsedSchoolCalendarEvent(
    val date: LocalDate,
    val title: String,
    /** Minutes after midnight when the calendar prints a start time. */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    /** 0..1 — drops when the row needed an unfuzzable guess (bare day, assumed year). */
    val confidence: Float = 1f,
    val kind: EventKind = EventKind.EXPLICIT_DATE
)

/** Result of parsing a school calendar scan. Warnings are non-fatal and go to the review step. */
data class SchoolCalendarParseReport(
    val events: List<ParsedSchoolCalendarEvent>,
    val warnings: List<String> = emptyList()
)
