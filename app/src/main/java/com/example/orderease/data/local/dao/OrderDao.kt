package com.example.orderease.data.local.dao

import androidx.room.*
import com.example.orderease.data.local.entities.Order
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderWithId(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    @Delete
    suspend fun deleteOrder(order: Order)

    @Query("SELECT * FROM orders WHERE order_id = :id")
    suspend fun getOrderById(id: Int): Order?

    @Query("SELECT * FROM orders WHERE order_date BETWEEN :startDate AND :endDate")
    fun getOrdersInRange(startDate: Long, endDate: Long): Flow<List<Order>>

    @Transaction
    @Query("SELECT * FROM orders WHERE collection_date BETWEEN :startDate AND :endDate")
    fun getOrdersWithDetailsInRange(startDate: Long, endDate: Long): Flow<List<OrderWithCustomerAndItems>>

    @Query("DELETE FROM orders WHERE order_date < :startDate OR order_date > :endDate")
    suspend fun clearOldOrders(startDate: Long, endDate: Long)
}
