package com.revyu.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val orderIndex: Int
)
