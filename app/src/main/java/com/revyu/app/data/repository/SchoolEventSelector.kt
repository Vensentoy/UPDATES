package com.revyu.app.data.repository

import com.revyu.app.core.widget.SchoolEventRow
import com.revyu.app.data.local.entities.CalendarEventEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure picker for the widget's "Next school event" footer line. Dates and exclusion rules
 * live here so the selection logic is unit-testable on the JVM; the repository feeds it the
 * same exam-like predicate used by the exam card so the footer never duplicates it.
 */
object SchoolEventSelector {

    fun pick(
        allEvents: List<CalendarEventEntity>,
        today: LocalDate,
        isExamLike: (CalendarEventEntity) -> Boolean
    ): SchoolEventRow? {
        val best = allEvents
            .filter { it.eventDate != null && !isExamLike(it) }
            .mapNotNull { e ->
                val days = ChronoUnit.DAYS.between(today, e.eventDate!!)
                if (days < 0) null else Triple(e, days, e.startMinute)
            }
            .minWithOrNull(compareBy({ it.second }, { it.third }))
            ?.first ?: return null
        // A dated event landing today belongs in the NOW/NEXT sections, not the "coming up" line.
        if (best.eventDate == today) return null
        return SchoolEventRow(
            daysUntil = ChronoUnit.DAYS.between(today, best.eventDate),
            title = best.subjectName
        )
    }
}