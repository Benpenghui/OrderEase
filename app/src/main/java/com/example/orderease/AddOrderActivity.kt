package com.example.orderease

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AddOrderActivity : AppCompatActivity() {

    private lateinit var customerNameInput: EditText
    private lateinit var phoneNumberInput: EditText
    private lateinit var totalPriceText: TextView
    private lateinit var recyclerViewItems: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_order)

        // Initialize views
        customerNameInput = findViewById(R.id.customer_name_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        totalPriceText = findViewById(R.id.total_price_text)
        recyclerViewItems = findViewById(R.id.recyclerViewItems)

        // Setup RecyclerView
        recyclerViewItems.layoutManager = LinearLayoutManager(this)

        // Toolbar Buttons
        findViewById<ImageView>(R.id.settings_button).setOnClickListener {
            Toast.makeText(this, "设置", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.menu_button).setOnClickListener {
            finish()
        }

        // Save Button
        findViewById<Button>(R.id.save_order_button).setOnClickListener {
            submitOrder()
        }
    }

    private fun submitOrder() {
        val customerName = customerNameInput.text.toString()
        val phoneNumber = phoneNumberInput.text.toString()

        if (customerName.isEmpty()) {
            Toast.makeText(this, "请输入顾客姓名", Toast.LENGTH_SHORT).show()
            return
        }

        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "请输入电话号码", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "订单已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}