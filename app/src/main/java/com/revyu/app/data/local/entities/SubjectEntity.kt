package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Index into SubjectAccents in the theme, kept stable per subject once assigned. */
    val accentIndex: Int,
    /** Official enrollment code from the study load, e.g. "IT2601-024". */
    val subjectCode: String? = null,
    /** Catalog code from the study load, e.g. "TPCC 311". */
    val catalogCode: String? = null,
    /** Credit units stated on the study load. */
    val units: Int? = null,
    val createdAt: Instant = Instant.now()
)
