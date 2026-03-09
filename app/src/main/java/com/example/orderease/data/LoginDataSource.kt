package com.example.orderease.data

import com.example.orderease.data.model.LoggedInUser
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        try {
            // Simple validation for testing
            if (username.isNotEmpty() && password.isNotEmpty()) {
                // For now, accept any non-empty credentials
                val user = LoggedInUser(java.util.UUID.randomUUID().toString(), username)
                return Result.Success(user)
            } else {
                return Result.Error(IOException("Username and password cannot be empty"))
            }
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}