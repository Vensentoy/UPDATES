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

    @Query("SELECT * FROM study_sets WHERE sourceMaterialId IN (:ids) AND generationStatus = 'READY' ORDER BY createdAt DESC")
    suspend fun getReadySetsForMaterialIds(ids: List<String>): List<StudySetEntity>

    @Query(
        "SELECT study_sets.* FROM study_sets " +
            "INNER JOIN study_materials ON study_sets.sourceMaterialId = study_materials.id " +
            "WHERE study_sets.subjectId = :subjectId " +
            "AND study_sets.generationStatus = 'READY' " +
            "AND study_materials.contentHash = :contentHash " +
            "ORDER BY study_sets.createdAt DESC"
    )
    fun observeSiblings(subjectId: String, contentHash: String): Flow<List<StudySetEntity>>

    @Query(
        "SELECT study_sets.* FROM study_sets " +
            "INNER JOIN study_materials ON study_sets.sourceMaterialId = study_materials.id " +
            "WHERE study_sets.subjectId = :subjectId " +
            "AND study_sets.generationStatus = 'READY' " +
            "AND study_materials.contentHash = :contentHash " +
            "ORDER BY study_sets.createdAt DESC"
    )
    suspend fun getSiblings(subjectId: String, contentHash: String): List<StudySetEntity>

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
