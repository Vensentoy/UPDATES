package com.revyu.app.data.repository

import androidx.room.withTransaction
import com.revyu.app.core.calendar.CalendarEventGenerator
import com.revyu.app.core.calendar.ClassBlock
import com.revyu.app.core.calendar.SmartStudyScheduler
import com.revyu.app.core.calendar.StudyScheduler
import com.revyu.app.core.calendar.SchedulerConfig
import com.revyu.app.core.calendar.SchedulerInput
import com.revyu.app.core.theme.SubjectAccents
import com.revyu.app.core.util.studyload.StudyLoadParseReport
import com.revyu.app.data.local.RevyuDatabase
import com.revyu.app.data.local.dao.ScheduleDao
import com.revyu.app.data.local.dao.StudyLoadDao
import com.revyu.app.data.local.dao.SubjectDao
import com.revyu.app.data.local.entities.CalendarEventEntity
import com.revyu.app.data.local.entities.ScheduleEntryEntity
import com.revyu.app.data.local.entities.StudyLoadEntity
import com.revyu.app.data.local.entities.SubjectEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class StudyLoadImportResult(
    val studyLoad: StudyLoadEntity,
    val subjects: List<SubjectEntity>,
    val classEvents: List<CalendarEventEntity>,
    val suggestedEvents: List<CalendarEventEntity>,
    val warnings: List<String>
)

/**
 * Turns an accepted, parsed study load into real Room data. Everything happens in one
 * transaction so an import is all-or-nothing:
 *
 *  1. one [SubjectEntity] per parsed subject (with code/catalog/units and a stable accent)
 *  2. legacy schedule entries (with room) so existing Create/Exam flows keep working
 *  3. weekly CLASS calendar events
 *  4. deterministic suggested study/review events from [SmartStudyScheduler]
 *
 * The previous import's suggested events are NOT deleted here - the user reviews this
 * import first; only an explicit "Re-import" action replaces them.
 */
class StudyLoadRepository(
    private val database: RevyuDatabase,
    private val subjectDao: SubjectDao,
    private val scheduleDao: ScheduleDao,
    private val studyLoadDao: StudyLoadDao,
    private val scheduler: StudyScheduler = SmartStudyScheduler(),
    private val onDataChanged: () -> Unit = {}
) {
    fun observeImports(): Flow<List<StudyLoadEntity>> = studyLoadDao.observeAll()

    suspend fun getImportCount(): Int = studyLoadDao.count()

    suspend fun importParsedStudyLoad(
        report: StudyLoadParseReport,
        fileName: String,
        config: SchedulerConfig = SchedulerConfig()
    ): StudyLoadImportResult {
        val parsed = report.studyLoad
        require(parsed.subjects.isNotEmpty()) { "Nothing to import" }

        val studyLoadId = java.util.UUID.randomUUID().toString()
        val accentStart = subjectDao.count()
        val classEvents = mutableListOf<CalendarEventEntity>()
        val suggestedEvents = mutableListOf<CalendarEventEntity>()
        val subjects = mutableListOf<SubjectEntity>()
        val scheduleEntries = mutableListOf<ScheduleEntryEntity>()

        val prepared = parsed.subjects.mapIndexed { i, subject ->
            val subjectId = java.util.UUID.randomUUID().toString()
            val entity = SubjectEntity(
                id = subjectId,
                name = subject.name,
                accentIndex = (accentStart + i) % SubjectAccents.size,
                subjectCode = subject.subjectCode,
                catalogCode = subject.catalogCode,
                units = subject.units
            )
            subjects.add(entity)
            subject.sessions.forEach { session ->
                scheduleEntries.add(
                    ScheduleEntryEntity(
                        subjectId = subjectId,
                        dayOfWeek = session.day.value,
                        startMinuteOfDay = session.startMinute,
                        endMinuteOfDay = session.endMinute,
                        room = session.room
                    )
                )
            }
            subjectId to subject
        }

        // CLASS events: one per session.
        classEvents += CalendarEventMappers.toEntityList(
            CalendarEventGenerator.classEvents(prepared)
        ).map { it.copy(sourceStudyLoadId = studyLoadId) }

        // Suggested study/review events from the deterministic planner.
        val classBlocks = prepared.flatMap { (subjectId, subject) ->
            subject.sessions.map { session ->
                ClassBlock(
                    subjectId = subjectId,
                    subjectName = subject.name,
                    units = subject.units ?: 3,
                    day = session.day,
                    startMinute = session.startMinute,
                    endMinute = session.endMinute
                )
            }
        }
        if (classBlocks.isNotEmpty()) {
            val plan = scheduler.plan(SchedulerInput(classBlocks, config))
            suggestedEvents += CalendarEventMappers.toEntityList(
                CalendarEventGenerator.studyEvents(plan)
            ).map { it.copy(sourceStudyLoadId = studyLoadId) }
        }

        val studyLoad = StudyLoadEntity(
            id = studyLoadId,
            fileName = fileName,
            institution = parsed.institution,
            semester = parsed.semester,
            academicYear = parsed.academicYear,
            course = parsed.course,
            yearLevel = parsed.yearLevel,
            section = parsed.section,
            studentName = parsed.studentName,
            unitTotal = parsed.totalUnits,
            subjectCount = parsed.subjects.size,
            importedAt = Instant.now()
        )

        database.withTransaction {
            subjectDao.insertAll(subjects)
            if (scheduleEntries.isNotEmpty()) scheduleDao.insertAll(scheduleEntries)
            if (classEvents.isNotEmpty()) database.calendarEventDao().insertAll(classEvents)
            if (suggestedEvents.isNotEmpty()) database.calendarEventDao().insertAll(suggestedEvents)
            studyLoadDao.insert(studyLoad)
        }

        onDataChanged()

        return StudyLoadImportResult(
            studyLoad = studyLoad,
            subjects = subjects,
            classEvents = classEvents,
            suggestedEvents = suggestedEvents,
            warnings = report.warnings
        )
    }

    /** Deletes an import's history row and its calendar events; subjects are kept (they may
     *  own Study Sets the user created after importing). */
    suspend fun deleteImport(studyLoad: StudyLoadEntity) {
        database.withTransaction {
            database.calendarEventDao().deleteForStudyLoad(studyLoad.id)
            studyLoadDao.delete(studyLoad)
        }
        onDataChanged()
    }
}