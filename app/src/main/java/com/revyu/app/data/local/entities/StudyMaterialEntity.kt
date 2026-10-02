package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A single uploaded source document, reduced to plain extracted text at upload time.
 * We deliberately keep the extracted text and drop the raw file bytes: it's what the
 * model needs for (re)generation, it's far smaller than the original PDF/DOCX, and it
 * avoids ever needing scoped-storage file-permission plumbing after the initial import.
 */
@Entity(
    tableName = "study_materials",
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
data class StudyMaterialEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val fileName: String,
    val sourceType: SourceFileType,
    val extractedText: String,
    val characterCount: Int,
    val uploadedAt: Instant = Instant.now(),
    val contentHash: String? = null
)
