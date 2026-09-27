package com.revyu.app.data.repository

import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.util.schoolcalendar.ParsedSchoolCalendarEvent
import com.revyu.app.data.local.entities.CalendarEventEntity
import java.util.UUID

/**
 * Converts parsed school-calendar events into [CalendarEventEntity] rows. Pure mapping so
 * the import logic is unit-testable without Room.
 *
 * Each entry becomes a one-off REMINDER (no subject), dated with [eventDate], and grouped
 * under one "school_calendar-*" generationGroupId so a re-import replaces the previous
 * calendar wholesale. isCustom = true keeps the events safe from the scheduler's
 * suggestion-cleanup (which deletes isCustom = 0, non-CLASS rows).
 */
object SchoolCalendarMappers {

    const val GENERATION_GROUP_PREFIX = "school_calendar"

    fun newGenerationGroupId(): String =
        "$GENERATION_GROUP_PREFIX-${UUID.randomUUID()}"

    fun toEntity(
        event: ParsedSchoolCalendarEvent,
        groupId: String
    ): CalendarEventEntity {
        val start = event.startMinute ?: 0
        val end = event.endMinute ?: event.startMinute ?: 1439
        return CalendarEventEntity(
            id = UUID.randomUUID().toString(),
            type = CalendarEventType.REMINDER.name,
            dayOfWeek = event.date.dayOfWeek.value,
            startMinute = start,
            endMinute = end,
            subjectName = event.title,
            generationGroupId = groupId,
            isCustom = true,
            eventDate = event.date
        )
    }

    fun toEntityList(
        events: List<ParsedSchoolCalendarEvent>,
        groupId: String
    ): List<CalendarEventEntity> = events.map { toEntity(it, groupId) }
}