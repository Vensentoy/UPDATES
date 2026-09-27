package com.revyu.app.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class SmartStudySchedulerTest {

    // SAMPLE 1 class: Monday 3-5pm (a 3-unit subject) - leaves room for an after-class study.
    private val oneLateClass = listOf(
        ClassBlock("s1", "Mobile Programming", 3, DayOfWeek.MONDAY, 15 * 60, 17 * 60)
    )

    @Test
    fun `plans study after class and review on a spaced non-class day`() {
        val config = SchedulerConfig(
            preferredStartMinute = 7 * 60,
            preferredEndMinute = 22 * 60
        )
        val sessions = SmartStudyScheduler().plan(SchedulerInput(oneLateClass, config))

        assertEquals(3, sessions.size) // 2 studies + 1 review for a 3-unit subject
        // First study starts after the Monday class ends (+30m window).
        val monStudy = sessions.first { it.day == DayOfWeek.MONDAY }
        assertTrue(monStudy.startMinute >= 17 * 60 + 30)
        assertEquals(StudySessionKind.SUGGESTED_STUDY, monStudy.kind)
        // Review lands on a day without a class for this subject (never Monday).
        val review = sessions.first { it.kind == StudySessionKind.REVIEW }
        assertTrue(review.day != DayOfWeek.MONDAY)
        assertEquals(DayOfWeek.SATURDAY, review.day) // weekend preferred first
    }

    @Test
    fun `sessions never overlap classes or each other`() {
        val config = SchedulerConfig(
            preferredStartMinute = 7 * 60,
            preferredEndMinute = 22 * 60
        )
        val classes = listOf(
            ClassBlock("m", "Math", 3, DayOfWeek.MONDAY, 8 * 60, 10 * 60),
            ClassBlock("m", "Math", 3, DayOfWeek.WEDNESDAY, 13 * 60, 15 * 60),
            ClassBlock("e", "English", 3, DayOfWeek.TUESDAY, 9 * 60, 11 * 60),
            ClassBlock("e", "English", 3, DayOfWeek.THURSDAY, 14 * 60, 16 * 60)
        )
        val sessions = SmartStudyScheduler().plan(SchedulerInput(classes, config))

        assertTrue(sessions.isNotEmpty())
        val all = (classes.map { it.day to (it.startMinute..<it.endMinute) }) +
            sessions.map { it.day to (it.startMinute..<it.endMinute) }
        val byDay = all.groupBy { it.first }
        for (day in DayOfWeek.values()) {
            val ranges = byDay[day].orEmpty().map { it.second }.sortedBy { it.first }
            for (i in 0 until ranges.size - 1) {
                assertTrue("Overlap on $day", ranges[i].last < ranges[i + 1].first)
            }
        }
        for (s in sessions) {
            assertTrue(s.startMinute >= config.preferredStartMinute)
            assertTrue(s.endMinute <= config.preferredEndMinute)
            assertEquals(config.studyBlockMinutes, s.endMinute - s.startMinute)
        }
    }

    @Test
    fun `deterministic across runs`() {
        val classes = listOf(
            ClassBlock("m", "Math", 3, DayOfWeek.MONDAY, 8 * 60, 10 * 60),
            ClassBlock("e", "English", 3, DayOfWeek.TUESDAY, 9 * 60, 11 * 60)
        )
        val input = SchedulerInput(classes, SchedulerConfig())
        val a = SmartStudyScheduler().plan(input)
        val b = SmartStudyScheduler().plan(input)
        assertEquals(a, b)
    }

    @Test
    fun `returns empty when auto generate is disabled`() {
        val config = SchedulerConfig(autoGenerate = false)
        assertTrue(SmartStudyScheduler().plan(SchedulerInput(oneLateClass, config)).isEmpty())
    }

    @Test
    fun `respects daily suggested minutes cap`() {
        val config = SchedulerConfig(
            preferredStartMinute = 7 * 60,
            preferredEndMinute = 22 * 60,
            maxSuggestedMinutesPerDay = 45
        )
        val sessions = SmartStudyScheduler()
            .plan(SchedulerInput(oneLateClass, config))
        val minutesByDay = sessions.groupBy { it.day }.mapValues { (_, list) ->
            list.sumOf { it.endMinute - it.startMinute }
        }
        for ((day, minutes) in minutesByDay) {
            assertTrue("$day exceeded cap: $minutes", minutes <= 45)
        }
    }
}