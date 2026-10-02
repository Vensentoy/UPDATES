package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
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
    val orderIndex: Int,
    @ColumnInfo(defaultValue = "'[]'")
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String? = null,
    val topicId: String? = null,
    val topicTitle: String? = null,
    @ColumnInfo(defaultValue = "2")
    val difficulty: Int = 2,
    @ColumnInfo(defaultValue = "'RECALL'")
    val level: String = "RECALL",
    @ColumnInfo(defaultValue = "0")
    val timesAsked: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val timesCorrect: Int = 0,
    val lastAskedAt: Instant? = null,
    val lastCorrect: Boolean? = null
)
