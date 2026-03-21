package com.example.orderease.data.local.dao

import androidx.room.*
import com.example.orderease.data.local.entities.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM products WHERE shop_id = :shopId AND is_deleted = 0")
    fun getProductsByShop(shopId: Int): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE shop_id = :shopId")
    fun getAllProductsByShop(shopId: Int): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE product_id = :id")
    suspend fun getProductById(id: Int): Product?
}
