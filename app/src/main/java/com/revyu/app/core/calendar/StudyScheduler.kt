package com.revyu.app.core.calendar

import java.time.DayOfWeek

enum class StudySessionKind {
    SUGGESTED_STUDY,
    REVIEW
}

/** A weekly suggested study/review block produced by the scheduler. */
data class SuggestedSession(
    val subjectId: String,
    val subjectName: String,
    val kind: StudySessionKind,
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int
)

/** One recurring weekly class block for the scheduler to plan around. */
data class ClassBlock(
    val subjectId: String,
    val subjectName: String,
    val units: Int,
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int
)

/** Deterministic, configuration-driven scheduler settings. */
data class SchedulerConfig(
    val autoGenerate: Boolean = true,
    /** Preferred length (minutes) of a suggested study block. 30-60 recommended. */
    val studyBlockMinutes: Int = 45,
    /** Preferred length (minutes) of a suggested review block. */
    val reviewBlockMinutes: Int = 45,
    /** Earliest a suggested session may start (minutes since midnight). */
    val preferredStartMinute: Int = 16 * 60,
    /** Latest a suggested session may end (minutes since midnight). */
    val preferredEndMinute: Int = 21 * 60,
    /** Whether review sessions may land on Saturday/Sunday. */
    val weekendStudy: Boolean = true,
    /** Soft cap on total suggested study minutes per day. */
    val maxSuggestedMinutesPerDay: Int = 90
)

data class SchedulerInput(
    val classes: List<ClassBlock>,
    val config: SchedulerConfig = SchedulerConfig()
)

/**
 * Plans suggested study/review sessions around the user's real class schedule.
 *
 * Deterministic and fully offline - no AI required for the first version. The interface
 * exists so an AI-assisted planner can be slotted in later (e.g. "move my sessions,
 * I have an exam tomorrow") without touching callers.
 */
interface StudyScheduler {
    fun plan(input: SchedulerInput): List<SuggestedSession>
}

/**
 * Rule-based planner. Guarantees:
 *  - no suggested session ever overlaps a class
 *  - no two suggested sessions overlap
 *  - sessions only land in free time inside the user's preferred hours
 *  - higher-unit subjects get priority placement
 *  - a review is placed on a spaced, non-class day for the subject
 */
class SmartStudyScheduler : StudyScheduler {

    private val weekdayIndex = DayOfWeek.values().withIndex().associate { it.value to it.index }

    override fun plan(input: SchedulerInput): List<SuggestedSession> {
        val config = input.config
        if (!config.autoGenerate || input.classes.isEmpty()) return emptyList()

        val planner = Planner(input.classes, config, weekdayIndex)
        return planner.run()
    }

