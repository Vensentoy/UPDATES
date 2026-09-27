package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revyu.app.data.local.entities.ScheduleEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_entries")
    fun observeAll(): Flow<List<ScheduleEntryEntity>>

    @Query("SELECT * FROM schedule_entries")
    suspend fun getAll(): List<ScheduleEntryEntity>

    @Query("SELECT * FROM schedule_entries WHERE subjectId = :subjectId")
    fun observeForSubject(subjectId: String): Flow<List<ScheduleEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ScheduleEntryEntity>)

    @Delete
    suspend fun delete(entry: ScheduleEntryEntity)

    @Query("DELETE FROM schedule_entries WHERE subjectId = :subjectId")
    suspend fun deleteForSubject(subjectId: String)
}
