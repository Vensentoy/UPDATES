package com.revyu.app.data.repository

import androidx.room.withTransaction
import com.revyu.app.core.util.schoolcalendar.SchoolCalendarParseReport
import com.revyu.app.data.local.RevyuDatabase
import com.revyu.app.data.local.dao.CalendarEventDao
import com.revyu.app.data.local.entities.CalendarEventEntity

/**
 * Persists a parsed school calendar as one-off dated events. A re-import is all-or-nothing:
 * the previous "school_calendar-*" group is deleted in the same transaction, so users never
 * carry stale holidays/intramurals after the official calendar changes.
 */
class SchoolCalendarRepository(
    private val database: RevyuDatabase,
    private val calendarEventDao: CalendarEventDao,
    private val onDataChanged: () -> Unit = {}
) {

    suspend fun importParsedCalendar(
        report: SchoolCalendarParseReport
    ): List<CalendarEventEntity> {
        if (report.events.isEmpty()) return emptyList()

        val groupId = SchoolCalendarMappers.newGenerationGroupId()
        val entities = SchoolCalendarMappers.toEntityList(report.events, groupId)

        database.withTransaction {
            calendarEventDao.deleteSchoolCalendarEvents()
            calendarEventDao.insertAll(entities)
        }

        onDataChanged()
        return entities
    }
}