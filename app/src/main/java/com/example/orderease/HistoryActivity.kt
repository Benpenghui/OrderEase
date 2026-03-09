package com.example.orderease

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var searchNameInput: EditText
    private lateinit var searchDateInput: EditText
    private lateinit var searchBtn: Button

    private lateinit var displayOrderId: TextView
    private lateinit var displayOrderDate: TextView
    private lateinit var displayCustomerName: TextView
    private lateinit var displayOrderTime: TextView
    private lateinit var displayOrderPrice: TextView
    private lateinit var displayOrderItems: TextView
    private lateinit var orderDetailsSection: LinearLayout
    private lateinit var emptyMessage: TextView

    private val allOrders = mutableListOf<HistoryOrder>()
    private val dateFormat = SimpleDateFormat("yyyy年 MM月 dd日", Locale.CHINESE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Initialize views
        searchNameInput = findViewById(R.id.search_name_input)
        searchDateInput = findViewById(R.id.search_date_input)
        searchBtn = findViewById(R.id.search_btn)

        displayOrderId = findViewById(R.id.display_order_id)
        displayOrderDate = findViewById(R.id.display_order_date)
        displayCustomerName = findViewById(R.id.display_customer_name)
        displayOrderTime = findViewById(R.id.display_order_time)
        displayOrderPrice = findViewById(R.id.display_order_price)
        displayOrderItems = findViewById(R.id.display_order_items)
        orderDetailsSection = findViewById(R.id.order_details_section)
        emptyMessage = findViewById(R.id.empty_message)

        // Back button
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        // Load sample data
        loadSampleOrders()

        // Make date input not editable and show date picker on click
        searchDateInput.isFocusable = false
        searchDateInput.isClickable = true
        searchDateInput.setOnClickListener {
            showDatePicker()
        }

        // Search button click
        searchBtn.setOnClickListener {
            performSearch()
        }
    }

    private fun loadSampleOrders() {
        allOrders.clear()
        allOrders.addAll(listOf(
            HistoryOrder(
                id = "98562014",
                customerName = "老板娘",
                date = "2025年 11月 29日",
                time = "早上 10 点",
                price = "$15.00",
                items = listOf("物品 1 x2", "物品 2 x1")
            ),
            HistoryOrder(
                id = "814134562",
                customerName = "林先生",
                date = "2025年 11月 26日",
                time = "下午 12 点",
                price = "$3.00",
                items = listOf("物品 3 x1")
            ),
            HistoryOrder(
                id = "741852963",
                customerName = "王小姐",
                date = "2025年 11月 27日",
                time = "下午 2 点",
                price = "$19.20",
                items = listOf("物品 1 x3")
            ),
            HistoryOrder(
                id = "159753486",
                customerName = "老板娘",
                date = "2025年 11月 28日",
                time = "早上 9 点",
                price = "$25.50",
                items = listOf("物品 4 x2")
            ),
            HistoryOrder(
                id = "357951486",
                customerName = "陈先生",
                date = "2025年 11月 29日",
                time = "下午 1 点",
                price = "$12.00",
                items = listOf("物品 2 x2")
            ),
            HistoryOrder(
                id = "246813579",
                customerName = "林先生",
                date = "2025年 11月 30日",
                time = "早上 11 点",
                price = "$8.40",
                items = listOf("物品 5 x1")
            )
        ))
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Format the selected date
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                val formattedDate = dateFormat.format(selectedCalendar.time)

                searchDateInput.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun performSearch() {
        val nameQuery = searchNameInput.text.toString().lowercase()
        val dateQuery = searchDateInput.text.toString()

        val results = allOrders.filter { order ->
            val matchName = nameQuery.isEmpty() || order.customerName.lowercase().contains(nameQuery)
            val matchDate = dateQuery.isEmpty() || order.date.contains(dateQuery)
            matchName && matchDate
        }

        if (results.isNotEmpty()) {
            displayOrder(results[0])
        } else {
            showNoResults()
        }
    }

    private fun displayOrder(order: HistoryOrder) {
        emptyMessage.visibility = View.GONE
        orderDetailsSection.visibility = View.VISIBLE

        displayOrderId.text = order.id
        displayOrderDate.text = order.date
        displayCustomerName.text = "顾客: ${order.customerName}"
        displayOrderTime.text = "时间: ${order.time}"
        displayOrderPrice.text = "价格: ${order.price}"

        val itemsText = order.items.joinToString("\n") { "• $it" }
        displayOrderItems.text = itemsText
    }

    private fun showNoResults() {
        displayOrderId.text = "无结果"
        displayOrderDate.text = ""
        orderDetailsSection.visibility = View.GONE
        emptyMessage.visibility = View.GONE

        Toast.makeText(this, "没有找到匹配的订单", Toast.LENGTH_SHORT).show()
    }
}

// Data class for history orders
data class HistoryOrder(
    val id: String,
    val customerName: String,
    val date: String,
    val time: String,
    val price: String,
    val items: List<String>
)