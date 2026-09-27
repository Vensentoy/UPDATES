package com.revyu.app.core.util.schoolcalendar

import java.time.LocalDate

/**
 * Official Academic Calendar AY 2026-2027 for Lapu-Lapu City College (LLCC).
 * Pre-compiled into structured ParsedSchoolCalendarEvent records so when an LLCCian logs in,
 * the calendar is automatically seeded and utilized by the study scheduler.
 */
object LLCCCalendarData {

    fun getEvents(): List<ParsedSchoolCalendarEvent> {
        val list = mutableListOf<ParsedSchoolCalendarEvent>()

        fun single(year: Int, month: Int, day: Int, title: String) {
            try {
                list.add(ParsedSchoolCalendarEvent(date = LocalDate.of(year, month, day), title = title))
            } catch (_: Exception) {}
        }

        fun range(year: Int, month: Int, startDay: Int, endDay: Int, title: String) {
            for (d in startDay..endDay) {
                single(year, month, d, title)
            }
        }

        fun crossMonthRange(startYear: Int, startMonth: Int, startDay: Int, endYear: Int, endMonth: Int, endDay: Int, title: String) {
            var curr = LocalDate.of(startYear, startMonth, startDay)
            val end = LocalDate.of(endYear, endMonth, endDay)
            while (!curr.isAfter(end)) {
                list.add(ParsedSchoolCalendarEvent(date = curr, title = title))
                curr = curr.plusDays(1)
            }
        }

        // ==========================================
        // FIRST SEMESTER (AY 2026-2027)
        // ==========================================
        crossMonthRange(2026, 2, 1, 2026, 3, 23, "Admission Period")
        crossMonthRange(2026, 5, 11, 2026, 6, 5, "Enrollment Period")
        single(2026, 6, 8, "Start of Classes")
        single(2026, 6, 8, "Orientation Seminar for Freshmen & Transferee")
        single(2026, 6, 8, "First day of reporting for permanent faculty")
        range(2026, 6, 8, 11, "Changing and/or dropping of subjects")
        single(2026, 6, 12, "Independence Day")
        single(2026, 6, 17, "Lapu-Lapu City Charter Day")
        single(2026, 8, 6, "Cebu Provincial Charter Day")
        range(2026, 8, 3, 8, "Midterm Examinations")
        single(2026, 8, 19, "Submission of Midterm Grades")
        single(2026, 8, 21, "Ninoy Aquino Day")
        single(2026, 8, 25, "National Heroes Day")
        single(2026, 9, 2, "Intramurals 2026")
        single(2026, 9, 3, "Intramurals 2026")
        single(2026, 9, 4, "Intramurals 2026")
        single(2026, 9, 9, "Don Sergio Osmeña Day")
        range(2026, 10, 5, 10, "Final Examinations")
        range(2026, 10, 12, 16, "Signing of Student Clearance")
        single(2026, 10, 19, "Start of semester break for permanent faculty")
        crossMonthRange(2026, 10, 19, 2026, 11, 7, "Semester Break")
        single(2026, 10, 21, "Submission of Final Grades")
        single(2026, 10, 31, "Special Non-Working Day")
        single(2026, 11, 1, "All Saints' Day")

        // ==========================================
        // SECOND SEMESTER (AY 2026-2027)
        // ==========================================
        crossMonthRange(2026, 9, 21, 2026, 10, 2, "Admission Period")
        range(2026, 11, 2, 6, "Enrollment Period")
        single(2026, 11, 9, "Start of Classes")
        single(2026, 11, 9, "Resumption of duty for permanent faculty")
        range(2026, 11, 9, 13, "Changing and/or dropping of subjects")
        single(2026, 11, 30, "Bonifacio Day")
        single(2026, 12, 8, "Feast of the Immaculate Conception of Mary")
        single(2026, 12, 11, "General Assembly with the graduating students")
        single(2026, 12, 17, "Students' Christmas Party")
        single(2026, 12, 18, "Year-End Assessment and Evaluation")
        crossMonthRange(2026, 12, 21, 2027, 1, 2, "Christmas Vacation")
        single(2026, 12, 24, "Christmas Eve")
        single(2026, 12, 25, "Christmas Day")
        single(2026, 12, 30, "Rizal Day")
        single(2026, 12, 31, "New Year's Eve")
        single(2027, 1, 1, "New Year's Day")
        single(2027, 1, 4, "Classes Resume")
        range(2027, 1, 6, 8, "Midterm Exams (Graduating)")
        single(2027, 1, 19, "Submission of Midterm Grades (Graduating)")
        range(2027, 1, 20, 29, "Processing of Application for Graduation")
        range(2027, 1, 18, 23, "Midterm Exams (Non-Graduating)")
        single(2027, 2, 3, "Submission of Midterm Grades (Non-Graduating)")
        single(2027, 2, 6, "Chinese New Year")
        range(2027, 2, 15, 16, "Final Exams (Graduating)")
        single(2027, 2, 16, "RING HOP")
        single(2027, 2, 27, "Submission of Final Grades (Graduating)")
        single(2027, 3, 1, "Recollection")
        single(2027, 3, 9, "Earthquake Drill and Fire Drill / Basic Life Support")
        single(2027, 3, 10, "Eidul-Fitr (Tentative date)")
        crossMonthRange(2027, 3, 15, 2027, 4, 6, "Processing of Oath of Candidacy")
        single(2027, 3, 19, "Deliberation for Honors")
        range(2027, 3, 25, 27, "Maundy Thursday / Holy Friday / Black Saturday")
        range(2027, 3, 29, 31, "Final Exams (Non-Graduating)")
        range(2027, 4, 5, 7, "Signing of Student Clearance")
        single(2027, 4, 7, "Commencement Exercises")
        single(2027, 4, 8, "Start of vacation break of permanent faculty")
        single(2027, 4, 1, "LLCC Days")
        single(2027, 4, 9, "Araw ng Kagitingan")
        single(2027, 4, 12, "Submission of Final Grades (Non-Graduating)")
        single(2027, 4, 27, "Kadaugán sa Mactan")
        single(2027, 5, 1, "Labor Day")
        single(2027, 5, 17, "Eid-al-Adha (Tentative date)")

        return list
    }

    fun getParseReport(): SchoolCalendarParseReport =
        SchoolCalendarParseReport(events = getEvents())
}
