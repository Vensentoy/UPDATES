package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revyu.app.data.local.entities.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY dayOfWeek, startMinute")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY dayOfWeek, startMinute")
    suspend fun getAll(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute")
    fun observeForDay(dayOfWeek: Int): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE generationGroupId = :groupId ORDER BY startMinute")
    suspend fun forGroup(groupId: String): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE subjectId = :subjectId ORDER BY dayOfWeek, startMinute")
    fun observeForSubject(subjectId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: String): CalendarEventEntity?

    @Query("DELETE FROM calendar_events WHERE sourceStudyLoadId = :studyLoadId")
    suspend fun deleteForStudyLoad(studyLoadId: String)

    @Query("DELETE FROM calendar_events WHERE generationGroupId LIKE 'school_calendar-%'")
    suspend fun deleteSchoolCalendarEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity)

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE generationGroupId = :groupId")
    suspend fun deleteByGenerationGroup(groupId: String)

    @Query("DELETE FROM calendar_events WHERE isCustom = 0 AND type != :classType")
    suspend fun deleteAllSuggestions(classType: String)

    @Query("DELETE FROM calendar_events WHERE subjectId = :subjectId")
    suspend fun deleteForSubject(subjectId: String)

    @Delete
    suspend fun delete(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}