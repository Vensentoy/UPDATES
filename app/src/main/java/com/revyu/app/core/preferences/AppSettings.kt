package com.revyu.app.core.preferences

enum class ThemeMode(val storedName: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedName == value } ?: SYSTEM
    }
}

/**
 * The four accent swatches from revyu-prototype.html. Only the accent is persisted here —
 * the full palette per accent lives in core/theme/Color.kt (AccentPalettes), keeping the
 * preferences layer free of UI types.
 */
enum class AccentColor(val storedName: String) {
    ORANGE("orange"),
    GREEN("green"),
    PURPLE("purple"),
    BLUE("blue");

    companion object {
        fun fromStored(value: String?): AccentColor =
            entries.firstOrNull { it.storedName == value } ?: ORANGE
    }
}

enum class CatExpression {
    CHEER,
    LAY,
    STUDY
}

fun AccentColor.getCatDrawableRes(expression: CatExpression = CatExpression.CHEER): Int = when (this) {
    AccentColor.ORANGE -> when (expression) {
        CatExpression.CHEER -> com.revyu.app.R.drawable.cat_orange_cheer
        CatExpression.LAY -> com.revyu.app.R.drawable.cat_orange_lay
        CatExpression.STUDY -> com.revyu.app.R.drawable.cat_orange_study
    }
    AccentColor.PURPLE -> when (expression) {
        CatExpression.CHEER -> com.revyu.app.R.drawable.cat_tuxedo_cheer
        CatExpression.LAY -> com.revyu.app.R.drawable.cat_tuxedo_lay
        CatExpression.STUDY -> com.revyu.app.R.drawable.cat_tuxedo_study
    }
    AccentColor.GREEN -> when (expression) {
        CatExpression.CHEER -> com.revyu.app.R.drawable.cat_white_cheer
        CatExpression.LAY -> com.revyu.app.R.drawable.cat_white_lay
        CatExpression.STUDY -> com.revyu.app.R.drawable.cat_white_study
    }
    AccentColor.BLUE -> when (expression) {
        CatExpression.CHEER -> com.revyu.app.R.drawable.cat_tabby_cheer
        CatExpression.LAY -> com.revyu.app.R.drawable.cat_tabby_lay
        CatExpression.STUDY -> com.revyu.app.R.drawable.cat_tabby_study
    }
}

/** Non-sensitive user preferences; secrets live in EncryptedSharedPreferences instead. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.ORANGE,
    val autoStudySuggestions: Boolean = true,
    val studyBlockMinutes: Int = 45,
    val reviewBlockMinutes: Int = 45,
    val preferredStartHour: Int = 16,
    val preferredEndHour: Int = 21,
    val weekendStudy: Boolean = true,
    val maxSuggestedMinutesPerDay: Int = 90,
    val widgetUpdatesEnabled: Boolean = true
) {
    fun schedulerConfig(): com.revyu.app.core.calendar.SchedulerConfig =
        com.revyu.app.core.calendar.SchedulerConfig(
            autoGenerate = autoStudySuggestions,
            studyBlockMinutes = studyBlockMinutes,
            reviewBlockMinutes = reviewBlockMinutes,
            preferredStartMinute = preferredStartHour * 60,
            preferredEndMinute = preferredEndHour * 60,
            weekendStudy = weekendStudy,
            maxSuggestedMinutesPerDay = maxSuggestedMinutesPerDay
        )
}