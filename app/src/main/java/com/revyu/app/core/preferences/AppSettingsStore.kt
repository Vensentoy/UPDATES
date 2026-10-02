package com.revyu.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "revyu_settings")

private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val AUTO_SUGGEST = booleanPreferencesKey("auto_study_suggestions")
    val STUDY_BLOCK = intPreferencesKey("study_block_minutes")
    val REVIEW_BLOCK = intPreferencesKey("review_block_minutes")
    val START_HOUR = intPreferencesKey("preferred_start_hour")
    val END_HOUR = intPreferencesKey("preferred_end_hour")
    val WEEKEND = booleanPreferencesKey("weekend_study")
    val MAX_MIN_PER_DAY = intPreferencesKey("max_suggested_minutes_per_day")
    val WIDGET_UPDATES = booleanPreferencesKey("widget_updates_enabled")
    val FLASHCARD_PIN = stringPreferencesKey("widget_pin_flashcard")
    val QUIZ_PIN = stringPreferencesKey("widget_pin_question")
}

/** Which Study Set (if any) each pinnable practice widget is locked to. Null = all sets. */
data class PracticeWidgetPins(
    val flashcardStudySetId: String? = null,
    val questionStudySetId: String? = null
)

class AppSettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromStored(prefs[Keys.THEME_MODE]),
            accentColor = AccentColor.fromStored(prefs[Keys.ACCENT_COLOR]),
            autoStudySuggestions = prefs[Keys.AUTO_SUGGEST] ?: true,
            studyBlockMinutes = prefs[Keys.STUDY_BLOCK] ?: 45,
            reviewBlockMinutes = prefs[Keys.REVIEW_BLOCK] ?: 45,
            preferredStartHour = prefs[Keys.START_HOUR] ?: 16,
            preferredEndHour = prefs[Keys.END_HOUR] ?: 21,
            weekendStudy = prefs[Keys.WEEKEND] ?: true,
            maxSuggestedMinutesPerDay = prefs[Keys.MAX_MIN_PER_DAY] ?: 90,
            widgetUpdatesEnabled = prefs[Keys.WIDGET_UPDATES] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.storedName }
    }

    /** Persists the active accent swatch (one of the four prototype accents). */
    suspend fun setAccentColor(accent: AccentColor) {
        context.appSettingsDataStore.edit { it[Keys.ACCENT_COLOR] = accent.storedName }
    }

    suspend fun setAutoStudySuggestions(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.AUTO_SUGGEST] = enabled }
    }

    suspend fun setStudyBlockMinutes(minutes: Int) {
        context.appSettingsDataStore.edit { it[Keys.STUDY_BLOCK] = minutes.coerceIn(30, 60) }
    }

    suspend fun setReviewBlockMinutes(minutes: Int) {
        context.appSettingsDataStore.edit { it[Keys.REVIEW_BLOCK] = minutes.coerceIn(30, 60) }
    }

    suspend fun setPreferredStartHour(hour: Int) {
        context.appSettingsDataStore.edit { it[Keys.START_HOUR] = hour.coerceIn(6, 20) }
    }

    suspend fun setPreferredEndHour(hour: Int) {
        context.appSettingsDataStore.edit { it[Keys.END_HOUR] = hour.coerceIn(7, 23) }
    }

    suspend fun setWeekendStudy(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.WEEKEND] = enabled }
    }

    suspend fun setMaxSuggestedMinutesPerDay(minutes: Int) {
        context.appSettingsDataStore.edit { it[Keys.MAX_MIN_PER_DAY] = minutes.coerceIn(15, 240) }
    }

    suspend fun setWidgetUpdatesEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.WIDGET_UPDATES] = enabled }
    }

    val practiceWidgetPins: Flow<PracticeWidgetPins> = context.appSettingsDataStore.data.map { prefs ->
        PracticeWidgetPins(
            flashcardStudySetId = prefs[Keys.FLASHCARD_PIN],
            questionStudySetId = prefs[Keys.QUIZ_PIN]
        )
    }

    /** Pins the flashcard widget to one Study Set; null clears the pin (use all sets). */
    suspend fun setFlashcardPin(studySetId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (studySetId == null) prefs.remove(Keys.FLASHCARD_PIN) else prefs[Keys.FLASHCARD_PIN] = studySetId
        }
    }

    /** Pins the question widget to one Study Set; null clears the pin (use all sets). */
    suspend fun setQuestionPin(studySetId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (studySetId == null) prefs.remove(Keys.QUIZ_PIN) else prefs[Keys.QUIZ_PIN] = studySetId
        }
    }
}