package com.example.orderease

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.*
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
        if (!isOnline()) return

        try {
            Log.d("Sync", "Starting Firebase sync...")
            val shop = db.shopDao().getShop()
            shop?.let { currentShop ->
                firestore.collection("shops").document(currentShop.username).set(currentShop).await()

                // Sync Products - FILTER OUT local paths
                val products = db.productDao().getProductsByShop(currentShop.shopId).first()
                products.forEach { product ->
                    val path = product.imagePath
                    // Only sync to Firestore if it's a web URL. If it's a local path (/...), sync null.
                    val syncProduct = if (path != null && path.startsWith("/")) {
                        product.copy(imagePath = null)
                    } else {
                        product
                    }
                    firestore.collection("products").document(product.productId.toString()).set(syncProduct).await()
                }
            }

            // Sync Customers
            val customers = db.customerDao().getAllCustomers().first()
            customers.forEach {
                firestore.collection("customers").document(it.customerId.toString()).set(it).await()
            }

            // Sync Orders
            val ordersWithDetails = db.orderDao().getOrdersWithDetailsInRange(0, Long.MAX_VALUE).first()
            ordersWithDetails.forEach { detail ->
                val orderId = detail.order.orderId.toString()
                firestore.collection("orders").document(orderId).set(detail.order).await()
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

    suspend fun syncFirebaseToLocal(username: String) {
        if (!isOnline()) return
        try {
            Log.d("Sync", "Pulling data for user: $username")
            val shopDoc = firestore.collection("shops").document(username).get().await()
            if (shopDoc.exists()) {
                val shop = shopDoc.toObject(Shop::class.java)
                shop?.let { 
                    Log.d("Sync", "Found Shop: ${it.name}, ShopId: ${it.shopId}")
                    db.shopDao().insertShop(it) 
                    
                    // Pull Products for this shop
                    val productsSnapshot = firestore.collection("products")
                        .whereEqualTo("shopId", it.shopId)
                        .get().await()
                    
                    Log.d("Sync", "Found ${productsSnapshot.size()} products in Cloud")
                    productsSnapshot.documents.forEach { doc ->
                        doc.toObject(Product::class.java)?.let { prod -> 
                            db.productDao().insertProduct(prod) 
                        }
                    }
                }
            } else {
                Log.w("Sync", "No shop document found for $username")
            }
            
            // Pull Customers
            val customersSnapshot = firestore.collection("customers").get().await()
            Log.d("Sync", "Found ${customersSnapshot.size()} customers in Cloud")
            customersSnapshot.documents.forEach { doc ->
                doc.toObject(Customer::class.java)?.let { db.customerDao().insertCustomer(it) }
            }
            
            // Pull Orders
            val ordersSnapshot = firestore.collection("orders").get().await()
            Log.d("Sync", "Found ${ordersSnapshot.size()} orders in Cloud")
            ordersSnapshot.documents.forEach { doc ->
                val order = doc.toObject(Order::class.java)
                order?.let {
                    db.orderDao().insertOrder(it)
                    val itemsSnapshot = firestore.collection("orders").document(doc.id).collection("items").get().await()
                    itemsSnapshot.documents.forEach { itemDoc ->
                        itemDoc.toObject(OrderItem::class.java)?.let { item -> db.orderItemDao().insertOrderItem(item) }
                    }
                }
            }
            Log.d("Sync", "Cloud to Local sync complete.")
        } catch (e: Exception) {
            Log.e("Sync", "Pull sync failed: ${e.message}", e)
        }
    }

    suspend fun deleteOrderFromFirebase(orderId: Int) {
        if (!isOnline()) return
        try {
            firestore.collection("orders").document(orderId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("Sync", "Failed to delete order: ${e.message}")
        }
    }
}
