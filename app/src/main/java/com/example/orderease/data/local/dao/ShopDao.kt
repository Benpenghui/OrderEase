package com.example.orderease.data.local.dao

import androidx.room.*
import com.example.orderease.data.local.entities.Shop

@Dao
interface ShopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: Shop)

    @Query("SELECT * FROM shops LIMIT 1")
    suspend fun getShop(): Shop?

    @Query("SELECT * FROM shops WHERE username = :username LIMIT 1")
    suspend fun getShopByUsername(username: String): Shop?
}
