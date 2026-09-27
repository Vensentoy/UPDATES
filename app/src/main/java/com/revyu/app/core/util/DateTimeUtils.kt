package com.revyu.app.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateTimeUtils {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeFromMinuteOfDay(minutes: Int): LocalTime =
        LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

    fun formatTime(minutes: Int): String = timeFromMinuteOfDay(minutes).format(timeFormatter)

    fun formatTimeRange(startMinutes: Int, endMinutes: Int): String =
        "${formatTime(startMinutes)} – ${formatTime(endMinutes)}"

    fun dayLabel(dayOfWeek: Int, short: Boolean = true): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(
            if (short) TextStyle.SHORT else TextStyle.FULL,
            Locale.US
        )

    /** Days from today (0 = today) until the next occurrence of the given weekday. */
    fun daysUntilNextOccurrence(dayOfWeek: Int, from: LocalDate = LocalDate.now()): Int {
        val target = DayOfWeek.of(dayOfWeek)
        var diff = target.value - from.dayOfWeek.value
        if (diff < 0) diff += 7
        return diff
    }

    /** Best-effort parse of common schedule time formats: "9:00 AM", "09:00", "13:30". */
    fun parseTimeFlexible(raw: String): LocalTime? {
        val trimmed = raw.trim()
        val patterns = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm", "h a", "ha")
        for (pattern in patterns) {
            try {
                return LocalTime.parse(trimmed.uppercase(Locale.US), DateTimeFormatter.ofPattern(pattern, Locale.US))
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    /** Best-effort parse of a day token into java.time.DayOfWeek value (1=Mon..7=Sun). Supports "M/T/W/TH/F/S/SU" style and full names. */
    fun parseDayFlexible(raw: String): Int? {
        val token = raw.trim().uppercase(Locale.US)
        return when (token) {
            "M", "MON", "MONDAY" -> 1
            "T", "TUE", "TUES", "TUESDAY" -> 2
            "W", "WED", "WEDNESDAY" -> 3
            "TH", "THU", "THUR", "THURS", "THURSDAY" -> 4
            "F", "FRI", "FRIDAY" -> 5
            "S", "SAT", "SATURDAY" -> 6
            "SU", "SUN", "SUNDAY" -> 7
            else -> null
        }
    }
}
