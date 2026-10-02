package com.revyu.app.core.widget

import com.revyu.app.data.local.entities.QuestionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Stable per-widget seeds so "of the day" cards cycle deterministically within a day. */
const val FLASHCARD_SEED = 0x5fL
const val QUESTION_SEED = 0x51L

/** One flashcard picked for today, with enough context to render and deep-link. */
data class DailyFlashcardData(
    val front: String,
    val back: String,
    val setTitle: String,
    val subjectName: String,
    val studySetId: String
)

/** One practice question picked for today. */
data class DailyQuestionData(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctAnswers: List<String>,
    val setTitle: String,
    val subjectName: String,
    val studySetId: String
)

/** One row in the "recent practice" widget. */
data class RecentScoreRow(
    val setTitle: String,
    val subjectName: String,
    val scorePercentage: Float,
    val daysAgo: Long,
    val studySetId: String
)

/** An exam occurrence: weekly (dayOfWeek) or one-off (date). Keep in sync with CalendarEventEntity. */
data class ExamOccurrence(
    val title: String,
    val dayOfWeek: Int?,
    val startMinute: Int,
    val date: LocalDate? = null
)

data class ExamCountdownRow(
    val title: String,
    val daysUntil: Int,
    val atMinute: Int?
)

/** One subject awaiting a first practice attempt. */
data class PracticeReadyRow(
    val subjectName: String,
    val firstStudySetId: String,
    val setCount: Int
)

/** One selectable Study Set for the "Choose study set" pin picker. */
data class PinOption(
    val studySetId: String,
    val setTitle: String,
    val subjectName: String
)

/**
 * Pure, injectable-free selection logic for the practice Home widgets. Everything here is a
 * function of its inputs (date via [LocalDate], the pool, and a fixed seed), so the "of the
 * day" cards are stable for a whole day and unit-testable with pinned dates.
 */
object PracticeWidgetPicker {

    /** Deterministic index into [poolSize] for a given date + seed; stable per day. */
    fun pickIndex(date: LocalDate, poolSize: Int, seed: Long): Int {
        require(poolSize > 0) { "poolSize must be positive" }
        return Math.floorMod(date.toEpochDay() * 31L + seed, poolSize.toLong()).toInt()
    }

    /**
     * Days from [today] (0 = today) to the next occurrence of [dayOfWeek] (1=Mon..7=Sun).
     * Kept local so this object stays independent of DateTimeUtils.
     */
    fun daysUntilNextWeekday(dayOfWeek: Int, today: LocalDate): Int {
        var diff = dayOfWeek - today.dayOfWeek.value
        if (diff < 0) diff += 7
        return diff
    }

    /**
     * Closest upcoming exam. Weekly exams roll to next week once their start minute passes
     * today; one-off exams (date != null) are skipped once past.
     */
    fun nextExamCountdown(today: LocalDate, nowMinute: Int, occurrences: List<ExamOccurrence>): ExamCountdownRow? {
        var best: ExamCountdownRow? = null
        for (o in occurrences) {
            val daysUntil: Long = when {
                o.date != null -> ChronoUnit.DAYS.between(today, o.date)
                o.dayOfWeek != null -> daysUntilNextWeekday(o.dayOfWeek, today).toLong()
                else -> continue
            }
            if (daysUntil < 0) continue
            // A weekly exam that already started today rolls to next week (7 days out); the
            // next occurrence still starts at the same minute, so atMinute is always set.
            val effectiveDays = if (o.date == null && daysUntil == 0L && o.startMinute < nowMinute) 7L else daysUntil
            val candidate = ExamCountdownRow(o.title, effectiveDays.toInt(), o.startMinute)
            if (best == null || candidate.daysUntil < best.daysUntil) best = candidate
        }
        return best
    }
}