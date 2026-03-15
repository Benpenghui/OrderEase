package com.example.orderease.data.remote

import com.example.orderease.data.local.entities.Holiday
import retrofit2.http.GET
import retrofit2.http.Path

interface HolidayApiService {
    @GET("PublicHolidays/{year}/{countryCode}")
    suspend fun getPublicHolidays(
        @Path("year") year: Int,
        @Path("countryCode") countryCode: String
    ): List<Holiday>
}
