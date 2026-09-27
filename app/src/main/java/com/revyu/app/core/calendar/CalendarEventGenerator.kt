package com.revyu.app.core.calendar

import com.revyu.app.core.util.studyload.ParsedStudyLoadSubject
import java.util.UUID

/**
 * Converts parsed study-load data into structured [CalendarEvent]s.
 * Every weekly class session becomes its own CLASS event; multi-session subjects yield
 * one event per session, all tied to the same subject id.
 */
object CalendarEventGenerator {

    fun classEvents(subjects: List<Pair<String, ParsedStudyLoadSubject>>): List<CalendarEvent> =
        subjects.flatMap { (subjectId, parsed) ->
            parsed.sessions.map { session ->
                CalendarEvent(
                    subjectId = subjectId,
                    type = CalendarEventType.CLASS,
                    dayOfWeek = session.day,
                    startMinute = session.startMinute,
                    endMinute = session.endMinute,
                    subjectName = parsed.name,
                    subjectCode = parsed.subjectCode,
                    room = session.room
                )
            }
        }

    fun studyEvents(
        sessions: List<SuggestedSession>,
        groupId: String = UUID.randomUUID().toString()
    ): List<CalendarEvent> =
        sessions.map { session ->
            CalendarEvent(
                subjectId = session.subjectId,
                type = if (session.kind == StudySessionKind.REVIEW) CalendarEventType.REVIEW
                else CalendarEventType.SUGGESTED_STUDY,
                dayOfWeek = session.day,
                startMinute = session.startMinute,
                endMinute = session.endMinute,
                subjectName = session.subjectName,
                generationGroupId = groupId
            )
        }
}