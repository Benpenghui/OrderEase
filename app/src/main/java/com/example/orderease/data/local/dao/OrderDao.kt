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

    @Query("SELECT * FROM orders WHERE shop_id = :shopId AND order_date BETWEEN :startDate AND :endDate")
    fun getOrdersInRange(shopId: Int, startDate: Long, endDate: Long): Flow<List<Order>>

    @Transaction
    @Query("SELECT * FROM orders WHERE shop_id = :shopId AND collection_date BETWEEN :startDate AND :endDate")
    fun getOrdersWithDetailsInRange(shopId: Int, startDate: Long, endDate: Long): Flow<List<OrderWithCustomerAndItems>>

    @Transaction
    @Query("""
        SELECT orders.* FROM orders 
        INNER JOIN customers ON orders.customer_id = customers.customer_id 
        WHERE orders.shop_id = :shopId AND customers.name LIKE :name
    """)
    fun searchOrdersByName(shopId: Int, name: String): Flow<List<OrderWithCustomerAndItems>>

    @Transaction
    @Query("""
        SELECT orders.* FROM orders 
        INNER JOIN customers ON orders.customer_id = customers.customer_id 
        WHERE orders.shop_id = :shopId AND customers.name LIKE :name AND orders.collection_date BETWEEN :startDate AND :endDate
    """)
    fun searchOrdersByNameAndDate(shopId: Int, name: String, startDate: Long, endDate: Long): Flow<List<OrderWithCustomerAndItems>>

    @Query("DELETE FROM orders WHERE order_date < :startDate OR order_date > :endDate")
    suspend fun clearOldOrders(startDate: Long, endDate: Long)
}
