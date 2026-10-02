package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "flashcards",
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
data class FlashcardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studySetId: String,
    val front: String,
    val back: String,
    val orderIndex: Int,
    val hint: String? = null,
    val topicId: String? = null,
    val topicTitle: String? = null,
    @ColumnInfo(defaultValue = "2")
    val difficulty: Int = 2,
    @ColumnInfo(defaultValue = "'TERM'")
    val kind: String = "TERM",
    @ColumnInfo(defaultValue = "0")
    val mastery: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val reviewCount: Int = 0,
    val lastReviewedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0")
    val starred: Boolean = false
)
