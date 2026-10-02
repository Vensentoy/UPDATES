package com.revyu.app.core.util.schoolcalendar

import java.time.LocalDate

/** Month/day grammar shared by the calendar files (case-insensitive, first winning match). */
internal object SchoolCalendarDates {

    private val M: Map<String, Int> = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    val MONTH_DAY = Regex("""(?i)^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?[\s,]+(\d{1,2})(?:st|nd|rd|th)?(?:[\s,]+(\d{4}))?""")
    val NUMERIC = Regex("""^(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?""")
    val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
    val BARE_DAY = Regex("""^(\d{1,2})(?:st|nd|rd|th)?""")
    val DAY_RANGE = Regex("""(\d{1,2})(?:st|nd|rd|th)?\s*[-–—]\s*(\d{1,2})(?:st|nd|rd|th)?""")
    val TIME_RANGE = Regex("""(?i)\b(\d{1,2}):(\d{2})\s*(?:-|–|—|to)\s*(\d{1,2}):(\d{2})\s*(am|pm|nn)?""")
    val TIME_SINGLE = Regex("""(?i)\b(\d{1,2}):(\d{2})\s*(am|pm|nn)""")
    val WEEKDAY = Regex("""(?i)\b(mon|tue|tues|wed|wednes|thu|thur|thurs|fri|sat|sun)[a-z]*\b""")

    /** Section scoping: bare days/ranges below a month header reuse [month] (+ [year]). */
    data class Scope(var month: Int? = null, var year: Int? = null)

    /** A leading date token with the OCR text row the parser consumed to reach it. */
    data class DateToken(
        val date: LocalDate,
        val consumed: String,
        val dayToken: String,
        val fuzzy: Boolean
    )

    fun monthNum(s: String): Int? = M[s.trim().lowercase().take(3)]

    fun safeDate(month: Int, day: Int, year: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: java.time.DateTimeException) {
        null
    }
}
