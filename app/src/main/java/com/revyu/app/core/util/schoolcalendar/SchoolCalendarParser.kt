package com.revyu.app.core.util.schoolcalendar

import java.time.LocalDate

/**
 * Rows -> one dated, non-recurring event per row. Grammar (see SchoolCalendarDates for the
 * shared token grammar; this passes keep markers):
 *  - Month/sy headers ("SEPT. 2026", "S.Y. 20xx-20xx") scope month+year for the rows below.
 *  - Dated rows: MONTH_DAY ("Aug 24", "Aug 24, 2026"), NUMERIC ("08/24" or "08/24/2026"),
 *    ISO ("2026-08-24"), or a bare day ("24"/"24th") within a month scope. Ranges (Aug 24-26)
 *    expand to one event per day; times and weekdays are consumed, never part of the title.
 *  - Non-dated rows continue the previous event's title; "|" splits scanner two-column rows.
 *  - Confidence drops for fuzzy dates (bare day, assumed year); warnings are non-fatal.
 */
object SchoolCalendarParser {

    private val MONTH_DAY = Regex(
        """(?i)^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?[\s,]+(\d{1,2})(?:st|nd|rd|th)?(?:[\s,]+(\d{4}))?"""
    )
    private val NUMERIC = Regex("""^(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?""")
    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val BARE_DAY = Regex("""^(\d{1,2})(?:st|nd|rd|th)?""")
    private val DAY_RANGE = Regex("""(\d{1,2})(?:st|nd|rd|th)?\s*[-â€“â€”]\s*(\d{1,2})(?:st|nd|rd|th)?""")
    private val TIME_RANGE = Regex(
        """(?i)\b(\d{1,2}):(\d{2})(?:\s*(?:am|pm|nn))?\s*(?:-|â€“|â€”|to)\s*(\d{1,2}):(\d{2})\s*(?:am|pm|nn)?"""
    )
    private val TIME_SINGLE = Regex("""(?i)\b(\d{1,2}):(\d{2})\s*(am|pm|nn)""")
    private val WEEKDAY = Regex("""(?i)\b(mon|tue|tues|wed|wednes|thu|thur|thurs|fri|sat|sun)[a-z]*\b""")
    private val SCHOOL_YEAR_HEADER = Regex(
        """(?i)^(?:s\.?\s*y\.?\s*20\d{2}[\s-]\s*20\d{2}|first\s+semester|second\s+semester|semester)$"""
    )
    private val GLUED_RANGE = Regex("""^\s*[-–—]\s*(\d{1,2})(?:st|nd|rd|th)?""")
    private val MONTH_HEADER = Regex(
        """(?i)^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?[\s,]*(\d{4})?[\s,]*$"""
    )

    private fun monthOf(s: String): Int? = when (s.take(3).lowercase()) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
        else -> null
    }

    private fun d(month: Int, day: Int, year: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: Exception) {
        null
    }

    fun parse(lines: List<OcrLine>): SchoolCalendarParseReport {
        val anyGeometry = lines.any { it.elements.isNotEmpty() }
        return if (anyGeometry) SchoolCalendarGridExtractor.parse(lines) else parseLinear(lines)
    }

    /** Left-over linear lexer for digital (text-layer) calendars without geometry. */
    private fun parseLinear(lines: List<OcrLine>): SchoolCalendarParseReport {
        val out = mutableListOf<ParsedSchoolCalendarEvent>()
        val warn = mutableListOf<String>()
        var mo: Int? = null
        var yr: Int? = null
        var open: ParsedSchoolCalendarEvent? = null

        fun mint(date: LocalDate, title: String, conf: Float, s: Int?, e: Int?) {
            out.add(ParsedSchoolCalendarEvent(date, title, s, e, conf))
            open = out.last()
        }

        for (row in lines) {
            for (seg in row.text.split("|")) {
                var s = seg.trim()
                s = WEEKDAY.replaceFirst(s, "").trim()
                if (s.isEmpty()) continue
                if (SCHOOL_YEAR_HEADER.matchEntire(s) != null) { warn.add(s); continue }
                val mh = MONTH_HEADER.matchEntire(s)
                if (mh != null) {
                    mo = monthOf(mh.groupValues[1])
                    yr = mh.groupValues[2].takeIf { it.isNotBlank() }?.toInt()
                    open = null
                    continue
                }
                var date: LocalDate? = null
                var taken = ""
                var fuzzy = false
                val iso = ISO.find(s)
                if (iso != null) { date = d(iso.groupValues[2].toInt(), iso.groupValues[3].toInt(), iso.groupValues[1].toInt()); taken = iso.value }
                if (date == null) {
                    val num = NUMERIC.find(s)
                    if (num != null) { date = d(num.groupValues[1].toInt(), num.groupValues[2].toInt(), num.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: yr ?: 2026); taken = num.value }
                }
                if (date == null) {
                    val md = MONTH_DAY.find(s)
                    if (md != null) {
                        val m = monthOf(md.groupValues[1]) ?: mo
                        if (m != null) { date = d(m, md.groupValues[2].toInt(), md.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: yr ?: 2026); taken = md.value }
                    }
                }
                if (date == null) {
                    val bare = BARE_DAY.find(s)
                    if (bare != null && mo != null) { date = d(mo, bare.groupValues[1].toInt(), yr ?: 2026); taken = bare.value; fuzzy = true }
                }
                if (date == null) {
                    warn.add("row: $s")
                    if (open != null) {
                        val u = open!!.copy(title = open!!.title + " " + s)
                        out[out.lastIndex] = u
                        open = u
                    }
                    continue
                }
                val rest = s.substring(taken.length)
                val gr = GLUED_RANGE.find(rest)
                val dr = gr ?: DAY_RANGE.find(rest)
                var st: Int? = null
                var en: Int? = null
                val tr = TIME_RANGE.find(rest)
                if (tr != null) { st = tr.groupValues[1].toInt() * 60 + tr.groupValues[2].toInt(); en = tr.groupValues[3].toInt() * 60 + tr.groupValues[4].toInt() }
                else {
                    val ts = TIME_SINGLE.find(rest)
                    if (ts != null) st = ts.groupValues[1].toInt() * 60 + ts.groupValues[2].toInt()
                }
                val t = rest.replace(GLUED_RANGE, " ").replace(TIME_RANGE, " ").replace(TIME_SINGLE, " ").replace(WEEKDAY, " ").trim().ifEmpty { "Untitled event" }
                if (dr != null) {
                    val a = if (gr != null) date.dayOfMonth else dr.groupValues[1].toInt()
                    val b = if (gr != null) dr.groupValues[1].toInt() else dr.groupValues[2].toInt()
                    val span = if (b >= a) (a..b) else (a..a)
                    for (x in span) {
                        val dd = d(date.monthValue, x, date.year)
                        if (dd != null) mint(dd, t, if (fuzzy) 0.7f else 1f, st, en)
                    }
                } else mint(date, t, if (fuzzy) 0.7f else 1f, st, en)
            }
        }
        return SchoolCalendarParseReport(out, warn)
    }
}

