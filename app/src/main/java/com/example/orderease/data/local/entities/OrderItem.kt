package com.example.orderease.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["product_id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "order_item_id") val orderItemId: Int = 0,
    val quantity: Int = 0,
    @ColumnInfo(name = "total_price") val totalPrice: Int = 0,
    @ColumnInfo(name = "order_id") val orderId: Int = 0,
    @ColumnInfo(name = "product_id") val productId: Int = 0
)
