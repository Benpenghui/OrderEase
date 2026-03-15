package com.example.orderease.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        // Initialize DB only if empty
        initDummyAdmin()

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(applicationContext))[LoginViewModel::class.java]

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
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
                updateUiWithUser(loginResult.success)

                // Create rest of dummy data on successful login
                // createDummyData()

                // Firebase Sync Logic with Toast
                val syncManager = FirebaseSyncManager(applicationContext)
                if (syncManager.isOnline()) {
                    Toast.makeText(applicationContext, "Online: Syncing data to Firebase...", Toast.LENGTH_SHORT).show()
                    
                    // Use a CoroutineScope that isn't tied to the Activity lifecycle
                    // so it doesn't cancel when finish() is called.
                    CoroutineScope(Dispatchers.IO).launch {
                        syncManager.syncLocalToFirebase()
                    }
                } else {
                    Toast.makeText(applicationContext, "Offline: No network detected, using local database.", Toast.LENGTH_SHORT).show()
                }

                // Redirect to MainActivity after successful login
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                startActivity(intent)
                finish() // Prevent going back to login screen
            }
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                loginViewModel.login(username.text.toString(), password.text.toString())
            }
        }
    }

    private fun initDummyAdmin() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // Only insert if shop table is empty
            val existingShop = db.shopDao().getShop()
            if (existingShop == null) {
                val shop = Shop(1, "OrderEase HQ", "admin", "password123", "123-456-7890")
                db.shopDao().insertShop(shop)
                
                // Also add one default product if none exists
                db.productDao().insertProduct(Product(101, "Classic Cake", 2500, 1))
            }
        }
    }

    private fun createDummyData() {
        // ... method implementation ...
    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        val displayName = model.displayName
        Toast.makeText(
            applicationContext,
            "$welcome $displayName",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
