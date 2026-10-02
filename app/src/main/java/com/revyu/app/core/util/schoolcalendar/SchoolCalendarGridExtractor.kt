package com.revyu.app.core.util.schoolcalendar

import java.time.LocalDate
import java.util.Locale

/**
 * Position-aware extraction for scanned *month-grid* calendars (JUN 2026 -> MAR 2027 wall
 * planners), which defeat linear row parsing: OCR reads grid cells column-major, so naive
 * line order interleaves month headers, the weekday row, day numbers, and cell text.
 *
 * Pipeline:
 *  1. Word boxes (ML Kit elements) are classified: month headers, day numbers, explicit
 *     dates, weekday letters / numeric residue (noise), and event text.
 *  2. Month headers carve vertical zones; every day number inside a zone becomes a dated
 *     candidate for that month (year inherited, Dec -> Jan rolls over).
 *  3. Event-text tokens that touch vertically/horizontally are clustered into cells.
 *  4. Each cell is dated: an explicit "Sep 19 …" that ends just left of it wins; otherwise
 *     the nearest day number in its month zone handles it.
 *  5. Cells matching academic vocabulary become "confirmed"; everything else is kept as a
 *     catch-all so the user never loses a cell, only fixes its date in review.
 *
 * Noise (day rows, weekday letters, page-header fragments) never becomes events or warnings.
 */
object SchoolCalendarGridExtractor {

    private val MONTH_WORD = Regex(
        """(?i)^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"""
    )
    private val YEAR4 = Regex("""\b(20\d{2})\b""")
    private val DAY_ONLY = Regex("""^\d{1,2}$""")
    private val DAY_START = Regex("""^\d{1,2}\b""")
    private val NUMERIC_RESIDUE = Regex("""^[\d\s./-]+$""")
    private val WEEKDAY_LETTERS = setOf("M", "T", "W", "F", "S")

    /** Vocabulary that marks a headline calendar event (vs. an unverified catch-all cell). */
    private val KEYWORDS = listOf(
        "intramural", "sports",
        "midterm", "final exam", "examination", "prelim",
        "semestral", "semester break", "christmas break",
        "start of classes", "beginning of classes", "opening of classes", "end of classes",
        "admission", "enrollment",
        "submission of", "clearance", "signing of",
        "orientation", "freshman", "graduation", "foundation day",
        "independence day", "ninoy", "aquino", "all saints", "all souls",
        "holy week", "charter day", "holiday", "non-working"
    )

    /** Section markers that must never surface as events. */
    private val EXCLUDED_PHRASES = listOf(
        "first semester", "second semester", "school year", "s.y.", "academic year"
    )

    private data class Token(
        val text: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    ) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    private sealed class Kind {
        object Noise : Kind()
        object Day : Kind()
        data class Date(val date: LocalDate) : Kind()
        data class ZoneHeader(val month: Int, val year: Int?) : Kind()
        object Text : Kind()
    }

    private class MonthZone(val month: Int, val headerYear: Int?, val yTop: Float, val cx: Float) {
        var yBottom = Float.MAX_VALUE
        var year = headerYear ?: DEFAULT_YEAR
    }

    private data class Cell(val tokens: List<Token>) {
        val cx get() = tokens.map { it.cx }.average().toFloat()
        val cy get() = tokens.map { it.cy }.average().toFloat()
        val text get() = tokens
            .sortedWith(compareBy({ it.y }, { it.x }))
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parse(lines: List<OcrLine>): SchoolCalendarParseReport {
        val tokens = flattenTokens(lines)
        if (tokens.isEmpty()) return SchoolCalendarParseReport(emptyList())

        val lineH = medianHeight(tokens)
        val headers = mutableListOf<Pair<Token, MonthZone>>()
        val dayTokens = mutableListOf<Token>()
        val dateTokens = mutableListOf<Pair<Token, LocalDate>>()
        val textTokens = mutableListOf<Token>()

        for (t in tokens) {
            when (val kind = classifyToken(t)) {
                Kind.Noise -> Unit
                Kind.Day -> dayTokens += t
                is Kind.Date -> dateTokens += t to kind.date
                is Kind.ZoneHeader -> {
                    val zone = MonthZone(kind.month, kind.year, t.y, t.cx)
                    headers += t to zone
                }
                Kind.Text -> textTokens += t
            }
        }
        if (headers.isEmpty()) return SchoolCalendarParseReport(emptyList(), textTokens.map { it.text })

        val zones = assignZones(headers)
        val cells = clusterCells(textTokens, lineH)

        val events = mutableListOf<ParsedSchoolCalendarEvent>()
        val warns = mutableListOf<String>()

        for (cell in cells) {
            val zone = zoneFor(zones, cell)
            var date: LocalDate? = null
            var kindResult = EventKind.CATCH_ALL

            val explicit = explicitDateOn(cell, dateTokens)
            if (explicit != null) {
                date = explicit
                kindResult = EventKind.EXPLICIT_DATE
            }
            if (date == null && zone != null) {
                date = nearestDay(cell, dayTokens, zone, lineH)
                if (date != null) {
                    kindResult = if (matchesKeyword(cell.text)) EventKind.KEYWORD else EventKind.CATCH_ALL
                }
            }

            val title = cell.text
            if (title.isBlank() || EXCLUDED_PHRASES.any { title.contains(it, ignoreCase = true) }) continue
            if (title.length <= 2 && kindResult != EventKind.EXPLICIT_DATE) continue

            if (date == null) {
                if (title.length >= 6) warns += "row: $title"
                continue
            }

            val confidence = when (kindResult) {
                EventKind.EXPLICIT_DATE -> 1f
                EventKind.KEYWORD -> if (zone != null && sameBand(cell, dayTokens, zone, lineH)) 0.95f else 0.8f
                EventKind.CATCH_ALL -> 0.6f
            }
            events += ParsedSchoolCalendarEvent(
                date = date,
                title = title,
                confidence = confidence,
                kind = kindResult
            )
        }

        return SchoolCalendarParseReport(
            events = dedupe(events),
            warnings = warns.distinct()
        )
    }

