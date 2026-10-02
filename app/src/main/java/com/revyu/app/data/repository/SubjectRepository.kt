package com.revyu.app.data.repository

import com.revyu.app.core.theme.SubjectAccents
import com.revyu.app.core.util.ParsedSubject
import com.revyu.app.data.local.dao.ScheduleDao
import com.revyu.app.data.local.dao.StudySetDao
import com.revyu.app.data.local.dao.SubjectDao
import com.revyu.app.data.local.entities.ScheduleEntryEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.SubjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SubjectWithSchedule(
    val subject: SubjectEntity,
    val schedule: List<ScheduleEntryEntity>
)

data class SubjectWithStudySets(
    val subject: SubjectEntity,
    val schedule: List<ScheduleEntryEntity>,
    val studySets: List<StudySetEntity>
)

class SubjectRepository(
    private val subjectDao: SubjectDao,
    private val scheduleDao: ScheduleDao,
    private val studySetDao: StudySetDao? = null
) {
    fun observeSubjectsWithSchedule(): Flow<List<SubjectWithSchedule>> =
        combine(subjectDao.observeAll(), scheduleDao.observeAll()) { subjects, allSchedule ->
            subjects.map { subject ->
                SubjectWithSchedule(subject, allSchedule.filter { it.subjectId == subject.id })
            }
        }

    fun observeSchedule(subjectId: String): Flow<List<ScheduleEntryEntity>> =
        scheduleDao.observeForSubject(subjectId)

    suspend fun getSubject(id: String): SubjectEntity? = subjectDao.getById(id)

    suspend fun countSubjects(): Int = subjectDao.count()

    /** Imports parsed Study Load subjects, assigning a stable rotating accent color to each. */
    suspend fun importStudyLoad(parsedSubjects: List<ParsedSubject>) {
        val startIndex = subjectDao.count()
        parsedSubjects.forEachIndexed { i, parsed ->
            val subject = SubjectEntity(
                name = parsed.name,
                accentIndex = (startIndex + i) % SubjectAccents.size
            )
            subjectDao.insert(subject)
            val entries = parsed.blocks.map { block ->
                ScheduleEntryEntity(
                    subjectId = subject.id,
                    dayOfWeek = block.dayOfWeek,
                    startMinuteOfDay = block.startMinute,
                    endMinuteOfDay = block.endMinute
                )
            }
            if (entries.isNotEmpty()) scheduleDao.insertAll(entries)
        }
    }

    suspend fun addSubjectManually(name: String): SubjectEntity {
        val count = subjectDao.count()
        val subject = SubjectEntity(name = name, accentIndex = count % SubjectAccents.size)
        subjectDao.insert(subject)
        return subject
    }

    suspend fun replaceSchedule(subjectId: String, blocks: List<ScheduleEntryEntity>) {
        scheduleDao.deleteForSubject(subjectId)
        if (blocks.isNotEmpty()) scheduleDao.insertAll(blocks)
    }

    /** Cascades to the subject's schedule, materials, and Study Sets via Room's FK CASCADE rules. */
    suspend fun deleteSubject(subject: SubjectEntity) = subjectDao.delete(subject)

    fun observeSubjectsWithStudySets(): Flow<List<SubjectWithStudySets>> {
        val dao = studySetDao
        require(dao != null) { "studySetDao must be injected to use observeSubjectsWithStudySets()" }
        return combine(
            subjectDao.observeAll(),
            scheduleDao.observeAll(),
            dao.observeAll()
        ) { subjects, allSchedule, allStudySets ->
            subjects.map { subject ->
                SubjectWithStudySets(
                    subject = subject,
                    schedule = allSchedule.filter { it.subjectId == subject.id },
                    studySets = allStudySets.filter { it.subjectId == subject.id }
                )
            }
        }
    }
}
