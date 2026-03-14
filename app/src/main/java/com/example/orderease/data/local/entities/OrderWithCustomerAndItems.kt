package com.example.orderease.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class OrderWithCustomerAndItems(
    @Embedded val order: Order,
    @Relation(
        parentColumn = "customer_id",
        entityColumn = "customer_id"
    )
    val customer: Customer,
    @Relation(
        entity = OrderItem::class,
        parentColumn = "order_id",
        entityColumn = "order_id"
    )
    val items: List<OrderItemWithProduct>
)
