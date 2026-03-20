package com.example.orderease.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shops")
data class Shop(
    @PrimaryKey @ColumnInfo(name = "shop_id") val shopId: Int = 0,
    val name: String = "",
    val username: String = "",
    val password: String = "",
    @ColumnInfo(name = "phone_number") val phoneNumber: String = ""
)
