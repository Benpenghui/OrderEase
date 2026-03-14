package com.example.orderease.data

import android.content.Context
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.model.LoggedInUser
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource(private val context: Context) {

    fun login(username: String, password: String): Result<LoggedInUser> {
        return try {
            val db = AppDatabase.getDatabase(context)
            // Using runBlocking here because the original LoginRepository/ViewModel structure 
            // is synchronous, but Room is asynchronous. 
            val shop = runBlocking {
                db.shopDao().getShopByUsername(username)
            }

            if (shop != null && shop.password == password) {
                val user = LoggedInUser(shop.shopId.toString(), shop.name)
                Result.Success(user)
            } else {
                Result.Error(IOException("Invalid username or password"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // revoking authentication
    }
}