    private class Planner(
        classes: List<ClassBlock>,
        private val config: SchedulerConfig,
        private val weekdayIndex: Map<DayOfWeek, Int>
    ) {
        private val classDays: List<List<ClassBlock>> =
            (0..6).map { dayIndex -> classes.filter { weekdayIndex[it.day] == dayIndex } }

        // Subjects ordered by (units desc, name asc) for deterministic priority placement.
        private val subjects: List<List<ClassBlock>> = classes
            .groupBy { it.subjectId }
            .values
            .map { it.sortedWith(compareBy({ it.startMinute }, { it.endMinute })) }
            .sortedWith(
                compareByDescending<List<ClassBlock>> { it.first().units }
                    .thenBy { it.first().subjectName.lowercase() }
            )

        private val placed = mutableListOf<SuggestedSession>()
        private val suggestedMinutesByDay = IntArray(7)

        fun run(): List<SuggestedSession> {
            for (subjectClasses in subjects) {
                planSubject(subjectClasses)
            }
            return placed.sortedWith(compareBy({ weekdayIndex.getValue(it.day) }, { it.startMinute }))
        }

        private fun planSubject(subjectClasses: List<ClassBlock>) {
            val name = subjectClasses.first().subjectName
            val units = subjectClasses.first().units
            val subjectId = subjectClasses.first().subjectId
            val classDaysOfSubject = subjectClasses.map { it.day }

            // Studies: a short study after the day of a class, spaced across the week.
            val studyCount = if (units >= 3) 2 else 1
            var placedStudies = 0
            for (classBlock in subjectClasses) {
                if (placedStudies >= studyCount) break
                val placedBlock = placeStudyAfter(classBlock)
                if (placedBlock != null) {
                    placedStudies++
                    placed.add(
                        SuggestedSession(
                            subjectId, name, StudySessionKind.SUGGESTED_STUDY,
                            placedBlock.day, placedBlock.start, placedBlock.end
                        )
                    )
                }
            }
            // Subjects with few class days may still deserve additional spaced studies.
            var fromDayIndex = subjectClasses.maxOf { weekdayIndex.getValue(it.day) }
            var guard = 0
            while (placedStudies < studyCount && guard < 7) {
                val fill = placeStudyNextFree(fromDayIndex)
                if (fill == null) break
                placed.add(
                    SuggestedSession(
                        subjectId, name, StudySessionKind.SUGGESTED_STUDY,
                        fill.day, fill.start, fill.end
                    )
                )
                placedStudies++
                fromDayIndex = weekdayIndex.getValue(fill.day)
                guard++
            }

            // One spaced review on a non-class day for this subject.
            val review = placeReview(classDaysOfSubject)
            if (review != null) {
                placed.add(
                    SuggestedSession(
                        subjectId, name, StudySessionKind.REVIEW,
                        review.day, review.start, review.end
                    )
                )
            }
        }

        private fun placeStudyAfter(
            classBlock: ClassBlock
        ): PlacedBlock? {
            val block = config.studyBlockMinutes
            val day = classBlock.day
            val earliestStart = classBlock.endMinute + 30

            val gap = bestGap(day, earliestStart, block)
            if (gap != null && fitsDailyCap(day, block)) {
                addSuggestedMinutes(day, block)
                return gap
            }

            // Fall back to later days of the week, spaced at least a day from the class day.
            val startIdx = weekdayIndex.getValue(day)
            for (offset in 1..6) {
                val candidateDay = dayOfIndex((startIdx + offset) % 7)
                if (!fitsDailyCap(candidateDay, block)) continue
                val candidateGap = bestGap(candidateDay, config.preferredStartMinute, block)
                if (candidateGap != null) {
                    addSuggestedMinutes(candidateDay, block)
                    return candidateGap
                }
            }
            return null
        }

        /** Places a study on the next free day at or after [fromDayIndex] + 1 (spaced). */
        private fun placeStudyNextFree(fromDayIndex: Int): PlacedBlock? {
            val block = config.studyBlockMinutes
            for (offset in 1..7) {
                val candidateDay = dayOfIndex(fromDayIndex + offset)
                if (!fitsDailyCap(candidateDay, block)) continue
                val candidateGap = bestGap(candidateDay, config.preferredStartMinute, block)
                if (candidateGap != null) {
                    addSuggestedMinutes(candidateDay, block)
                    return candidateGap
                }
            }
            return null
        }

        private fun placeReview(
            subjectClassDays: List<DayOfWeek>
        ): PlacedBlock? {
            val block = config.reviewBlockMinutes
            val isClassDay = { day: DayOfWeek -> subjectClassDays.any { it == day } }

            // Candidate days: weekend first if allowed, then non-class weekdays, then any day.
            val candidates = mutableListOf<DayOfWeek>()
            if (config.weekendStudy) {
                candidates.add(DayOfWeek.SATURDAY)
                candidates.add(DayOfWeek.SUNDAY)
            }
            candidates.addAll((0..6).map { dayOfIndex(it) }.filterNot { isClassDay(it) })

            for (day in candidates) {
                if (!fitsDailyCap(day, block)) continue
                val gap = bestGap(day, config.preferredStartMinute, block)
                if (gap != null) {
                    addSuggestedMinutes(day, block)
                    return gap
                }
            }
            return null
        }

        private fun dayOfIndex(index: Int): DayOfWeek =
            DayOfWeek.values()[index % 7]

        private fun fitsDailyCap(day: DayOfWeek, block: Int): Boolean {
            val cap = config.maxSuggestedMinutesPerDay
            if (cap <= 0) return true
            return suggestedMinutesByDay[weekdayIndex.getValue(day)] + block <= cap
        }

        private fun addSuggestedMinutes(day: DayOfWeek, minutes: Int) {
            suggestedMinutesByDay[weekdayIndex.getValue(day)] += minutes
        }

        private data class PlacedBlock(val day: DayOfWeek, val start: Int, val end: Int)

        private data class Interval(val start: Int, val end: Int) {
            fun length(): Int = end - start
        }

        /** Best free gap on [day] for a block of [block] minutes starting no earlier than [earliestStart]. */
        private fun bestGap(day: DayOfWeek, earliestStart: Int, block: Int): PlacedBlock? {
            for (gap in freeGaps(day).sortedWith(compareBy({ it.start }, { it.end }))) {
                if (gap.length() < block) continue
                val effStart = maxOf(gap.start, earliestStart)
                if (gap.end - effStart < block) continue
                val placed = Interval(effStart, effStart + block)
                if (startsRightBeforeLongClass(day, placed)) continue
                return PlacedBlock(day, effStart, effStart + block)
            }
            return null
        }

        /** Don't start a study session immediately before a long class (>= 2h). */
        private fun startsRightBeforeLongClass(day: DayOfWeek, gap: Interval): Boolean {
            val dayClasses = classDays[weekdayIndex.getValue(day)]
            return dayClasses.any { c ->
                val length = c.endMinute - c.startMinute
                length >= 120 && c.startMinute >= gap.start && c.startMinute - gap.start <= 30
            }
        }

        private fun mergedOccupied(day: DayOfWeek): List<Interval> {
            val intervals = classDays[weekdayIndex.getValue(day)].map { Interval(it.startMinute, it.endMinute) } +
                placed.filter { it.day == day }.map { Interval(it.startMinute, it.endMinute) }
            if (intervals.isEmpty()) return emptyList()
            val sorted = intervals.sortedWith(compareBy({ it.start }, { it.end }))
            val merged = mutableListOf(sorted[0])
            for (i in 1 until sorted.size) {
                val last = merged.last()
                val cur = sorted[i]
                if (cur.start <= last.end) {
                    merged[merged.size - 1] = last.copy(end = maxOf(last.end, cur.end))
                } else {
                    merged.add(cur)
                }
            }
            return merged
        }

        private fun freeGaps(day: DayOfWeek): List<Interval> {
            val windowStart = config.preferredStartMinute
            val windowEnd = config.preferredEndMinute
            val occupied = mergedOccupied(day)
            val gaps = mutableListOf<Interval>()
            var cursor = windowStart
            for (occ in occupied) {
                if (occ.start >= windowEnd) break
                if (occ.end <= windowStart) continue
                if (occ.start > cursor) gaps.add(Interval(cursor, minOf(occ.start, windowEnd)))
                cursor = maxOf(cursor, occ.end)
            }
            if (cursor < windowEnd) gaps.add(Interval(cursor, windowEnd))
            return gaps
        }
    }
}