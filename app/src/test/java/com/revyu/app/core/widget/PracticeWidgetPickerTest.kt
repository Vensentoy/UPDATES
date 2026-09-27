package com.revyu.app.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PracticeWidgetPickerTest {

    private val sunday = LocalDate.of(2026, 9, 13) // a Sunday

    @Test
    fun `pickIndex is deterministic and stays in bounds`() {
        val pool = 10
        val first = PracticeWidgetPicker.pickIndex(sunday, pool, FLASHCARD_SEED)
        for (i in 0..30) {
            val idx = PracticeWidgetPicker.pickIndex(sunday, pool, FLASHCARD_SEED)
            assertEquals(first, idx)
            assertTrue(idx in 0 until pool)
        }
    }

    @Test
    fun `pickIndex varies with seed and date`() {
        val s1 = PracticeWidgetPicker.pickIndex(sunday, 100, 1L)
        val s2 = PracticeWidgetPicker.pickIndex(sunday, 100, 2L)
        val s3 = PracticeWidgetPicker.pickIndex(sunday.plusDays(1), 100, 1L)
        assertTrue("different seeds or dates should differ", s1 != s2 || s1 != s3)
    }

    @Test
    fun `weekly occurrence - future weekday counted from today`() {
        val monday = ExamOccurrence("Midterm", dayOfWeek = 1, startMinute = 9 * 60)
        val best = PracticeWidgetPicker.nextExamCountdown(sunday, 0, listOf(monday))
        assertEquals("Midterm", best?.title)
        assertEquals(1, best?.daysUntil)
        assertEquals(9 * 60, best?.atMinute)
    }

    @Test
    fun `weekly exam today still shows at zero days before start`() {
        val today = ExamOccurrence("Quiz", dayOfWeek = 7, startMinute = 8 * 60)
        val best = PracticeWidgetPicker.nextExamCountdown(sunday, 6 * 60, listOf(today))
        assertEquals(0, best?.daysUntil)
    }

    @Test
    fun `weekly exam rolls to next week once its start time has passed`() {
        val today = ExamOccurrence("Quiz", dayOfWeek = 7, startMinute = 8 * 60)
        val best = PracticeWidgetPicker.nextExamCountdown(sunday, 14 * 60, listOf(today))
        assertEquals(7, best?.daysUntil)
        assertEquals(8 * 60, best?.atMinute)
    }

    @Test
    fun `past one-off exam is ignored`() {
        val past = ExamOccurrence("Final", dayOfWeek = null, startMinute = 9 * 60, date = sunday.minusDays(1))
        assertNull(PracticeWidgetPicker.nextExamCountdown(sunday, 0, listOf(past)))
    }

    @Test
    fun `one-off exam this week wins over later weekly`() {
        val weekly = ExamOccurrence("Weekly Quiz", dayOfWeek = 5, startMinute = 9 * 60) // Friday
        val oneOff = ExamOccurrence("Midterm", dayOfWeek = null, startMinute = 10 * 60, date = sunday.plusDays(1)) // Monday
        val best = PracticeWidgetPicker.nextExamCountdown(sunday, 0, listOf(weekly, oneOff))
        assertEquals("Midterm", best?.title)
        assertEquals(1, best?.daysUntil)
    }
}