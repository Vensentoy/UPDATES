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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: StudyMaterialEntity): Long
}