    // ---------------------------------------------------------------- classification

    private fun classifyToken(t: Token): Kind {
        val s = t.text.trim()
        if (s.isEmpty()) return Kind.Noise

        if (YEAR4.matchEntire(s) != null) return Kind.Noise // "2026" — consumed, never an event
        if (DAY_ONLY.matchEntire(s) != null && s.toInt() in 1..31) return Kind.Day
        if (NUMERIC_RESIDUE.matches(s)) return Kind.Noise // "24 17", "311", "25-27"
        if (s.length == 1 && s.uppercase(Locale.ROOT) in WEEKDAY_LETTERS) return Kind.Noise

        // Month header BEFORE date lexing: "AUGUST 2026", "SEPT. 2026", "MARCH W 2027" must
        // not let MONTH_DAY greedily read the first two digits of the year as a day.
        // A header is a month word *not* followed by a day number ("Aug 24" stays a date).
        MONTH_WORD.find(s)?.let { mw ->
            val after = s.substring(mw.value.length).trim()
            if (!DAY_START.containsMatchIn(after)) {
                val mo = SchoolCalendarDates.monthNum(mw.groupValues[1]) ?: return Kind.Noise
                return Kind.ZoneHeader(
                    month = mo,
                    year = YEAR4.find(s)?.groupValues?.get(1)?.toInt()
                )
            }
        }

        SchoolCalendarDates.MONTH_DAY.find(s)?.let { m ->
            val mo = SchoolCalendarDates.monthNum(m.groupValues[1]) ?: return Kind.Noise
            val yr = m.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: DEFAULT_YEAR
            val d = SchoolCalendarDates.safeDate(mo, m.groupValues[2].toInt(), yr)
            if (d != null) return Kind.Date(d)
        }
        SchoolCalendarDates.NUMERIC.find(s)?.let { n ->
            val yr = n.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: DEFAULT_YEAR
            val d = SchoolCalendarDates.safeDate(n.groupValues[1].toInt(), n.groupValues[2].toInt(), yr)
            if (d != null) return Kind.Date(d)
        }
        SchoolCalendarDates.ISO.matchEntire(s)?.let { iso ->
            val d = SchoolCalendarDates.safeDate(
                iso.groupValues[2].toInt(), iso.groupValues[3].toInt(), iso.groupValues[1].toInt()
            )
            if (d != null) return Kind.Date(d)
        }

        if (s.length <= 2) return Kind.Noise // "SM", "FS", "w", "w/"
        return Kind.Text
    }

    // ---------------------------------------------------------------- zones & dating

    private fun assignZones(headers: List<Pair<Token, MonthZone>>): List<MonthZone> {
        val ordered = headers.sortedBy { it.first.y }
        ordered.forEachIndexed { i, (_, zone) ->
            if (i + 1 < ordered.size) zone.yBottom = ordered[i + 1].first.y
        }
        var runningYear: Int? = null
        var runningMonth: Int? = null
        for ((_, zone) in ordered) {
            if (zone.headerYear != null) runningYear = zone.headerYear
            if (runningMonth != null && runningMonth == 12 && zone.month == 1) {
                runningYear = (runningYear ?: DEFAULT_YEAR) + 1
            }
            zone.year = zone.headerYear ?: runningYear ?: DEFAULT_YEAR
            runningMonth = zone.month
        }
        return ordered.map { it.second }.filter { it.year in 2020..2035 }
    }

