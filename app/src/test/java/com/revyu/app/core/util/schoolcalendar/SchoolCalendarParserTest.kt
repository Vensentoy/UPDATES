package com.revyu.app.core.util.schoolcalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolCalendarParserTest {

    private fun row(text: String) = OcrLine(text)

    @Test
    fun monthHeaderScopesBareDay() {
        val r = SchoolCalendarParser.parse(listOf(row("AUGUST 2026"), row("24 Foundation Day")))
        assertEquals(1, r.events.size)
        assertEquals(LocalDate.of(2026, 8, 24), r.events[0].date)
        assertEquals("Foundation Day", r.events[0].title)
        assertTrue(r.events[0].confidence < 1f)
    }

    @Test
    fun monthDayRowWithYear() {
        val r = SchoolCalendarParser.parse(listOf(row("Sept 5, 2026 Opening of Classes")))
        assertEquals(LocalDate.of(2026, 9, 5), r.events[0].date)
        assertEquals("Opening of Classes", r.events[0].title)
        assertEquals(1f, r.events[0].confidence)
    }

    @Test
    fun numericAndIsoRows() {
        val r = SchoolCalendarParser.parse(
            listOf(row("08/24/2026 Buwan ng Wika"), row("2026-09-05 Labor Day"))
        )
        assertEquals(LocalDate.of(2026, 8, 24), r.events[0].date)
        assertEquals(LocalDate.of(2026, 9, 5), r.events[1].date)
    }

    @Test
    fun rangeExpandsToOneEventPerDay() {
        val r = SchoolCalendarParser.parse(listOf(row("AUGUST 2026"), row("Aug 24-26 Intramurals")))
        assertEquals(3, r.events.size)
        assertEquals(LocalDate.of(2026, 8, 24), r.events[0].date)
        assertEquals(LocalDate.of(2026, 8, 26), r.events[2].date)
    }

    @Test
    fun timeAndWeekdayAreConsumed() {
        val r = SchoolCalendarParser.parse(listOf(row("Monday Aug 24 7:30 AM - 9:00 AM Orientation")))
        assertEquals("Orientation", r.events[0].title)
        assertEquals(7 * 60 + 30, r.events[0].startMinute)
        assertEquals(9 * 60, r.events[0].endMinute)
    }

    @Test
    fun twoColumnSplitAndContinuation() {
        val r = SchoolCalendarParser.parse(
            listOf(row("AUGUST 2026"), row("24 Sports Fest | 25 Science Fair"), row("Opening Ceremony"))
        )
        assertEquals(2, r.events.size)
        assertEquals("Sports Fest", r.events[0].title)
        assertEquals("Science Fair Opening Ceremony", r.events[1].title)
    }

    @Test
    fun termHeaderWarnsAndResets() {
        val r = SchoolCalendarParser.parse(listOf(row("S.Y. 2026-2027"), row("First Semester")))
        assertEquals(0, r.events.size)
        assertEquals(2, r.warnings.size)
    }
}

