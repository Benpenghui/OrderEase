package com.example.orderease.data

import android.content.Context
import android.util.Log
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.Shop
import com.example.orderease.data.model.LoggedInUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource(private val context: Context) {

    fun login(username: String, password: String): Result<LoggedInUser> {
        return try {
            val db = AppDatabase.getDatabase(context)

            // 1. Check Local DB first
            var shop = runBlocking {
                db.shopDao().getShopByUsername(username)
            }

            // 2. Fallback to Firebase if not found locally (Multi-device support)
            if (shop == null) {
                shop = runBlocking {
                    try {
                        Log.d("LoginCheck", "Checking Firebase Database for user: $username")
                        val doc = FirebaseFirestore.getInstance()
                            .collection("shops")
                            .document(username)
                            .get()
                            .await()

                        if (doc.exists()) {
                            Log.d("LoginCheck", "Document exists in Firebase")
                            val cloudShop = doc.toObject(Shop::class.java)
                            if (cloudShop != null) {
                                Log.d("LoginCheck", "Found shop: ${cloudShop.username}, Password match: ${cloudShop.password == password}")
                                if (cloudShop.password == password) {
                                    // Save to local DB so it's found locally next time
                                    db.shopDao().insertShop(cloudShop)
                                    cloudShop
                                } else null
                            } else {
                                Log.e("LoginCheck", "Failed to convert document to Shop object")
                                null
                            }
                        } else {
                            Log.d("LoginCheck", "No such document for user: $username")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("LoginCheck", "Firebase Error: ${e.message}", e)
                        null // Network error or not found
                    }
                }
            }

            // 3. Final validation
            if (shop != null && shop.password == password) {
                val user = LoggedInUser(shop.shopId.toString(), shop.name)
                Result.Success(user)
            } else {
                Log.d("LoginCheck", "Login validation failed for user: $username")
                Result.Error(IOException("Invalid username or password"))
            }
        } catch (e: Exception) {
            Log.e("LoginCheck", "General Login Error", e)
            Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // revoking authentication
    }
}
