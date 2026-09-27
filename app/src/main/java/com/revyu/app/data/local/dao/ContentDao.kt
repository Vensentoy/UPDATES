package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE studySetId = :studySetId ORDER BY orderIndex ASC")
    fun observeForStudySet(studySetId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE studySetId = :studySetId ORDER BY orderIndex ASC")
    suspend fun getForStudySetOnce(studySetId: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards ORDER BY studySetId, orderIndex ASC")
    suspend fun getAllOnce(): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE studySetId = :studySetId ORDER BY orderIndex ASC")
    fun observeForStudySet(studySetId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE studySetId = :studySetId ORDER BY orderIndex ASC")
    suspend fun getForStudySetOnce(studySetId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY studySetId, orderIndex ASC")
    suspend fun getAllOnce(): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)
}

@Dao
interface ExamAttemptDao {
    @Query("SELECT * FROM exam_attempts WHERE studySetId = :studySetId ORDER BY startedAt DESC")
    fun observeForStudySet(studySetId: String): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempts WHERE submittedAt IS NOT NULL ORDER BY submittedAt DESC")
    fun observeAllSubmitted(): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempts WHERE studySetId = :studySetId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestForStudySet(studySetId: String): ExamAttemptEntity?

    /**
     * One row per study set: the most recent attempt for each. Used by the Home practice
     * widgets ("recent practice scores", "ready to practice") so each set counts once.
     */
    @Query(
        "SELECT ea.* FROM exam_attempts ea, " +
            "(SELECT studySetId, MAX(startedAt) AS maxStarted FROM exam_attempts GROUP BY studySetId) m " +
            "WHERE ea.studySetId = m.studySetId AND ea.startedAt = m.maxStarted"
    )
    suspend fun getLatestPerStudySetOnce(): List<ExamAttemptEntity>

    @Query("SELECT * FROM exam_attempts WHERE id = :id")
    suspend fun getById(id: String): ExamAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: ExamAttemptEntity)

    @Update
    suspend fun update(attempt: ExamAttemptEntity)
}
