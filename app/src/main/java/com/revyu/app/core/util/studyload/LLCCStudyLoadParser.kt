package com.revyu.app.core.util.studyload

import java.time.DayOfWeek
import java.util.Locale

/**
 * Parses official Lapu-Lapu City College "STUDY LOAD" documents into structured subjects.
 *
 * The parser is LLCC-specific today but sits behind the [StudyLoadParser] interface so
 * other institutions can be added later. It is deliberately robust to the messy reality
 * of PDF text extraction:
 *
 *  - Rows that are collapsed onto a single line ("IT2601-024 TPCC 311 Mobile
 *    Programming 3 MON 07:00 AM - 09:00 AM CAS-RM5A/COT-RM2")
 *  - Block-style rows with day, time, and room split across separate lines
 *  - PdfBox's sortByPosition layout, where a 2-meeting subject's first session row is
 *    drawn ABOVE its code line and the second one BELOW it (so a session row lands in
 *    the previous subject's span). Trailing orphan session rows are carried forward to
 *    the next subject only when the current code line already carried an inline session.
 *  - A bare units cell ("3") instead of "3 units", because the registrar prints the
 *    number without the word
 *  - Multi-line subject names, repeated table headers, footers, and page breaks
 *  - Multiple weekly sessions per subject (never a second subject by accident)
 *  - Wrapped room cells that continue on the next row ("CAS-RM5A/COT-" + "RM2")
 *  - Missing rooms, missing AM/PM, noon, and "to" as a time separator
 *
 * Recognition is score-based: the college name, the "STUDY LOAD" heading, and subject
 * codes are all independent signals. A single one is enough to proceed, so an image-only
 * letterhead (or header words that sort apart from each other) can never block an
 * otherwise parseable table — the report simply carries a warning.
 *
 * The result always funnels through an editable review screen, so a confident-but-partial
 * parse is never a dead end. Nothing here is hardcoded schedule data — every documented
 * value comes from the uploaded text.
 */
object LLCCStudyLoadParser : StudyLoadParser {

    override val id = "llcc"
    override val displayName = "LLCC Study Load"

    // ---- Recognition -------------------------------------------------------

    private val lapuNameRegex = Regex("""LAPU\s*-?\s*LAPU""", RegexOption.IGNORE_CASE)
    private val cityCollegeRegex = Regex("""CITY\s+COLLEGE""", RegexOption.IGNORE_CASE)
    private val studyLoadHeadingRegex = Regex("""STUDY\s+LOAD""", RegexOption.IGNORE_CASE)
    private val semesterRegex = Regex(
        """(FIRST|SECOND|THIRD|SUMMER)\s+SEMESTER""",
        RegexOption.IGNORE_CASE
    )
    private val academicYearRegex = Regex(
        """A\.?\s*Y\.?\s*[:.\-]?\s*(\d{4}\s*[-–]\s*\d{4})""",
        RegexOption.IGNORE_CASE
    )

    /** True when the extracted text carries any independent LLCC signal. */
    override fun recognizes(rawText: String): Boolean =
        institutionSignal(rawText) ||
            hasSubjectCodes(rawText) ||
            studyLoadHeadingRegex.containsMatchIn(rawText) && hasAcademicTerms(rawText)

    private fun institutionSignal(text: String): Boolean =
        lapuNameRegex.containsMatchIn(text) && cityCollegeRegex.containsMatchIn(text)

    private fun hasSubjectCodes(text: String): Boolean =
        subjectCodeRegex.containsMatchIn(text)

    private fun hasAcademicTerms(text: String): Boolean =
        semesterRegex.containsMatchIn(text) || academicYearRegex.containsMatchIn(text)

    // ---- Metadata regexes --------------------------------------------------

    private val nameLabelRegex = Regex(
        """(?:STUDENT\s+NAME|NAME\s+OF\s+STUDENT|NAME)\s*[:.]?\s*([A-Z][A-Za-z ,.'"\-]{2,80})""",
        RegexOption.IGNORE_CASE
    )

