package com.example.orderease.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["shop_id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["customer_id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Order(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "order_id") val orderId: Int = 0,
    @ColumnInfo(name = "payment_status") val paymentStatus: Boolean = false,
    @ColumnInfo(name = "order_date") val orderDate: Long = 0,
    @ColumnInfo(name = "collection_date") val collectionDate: Long = 0,
    @ColumnInfo(name = "collection_status") val collectionStatus: Boolean = false,
    @ColumnInfo(name = "shop_id") val shopId: Int = 1,
    @ColumnInfo(name = "customer_id") val customerId: Int = 0
)
