package com.example.orderease

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.orderease.data.local.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncLocalToFirebase() {
        if (!isOnline()) {
            Log.d("Sync", "Offline: Skipping Firebase sync.")
            return
        }

        try {
            Log.d("Sync", "Online: Starting Firebase sync...")

            // 1. Sync Shop
            val shop = db.shopDao().getShop()
            shop?.let {
                firestore.collection("shops").document(it.shopId.toString()).set(it).await()
            }

            // 2. Sync Products
            val products = db.productDao().getProductsByShop(1).first()
            products.forEach {
                firestore.collection("products").document(it.productId.toString()).set(it).await()
            }

            // 3. Sync Customers
            val customers = db.customerDao().getAllCustomers().first()
            customers.forEach {
                firestore.collection("customers").document(it.customerId.toString()).set(it).await()
            }

            // 4. Sync Orders (and their items)
            val ordersWithDetails = db.orderDao().getOrdersWithDetailsInRange(0, Long.MAX_VALUE).first()
            ordersWithDetails.forEach { detail ->
                val orderId = detail.order.orderId.toString()
                firestore.collection("orders").document(orderId).set(detail.order).await()
                
                // Sync items as a sub-collection for each order
                detail.items.forEach { item ->
                    firestore.collection("orders").document(orderId)
                        .collection("items")
                        .document(item.orderItem.orderItemId.toString())
                        .set(item.orderItem)
                        .await()
                }
            }

            Log.d("Sync", "Sync successful!")
        } catch (e: Exception) {
            Log.e("Sync", "Sync failed: ${e.message}")
        }
    }
}
