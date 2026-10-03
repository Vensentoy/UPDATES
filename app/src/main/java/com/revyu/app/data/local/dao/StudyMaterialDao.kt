package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revyu.app.data.local.entities.StudyMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyMaterialDao {
    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId ORDER BY uploadedAt DESC")
    fun observeForSubject(subjectId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT * FROM study_materials WHERE id = :id")
    suspend fun getById(id: String): StudyMaterialEntity?

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId ORDER BY uploadedAt DESC")
    suspend fun getForSubjectOnce(subjectId: String): List<StudyMaterialEntity>

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId AND LOWER(TRIM(fileName)) = LOWER(TRIM(:fileName)) LIMIT 1")
    suspend fun getByFileName(subjectId: String, fileName: String): StudyMaterialEntity?

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId AND contentHash = :contentHash ORDER BY uploadedAt DESC")
    suspend fun getByContentHash(subjectId: String, contentHash: String): List<StudyMaterialEntity>

    @Query("UPDATE study_materials SET contentHash = :contentHash WHERE id = :id")
    suspend fun updateContentHash(id: String, contentHash: String)

    @Query("DELETE FROM study_materials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: StudyMaterialEntity): Long
}
