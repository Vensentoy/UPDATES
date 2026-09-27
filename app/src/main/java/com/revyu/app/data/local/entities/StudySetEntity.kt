package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "study_sets",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StudyMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceMaterialId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("sourceMaterialId")]
)
data class StudySetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val sourceMaterialId: String?,
    val title: String,

    /** REGULAR for a normal upload-based Study Set; MIDTERM_VAULT/FINAL_VAULT for a Semester Vault synthesis. */
    val kind: StudySetKind = StudySetKind.REGULAR,
    /** For a Vault set: which of the subject's REGULAR Study Sets it was synthesized from. Empty for REGULAR sets. */
    val sourceStudySetIds: List<String> = emptyList(),

    // Reviewer customization
    val reviewerColumns: Int,
    val reviewerFontStyle: ReviewerFontStyle,
    val reviewerFontSizeSp: Int,
    val reviewerMargins: ReviewerMargins,

    // Practice widget customization
    val questionLanguage: String,
    val maxQuestions: Int,
    val questionTypes: List<QuestionType>,

    // Generated content — reviewer body text (rendered into a PDF on demand / cached path below)
    val reviewerBodyText: String? = null,
    val reviewerPdfPath: String? = null,

    val generationStatus: GenerationStatus = GenerationStatus.PENDING,
    val generationError: String? = null,

    val examStatus: ExamStatus = ExamStatus.NOT_TAKEN,

    val createdAt: Instant = Instant.now()
)
