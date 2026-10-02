package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.StudySetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySetDao {
    @Query("SELECT * FROM study_sets WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun observeForSubject(subjectId: String): Flow<List<StudySetEntity>>

    @Query("SELECT * FROM study_sets WHERE id = :id")
    fun observeById(id: String): Flow<StudySetEntity?>

    @Query("SELECT * FROM study_sets WHERE id = :id")
    suspend fun getById(id: String): StudySetEntity?

    @Query("SELECT * FROM study_sets WHERE sourceMaterialId = :materialId AND generationStatus = 'READY' LIMIT 1")
    suspend fun getReadyByMaterialId(materialId: String): StudySetEntity?

    @Query("SELECT * FROM study_sets")
    suspend fun getAllOnce(): List<StudySetEntity>

    @Query("SELECT * FROM study_sets")
    fun observeAll(): Flow<List<StudySetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(studySet: StudySetEntity)

    @Update
    suspend fun update(studySet: StudySetEntity)

    @Delete
    suspend fun delete(studySet: StudySetEntity)

    @Query("UPDATE study_sets SET examStatus = :status WHERE id = :id")
    suspend fun updateExamStatus(id: String, status: ExamStatus)
}
