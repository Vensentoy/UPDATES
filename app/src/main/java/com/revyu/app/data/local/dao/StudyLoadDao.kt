package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revyu.app.data.local.entities.StudyLoadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyLoadDao {
    @Query("SELECT * FROM study_loads ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<StudyLoadEntity>>

    @Query("SELECT * FROM study_loads ORDER BY importedAt DESC")
    suspend fun getAll(): List<StudyLoadEntity>

    @Query("SELECT * FROM study_loads WHERE id = :id")
    suspend fun getById(id: String): StudyLoadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(studyLoad: StudyLoadEntity)

    @Delete
    suspend fun delete(studyLoad: StudyLoadEntity)

    @Query("SELECT * FROM study_loads WHERE LOWER(TRIM(fileName)) = LOWER(TRIM(:fileName)) LIMIT 1")
    suspend fun getByFileName(fileName: String): StudyLoadEntity?

    @Query("SELECT COUNT(*) FROM study_loads")
    suspend fun count(): Int
}