    private val studentNameLabelRegex = Regex("""NAME\s+OF\s+STUDENT""", RegexOption.IGNORE_CASE)
    private val surnameCommaNameRegex = Regex(
        """\b[A-Z][A-Za-z']+,\s*[A-Z][A-Za-z]+(?:\s+[A-Z]\.)?"""
    )

    private val courseLabelRegex = Regex(
        """(?:COURSE|PROGRAM)\s*[:.]?\s*([A-Z][A-Za-z0-9 /()&.'"-]{2,60})""",
        RegexOption.IGNORE_CASE
    )

    private val sectionLabelRegex = Regex(
        """(?:SECTION|SEC(?:TION)?(?=\s|[:.]|$))[ \t]*[:.]?[ \t]*([A-Z0-9][A-Za-z0-9 /\-]{0,20})""",
        RegexOption.IGNORE_CASE
    )

    // Course / year / section embedded inline, e.g. "BIndTech 3 - D", "BSIT 2-A".
    // The section token is constrained to start with a letter so academic years like
    // "A.Y. 2026-2027" can never be captured here ("20" / "26").
    private val yearSectionInlineRegex = Regex(
        """\b([A-Za-z][A-Za-z0-9.\s&]{0,20}?)\s+(\d{1,2})\s*[-–]\s*([A-Z][A-Z0-9]{0,2})\b"""
    )

    // "BIndTech 3 - D Computer Technology" -> the trailing words are the actual program.
    private val courseAfterYearSectionRegex = Regex(
        """\b[A-Za-z][A-Za-z0-9.]*\s+\d{1,2}\s*[-–]\s*[A-Z][A-Z0-9]{0,2}\s+([A-Z][A-Za-z &.'\-]{2,40})$""",
        RegexOption.MULTILINE
    )

    private val subjectCodeRegex = Regex("""\b[A-Z]{2,6}\d{3,5}-\d{2,4}\b""")
    private val catalogCodeRegex = Regex("""^\s*[A-Z]{1,6}\s+\d{1,5}\s*$""")
    private val inlineCatalogTokenRegex = Regex("""\b[A-Z]{1,6}\s+\d{1,5}\b""")

    private val unitsRegex = Regex(
        """(\d+(?:\.\d+)?)\s*unit""",
        RegexOption.IGNORE_CASE
    )

    // ---- Session token regexes ----------------------------------------------

