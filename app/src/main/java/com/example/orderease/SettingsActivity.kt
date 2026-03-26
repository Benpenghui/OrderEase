package com.example.orderease

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.ui.login.LoginActivity
import com.example.orderease.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var shopNameInput: TextInputEditText
    private lateinit var shopPhoneInput: TextInputEditText
    private lateinit var currentLanguageText: TextView
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sessionManager = SessionManager(this)
        shopNameInput = findViewById(R.id.shop_name_input)
        shopPhoneInput = findViewById(R.id.shop_phone_input)
        currentLanguageText = findViewById(R.id.current_language_text)

        // Set current language text based on actual locale
        updateLanguageDisplay()

        // Load current shop info
        loadShopInfo()

        findViewById<ImageView>(R.id.back_btn).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.update_info_btn).setOnClickListener {
            updateShopInfo()
        }

        findViewById<Button>(R.id.sync_data_btn).setOnClickListener {
            syncData()
        }

        findViewById<Button>(R.id.logout_btn).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<LinearLayout>(R.id.language_btn).setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun updateLanguageDisplay() {
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
        currentLanguageText.text = when (currentLocale) {
            "zh" -> "中文"
            else -> "English"
        }
    }

    private fun loadShopInfo() {
        lifecycleScope.launch {
            val username = sessionManager.getUsername()
            if (username != null) {
                val shop = db.shopDao().getShopByUsername(username)
                shop?.let {
                    shopNameInput.setText(it.name)
                    shopPhoneInput.setText(it.phoneNumber)
                }
            } else {
                // Fallback if session is missing for some reason
                val shop = db.shopDao().getShop()
                shop?.let {
                    shopNameInput.setText(it.name)
                    shopPhoneInput.setText(it.phoneNumber)
                }
            }
        }
    }

    private fun updateShopInfo() {
        val newName = shopNameInput.text.toString().trim()
        val newPhone = shopPhoneInput.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_name), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val username = sessionManager.getUsername()
            val shop = if (username != null) {
                db.shopDao().getShopByUsername(username)
            } else {
                db.shopDao().getShop()
            }
            
            shop?.let {
                val updatedShop = it.copy(name = newName, phoneNumber = newPhone)
                db.shopDao().updateShop(updatedShop)
                Toast.makeText(this@SettingsActivity, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncData() {
        val syncManager = FirebaseSyncManager(this)
        if (syncManager.isOnline()) {
            Toast.makeText(this, getString(R.string.syncing_data), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                // 1. Push local changes to Firebase
                syncManager.syncLocalToFirebase()
                
                // 2. Pull latest data from Firebase (including products from other devices/previous sessions)
                val username = sessionManager.getUsername()
                if (username != null) {
                    syncManager.syncFirebaseToLocal(username)
                }
                
                Toast.makeText(this@SettingsActivity, getString(R.string.sync_complete), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "中文")
        val languageTags = arrayOf("en", "zh")
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language))
            .setItems(languages) { _, which ->
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageTags[which])
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
            .show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_confirm))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun performLogout() {
        sessionManager.clearSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
