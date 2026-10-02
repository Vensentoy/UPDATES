package com.revyu.app.core.widget

/**
 * Models shared by the in-app Home widgets and the native launcher AppWidget.
 *
 * The native widget renders plain columns of text via RemoteViews, so everything here is
 * deliberately flat strings - title, time, room - plus a small type flag used by the
 * in-app Compose renderer to tint each row.
 */

enum class EventRowKind {
    CLASS,
    SUGGESTED_STUDY,
    REVIEW,
    OTHER
}

data class WidgetEventRow(
    val title: String,
    val timeText: String,
    val roomText: String? = null,
    val kind: EventRowKind
)

/** "Today" breakdown shown by the Home Today widget and the native AppWidget. */
data class WidgetScheduleData(
    val dayHeader: String,
    val secondaryHeader: String,
    val emptyText: String,
    val rows: List<WidgetEventRow>,
    /** Whether the widget has any real data at all (vs. no study load imported yet). */
    val hasSchedule: Boolean,
    /**
     * Optional one-line contextual prompt shown in the highlighter banner (e.g. "Midterm
     * exam next meeting — do you want to review first?"). Null/empty = banner hidden.
     * Detection not wired up yet; set by the data layer later.
     */
    val reminder: String? = null
) {
    companion object {
        const val MAX_ROWS = 4
    }
}

/** Subject currently in session, for the NOW card. */
data class NowCard(
    val subject: String,
    val timeRange: String,
    val minutesLeft: Int,
    val progressPercent: Int
)

/** Upcoming exam that prompts the user to review first. */
data class ExamReminder(
    val title: String,
    val prompt: String,
    val subjectName: String,
    val subjectId: String?,
    val studySetId: String? = null
)

/** Next upcoming session, for the NEXT section. */
data class NextRow(
    val timeLabel: String,
    val title: String,
    val timeRange: String
)

/** Closest upcoming one-off school-calendar event (e.g. INTRAMURALS), for the footer card. */
data class SchoolEventRow(
    /** Days from today (0 = today). Rendered by the widget via string resources. */
    val daysUntil: Long,
    val title: String
)

/**
 * Everything the native launcher widget needs for the smart-schedule card layout:
 * header, NOW card, exam reminder card, NEXT section, the day footer, and the next
 * school-calendar event line.
 */
data class SmartScheduleData(
    val weekdayLabel: String,
    val dateLabel: String,
    val now: NowCard?,
    val exam: ExamReminder?,
    val next: NextRow?,
    val classCountToday: Int,
    /** Whether the widget has any real data at all (vs. no study load imported yet). */
    val hasSchedule: Boolean,
    val nextSchoolEvent: SchoolEventRow? = null,
    val noClassMessage: String? = null
)

/** Weekly 7-cell strip for the mini "week overview" Home widget. */
data class WidgetWeekData(
    val days: List<WidgetDayCell>
)

data class WidgetDayCell(
    val label: String,
    val eventCount: Int,
    val hasEventToday: Boolean
)