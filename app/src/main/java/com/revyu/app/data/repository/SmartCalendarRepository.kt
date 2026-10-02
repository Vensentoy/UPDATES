package com.revyu.app.data.repository

import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.ScheduleEntryEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.SubjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One "you haven't finished this yet" item shown on Home and pushed as a notification. */
data class PendingReviewTask(
    val subject: SubjectEntity,
    val studySet: StudySetEntity,
    val daysUntilClass: Int,
    val dayOfWeek: Int
)

/**
 * Cross-references each subject's weekly schedule against its Study Sets' Exam Mode
 * status. A task surfaces when a subject meets within the next [WINDOW_DAYS] days and
 * has at least one Study Set whose Exam Mode is not yet COMPLETED.
 */
class SmartCalendarRepository(
    private val subjectRepository: SubjectRepository,
    private val studySetRepository: StudySetRepository
) {
    companion object {
        const val WINDOW_DAYS = 3
    }

    fun observePendingTasks(): Flow<List<PendingReviewTask>> =
        combine(
            subjectRepository.observeSubjectsWithSchedule(),
            studySetRepository.observeAllStudySets()
        ) { subjectsWithSchedule, allStudySets ->
            buildTasks(subjectsWithSchedule.map { it.subject to it.schedule }, allStudySets)
        }

    private fun buildTasks(
        subjects: List<Pair<SubjectEntity, List<ScheduleEntryEntity>>>,
        allStudySets: List<StudySetEntity>
    ): List<PendingReviewTask> {
        val tasks = mutableListOf<PendingReviewTask>()

        for ((subject, schedule) in subjects) {
            val incompleteSets = allStudySets.filter {
                it.subjectId == subject.id &&
                    it.examStatus != ExamStatus.COMPLETED &&
                    it.generationStatus == GenerationStatus.READY
            }
            if (incompleteSets.isEmpty() || schedule.isEmpty()) continue

            val nextBlock = schedule.minByOrNull { DateTimeUtils.daysUntilNextOccurrence(it.dayOfWeek) }
                ?: continue
            val daysUntil = DateTimeUtils.daysUntilNextOccurrence(nextBlock.dayOfWeek)

            if (daysUntil in 0..WINDOW_DAYS) {
                // Surface the most recently created incomplete set for this subject.
                val studySet = incompleteSets.maxByOrNull { it.createdAt } ?: continue
                tasks.add(
                    PendingReviewTask(
                        subject = subject,
                        studySet = studySet,
                        daysUntilClass = daysUntil,
                        dayOfWeek = nextBlock.dayOfWeek
                    )
                )
            }
        }
        return tasks.sortedBy { it.daysUntilClass }
    }

    fun notificationMessage(task: PendingReviewTask): String {
        val when_ = when (task.daysUntilClass) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in ${task.daysUntilClass} days"
        }
        return "${task.subject.name} is scheduled $when_. You haven't taken your Exam Mode yet. Take it now to prepare."
    }
}
