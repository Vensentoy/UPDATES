package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * One recurring weekly calendar occurrence (a class, a suggested study/review block, or a
 * custom reminder). Mirrors [com.revyu.app.core.calendar.CalendarEvent], with dayOfWeek
 * stored as getValue() (Monday=1..Sunday=7) to match ScheduleEntryEntity's convention.
 */
@Entity(
    tableName = "calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId"), Index("dayOfWeek"), Index("generationGroupId")]
)
data class CalendarEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    /** CalendarEventType.name - CLASS, SUGGESTED_STUDY, REVIEW, EXAM, REMINDER. */
    val type: String,
    /** java.time.DayOfWeek.getValue(): Monday=1 ... Sunday=7. */
    val dayOfWeek: Int,
    /** Minutes since midnight (0-1439). */
    val startMinute: Int,
    /** Minutes since midnight. */
    val endMinute: Int,
    val subjectName: String,
    val subjectCode: String? = null,
    val room: String? = null,
    val note: String? = null,
    /** Batch id so studies/reviews generated in one pass can be edited/deleted together. */
    val generationGroupId: String? = null,
    /** True when the user added/edited this manually; regeneration never touches it. */
    val isCustom: Boolean = false,
    /** Points to the StudyLoadEntity that produced this event, when applicable. */
    val sourceStudyLoadId: String? = null,
    /** One-off date for exam/reminder entries from the school calendar; null = weekly-recurring event. */
    val eventDate: LocalDate? = null
)