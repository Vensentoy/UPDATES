package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = StudySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["studySetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studySetId")]
)
data class QuestionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studySetId: String,
    val type: QuestionType,
    val prompt: String,
    /** Empty for IDENTIFICATION / SHORT_ANSWER / TRUE_FALSE (the latter uses fixed True/False options at render time). */
    val options: List<String>,
    /** Single element for everything except MULTIPLE_CHOICE, where more than one option may be correct. */
    val correctAnswers: List<String>,
    val orderIndex: Int
)
