package com.revyu.app.data.repository

import com.revyu.app.core.calendar.CalendarEvent
import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.data.local.entities.CalendarEventEntity
import java.time.DayOfWeek

/** Converts between the domain calendar model and the Room entity. Pure mapping, unit-testable. */
object CalendarEventMappers {

    fun toEntity(event: CalendarEvent): CalendarEventEntity = CalendarEventEntity(
        id = event.id,
        subjectId = event.subjectId,
        type = event.type.name,
        dayOfWeek = event.dayOfWeek.value,
        startMinute = event.startMinute,
        endMinute = event.endMinute,
        subjectName = event.subjectName,
        subjectCode = event.subjectCode,
        room = event.room,
        note = event.note,
        generationGroupId = event.generationGroupId,
        isCustom = event.isCustom,
        eventDate = event.eventDate
    )

    fun toEntityList(events: List<CalendarEvent>): List<CalendarEventEntity> =
        events.map { toEntity(it) }

    fun toDomain(event: CalendarEventEntity): CalendarEvent = CalendarEvent(
        id = event.id,
        subjectId = event.subjectId,
        type = runCatching { CalendarEventType.valueOf(event.type) }.getOrDefault(CalendarEventType.CLASS),
        dayOfWeek = DayOfWeek.of(event.dayOfWeek.coerceIn(1, 7)),
        startMinute = event.startMinute,
        endMinute = event.endMinute,
        subjectName = event.subjectName,
        subjectCode = event.subjectCode,
        room = event.room,
        note = event.note,
        generationGroupId = event.generationGroupId,
        isCustom = event.isCustom,
        eventDate = event.eventDate
    )

    fun toDomainList(events: List<CalendarEventEntity>): List<CalendarEvent> =
        events.map { toDomain(it) }
}