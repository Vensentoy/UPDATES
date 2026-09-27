package com.revyu.app.data.repository

import androidx.room.withTransaction
import com.revyu.app.core.calendar.CalendarEvent
import com.revyu.app.core.calendar.CalendarEventType
import com.revyu.app.core.calendar.ClassBlock
import com.revyu.app.core.calendar.SchedulerConfig
import com.revyu.app.core.calendar.SchedulerInput
import com.revyu.app.core.calendar.SmartStudyScheduler
import com.revyu.app.core.calendar.StudyScheduler
import com.revyu.app.core.calendar.CalendarEventGenerator
import com.revyu.app.data.local.dao.CalendarEventDao
import com.revyu.app.data.local.dao.ScheduleDao
import com.revyu.app.data.local.dao.SubjectDao
import com.revyu.app.data.local.RevyuDatabase
import com.revyu.app.data.local.entities.CalendarEventEntity
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read/edit layer over the weekly calendar. CLASS events from an import are immutable
 * records (delete them, there's no re-generating "the truth" without the import); the
 * suggested study/review events are fully regeneratable with [regenerateSuggestions].
 */
class CalendarEventRepository(
    private val database: RevyuDatabase,
    private val calendarEventDao: CalendarEventDao,
    private val subjectDao: SubjectDao,
    private val scheduleDao: ScheduleDao,
    private val scheduler: StudyScheduler = SmartStudyScheduler(),
    private val onDataChanged: () -> Unit = {}
) {
    fun observeAll(): Flow<List<CalendarEvent>> =
        calendarEventDao.observeAll().map { list -> CalendarEventMappers.toDomainList(list) }

    fun observeForDay(day: DayOfWeek): Flow<List<CalendarEvent>> =
        calendarEventDao.observeForDay(day.value).map { list -> CalendarEventMappers.toDomainList(list) }

    suspend fun getForGroup(groupId: String): List<CalendarEvent> =
        CalendarEventMappers.toDomainList(calendarEventDao.forGroup(groupId))

    fun observeForSubject(subjectId: String): Flow<List<CalendarEvent>> =
        calendarEventDao.observeForSubject(subjectId).map { list -> CalendarEventMappers.toDomainList(list) }

    suspend fun delete(event: CalendarEvent) {
        calendarEventDao.getById(event.id)?.let { calendarEventDao.delete(it) }
        onDataChanged()
    }

    /** Lets the user tweak room/note/times of any event after import. */
    suspend fun update(event: CalendarEvent) {
        calendarEventDao.getById(event.id)?.let { existing ->
            calendarEventDao.update(
                CalendarEventMappers.toEntity(event).copy(
                    type = existing.type,
                    sourceStudyLoadId = existing.sourceStudyLoadId
                )
            )
        }
        onDataChanged()
    }

    /** Replace every non-class suggestion with a fresh deterministic schedule. */
    suspend fun regenerateSuggestions(config: SchedulerConfig): Int {
        val subjects = subjectDao.getAll()
        val entries = scheduleDao.getAll()
        val bySubject = entries.groupBy { it.subjectId }
        val classBlocks = subjects.flatMap { subject ->
            (bySubject[subject.id] ?: emptyList()).map { entry ->
                ClassBlock(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    units = subject.units ?: 3,
                    day = DayOfWeek.of(entry.dayOfWeek),
                    startMinute = entry.startMinuteOfDay,
                    endMinute = entry.endMinuteOfDay
                )
            }
        }
        if (classBlocks.isEmpty()) return 0

        val suggestions = scheduler.plan(SchedulerInput(classBlocks, config))
        val newEvents = CalendarEventMappers.toEntityList(CalendarEventGenerator.studyEvents(suggestions))

        database.withTransaction {
            calendarEventDao.deleteAllSuggestions(CalendarEventType.CLASS.name)
            if (newEvents.isNotEmpty()) calendarEventDao.insertAll(newEvents)
        }
        onDataChanged()
        return newEvents.size
    }

    suspend fun addManualEvent(
        day: DayOfWeek,
        startMinute: Int,
        endMinute: Int,
        subjectName: String,
        room: String? = null,
        note: String? = null,
        subjectId: String? = null
    ): CalendarEvent {
        val event = CalendarEvent(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            type = CalendarEventType.CLASS,
            dayOfWeek = day,
            startMinute = startMinute.coerceIn(0, 1438),
            endMinute = endMinute.coerceIn(startMinute + 1, 1439),
            subjectName = subjectName,
            room = room,
            note = note,
            isCustom = true
        )
        calendarEventDao.insert(CalendarEventMappers.toEntity(event))
        onDataChanged()
        return event
    }

    suspend fun deleteAll() {
        database.withTransaction { calendarEventDao.deleteAll() }
        onDataChanged()
    }

    fun observeAnyExists(): Flow<Boolean> =
        observeAll().map { it.isNotEmpty() }
}