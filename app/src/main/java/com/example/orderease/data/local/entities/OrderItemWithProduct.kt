package com.example.orderease.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class OrderItemWithProduct(
    @Embedded val orderItem: OrderItem,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "product_id"
    )
    val product: Product
)
