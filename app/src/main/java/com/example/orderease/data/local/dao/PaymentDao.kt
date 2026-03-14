package com.example.orderease.data.local.dao

import androidx.room.*
import com.example.orderease.data.local.entities.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Query("SELECT * FROM payments WHERE order_id = :orderId")
    fun getPaymentsForOrder(orderId: Int): Flow<List<Payment>>

    @Delete
    suspend fun deletePayment(payment: Payment)
}
