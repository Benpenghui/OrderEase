package com.example.orderease.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "customer_id") val customerId: Int = 0,
    val name: String,
    val phone: String,
    val notes: String?
)
