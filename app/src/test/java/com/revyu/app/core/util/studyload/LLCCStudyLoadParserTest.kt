package com.revyu.app.core.util.studyload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class LLCCStudyLoadParserTest {

    // The official-format development fixture (block layout, like a clean PDF extraction).
    private val fixtureText = """
        LAPU-LAPU CITY COLLEGE
        FIRST SEMESTER
        A.Y. 2026-2027
        BIndTech 3 - D
        Computer Technology

        Name: JUAN DELA CRUZ
        Student No.: 2026-000123

        COURSE NO. COURSE TITLE UNITS SCHEDULE

        IT2601-024
        TPCC 311
        Mobile Programming
        3 units

        Monday
        07:00 AM - 09:00 AM
        CAS-RM5A/COT-RM2

        Tuesday
        07:00 AM - 10:00 AM
        CAS-RM5A/COT-RM2

        IT2601-025
        TPCC 312
        Systems Analysis and Design
        3 units

        Monday
        01:00 PM - 04:00 PM
        CAS-RM6

        IT2601-026
        TPCC 313
        Project Study 1 with Intellectual Property Design
        3 units

        Tuesday
        06:30 PM - 08:30 PM

        Thursday
        05:30 PM - 08:30 PM
        CAS-RM8

        IT2601-027
        TPCC 314
        Information Assurance and Security
        3 units

        Thursday
        07:00 AM - 12:00 PM
        CAS-RM2

        IT2601-028
        TACC 311
        Industrial Organization and Management
        3 units

        Monday
        05:00 PM - 08:00 PM
        CAS-RM1

        IT2601-029
        TACC 312
        Industrial Psychology
        3 units

        Monday
        09:00 AM - 12:00 PM
        CAS-RM1

        IT2601-030
        GE 7
        The Contemporary World
        3 units

        Tuesday
        01:00 PM - 02:30 PM

        Friday
        04:00 PM - 05:30 PM
        CAS-RM7

        IT2601-031
        GEE 3
        Environmental Science
        3 units

        Thursday
        02:30 PM - 05:30 PM
        COT-RM2

        IT2601-032
        FL 1
        Foreign Language 1 (Japanese Language)
        3 units

        Tuesday
        10:30 AM - 12:00 PM

        Thursday
        01:00 PM - 02:30 PM
        CAS-RM1
    """.trimIndent()

    @Test
    fun `recognizes official LLCC study load`() {
        assertTrue(LLCCStudyLoadParser.recognizes(fixtureText))
        assertFalse(LLCCStudyLoadParser.recognizes("University of Cebu\nSome other document"))
    }

    @Test
    fun `parses all fixture subjects with names codes and units`() {
        val report = LLCCStudyLoadParser.parse(fixtureText)
        assertNotNull(report)
        val load = report!!.studyLoad
        assertEquals(9, load.subjects.size)
        assertEquals("Mobile Programming", load.subjects[0].name)
        assertEquals("IT2601-024", load.subjects[0].subjectCode)
        assertEquals("TPCC 311", load.subjects[0].catalogCode)
        assertEquals(3, load.subjects[0].units)
        assertEquals(9 * 3, load.totalUnits)
    }

    @Test
    fun `multi-session subjects keep both sessions on the same subject`() {
        val report = LLCCStudyLoadParser.parse(fixtureText)!!
        val mobile = report.studyLoad.subjects.first { it.name == "Mobile Programming" }
        assertEquals(2, mobile.sessions.size)
        assertEquals(DayOfWeek.MONDAY, mobile.sessions[0].day)
        assertEquals(7 * 60, mobile.sessions[0].startMinute)
        assertEquals(9 * 60, mobile.sessions[0].endMinute)
        assertEquals("CAS-RM5A/COT-RM2", mobile.sessions[0].room)
        assertEquals(DayOfWeek.TUESDAY, mobile.sessions[1].day)
        assertEquals(7 * 60, mobile.sessions[1].startMinute)

        val projectStudy = report.studyLoad.subjects.first { it.subjectCode == "IT2601-026" }
        assertEquals("Project Study 1 with Intellectual Property Design", projectStudy.name)
        assertEquals(2, projectStudy.sessions.size)
        assertEquals(DayOfWeek.TUESDAY, projectStudy.sessions[0].day)
        assertEquals(DayOfWeek.THURSDAY, projectStudy.sessions[1].day)
        assertEquals("CAS-RM8", projectStudy.sessions[1].room)
    }

    @Test
    fun `extracts semester academic year course year section and student name`() {
        val report = LLCCStudyLoadParser.parse(fixtureText)!!
        val load = report.studyLoad
        assertEquals("FIRST", load.semester)
        assertEquals("2026-2027", load.academicYear)
        assertEquals("Computer Technology", load.course)
        assertEquals("3", load.yearLevel)
        assertEquals("D", load.section)
        assertEquals("JUAN DELA CRUZ", load.studentName)
    }

    @Test
    fun `parses single-line tabular rows`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            SECOND SEMESTER
            A.Y. 2025-2026
            STUDENT NAME: MARIA SANTOS

            IT2601-024 TPCC 311 Mobile Programming 3 units MON 07:00 AM - 09:00 AM CAS-RM5A/COT-RM2
            IT2601-024 TPCC 311 Mobile Programming 3 units TUE 07:00 AM - 10:00 AM CAS-RM5A/COT-RM2
            IT2601-030 GE 7 The Contemporary World 3 units TUE 01:00 PM - 02:30 PM CAS-RM7
            IT2601-030 GE 7 The Contemporary World 3 units FRI 04:00 PM - 05:30 PM CAS-RM7
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        val load = report.studyLoad
        assertEquals(2, load.subjects.size)
        val mobile = load.subjects.first { it.name == "Mobile Programming" }
        assertEquals(2, mobile.sessions.size)
        assertEquals(DayOfWeek.MONDAY, mobile.sessions[0].day)
        assertEquals("CAS-RM5A/COT-RM2", mobile.sessions[0].room)
        val world = load.subjects.first { it.name == "The Contemporary World" }
        assertEquals(2, world.sessions.size)
        assertEquals(DayOfWeek.FRIDAY, world.sessions[1].day)
    }

    @Test
    fun `handles missing room`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            IT2601-025
            Systems Analysis and Design
            3 units
            Monday
            01:00 PM - 04:00 PM
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        val subject = report.studyLoad.subjects.single()
        assertEquals(1, subject.sessions.size)
        assertEquals(null, subject.sessions[0].room)
    }

    @Test
    fun `handles a missing schedule with a graceful report`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            STUDENT NAME: ANNE
            This document contains no class schedules at all.
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)
        assertNotNull(report)
        assertTrue(report!!.studyLoad.subjects.isEmpty())
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun `rejects non LLCC documents`() {
        val report = LLCCStudyLoadParser.parse("Some university's syllabus for a physics class")
        assertEquals(null, report)
    }

    @Test
    fun `does not crash on malformed text`() {
        val report = LLCCStudyLoadParser.parse(
            "LAPU-LAPU CITY COLLEGE\n\n\u0000\u0001\u0002\n\n  \n" +
                "\uFFFD".repeat(50) + "\n### broken\n999 xyz"
        )!!
        assertNotNull(report)
    }

    @Test
    fun `handles multi-line subject names`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            IT2601-099
            ABM 4
            Advanced Quantitative
            Methods for Business
            3 units
            Monday
            09:00 AM - 11:00 AM
            CAS-RM3
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        val subject = report.studyLoad.subjects.single()
        assertEquals("Advanced Quantitative Methods for Business", subject.name)
        assertEquals("ABM 4", subject.catalogCode)
        assertEquals(1, subject.sessions.size)
    }

    @Test
    fun `handles repeated headers and page numbers across page breaks`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            COURSE NO. COURSE TITLE UNITS SCHEDULE
            IT2601-024 Mobile Programming 3 units
            Monday 07:00 AM - 09:00 AM CAS-RM5A/COT-RM2

            Page 2
            COURSE NO. COURSE TITLE UNITS SCHEDULE
            IT2601-025 Systems Analysis and Design 3 units
            Monday 01:00 PM - 04:00 PM CAS-RM6
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        assertEquals(2, report.studyLoad.subjects.size)
    }

    @Test
    fun `expands day combos into individual sessions`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            IT2601-100
            DC 1
            Discrete Computing
            3 units
            MWF 10:00 AM - 11:00 AM
            TTH 02:00 PM - 03:30 PM
            CAS-RM4
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        val subject = report.studyLoad.subjects.single()
        // MWF + TTH = 3 + 2 = 5 sessions. TTH is Tuesday + Thursday (one each).
        assertEquals(5, subject.sessions.size)
        assertEquals(1, subject.sessions.count { it.day == DayOfWeek.MONDAY })
        assertEquals(1, subject.sessions.count { it.day == DayOfWeek.THURSDAY })
        assertEquals(1, subject.sessions.count { it.day == DayOfWeek.TUESDAY })
    }

    @Test
    fun `handles times without AM PM and noon`() {
        val text = """
            LAPU-LAPU CITY COLLEGE
            FIRST SEMESTER
            IT2601-101
            PE 1
            Physical Education 1
            2 units
            Friday
            12:00 - 01:30 PM
            GYM-RM1
        """.trimIndent()
        val report = LLCCStudyLoadParser.parse(text)!!
        val subject = report.studyLoad.subjects.single()
        assertEquals(1, subject.sessions.size)
        assertEquals(12 * 60, subject.sessions[0].startMinute)
        assertEquals(13 * 60 + 30, subject.sessions[0].endMinute)
    }
}