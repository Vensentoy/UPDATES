package com.revyu.app.core.util.schoolcalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolCalendarGridExtractorTest {

    private fun el(text: String, x: Float, y: Float, w: Float = 60f, h: Float = 20f) =
        OcrElement(text, x, y, w, h)

    /** A full month: header, weekday letters row, and week rows of day numbers. */
    private fun monthGrid(header: String, headerY: Float, weekRowY: Float): List<OcrElement> {
        val elements = mutableListOf(el(header, 200f, headerY, 160f, 22f))
        "MTWTFSS".forEachIndexed { i, ch ->
            if (ch != ' ') elements += el(ch.toString(), 60f + i * 60f, headerY + 40f, 20f, 20f)
        }
        var day = 1
        for (week in 0 until 5) {
            for (i in 0 until 7) {
                if (day > 31) break
                elements += el(day.toString(), 60f + i * 60f, weekRowY + week * 45f, 24f, 20f)
                day++
            }
        }
        return elements
    }

    private fun parse(elements: List<OcrElement>): SchoolCalendarParseReport =
        SchoolCalendarGridExtractor.parse(listOf(OcrLine("", 0, 0, elements)))

    private fun title(events: List<ParsedSchoolCalendarEvent>, text: String): ParsedSchoolCalendarEvent? =
        events.firstOrNull { it.title.contains(text, ignoreCase = true) }

    @Test
    fun `explicit date on the caption line wins and never becomes its own event`() {
        val grid = monthGrid("AUGUST 2026", headerY = 20f, weekRowY = 90f).toMutableList()
        grid += el("Aug 24", 40f, 180f, 60f)
        grid += el("Start of Classes", 140f, 180f, 180f)

        val events = parse(grid).events

        val start = title(events, "Start of Classes")
        assertEquals(LocalDate.of(2026, 8, 24), start?.date)
        assertEquals(EventKind.EXPLICIT_DATE, start?.kind)
        assertEquals("Start of Classes", start?.title)
        // Day numbers and weekday letters must never become events.
        assertTrue(events.none { it.title == "24" || it.title == "24" })
        assertTrue(events.count { it.title.length <= 2 } == 0)
    }

    @Test
    fun `keyword cells are dated by the nearest day in their week`() {
        val grid = monthGrid("SEPTEMBER 2026", headerY = 20f, weekRowY = 90f).toMutableList()
        // Day columns: i * 60 + 60, so day N of a week sits at x = 60 + (N-1-in-week)*60.
        grid += el("Intramurals", 260f, 180f, 80f)   // week 15-21 row, under day 19
        grid += el("Midterm Exams", 168f, 225f, 80f)   // week 22-28 row, under day 24
        grid += el("Final Exams", 80f, 270f, 80f)    // last week, under day 30
        // Page masthead before the first month + a date-grid residue token.
        grid += el("LAPU-LAPU CITY COLLEGE", 60f, 4f, 220f)
        grid += el("24 17", 400f, 300f, 60f)

        val report = parse(grid)
        val events = report.events

        assertEquals(LocalDate.of(2026, 9, 19), title(events, "Intramurals")?.date)
        assertEquals(EventKind.KEYWORD, title(events, "Intramurals")?.kind)
        assertEquals(LocalDate.of(2026, 9, 24), title(events, "Midterm Exams")?.date)
        assertEquals(EventKind.KEYWORD, title(events, "Midterm Exams")?.kind)
        assertEquals(LocalDate.of(2026, 9, 30), title(events, "Final Exams")?.date)
        assertEquals(EventKind.KEYWORD, title(events, "Final Exams")?.kind)

        // The masthead is undated -> warning, never an event; grid residue is pure noise.
        assertTrue(report.warnings.any { it.contains("LAPU-LAPU") })
        assertTrue(events.none { it.title == "24 17" || it.title == "17" })
        // Exactly the three captions.
        assertEquals(3, events.size)
    }

    @Test
    fun `December to January rolls the inherited year over`() {
        val elements = mutableListOf<OcrElement>()
        elements += el("DECEMBER", 200f, 20f, 160f, 22f)
        elements += el("JANUARY", 200f, 100f, 150f, 22f)
        elements += el("3", 100f, 140f, 24f, 20f)
        elements += el("Start of Classes", 200f, 140f, 180f)

        val events = parse(elements).events

        assertEquals(LocalDate.of(2027, 1, 3), title(events, "Start of Classes")?.date)
    }

    @Test
    fun `wrapped caption lines merge into a single cell`() {
        val grid = monthGrid("OCTOBER 2026", headerY = 20f, weekRowY = 90f).toMutableList()
        grid += el("Don Sergio Osme\u00f1a", 200f, 180f, 120f)
        grid += el("Intramurals 2026", 200f, 200f, 120f)

        val events = parse(grid).events
        val cell = title(events, "Don Sergio Osme\u00f1a")
        assertTrue(cell != null)
        assertTrue(cell!!.title.contains("Intramurals"))
        assertTrue(events.none { it.title == "Intramurals 2026" })
    }
}