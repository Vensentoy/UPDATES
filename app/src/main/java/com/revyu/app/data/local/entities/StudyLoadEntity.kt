package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One uploaded & imported study-load document. Kept even after its individual subjects
 * are edited away, so History can always show "what did I import, and when".
 */
@Entity(tableName = "study_loads")
data class StudyLoadEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Original PDF/external file name, for the History list. */
    val fileName: String,
    val institution: String? = null,
    val semester: String? = null,
    val academicYear: String? = null,
    val course: String? = null,
    val yearLevel: String? = null,
    val section: String? = null,
    val studentName: String? = null,
    val unitTotal: Int,
    val subjectCount: Int,
    val importedAt: Instant
)