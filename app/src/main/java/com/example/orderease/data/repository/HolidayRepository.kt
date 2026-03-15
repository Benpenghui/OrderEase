package com.example.orderease.data.repository

import com.example.orderease.data.local.dao.HolidayDao
import com.example.orderease.data.local.entities.Holiday
import com.example.orderease.data.remote.HolidayApiService
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HolidayRepository(private val holidayDao: HolidayDao) {

    private val apiService: HolidayApiService = Retrofit.Builder()
        .baseUrl("https://date.nager.at/api/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HolidayApiService::class.java)

    fun getHolidays(year: Int): Flow<List<Holiday>> = holidayDao.getHolidaysByYear(year)

    suspend fun refreshHolidays(year: Int, countryCode: String) {
        try {
            val count = holidayDao.getHolidayCountByYear(year)
            if (count == 0) {
                val holidays = apiService.getPublicHolidays(year, countryCode)
                val holidaysWithYear = holidays.map { it.copy(year = year) }
                holidayDao.insertHolidays(holidaysWithYear)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
