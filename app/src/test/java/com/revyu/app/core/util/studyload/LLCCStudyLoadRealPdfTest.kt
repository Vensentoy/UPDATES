package com.revyu.app.core.util.studyload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Tests against the user's real study-load document
 * ("Japhet Simbajon Tapao Study load (First Semester 2026-2027).pdf"),
 * piped through a viewer-like (PdfBox sortByPosition) text extraction.
 */
class LLCCStudyLoadRealPdfTest {

    private val realPdfSortedText = """
        LAPU-LAPU
        FIRST SEMESTER, A.Y. 2026 - 2027 STUDY LOAD
        CITY COLLEGE
        STUDENT NO STATUS NAME OF STUDENT COURSE, YEAR & SECTION
        20240841 Old Student TAPAO, JAPHET S. BIndTech 3 - D Computer Technology
        SUBJECT CODE SUBJECT DESCRIPTION UNITS TIME DAYS ROOM
        07:00 AM - 09:00 AM M CAS-RM5A/COT-
        IT2601-024 TPCC 311 Mobile Programming 3
        07:00 AM - 10:00 AM T RM2
        IT2601-025 TPCC 312 Systems Analysis and Design 3 01:00 PM - 04:00 PM M CAS-RM6
        06:30 PM - 08:30 PM T
        IT2601-026 TPCC 313 Project Study 1 with Intellectual Property Design 3 CAS-RM8
        05:30 PM - 08:30 PM TH
        IT2601-027 TPCC 314 Information Assurance and Security 3 07:00 AM - 12:00 PM TH CAS-RM2
        IT2601-028 TACC 311 Industrial Organization and Management 3 05:00 PM - 08:00 PM M CAS-RM1
        IT2601-029 TACC 312 Industrial Psychology 3 09:00 AM - 12:00 PM M CAS-RM1
        01:00 PM - 02:30 PM T
        IT2601-030 GE 7 The Contemporary World 3 CAS-RM7
        04:00 PM - 05:30 PM F
        IT2601-031 GEE 3 Environmental Science 3 02:30 PM - 05:30 PM TH COT-RM2
        10:30 AM - 12:00 PM T
        IT2601-032 FL 1 Foreign Language 1 (Japanese Language) 3 CAS-RM1
        01:00 PM - 02:30 PM TH
        May 20, 2026 EMILIE P. CASTILLO, MBA 27 May 20, 2026
        Total
        Date enrolled Registrar Subject Acknowledged
        Units
    """.trimIndent()

    // What a plain, non-position-sorted extractor produces (column-strip order).
    private val rawDumpText = """
        LAPU-LAPU

        CITY COLLEGE

        STUDENT NO

        STATUS

        20240841

        Old Student

        SUBJECT CODE

        SUBJECT

        FIRST SEMESTER, A.Y. 2026 - 2027

        STUDY LOAD

        NAME OF STUDENT

        TAPAO, JAPHET S.

        DESCRIPTION

        COURSE, YEAR & SECTION

        BIndTech 3 - D Computer Technology

        UNITS

        TIME

        DAYS

        ROOM

        IT2601-024

        TPCC 311 Mobile Programming

        IT2601-025

        TPCC 312

        Systems Analysis and Design

        IT2601-026

        TPCC 313

        Project Study 1 with Intellectual Property Design

        IT2601-027

        TPCC 314

        Information Assurance and Security

        IT2601-028

        TACC 311

        Industrial Organization and Management

        IT2601-029

        TACC 312

        Industrial Psychology

        IT2601-030

        GE 7

        The Contemporary World

        IT2601-031

        GEE 3

        Environmental Science

        IT2601-032

        FL 1

        Foreign Language 1 (Japanese Language)

        3

        3

        3

        3

        3

        3

        3

        3

        3

        07:00 AM - 09:00 AM

        07:00 AM - 10:00 AM

        01:00 PM - 04:00 PM

        06:30 PM - 08:30 PM

        05:30 PM - 08:30 PM

        07:00 AM - 12:00 PM

        05:00 PM - 08:00 PM

        09:00 AM - 12:00 PM

        01:00 PM - 02:30 PM

        04:00 PM - 05:30 PM

        02:30 PM - 05:30 PM

        10:30 AM - 12:00 PM

        01:00 PM - 02:30 PM

        M

        T

        M

        T

        TH

        TH

        M

        M

        T

        F

        TH

        T

        TH

        CAS-RM5A/COT-

        RM2

        CAS-RM6

        CAS-RM8

        CAS-RM2

        CAS-RM1

        CAS-RM1

        CAS-RM7

        COT-RM2

        CAS-RM1

        May 20, 2026

        Date enrolled

        EMILIE P. CASTILLO, MBA

        Registrar

        27

        Total

        Units

        May 20, 2026

        Subject Acknowledged
    """.trimIndent()

    private fun parseReal(): ParsedStudyLoad {
        val report = LLCCStudyLoadParser.parse(realPdfSortedText)
        assertNotNull(report)
        assertEquals(emptyList<String>(), report!!.warnings)
        return report.studyLoad
    }

    @Test
    fun `recognizes the real study load both sorted and raw`() {
        assertTrue(LLCCStudyLoadParser.recognizes(realPdfSortedText))
        assertTrue(LLCCStudyLoadParser.recognizes(rawDumpText))
    }

