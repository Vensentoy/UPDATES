package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One recurring weekly time block for a subject (e.g. "MWF 9:00–10:00").
 * A subject can have more than one entry (lecture + lab, different days, etc).
 *
 * dayOfWeek follows java.time.DayOfWeek.getValue(): Monday=1 ... Sunday=7.
 * start/endMinuteOfDay are minutes since midnight (0–1439), so comparisons and the
 * Smart Calendar's "N days from now" math stay simple integer arithmetic.
 */
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ScheduleEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /** Room code from the study load, e.g. "CAS-RM5A/COT-RM2". */
    val room: String? = null
)
