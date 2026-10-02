package com.revyu.app.data.repository

import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.util.schoolcalendar.ParsedSchoolCalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolCalendarMappersTest {

    private val groupId = "school_calendar-abc123"

    @Test
    fun `maps a dated event to a one-off custom reminder`() {
        val event = ParsedSchoolCalendarEvent(
            date = LocalDate.of(2026, 10, 14),
            title = "Intramurals Sports 2026",
            startMinute = 8 * 60,
            endMinute = 17 * 60
        )
        val entity = SchoolCalendarMappers.toEntity(event, groupId)

        assertEquals(CalendarEventType.REMINDER.name, entity.type)
        assertEquals(LocalDate.of(2026, 10, 14), entity.eventDate)
        // Wednesday = DayOfWeek.WEDNESDAY.getValue() = 3.
        assertEquals(3, entity.dayOfWeek)
        assertEquals(8 * 60, entity.startMinute)
        assertEquals(17 * 60, entity.endMinute)
        assertEquals("Intramurals Sports 2026", entity.subjectName)
        assertNull(entity.subjectId)
        assertTrue(entity.isCustom)
        assertEquals(groupId, entity.generationGroupId)
    }

    @Test
    fun `event with no times defaults to an all-day window`() {
        val entity = SchoolCalendarMappers.toEntity(
            ParsedSchoolCalendarEvent(date = LocalDate.of(2026, 8, 24), title = "First Day"),
            groupId
        )
        assertEquals(0, entity.startMinute)
        assertEquals(1439, entity.endMinute)
    }

    @Test
    fun `start-only time pads the end to the same minute`() {
        val entity = SchoolCalendarMappers.toEntity(
            ParsedSchoolCalendarEvent(
                date = LocalDate.of(2026, 8, 24),
                title = "Flag Ceremony",
                startMinute = 7 * 60
            ),
            groupId
        )
        assertEquals(7 * 60, entity.startMinute)
        assertEquals(7 * 60, entity.endMinute)
    }

    @Test
    fun `group id carries the school_calendar prefix`() {
        assertTrue(SchoolCalendarMappers.newGenerationGroupId().startsWith("school_calendar-"))
        assertFalse(SchoolCalendarMappers.newGenerationGroupId().equals(SchoolCalendarMappers.newGenerationGroupId()))
    }

    @Test
    fun `list maps all entries under one group`() {
        val events = listOf(
            ParsedSchoolCalendarEvent(LocalDate.of(2026, 8, 24), "A"),
            ParsedSchoolCalendarEvent(LocalDate.of(2026, 8, 26), "B")
        )
        val entities = SchoolCalendarMappers.toEntityList(events, groupId)
        assertEquals(2, entities.size)
        assertTrue(entities.all { it.generationGroupId == groupId })
    }
}