    // The lookbehind (no preceding digit/letter) is critical: it stops "IT2601-024" (a
    // subject code) from parsing as a time "01-02" or "13:30-14:00" glued to a code.
    private val timeRangeRegex = Regex(
        """(?<![\dA-Za-z])(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*(?:-|–|—|to)\s*(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
        RegexOption.IGNORE_CASE
    )

    private val roomTokenRegex = Regex(
        """(?:\b[A-Za-z]{2,6}-)*RM\d[A-Za-z0-9/&\-]*""",
        RegexOption.IGNORE_CASE
    )

    private val headerGarbageRegex = Regex(
        """(?i)^(COURSE|SUBJECT|UNITS|SCHEDULE|TIME|DAY|ROOM|PAGE|CODE|NO)[A-Z\s.]*${'$'}"""
    )

    private val pageNumberRegex = Regex("""^\s*(?:page\s*)?\d{1,3}\s*${'$'}""", RegexOption.IGNORE_CASE)

    private val fullDayRegex = Regex(
        """(?i)(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)"""
    )

    // Day-combo tokens, longest first so "TTH" isn't split into "T" + "TH".
    private val dayComboRegex = Regex(
        """(?i)\b(MTHF|MWF|TTHS|MTWTHF|TTH|THU|THUR|THR|THS|TH|TUE|WED|FRI|SAT|SUN|MON|SU|MW|MT|M|T|W|F|S)\b"""
    )

    private val dayAbbreviation = mapOf(
        "MON" to DayOfWeek.MONDAY,
        "TUE" to DayOfWeek.TUESDAY,
        "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY,
        "THUR" to DayOfWeek.THURSDAY,
        "THR" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY,
        "SAT" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY
    )

    override fun parse(rawText: String): StudyLoadParseReport? {
        if (!recognizes(rawText)) return null
        val text = normalize(rawText)

        val warnings = mutableListOf<String>()
        if (!institutionSignal(text)) {
            warnings.add("Couldn't find the official college heading; recognized by subject codes and schedule instead.")
        }
        if (!studyLoadHeadingRegex.containsMatchIn(text)) {
            warnings.add("Couldn't find the \"STUDY LOAD\" heading; parsing anyway.")
        }

        val metadata = extractMetadata(text)
        val subjects = parseSubjects(text)

        if (subjects.isEmpty()) {
            return StudyLoadParseReport(
                studyLoad = ParsedStudyLoad(institution = "Lapu-Lapu City College", subjects = subjects),
                warnings = warnings + listOf("No subjects with class schedules were detected.")
            )
        }

        return StudyLoadParseReport(
            studyLoad = ParsedStudyLoad(
                institution = metadata.institution ?: "Lapu-Lapu City College",
                semester = metadata.semester,
                academicYear = metadata.academicYear,
                course = metadata.course,
                yearLevel = metadata.yearLevel,
                section = metadata.section,
                studentName = metadata.studentName,
                subjects = subjects
            ),
            warnings = warnings
        )
    }

    // ---- Normalization ------------------------------------------------------

    private fun normalize(rawText: String): String {
        return rawText
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+"""), " ")
    }

    // ---- Metadata -----------------------------------------------------------

    private data class Metadata(
        val institution: String? = null,
        val semester: String? = null,
        val academicYear: String? = null,
        val course: String? = null,
        val yearLevel: String? = null,
        val section: String? = null,
        val studentName: String? = null
    )

    private fun extractMetadata(text: String): Metadata {
        val lines = text.lines()
        var course: String? = null
        var yearLevel: String? = null
        var section: String? = null

        // 1) Explicit "COURSE:" / "PROGRAM:" label. Ignore junk results that actually
        //    came from the table header "COURSE NO. COURSE TITLE UNITS SCHEDULE".
        courseLabelRegex.find(text)?.let { m ->
            val candidate = m.groupValues[1].trim()
            if (!isJunkCourseLabel(candidate)) course = candidate
        }

        // 2) Standalone program line, e.g. "Computer Technology" on its own line.
        if (course == null) {
            val firstCodeIndex = lines.indexOfFirst { line -> subjectCodeRegex.containsMatchIn(line) }
            val window = if (firstCodeIndex >= 0) lines.subList(0, firstCodeIndex) else lines
            course = window.firstOrNull { line -> isProgramLine(line) }?.trim()
        }

        sectionLabelRegex.find(text)?.let { m ->
            val lc = m.groupValues[1].trim(' ', ':', '.')
            val digit = Regex("""(\d{1,2})""").find(lc)?.groupValues?.get(1)
            section = lc
            yearLevel = digit
        }

        // 3) Inline "Program 3 - D" style line (e.g. "BIndTech 3 - D Computer Technology").
        val yearSectionMatch = yearSectionInlineRegex.find(text)
        if (yearLevel == null || section == null) {
            yearSectionMatch?.let { m ->
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotEmpty() && candidate.length <= 20 && text.lines().any {
                        it.contains(candidate, ignoreCase = true) &&
                            it.contains(m.groupValues[2]) &&
                            it.contains(m.groupValues[3])
                    }) {
                    if (course == null) course = candidate
                    yearLevel = m.groupValues[2]
                    if (section == null) section = m.groupValues[3]
                }
            }
        }

        // Prefer the program that trails the year-section line: "BIndTech 3 - D Computer
        // Technology" -> "Computer Technology", not "BIndTech".
        courseAfterYearSectionRegex.find(text)?.let { m ->
            val trailing = m.groupValues[1].trim()
            if (isProgramLine(trailing)) course = trailing
        }

        return Metadata(
            institution = "Lapu-Lapu City College",
            semester = semesterRegex.find(text)?.groupValues?.get(1)?.uppercase(Locale.US),
            academicYear = academicYearRegex.find(text)?.groupValues?.get(1),
            course = course,
            yearLevel = yearLevel,
            section = section,
            studentName = extractStudentName(text)
        )
    }

    private fun extractStudentName(text: String): String? {
        // The official PDF prints "NAME OF STUDENT" on its label row and the value
        // ("TAPAO, JAPHET S.") on the following value row.
        val lines = text.lines()
        val labelIndex = lines.indexOfFirst { studentNameLabelRegex.containsMatchIn(it) }
        if (labelIndex >= 0) {
            lines.subList(labelIndex + 1, lines.size).firstOrNull { it.isNotBlank() }?.let { row ->
                surnameCommaNameRegex.find(row)?.let { return it.value.trim() }
            }
        }
        // Plain "Name: JUAN DELA CRUZ" style.
        nameLabelRegex.find(text)?.let { m ->
            val candidate = m.groupValues[1].trim()
            if (!candidate.contains("SEC", ignoreCase = true) && !candidate.startsWith("OF ", true)) {
                return candidate
            }
        }
        // Last resort: any surname, given-name pattern anywhere in the document.
        return surnameCommaNameRegex.find(text)?.value?.trim()
    }

    /** A "COURSE NO. COURSE TITLE UNITS SCHEDULE"-style header masking as a course value. */
    private fun isJunkCourseLabel(value: String): Boolean =
        Regex("""^(NO\.?|TITLE|SUBJECT|UNITS|SCHEDULE|TIME|DAY|ROOM|CODE|NAME)""")
            .containsMatchIn(value.trim())

    /** A standalone all-title-case program line (no digits, no labels, before any subject). */
    private fun isProgramLine(line: String): Boolean {
        val l = line.trim()
        if (l.isBlank() || l.length > 50) return false
        if (!Regex("""^[A-Z][A-Za-z &'.\-]{2,40}$""").matches(l)) return false
        if (l.contains(Regex("""[0-9]"""))) return false
        if (lapuNameRegex.containsMatchIn(l)) return false
        if (semesterRegex.containsMatchIn(l)) return false
        if (isHeaderGarbage(l)) return false
        if (l.startsWith("NAME", true) || l.startsWith("STUDENT", true) ||
            l.equals("STUDY LOAD", true) || l.startsWith("A.Y.", true)
        ) return false
        return true
    }

    // ---- Subject parsing ----------------------------------------------------

    private fun parseSubjects(text: String): List<ParsedStudyLoadSubject> {
        val lines = text.lines()
        val (preambleLines, blocks) = splitBlocks(lines)

        // PdfBox sorts by position, so a 2-meeting subject's first session row can be
        // drawn ABOVE its code line — right after the preamble/header. Those orphan rows
        // belong to the first subject. Lead with them.
        val parsedBlocks = blocks.map { (code, blockLines) -> parseSubjectBlock(code, blockLines) }
        var carry = scanSessions(preambleLines).toMutableList()
        val results = mutableListOf<ParsedStudyLoadSubject>()

        for (i in parsedBlocks.indices) {
            val parsed = parsedBlocks[i]
            val nextParsed = parsedBlocks.getOrNull(i + 1)
            var sessions = parsed.sessions
            var nextCarry = emptyList<ParsedClassSession>()

            // A trailing orphan session at the end of this subject's block belongs to the NEXT
            // subject whenever the current code line already held a session OR the next subject's
            // code line does NOT have an inline session (meaning PdfBox placed its 1st session above it).
            val trailingBelongsToNext = parsed.trailingOrphanSessions.isNotEmpty() &&
                (parsed.codeLineInlineSession || (nextParsed != null && !nextParsed.codeLineInlineSession))

            if (trailingBelongsToNext) {
                nextCarry = parsed.trailingOrphanSessions
                sessions = sessions - nextCarry.toSet()
            }
            sessions = carry + sessions
            results.add(
                ParsedStudyLoadSubject(
                    subjectCode = parsed.code,
                    catalogCode = parsed.catalogCode,
                    name = parsed.name,
                    units = parsed.units,
                    sessions = joinRoomFragments(sessions)
                )
            )
            carry = nextCarry.toMutableList()
        }

        // Defensive: stray trailing schedule rows after the very last subject.
        if (carry.isNotEmpty() && results.isNotEmpty()) {
            val last = results.removeAt(results.size - 1)
            results.add(last.copy(sessions = joinRoomFragments(last.sessions + carry)))
        }
        return results
    }

    /**
     * Splits the body into per-subject blocks. A block starts at a line containing a
     * subject code (e.g. "IT2601-024") and runs until a line containing a DIFFERENT
     * subject code. Rows that repeat the same code (tabular multi-session rows) stay in
     * the same block, so every weekly session of a subject is kept together. Returns the
     * preamble lines too, for the leading-orphan handling above.
     */
    private fun splitBlocks(lines: List<String>): Pair<List<String>, List<Pair<String, List<String>>>> {
        val preamble = mutableListOf<String>()
        val blocks = mutableListOf<Pair<String, List<String>>>()
        var currentCode: String? = null
        val blockLines = mutableListOf<String>()
        for (line in lines) {
            val code = subjectCodeRegex.find(line)?.value
            if (code != null) {
                if (code != currentCode) {
                    if (currentCode != null && blockLines.isNotEmpty()) {
                        blocks.add(currentCode to blockLines.toList())
                    }
                    currentCode = code
                    blockLines.clear()
                }
                blockLines.add(line)
            } else {
                if (currentCode != null) blockLines.add(line) else preamble.add(line)
            }
        }
        if (currentCode != null && blockLines.isNotEmpty()) {
            blocks.add(currentCode to blockLines.toList())
        }
        return preamble to blocks
    }

    private data class BlockParseResult(
        val code: String?,
        val catalogCode: String?,
        val name: String,
        val units: Int?,
        val sessions: List<ParsedClassSession>,
        val codeLineInlineSession: Boolean,
        val trailingOrphanSessions: List<ParsedClassSession>
    )

    private fun parseSubjectBlock(anchorCode: String?, blockLines: List<String>): BlockParseResult {
        val lines = blockLines.map { it.trim() }.filter { it.isNotBlank() }

        var subjectCode: String? = anchorCode?.takeIf { it.isNotBlank() }
        var catalogCode: String? = null
        var units: Int? = null
        val nameParts = mutableListOf<String>()

        var firstCodeLineHasSession = false
        var lastCodeIndex = -1
        lines.forEachIndexed { index, l ->
            if (subjectCodeRegex.containsMatchIn(l)) lastCodeIndex = index
        }

        for (line in lines) {
            if (line.isEmpty()) continue

            val days = findDays(line)
            val time = findTimeRange(line)
            val room = findRoom(line)
            val unitsMatch = unitsRegex.find(line)

            when {
                isHeaderGarbage(line) || isPageFooter(line) || isFooterNoise(line) -> {
                    // Repeated table headers / footers / page numbers are noise.
                }

                catalogCode == null && catalogCodeRegex.matches(line) &&
                    days == null && time == null && line.trim().length <= 12 -> {
                    catalogCode = line.trim()
                }

                days != null || time != null || room != null -> {
                    // Single-line table rows carry units, e.g. "... 3 MON ..." or "3 units".
                    if (units == null) {
                        units = unitsMatch?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
                    }
                    if (subjectCodeRegex.containsMatchIn(line) && (time != null || days != null)) {
                        firstCodeLineHasSession = true
                    }
                    // First session row may carry the subject description with it
                    // (single-line table format). Capture it before discarding the row.
                    if (nameParts.isEmpty()) {
                        val remainder = line
                            .replace(subjectCodeRegex, " ")
                            .replace(unitsRegex, " ")
                            .replace(roomTokenRegex, " ")
                            .replace(fullDayRegex, " ")
                            .replace(dayComboRegex, " ")
                            .replace(timeRangeRegex, " ")
                            .trim()
                        val cleaned = stripCatalogAndUnits(remainder)
                        if (units == null) units = cleaned.units
                        if (catalogCode == null) catalogCode = cleaned.catalog
                        if (cleaned.name.isNotBlank() && !isHeaderGarbage(cleaned.name)) {
                            nameParts.add(cleaned.name)
                        }
                    }
                }

                unitsMatch != null && units == null -> {
                    units = unitsMatch.groupValues[1].toDoubleOrNull()?.toInt()
                    // Block format puts units on its own line, but admission/pdf exports
                    // sometimes join units with the subject name on one line.
                    if (nameParts.size < 8) {
                        val token = line
                            .replace(subjectCodeRegex, " ")
                            .replace(unitsRegex, " ")
                            .replace(roomTokenRegex, " ")
                            .replace(fullDayRegex, " ")
                            .replace(dayComboRegex, " ")
                            .replace(timeRangeRegex, " ")
                            .trim()
                        if (token.isNotBlank() && !token.startsWith("units", true) &&
                            !isHeaderGarbage(token)
                        ) {
                            nameParts.add(token)
                        }
                    }
                }

                else -> {
                    // Name line (until the first session appears), or the standalone code
                    // line of a subject whose sessions/repo live on their own lines.
                    if (nameParts.size < 8) {
                        val token = line
                            .replace(subjectCodeRegex, " ")
                            .replace(timeRangeRegex, " ")
                            .replace(roomTokenRegex, " ")
                            .replace(fullDayRegex, " ")
                            .replace(dayComboRegex, " ")
                            .trim()
                        val cleaned = stripCatalogAndUnits(token)
                        if (units == null) units = cleaned.units
                        if (cleaned.name.isNotBlank() && !catalogCodeRegex.matches(cleaned.name) &&
                            !isHeaderGarbage(cleaned.name)
                        ) {
                            if (catalogCode == null) catalogCode = cleaned.catalog
                            nameParts.add(cleaned.name)
                        }
                    }
                }
            }
        }

        val name = nameParts.filter { it.isNotBlank() }.joinToString(" ").trim()
        val sessions = scanSessions(lines)

        // Schedule-only rows on non-code lines after the last code-bearing line: in the
        // row-major PdfBox layout these are the NEXT subject's sessions, waiting to be
        // carried forward (only when the current code line already held its own session).
        val trailingSessions = mutableListOf<ParsedClassSession>()
        if (lines.size > lastCodeIndex + 1) {
            for (trailingLine in lines.subList(lastCodeIndex + 1, lines.size)) {
                if (subjectCodeRegex.containsMatchIn(trailingLine)) break
                if (findTimeRange(trailingLine) != null) {
                    val scanned = scanSessions(listOf(trailingLine))
                    if (scanned.isNotEmpty()) trailingSessions.addAll(scanned)
                }
            }
        }

        return BlockParseResult(
            code = subjectCode,
            catalogCode = catalogCode,
            name = name.ifBlank { "Subject (name not detected - please edit)" },
            units = units,
            sessions = sessions,
            codeLineInlineSession = firstCodeLineHasSession,
            trailingOrphanSessions = trailingSessions
        )
    }

    /**
     * Strips a leading catalog code ("TPCC 311", "GE 7") off a cleaned subject fragment
     * and reads the units from a trailing bare integer ("Mobile Programming 3") or from a
     * "N units" phrase. Returns the cleaned name plus whatever was found.
     */
    private fun stripCatalogAndUnits(fragment: String): CleanedFragment {
        var s = fragment.replace(Regex("""\s+"""), " ").trim()
        var catalog: String? = null

        val catalogMatch = inlineCatalogTokenRegex.find(s)
        if (catalogMatch != null && catalogMatch.range.first <= 1) {
            catalog = catalogMatch.value
            s = s.replaceFirst(catalog, " ").replace(Regex("""\s+"""), " ").trim()
        }

        var units: Int? = null
        unitsRegex.find(s)?.let { m ->
            units = m.groupValues[1].toDoubleOrNull()?.toInt()
            s = s.replace(m.value, " ")
        }

        val trailingUnits = Regex("""\s+(\d+(?:\.\d+)?)\s*${'$'}""").find(s)
        if (trailingUnits != null && units == null) {
            val value = trailingUnits.groupValues[1].toDoubleOrNull()?.toInt()
            // Credit units are small (1-8); anything bigger is almost certainly part of a
            // title (e.g. "... 101"), so leave it in the name.
            if (value != null && value in 1..8) {
                units = value
                s = s.substring(0, trailingUnits.range.first).trim()
            }
        }
        return CleanedFragment(name = s.replace(Regex("""\s+"""), " ").trim(), catalog = catalog, units = units)
    }

    private data class CleanedFragment(val name: String, val catalog: String?, val units: Int?)

    /** Scans a list of lines into sessions, pairing days, times, and rooms across stacked lines. */
    private fun scanSessions(lines: List<String>): List<ParsedClassSession> {
        val sessions = mutableListOf<ParsedClassSession>()
        var pendingDays: List<DayOfWeek>? = null
        var pendingTime: Pair<Int, Int>? = null
        val roomQueue = ArrayDeque<String>()

        fun emitDays(days: List<DayOfWeek>) {
            val timeStart = pendingTime!!.first
            val timeEnd = pendingTime!!.second.coerceAtMost(23 * 60 + 59)
            val room = if (roomQueue.isNotEmpty()) roomQueue.removeFirst() else null
            for (day in days) sessions.add(ParsedClassSession(day, timeStart, timeEnd, room))
            pendingDays = null
            pendingTime = null
        }

        for (line in lines) {
            if (line.isEmpty()) continue
            val days = findDays(line)
            val time = findTimeRange(line)
            val room = findRoom(line)
            when {
                days != null || time != null || room != null -> {
                    if (room != null && days == null && time == null) {
                        // Room-only line. If it trails a completed session (block layout)
                        // attach it, otherwise keep it for the next emitted session (a
                        // room whose session row comes after it in PdfBox order).
                        if (pendingDays == null && pendingTime == null && sessions.isNotEmpty()) {
                            val last = sessions.last()
                            if (last.room == null) sessions[sessions.size - 1] = last.copy(room = room)
                        } else {
                            roomQueue.addLast(room)
                        }
                    } else {
                        if (room != null) roomQueue.addLast(room)
                        if (days != null) pendingDays = days
                        if (time != null) pendingTime = time
                        if (pendingDays != null && pendingTime != null) emitDays(pendingDays!!)
                    }
                }
            }
        }
        if (pendingDays != null && pendingTime != null) emitDays(pendingDays!!)
        return sessions
    }

    /**
     * Rejoins a wrapped room cell that PdfBox splits across two session rows
     * ("CAS-RM5A/COT-" then "RM2") back into one room.
     */
    private fun joinRoomFragments(sessions: List<ParsedClassSession>): List<ParsedClassSession> {
        if (sessions.size < 2) return sessions
        val result = sessions.toMutableList()
        for (i in 0 until result.size - 1) {
            val current = result[i].room
            val next = result[i + 1].room
            if (current != null && current.endsWith("-") && next != null && next.startsWith("RM", true)) {
                result[i] = result[i].copy(room = current.trimEnd('-') + "-" + next)
                result[i + 1] = result[i + 1].copy(room = null)
            }
        }
        return result
    }

    // ---- Session token helpers ----------------------------------------------

    private fun findDays(line: String): List<DayOfWeek>? {
        val fullDays = fullDayRegex.findAll(line).mapNotNull { parseDayName(it.value) }.toList()
        if (fullDays.isNotEmpty()) return fullDays.distinct()

        val matches = dayComboRegex.findAll(line).map { it.value }.toList()
        if (matches.isEmpty()) return null

        val resultDays = mutableListOf<DayOfWeek>()
        for (token in matches) {
            val upper = token.uppercase(Locale.US)
            val abbr = dayAbbreviation[upper]
            if (abbr != null) {
                resultDays.add(abbr)
            } else {
                expandDayCombo(token)?.let { resultDays.addAll(it) }
            }
        }
        return if (resultDays.isNotEmpty()) resultDays.distinct() else null
    }

    private fun parseDayName(token: String): DayOfWeek? = when (token.uppercase(Locale.US)) {
        "MONDAY" -> DayOfWeek.MONDAY
        "TUESDAY" -> DayOfWeek.TUESDAY
        "WEDNESDAY" -> DayOfWeek.WEDNESDAY
        "THURSDAY" -> DayOfWeek.THURSDAY
        "FRIDAY" -> DayOfWeek.FRIDAY
        "SATURDAY" -> DayOfWeek.SATURDAY
        "SUNDAY" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun expandDayCombo(token: String): List<DayOfWeek>? {
        val upper = token.uppercase(Locale.US)
        val days = mutableListOf<DayOfWeek>()
        var i = 0
        while (i < upper.length) {
            val two = upper.substring(i, minOf(i + 2, upper.length))
            when {
                two == "TH" -> { days.add(DayOfWeek.THURSDAY); i += 2 }
                two == "SU" -> { days.add(DayOfWeek.SUNDAY); i += 2 }
                else -> {
                    val day = when (upper[i]) {
                        'M' -> DayOfWeek.MONDAY
                        'T' -> DayOfWeek.TUESDAY
                        'W' -> DayOfWeek.WEDNESDAY
                        'F' -> DayOfWeek.FRIDAY
                        'S' -> DayOfWeek.SATURDAY
                        else -> null
                    }
                    if (day == null) return null
                    days.add(day); i += 1
                }
            }
        }
        if (days.isEmpty()) return null
        // "S" alone conventionally means Saturday for a study-load day combo.
        return days
    }

    private fun findTimeRange(line: String): Pair<Int, Int>? {
        val m = timeRangeRegex.find(line) ?: return null
        var startToken = m.groupValues[1].trim()
        var endToken = m.groupValues[2].trim()

        // Infer AM/PM: "7:00 - 9:00 AM" -> both AM; "1:00 - 2:30 PM" -> both PM.
        val startHasSuffix = startToken.takeLast(2).equals("AM", true) ||
            startToken.takeLast(2).equals("PM", true)
        val endHasSuffix = endToken.takeLast(2).equals("AM", true) ||
            endToken.takeLast(2).equals("PM", true)
        if (!startHasSuffix && endHasSuffix) {
            val suffix = endToken.takeLast(2)
            startToken = "$startToken $suffix"
        } else if (startHasSuffix && !endHasSuffix) {
            val suffix = startToken.takeLast(2)
            endToken = "$endToken $suffix"
        }

        val start = parseClock(startToken)
        var end = parseClock(endToken)
        if (start == null || end == null) return null
        if (end <= start) end += 12 * 60
        return start to end
    }

    private fun parseClock(token: String): Int? {
        val t = token.trim().uppercase(Locale.US).replace(" ", "")
        if (t.isEmpty()) return null
        val suffix = when {
            t.endsWith("AM") -> "AM"
            t.endsWith("PM") -> "PM"
            else -> ""
        }
        val digits = if (suffix.isNotEmpty()) t.dropLast(2) else t
        val parts = digits.split(":")
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (hour !in 0..24 || minute !in 0..59) return null
        var h = hour
        when (suffix) {
            "AM" -> if (h == 12) h = 0
            "PM" -> if (h != 12) h += 12
        }
        if (h > 23) return null
        return h * 60 + minute
    }

    private fun findRoom(line: String): String? =
        roomTokenRegex.find(line)?.value

    private fun isHeaderGarbage(line: String): Boolean =
        headerGarbageRegex.matches(line) && findTimeRange(line) == null

    private fun isPageFooter(line: String): Boolean =
        pageNumberRegex.matches(line)

    /** Registrar signature and "Total / Units / Date enrolled" footer rows at the end. */
    private fun isFooterNoise(line: String): Boolean {
        val l = line.trim()
        if (l.isEmpty() || l.length > 90) return false
        if (Regex(
                """\b(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT|SEP|OCT|NOV|DEC)[A-Z]*\s+\d{1,2},\s+\d{4}\s+""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(l)
        ) return true
        val labels = setOf(
            "total", "units", "date", "enrolled", "registrar", "subject", "acknowledged"
        )
        val words = l.lowercase(Locale.US).split(Regex("""\s+"""))
        return words.isNotEmpty() && words.all { it in labels }
    }

    // ---- Public helpers used by the review UI -------------------------------

    fun dayComboOf(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "SU"
    }
}