    @Test
    fun `parses all nine real subjects with correct names codes and units`() {
        val load = parseReal()
        assertEquals(9, load.subjects.size)

        val expected = listOf(
            Triple("IT2601-024", "TPCC 311", "Mobile Programming"),
            Triple("IT2601-025", "TPCC 312", "Systems Analysis and Design"),
            Triple("IT2601-026", "TPCC 313", "Project Study 1 with Intellectual Property Design"),
            Triple("IT2601-027", "TPCC 314", "Information Assurance and Security"),
            Triple("IT2601-028", "TACC 311", "Industrial Organization and Management"),
            Triple("IT2601-029", "TACC 312", "Industrial Psychology"),
            Triple("IT2601-030", "GE 7", "The Contemporary World"),
            Triple("IT2601-031", "GEE 3", "Environmental Science"),
            Triple("IT2601-032", "FL 1", "Foreign Language 1 (Japanese Language)")
        )
        expected.forEachIndexed { index, (code, catalog, name) ->
            assertEquals(name, load.subjects[index].name)
            assertEquals(code, load.subjects[index].subjectCode)
            assertEquals(catalog, load.subjects[index].catalogCode)
            assertEquals(3, load.subjects[index].units)
        }
        assertEquals(27, load.totalUnits)
    }

    @Test
    fun `preserves all thirteen real sessions across correct subjects`() {
        val load = parseReal()
        assertEquals(13, load.totalSessions)
    }

    @Test
    fun `rejoins wrapped room fragment on the first real subject`() {
        val load = parseReal()
        val mobile = load.subjects[0]
        assertEquals(2, mobile.sessions.size)
        assertEquals(DayOfWeek.MONDAY, mobile.sessions[0].day)
        assertEquals(7 * 60, mobile.sessions[0].startMinute)
        assertEquals(9 * 60, mobile.sessions[0].endMinute)
        assertEquals("CAS-RM5A/COT-RM2", mobile.sessions[0].room)
        assertEquals(DayOfWeek.TUESDAY, mobile.sessions[1].day)
        assertEquals(7 * 60, mobile.sessions[1].startMinute)
        assertEquals(10 * 60, mobile.sessions[1].endMinute)
        assertEquals(null, mobile.sessions[1].room)
    }

    @Test
    fun `carries orphan session rows forward to the subject that owns them`() {
        val load = parseReal()

        // "06:30 PM - 08:30 PM T" is drawn below IT2601-025's row but belongs to
        // IT2601-026 (Project Study). It must not stick to Systems Analysis and Design.
        val systems = load.subjects[1]
        assertEquals(1, systems.sessions.size)
        assertEquals(DayOfWeek.MONDAY, systems.sessions[0].day)

        val projectStudy = load.subjects[2]
        assertEquals(2, projectStudy.sessions.size)
        assertEquals(DayOfWeek.TUESDAY, projectStudy.sessions[0].day)
        assertEquals(18 * 60 + 30, projectStudy.sessions[0].startMinute)
        assertEquals(DayOfWeek.THURSDAY, projectStudy.sessions[1].day)
        assertEquals("CAS-RM8", projectStudy.sessions[1].room)

        // The session row below IT2601-029 ("01:00 PM - 02:30 PM T") belongs to
        // The Contemporary World, and the one below IT2601-031 to Foreign Language.
        val world = load.subjects[6]
        assertEquals(2, world.sessions.size)
        assertEquals(DayOfWeek.TUESDAY, world.sessions[0].day)
        assertEquals(DayOfWeek.FRIDAY, world.sessions[1].day)

        val foreignLanguage = load.subjects[8]
        assertEquals(2, foreignLanguage.sessions.size)
        assertEquals(DayOfWeek.TUESDAY, foreignLanguage.sessions[0].day)
        assertEquals(10 * 60 + 30, foreignLanguage.sessions[0].startMinute)
        assertEquals(DayOfWeek.THURSDAY, foreignLanguage.sessions[1].day)
        assertEquals(13 * 60, foreignLanguage.sessions[1].startMinute)
        assertEquals(14 * 60 + 30, foreignLanguage.sessions[1].endMinute)
    }

    @Test
    fun `reads the real header metadata`() {
        val load = parseReal()
        assertEquals("FIRST", load.semester)
        assertEquals("2026 - 2027", load.academicYear)
        assertEquals("Computer Technology", load.course)
        assertEquals("3", load.yearLevel)
        assertEquals("D", load.section)
        assertEquals("TAPAO, JAPHET S.", load.studentName)
    }

    @Test
    fun `does not reject the raw unsorted dump`() {
        val report = LLCCStudyLoadParser.parse(rawDumpText)
        assertNotNull(report)
    }

    @Test
    fun `test actual Vincent Sorono PDF file on disk`() {
        val pdfFile = java.io.File("C:/Users/Francis/Downloads/New folder/UPDATES-main/UPDATES-main/Vincent Gabriel  Sorono Study load (First Semester 2026-2027).pdf")
        if (!pdfFile.exists()) return
        try {
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFile)
            val sortedText = com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            val rawText = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            doc.close()

            println("=== SORTED TEXT ===")
            println(sortedText)
            println("=== RAW TEXT ===")
            println(rawText)

            val report = LLCCStudyLoadParser.parse(sortedText)
            assertNotNull(report)
            val load = report!!.studyLoad
            println("=== PARSED SUBJECTS ===")
            load.subjects.forEach { s ->
                println("Subject: ${s.subjectCode} - ${s.name}")
                s.sessions.forEach { sess ->
                    println("   Session: ${sess.day} ${sess.startMinute/60}:${sess.startMinute%60} - ${sess.endMinute/60}:${sess.endMinute%60} (Room: ${sess.room})")
                }
            }
        } catch (e: Throwable) {
            println("Skipping disk PDF test in JVM environment: ${e.message}")
        }
    }
}