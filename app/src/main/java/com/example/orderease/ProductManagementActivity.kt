package com.example.orderease

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ProductManagementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_management)

        findViewById<Button>(R.id.back_btn).setOnClickListener {
            finish()
        }
    }
}
