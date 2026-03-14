package com.example.orderease.data.local.dao

import androidx.room.*
import com.example.orderease.data.local.entities.OrderItem
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(orderItem: OrderItem)

    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    fun getItemsForOrder(orderId: Int): Flow<List<OrderItem>>

    @Delete
    suspend fun deleteOrderItem(orderItem: OrderItem)
}
