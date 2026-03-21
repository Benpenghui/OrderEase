package com.example.orderease.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["shop_id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "product_id") val productId: Int = 0,
    val name: String = "",
    val cost: Int = 0,
    @ColumnInfo(name = "shop_id") val shopId: Int = 1,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)
