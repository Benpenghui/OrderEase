package com.example.orderease.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.orderease.FirebaseSyncManager
import com.example.orderease.MainActivity
import com.example.orderease.R
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.*
import com.example.orderease.databinding.ActivityLoginBinding
import com.example.orderease.utils.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private var isNewUser = false
    private var isUserChecked = false
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(applicationContext))[LoginViewModel::class.java]

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer
            login.isEnabled = loginState.isDataValid
            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer
            loading.visibility = View.GONE
            
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                handleLoginSuccess(username.text.toString())
            }
        })

        login.setOnClickListener {
            val user = username.text.toString().trim()
            if (user.isEmpty()) {
                username.error = getString(R.string.enter_username_error)
                return@setOnClickListener
            }

            if (!isUserChecked) {
                checkUserExists(user)
            } else {
                if (isNewUser) {
                    createNewAccount()
                } else {
                    loading.visibility = View.VISIBLE
                    loginViewModel.login(user, password.text.toString())
                }
            }
        }
    }

    private fun checkUserExists(username: String) {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("shops")
                    .document(username)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    binding.loading.visibility = View.GONE
                    isUserChecked = true
                    if (doc.exists()) {
                        // User exists, show password only
                        isNewUser = false
                        binding.loginHeader?.text = getString(R.string.login_header_returning)
                        binding.loginSubtitle?.text = getString(R.string.login_subtitle_returning)
                        binding.password.visibility = View.VISIBLE
                        binding.login.text = getString(R.string.action_sign_in_short)
                        Toast.makeText(this@LoginActivity, R.string.user_found_prompt, Toast.LENGTH_SHORT).show()
                    } else {
                        // New user, show all fields
                        isNewUser = true
                        binding.loginHeader?.text = getString(R.string.login_header_new)
                        binding.loginSubtitle?.text = getString(R.string.login_subtitle_new)
                        binding.shopName?.visibility = View.VISIBLE
                        binding.phoneNumber?.visibility = View.VISIBLE
                        binding.password.visibility = View.VISIBLE
                        binding.login.text = getString(R.string.register_btn_text)
                        Toast.makeText(this@LoginActivity, R.string.user_not_found_prompt, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loading.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createNewAccount() {
        val user = binding.username.text.toString().trim()
        val pass = binding.password.text.toString()
        val name = binding.shopName?.text.toString().trim()
        val phone = binding.phoneNumber?.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty() || pass.length <= 5) {
            Toast.makeText(this, R.string.fill_all_fields_error, Toast.LENGTH_SHORT).show()
            return
        }

        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Generate a simple ID for new shops. 
                // Since shopId is Int, we'll use seconds since epoch.
                val newShopId = (System.currentTimeMillis() / 1000).toInt()
                val newShop = Shop(newShopId, name, user, pass, phone)
                
                // Save to Firebase
                FirebaseFirestore.getInstance().collection("shops").document(user).set(newShop).await()
                
                // Save to Local
                AppDatabase.getDatabase(applicationContext).shopDao().insertShop(newShop)

                withContext(Dispatchers.Main) {
                    handleLoginSuccess(user)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loading.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "${getString(R.string.registration_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleLoginSuccess(username: String) {
        sessionManager.saveUsername(username)
        val syncManager = FirebaseSyncManager(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            if (syncManager.isOnline()) {
                syncManager.syncFirebaseToLocal(username)
            }
            
            withContext(Dispatchers.Main) {
                binding.loading.visibility = View.GONE
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}
