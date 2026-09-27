package com.revyu.app.data.repository

import com.revyu.app.data.local.entities.CalendarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SchoolEventSelectorTest {

    private val today = LocalDate.of(2026, 9, 21)
    private val examLike: (CalendarEventEntity) -> Boolean = { it.subjectName.contains("exam", true) }

    private fun event(
        date: LocalDate?,
        title: String,
        startMinute: Int = 8 * 60,
        type: String = "REMINDER"
    ) = CalendarEventEntity(
        type = type,
        dayOfWeek = date?.dayOfWeek?.value ?: 1,
        startMinute = startMinute,
        endMinute = startMinute + 60,
        subjectName = title,
        eventDate = date
    )

    @Test
    fun `picks the closest future dated non-exam event`() {
        val row = SchoolEventSelector.pick(
            listOf(
                event(LocalDate.of(2026, 11, 14), "Intramurals"),
                event(LocalDate.of(2026, 10, 14), "Midterm Exams", type = "EXAM")
            ),
            today,
            examLike
        )
        assertEquals(54, row!!.daysUntil)
        assertEquals("Intramurals", row.title)
    }

    @Test
    fun `excludes exam-like events so the footer never duplicates the exam card`() {
        val row = SchoolEventSelector.pick(
            listOf(event(LocalDate.of(2026, 10, 14), "Midterm Exams")),
            today,
            examLike
        )
        assertNull(row)
    }

    @Test
    fun `ignores past and weekly-recurring events`() {
        assertNull(
            SchoolEventSelector.pick(
                listOf(event(LocalDate.of(2026, 8, 1), "Long past"), event(null, "Weekly repeat")),
                today,
                examLike
            )
        )
    }

    @Test
    fun `returns null when the dated event lands today`() {
        assertNull(
            SchoolEventSelector.pick(listOf(event(today, "Intramurals Today")), today, examLike)
        )
    }

    @Test
    fun `ties on the same date break by earlier start minute`() {
        val row = SchoolEventSelector.pick(
            listOf(
                event(LocalDate.of(2026, 11, 14), "Flags", startMinute = 7 * 60),
                event(LocalDate.of(2026, 11, 14), "Parade", startMinute = 15 * 60)
            ),
            today,
            examLike
        )
        assertEquals(54, row!!.daysUntil)
        assertEquals("Flags", row.title)
    }
}