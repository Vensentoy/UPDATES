package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Flags describing the Home widget grid. */
object HomeWidgetFlags {
    const val FLAG_SMALL = "small"      // 2x2 on the launcher
    const val FLAG_MEDIUM = "medium"    // 4x2 on the launcher
    const val FLAG_LARGE = "large"      // 4x4 on the launcher
    const val FLAG_EMPTY = "empty"      // no schedule loaded yet
}

/**
 * One widget in the in-app Home grid. `key` is a stable [HomeWidgetKeys] slug; the grid is
 * fully user-reorderable (move up/down) and widgets can be hidden without deleting data.
 */
@Entity(tableName = "home_widget_prefs")
data class HomeWidgetPrefEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
    val sortOrder: Int
)

object HomeWidgetKeys {
    val TODAY_SCHEDULE = "today_schedule"
    val NEXT_CLASS = "next_class"
    val STUDY_PLAN = "study_plan"
    val WEEK_OVERVIEW = "week_overview"
    val STUDY_MINUTES = "study_minutes"
    val SUBJECTS = "subjects"

    val FLASHCARD = "flashcard_of_day"
    val QUIZ = "question_of_day"
    val PRACTICE_SCORES = "recent_scores"
    val EXAM_COUNTDOWN = "next_exam"
    val PRACTICE_READY = "practice_ready"
}

/** Every widget kind the grid knows about, in the canonical reading order. */
val homeWidgetCatalog: List<String> = listOf(
    HomeWidgetKeys.TODAY_SCHEDULE,
    HomeWidgetKeys.NEXT_CLASS,
    HomeWidgetKeys.STUDY_PLAN,
    HomeWidgetKeys.FLASHCARD,
    HomeWidgetKeys.QUIZ,
    HomeWidgetKeys.WEEK_OVERVIEW,
    HomeWidgetKeys.STUDY_MINUTES,
    HomeWidgetKeys.SUBJECTS,
    HomeWidgetKeys.PRACTICE_SCORES,
    HomeWidgetKeys.EXAM_COUNTDOWN,
    HomeWidgetKeys.PRACTICE_READY
)

/** The interactive study-practice widgets. They render full-width and out-of-band from the schedule widgets. */
val practiceWidgetKeys: Set<String> = setOf(
    HomeWidgetKeys.FLASHCARD,
    HomeWidgetKeys.QUIZ,
    HomeWidgetKeys.PRACTICE_SCORES,
    HomeWidgetKeys.EXAM_COUNTDOWN,
    HomeWidgetKeys.PRACTICE_READY
)

/** Widgets whose content can be pinned to a specific Study Set via the Widgets tab. */
val pinnableWidgetKeys: Set<String> = setOf(
    HomeWidgetKeys.FLASHCARD,
    HomeWidgetKeys.QUIZ
)

/** Default grid: the essential widgets on first launch, in a sensible reading order. */
val defaultHomeWidgetKeys: List<String> = listOf(
    HomeWidgetKeys.TODAY_SCHEDULE,
    HomeWidgetKeys.NEXT_CLASS,
    HomeWidgetKeys.STUDY_PLAN,
    HomeWidgetKeys.FLASHCARD,
    HomeWidgetKeys.QUIZ,
    HomeWidgetKeys.WEEK_OVERVIEW,
    HomeWidgetKeys.STUDY_MINUTES,
    HomeWidgetKeys.SUBJECTS
)

/** Constructs the default, unordered set of grid rows. */
fun defaultHomeWidgetPrefs(): List<HomeWidgetPrefEntity> =
    defaultHomeWidgetKeys.withIndex().map { (i, key) ->
        HomeWidgetPrefEntity(key = key, enabled = true, sortOrder = i)
    }