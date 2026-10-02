package com.revyu.app.core.util

import java.util.Locale

data class ParsedScheduleBlock(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int
)

data class ParsedSubject(
    val name: String,
    val blocks: List<ParsedScheduleBlock>
)

/**
 * Best-effort Study Load parser. School Study Load documents vary a lot in layout, so
 * this is deliberately heuristic — it looks for a day-combo token (e.g. "MWF", "TTH")
 * and a time range on each line, and treats the rest of that line as the subject name.
 *
 * This is always followed by an editable review table in the UI (StudyLoadReviewScreen),
 * so an imperfect parse is never a dead end — the student can fix names/days/times
 * directly before anything is saved.
 */
object StudyLoadParser {

    private val timeRangeRegex = Regex(
        """(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*(?:-|–|to)\s*(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
        RegexOption.IGNORE_CASE
    )

    // Longest day tokens first so "TTH" isn't chopped into "T" + "TH" incorrectly, etc.
    private val dayTokenPattern = Regex(
        """\b(MTHF|MWF|TTHS|MTWTHF|TTH|SU|TH|MW|MT|M|T|W|F|S)\b""",
        RegexOption.IGNORE_CASE
    )

    // Matches LLCC's registrar subject-code format, e.g. "IT2601-024" — 2-6 letters,
    // 3-5 digits, a dash, 2-4 digits, with no internal whitespace. This single intact
    // token survives essentially any PDF text extractor's line-ordering quirks, which is
    // why it — not day/time adjacency — is the anchor signal for the table-aware parser.
    private val subjectCodeRegex = Regex("""\b[A-Z]{2,6}\d{3,5}-\d{2,4}\b""")

    // The short subject code column right after the long code, e.g. "TPCC 311", "GE 7",
    // "GEE 3", "FL 1" — one or more uppercase letters, then a number.
    private val shortCodePrefixRegex = Regex("""^\s*[A-Z]{2,6}\s+\d+(?:\.\d+)?\s+""")

    // Room codes in this format always pair "RM" with an immediately following digit
    // ("RM6", "CAS-RM6", "COT-RM2", "CAS-RM5A/COT-"). Requiring the digit avoids
    // accidentally stripping ordinary words that merely contain "RM".
    private val roomTokenRegex = Regex("""\S*RM\d\S*""")

    private val trailingUnitRegex = Regex("""\s+\d+(?:\.\d+)?\s*$""")

    fun parse(rawText: String): List<ParsedSubject> {
        // Prefer the tabular parser whenever it found at least 2 subject codes — after the
        // blank-name fix above, one row per detected code is guaranteed, so its count is a
        // reliable lower bound. Only fall back to the weaker same-line generic parser when
        // the tabular parser found nothing usable (no LLCC-style codes at all), and even
        // then, if generic finds MORE subjects than tabular did, prefer whichever found more
        // rather than blindly trusting tabular's "≥2" threshold.
        val tabular = parseTabularFormat(rawText)
        val generic = parseGenericLineFormat(rawText)
        return when {
            tabular.isEmpty() -> generic
            generic.size > tabular.size -> generic
            else -> tabular
        }
    }

    /**
     * Table-aware parser for LLCC's registrar Study Load layout. PDF text extractors
     * disagree on line ordering for wrapped table cells — some keep a subject's time and
     * day on the same line, some split a 2-meeting subject's schedule across the lines
     * immediately before and after its description, some group all of a subject's times
     * then all its days as separate lines. This parser is deliberately independent of any
     * single ordering:
     *
     * 1. Find every subject-code occurrence (a stable, intact token in every ordering
     *    style tested) and slice the text into per-subject spans between consecutive
     *    codes.
     * 2. Within each span, the first line always carries the code + short code +
     *    description (+ possibly an inline day/time if the subject meets once).
     * 3. Day/time pairs are extracted by matching all time ranges and all day tokens in a
     *    block independently, then pairing them by position — this is robust whether a
     *    line holds "time day" together or times and days land on separate lines.
     * 4. A subject whose first line has no inline pair needs its block(s) from
     *    elsewhere: a trailing line in the PREVIOUS subject's span (a wrapped table row
     *    "leaking" backward) and/or a trailing line in its own span. A one-step lookahead
     *    at the next subject's first line decides whether a span's trailing pairs belong
     *    to it or should carry forward to the next subject.
     */
    private fun parseTabularFormat(rawText: String): List<ParsedSubject> {
        val codeMatches = subjectCodeRegex.findAll(rawText).toList()
        if (codeMatches.size < 2) return emptyList()

        data class Span(val codeText: String, val text: String)
        val spans = codeMatches.mapIndexed { i, m ->
            val start = m.range.first
            val end = if (i + 1 < codeMatches.size) codeMatches[i + 1].range.first else rawText.length
            Span(m.value, rawText.substring(start, end))
        }
        val prefixText = rawText.substring(0, codeMatches.first().range.first)

        data class SpanInfo(val name: String, val firstLinePairs: List<ParsedScheduleBlock>, val remainderPairs: List<ParsedScheduleBlock>)

        val infos = spans.map { span ->
            val lines = span.text.lines()
            val firstLine = lines.firstOrNull().orEmpty()
            val remainderText = lines.drop(1).joinToString("\n")

            // Strip the code + short code BEFORE any time/day scanning — the long code
            // itself (e.g. "IT2601-025") looks enough like "NN-NN" to false-match the
            // time-range regex otherwise, corrupting every single-line subject's time.
            val afterCode = firstLine.replaceFirst(span.codeText, "").trimStart()
            val description = afterCode.replaceFirst(shortCodePrefixRegex, "")

            SpanInfo(
                name = cleanDescription(description),
                firstLinePairs = extractPairedBlocks(description),
                remainderPairs = extractPairedBlocks(remainderText)
            )
        }

        // Only the immediate last line of the header/preamble can be a genuine leading
        // schedule line for the first subject (same adjacency logic as inter-subject
        // gaps) — scanning the whole multi-line preamble risks false matches like a
        // "2026 - 2027" school-year range being mistaken for a time range.
        val prefixLastLine = prefixText.lines().lastOrNull { it.isNotBlank() }.orEmpty()
        val pendingCarry = extractPairedBlocks(prefixLastLine).toMutableList()
        val results = mutableListOf<ParsedSubject>()

        for (i in infos.indices) {
            val info = infos[i]
            val blocks = mutableListOf<ParsedScheduleBlock>()

            if (info.firstLinePairs.isNotEmpty()) {
                blocks.addAll(info.firstLinePairs)
                val nextNeedsExternalBlocks = infos.getOrNull(i + 1)?.firstLinePairs?.isEmpty() ?: false
                when {
                    nextNeedsExternalBlocks && info.remainderPairs.isNotEmpty() -> pendingCarry.addAll(info.remainderPairs)
                    info.remainderPairs.isNotEmpty() -> blocks.addAll(info.remainderPairs)
                }
            } else {
                blocks.addAll(pendingCarry)
                pendingCarry.clear()
                blocks.addAll(info.remainderPairs)
            }

            // Never silently drop a subject just because its name came out blank after
            // cleanup (e.g. a wrapped line where the description landed elsewhere). Keep
            // a placeholder so the student sees every code we detected and can fix the
            // name in the review table, instead of losing the row entirely.
            val safeName = info.name.ifBlank { "Subject ${i + 1} (name not detected — please edit)" }
            results.add(ParsedSubject(name = safeName, blocks = blocks))
        }
        return results
    }

    /** Finds all time ranges and all day tokens in a block of text independently, then pairs them by position. */
    private fun extractPairedBlocks(text: String): List<ParsedScheduleBlock> {
        val timeMatches = timeRangeRegex.findAll(text).toList()
        if (timeMatches.isEmpty()) return emptyList()

        // Blank out the matched time ranges before searching for day tokens, so a day
        // token can never be found "inside" an already-claimed time-range match.
        val blanked = StringBuilder(text).also { sb ->
            for (m in timeMatches) for (idx in m.range) sb.setCharAt(idx, ' ')
        }.toString()
        val dayMatches = dayTokenPattern.findAll(blanked).map { it.value }.toList()

        val pairCount = minOf(timeMatches.size, dayMatches.size)
        return (0 until pairCount).flatMap { idx ->
            val start = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatches[idx].groupValues[1]))
            val end = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatches[idx].groupValues[2]))
            val days = parseDayCombo(dayMatches[idx])
            if (start == null || end == null || days == null) return@flatMap emptyList()

            val startMin = DateTimeUtils.minuteOfDay(start)
            var endMin = DateTimeUtils.minuteOfDay(end)
            if (endMin <= startMin) endMin += 12 * 60
            days.map { day -> ParsedScheduleBlock(day, startMin, endMin.coerceAtMost(23 * 60 + 59)) }
        }
    }

    /** Strips time ranges, day tokens, room codes, and a trailing units number from an already code/shortcode-stripped description. */
    private fun cleanDescription(description: String): String {
        var s = timeRangeRegex.replace(description, " ")
        s = dayTokenPattern.replace(s, " ")
        s = roomTokenRegex.replace(s, " ")
        s = trailingUnitRegex.replace(s, "")
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseGenericLineFormat(rawText: String): List<ParsedSubject> {
        val subjects = mutableListOf<ParsedSubject>()

        rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val timeMatch = timeRangeRegex.find(line) ?: return@forEach
                val dayMatch = dayTokenPattern.find(line) ?: return@forEach

                val days = parseDayCombo(dayMatch.value) ?: return@forEach
                val start = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatch.groupValues[1]))
                val end = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatch.groupValues[2]))
                if (start == null || end == null) return@forEach

                val name = line
                    .replace(timeMatch.value, "")
                    .replace(dayMatch.value, "")
                    .trim(' ', '-', '–', ',', '|', '\t')
                    .ifBlank { "Untitled Subject" }

                val startMin = DateTimeUtils.minuteOfDay(start)
                var endMin = DateTimeUtils.minuteOfDay(end)
                if (endMin <= startMin) endMin += 12 * 60 // crude AM/PM-missing correction, editable afterward

                val blocks = days.map { day -> ParsedScheduleBlock(day, startMin, endMin.coerceAtMost(23 * 60 + 59)) }

                val existingIndex = subjects.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (existingIndex >= 0) {
                    val existing = subjects[existingIndex]
                    subjects[existingIndex] = existing.copy(blocks = existing.blocks + blocks)
                } else {
                    subjects.add(ParsedSubject(name = name, blocks = blocks))
                }
            }

        return subjects
    }

    /** Renders a subject's blocks back into an editable "MWF 9:00 AM-10:00 AM; ..." string. */
    fun blocksToEditableText(blocks: List<ParsedScheduleBlock>): String {
        // Group blocks that share the same time range so "M 9-10; W 9-10; F 9-10" collapses to "MWF 9-10".
        val byTime = blocks.groupBy { it.startMinute to it.endMinute }
        return byTime.entries.joinToString("; ") { (time, blocksForTime) ->
            val days = blocksForTime.map { it.dayOfWeek }.sorted().joinToString("") { dayLetter(it) }
            val (start, end) = time
            "$days ${DateTimeUtils.formatTime(start)}-${DateTimeUtils.formatTime(end)}"
        }
    }

    private fun dayLetter(day: Int): String = when (day) {
        1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "TH"; 5 -> "F"; 6 -> "S"; 7 -> "SU"
        else -> ""
    }

    /** Parses a single edited fragment like "MWF 9:00 AM-10:00 AM" back into blocks. Returns empty on failure. */
    fun parseFragment(fragment: String): List<ParsedScheduleBlock> {
        val trimmed = fragment.trim()
        if (trimmed.isBlank()) return emptyList()
        val timeMatch = timeRangeRegex.find(trimmed) ?: return emptyList()
        val dayMatch = dayTokenPattern.find(trimmed) ?: return emptyList()
        val days = parseDayCombo(dayMatch.value) ?: return emptyList()
        val start = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatch.groupValues[1])) ?: return emptyList()
        val end = DateTimeUtils.parseTimeFlexible(normalizeTimeToken(timeMatch.groupValues[2])) ?: return emptyList()
        val startMin = DateTimeUtils.minuteOfDay(start)
        val endMin = DateTimeUtils.minuteOfDay(end).let { if (it <= startMin) it + 12 * 60 else it }
        return days.map { day -> ParsedScheduleBlock(day, startMin, endMin.coerceAtMost(23 * 60 + 59)) }
    }

    /** Parses a full editable text field that may contain multiple ";"-separated fragments. */
    fun parseEditableText(text: String): List<ParsedScheduleBlock> =
        text.split(";").flatMap { parseFragment(it) }

    private fun normalizeTimeToken(token: String): String {
        val trimmed = token.trim().uppercase(Locale.US)
        // Bare hour like "9" or "1" — assume :00
        return if (Regex("""^\d{1,2}(AM|PM)?$""").matches(trimmed.replace(" ", ""))) {
            val digits = trimmed.takeWhile { it.isDigit() }
            val suffix = trimmed.drop(digits.length).trim()
            "$digits:00${if (suffix.isNotBlank()) " $suffix" else ""}"
        } else trimmed
    }

    private fun parseDayCombo(token: String): List<Int>? {
        val upper = token.uppercase(Locale.US)
        val days = mutableListOf<Int>()
        var i = 0
        while (i < upper.length) {
            val two = upper.substring(i, minOf(i + 2, upper.length))
            when {
                two == "TH" -> { days.add(4); i += 2 }
                two == "SU" -> { days.add(7); i += 2 }
                else -> {
                    val one = upper[i]
                    val day = DateTimeUtils.parseDayFlexible(one.toString())
                    if (day != null) { days.add(day); i += 1 } else return null
                }
            }
        }
        return days.ifEmpty { null }
    }
}
