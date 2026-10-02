package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "exam_attempts",
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
data class ExamAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studySetId: String,
    val startedAt: Instant = Instant.now(),
    val submittedAt: Instant? = null,
    /** JSON-encoded Map<questionId, List<selectedAnswer>>, kept as raw JSON to avoid a bespoke Room converter. */
    val answersJson: String = "{}",
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val scorePercentage: Float = 0f,
    @ColumnInfo(defaultValue = "'[]'")
    val questionOrderJson: String = "[]",
    @ColumnInfo(defaultValue = "'{}'")
    val optionOrderJson: String = "{}",
    val timeLimitSeconds: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val elapsedSeconds: Int = 0,
    @ColumnInfo(defaultValue = "'[]'")
    val flaggedJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'")
    val overridesJson: String = "[]",
    val basedOnAttemptId: String? = null
)