    private fun zoneFor(zones: List<MonthZone>, cell: Cell): MonthZone? =
        zones.filter { cell.cy in it.yTop..it.yBottom }
            .minByOrNull { kotlin.math.abs(cell.cx - it.cx) }

    private fun explicitDateOn(cell: Cell, dateTokens: List<Pair<Token, LocalDate>>): LocalDate? {
        if (dateTokens.isEmpty()) return null
        val lineH = medianHeight(dateTokens.map { it.first })
        val cellLeft = cell.tokens.minOf { it.x }
        val cellCy = cell.tokens.map { it.cy }.average()
        // Same text row, ending just left of the cell ("Sep 19 Start of Classes").
        val sameRow = dateTokens.filter { (t, _) ->
            kotlin.math.abs(t.cy - cellCy) <= lineH * 1.2f && t.x + t.w <= cellLeft + 24f
        }
        if (sameRow.isNotEmpty()) {
            return sameRow.minByOrNull { (t, _) -> kotlin.math.abs(t.cx - cell.cx) }!!.second
        }
        // Date spilled to the row above the caption.
        val above = dateTokens.filter { (t, _) -> t.cy < cellCy }
        return above.minByOrNull { (t, _) -> kotlin.math.abs(t.cy - cellCy) }?.second
    }

    private fun nearestDay(
        cell: Cell,
        dayTokens: List<Token>,
        zone: MonthZone,
        lineH: Float
    ): LocalDate? {
        var best: LocalDate? = null
        var bestScore = Float.MAX_VALUE
        for (d in dayTokens) {
            if (d.cy !in zone.yTop..zone.yBottom) continue
            val dy = kotlin.math.abs(d.cy - cell.cy)
            if (dy > lineH * 4f) continue
            val score = kotlin.math.abs(d.cx - cell.cx) + dy * 0.5f
            if (score < bestScore) {
                val date = SchoolCalendarDates.safeDate(zone.month, d.text.toInt(), zone.year)
                if (date != null) {
                    bestScore = score
                    best = date
                }
            }
        }
        return best
    }

    private fun sameBand(cell: Cell, dayTokens: List<Token>, zone: MonthZone, lineH: Float): Boolean {
        val near = dayTokens
            .filter { it.cy in zone.yTop..zone.yBottom }
            .minByOrNull { kotlin.math.abs(it.cy - cell.cy) } ?: return false
        return kotlin.math.abs(near.cy - cell.cy) <= lineH * 1.5f
    }

    /** Merge text tokens that belong to the same wrapped cell (proximity union-find). */
    private fun clusterCells(textTokens: List<Token>, lineH: Float): List<Cell> {
        if (textTokens.isEmpty()) return emptyList()
        val parent = IntArray(textTokens.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var c = i
            while (parent[c] != c) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }
        val rowSlack = lineH * 1.6f
        val colSlack = lineH * 4.5f
        for (i in textTokens.indices) {
            for (j in i + 1 until textTokens.size) {
                val a = textTokens[i]
                val b = textTokens[j]
                if (kotlin.math.abs(a.cy - b.cy) <= rowSlack && kotlin.math.abs(a.cx - b.cx) <= colSlack) {
                    val ra = find(i)
                    val rb = find(j)
                    if (ra != rb) parent[rb] = ra
                }
            }
        }
        return textTokens.indices.groupBy { find(it) }
            .values.map { Cell(it.map { index -> textTokens[index] }) }
    }

    private fun dedupe(events: List<ParsedSchoolCalendarEvent>): List<ParsedSchoolCalendarEvent> {
        val seen = mutableSetOf<String>()
        return events.filter { e ->
            val key = e.date.toString() + "|" + e.title.lowercase(Locale.ROOT)
            seen.add(key)
        }
    }

    private fun matchesKeyword(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return KEYWORDS.any { t.contains(it) }
    }

    private fun medianHeight(tokens: List<Token>): Float =
        tokens.map { it.h }.sorted().let { if (it.isEmpty()) 1f else it[it.size / 2] }

    private fun flattenTokens(lines: List<OcrLine>): List<Token> {
        val out = mutableListOf<Token>()
        for (line in lines) {
            if (line.elements.isNotEmpty()) {
                for (el in line.elements) {
                    if (el.text.isBlank()) continue
                    out += Token(el.text.trim(), el.x, el.y, el.width, el.height)
                }
            } else {
                // Text-layer fallback: give each word a synthetic box in reading order.
                val words = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                val step = 36f
                words.forEachIndexed { i, w ->
                    out += Token(w, line.x + i * step, line.y.toFloat(), step * 0.9f, 20f)
                }
            }
        }
        return out
    }

    private const val DEFAULT_YEAR = 2026
}