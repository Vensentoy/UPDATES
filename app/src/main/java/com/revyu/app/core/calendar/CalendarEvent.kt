package com.revyu.app.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * A single weekly calendar occurrence. Events are weekly-recurring (day of week + time),
 * which is exactly the shape of an LLCC study load and the suggested study sessions.
 *
 * The type set is deliberately extensible - EXAM and REMINDER are already represented so
 * future features (exam schedules, school holidays, deadlines) just add more rows.
 */
enum class CalendarEventType {
    CLASS,
    SUGGESTED_STUDY,
    REVIEW,
    EXAM,
    REMINDER
}

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    /** Subject this event belongs to (classes and study/review sessions). Nullable for future school-wide events. */
    val subjectId: String? = null,
    val type: CalendarEventType,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val subjectName: String,
    val subjectCode: String? = null,
    val room: String? = null,
    val note: String? = null,
    /** All events generated in one scheduler pass share a group id, so users can edit/delete/regenerate them as a batch. */
    val generationGroupId: String? = null,
    /** True when the user edited/added this session manually; scheduler regeneration won't touch it. */
    val isCustom: Boolean = false,
    /** One-off date (school calendar exams/reminders); null = weekly-recurring event. */
    val eventDate: LocalDate? = null
)

/** Helpers shared by the calendar screens, the Home widget, and the launcher widget. */
object CalendarEventSupport {

    fun displayLabel(event: CalendarEvent): String = when (event.type) {
        CalendarEventType.CLASS -> event.subjectName
        CalendarEventType.SUGGESTED_STUDY -> "Suggested Study · ${event.subjectName}"
        CalendarEventType.REVIEW -> "Suggested Review · ${event.subjectName}"
        CalendarEventType.EXAM -> "Exam · ${event.subjectName}"
        CalendarEventType.REMINDER -> event.note ?: event.subjectName
    }

    fun shortLabel(event: CalendarEvent): String = when (event.type) {
        CalendarEventType.CLASS -> event.subjectName
        CalendarEventType.SUGGESTED_STUDY -> "Study"
        CalendarEventType.REVIEW -> "Review"
        CalendarEventType.EXAM -> "Exam"
        CalendarEventType.REMINDER -> "Reminder"
    }
}