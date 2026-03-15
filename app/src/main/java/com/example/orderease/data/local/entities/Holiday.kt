package com.example.orderease.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Format: YYYY-MM-DD
    val localName: String,
    val name: String,
    val countryCode: String,
    val year: Int
)
