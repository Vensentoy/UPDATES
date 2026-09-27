package com.revyu.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revyu.app.data.local.entities.HomeWidgetPrefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeWidgetPrefDao {
    @Query("SELECT * FROM home_widget_prefs ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<HomeWidgetPrefEntity>>

    @Query("SELECT * FROM home_widget_prefs")
    suspend fun getAll(): List<HomeWidgetPrefEntity>

    @Query("SELECT * FROM home_widget_prefs WHERE key = :key")
    suspend fun get(key: String): HomeWidgetPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prefs: List<HomeWidgetPrefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: HomeWidgetPrefEntity)

    @Update
    suspend fun update(pref: HomeWidgetPrefEntity)

    @Query("DELETE FROM home_widget_prefs")
    suspend fun deleteAll()
}