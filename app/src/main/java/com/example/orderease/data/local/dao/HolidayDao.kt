package com.example.orderease.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.orderease.data.local.entities.Holiday
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays WHERE year = :year")
    fun getHolidaysByYear(year: Int): Flow<List<Holiday>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolidays(holidays: List<Holiday>)

    @Query("SELECT COUNT(*) FROM holidays WHERE year = :year")
    suspend fun getHolidayCountByYear(year: Int): Int

    @Query("SELECT * FROM holidays WHERE date >= :dateStr ORDER BY date ASC LIMIT 1")
    suspend fun getNextHoliday(dateStr: String): Holiday?
}
