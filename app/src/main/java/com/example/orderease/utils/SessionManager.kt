package com.example.orderease.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("OrderEasePrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "logged_in_username"
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun clearSession() {
        prefs.edit().remove(KEY_USERNAME).apply()
    }
}
