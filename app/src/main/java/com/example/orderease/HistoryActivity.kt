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
    // Use Locale.getDefault() for system language matching
    private val dateFormat get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

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
        // Sample data with generic date format for searching
        allOrders.addAll(listOf(
            HistoryOrder(
                id = "98562014",
                customerName = "老板娘",
                date = "29/11/2025",
                time = "10:00 AM",
                price = "$15.00",
                items = listOf("物品 1 x2", "物品 2 x1")
            ),
            HistoryOrder(
                id = "814134562",
                customerName = "林先生",
                date = "26/11/2025",
                time = "12:00 PM",
                price = "$3.00",
                items = listOf("物品 3 x1")
            ),
            HistoryOrder(
                id = "741852963",
                customerName = "王小姐",
                date = "27/11/2025",
                time = "02:00 PM",
                price = "$19.20",
                items = listOf("物品 1 x3")
            )
        ))
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
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
        displayCustomerName.text = getString(R.string.customer_label_prefix) + order.customerName
        displayOrderTime.text = getString(R.string.time_label_prefix) + order.time
        displayOrderPrice.text = getString(R.string.price_label_prefix) + order.price

        val itemsText = order.items.joinToString("\n") { "• $it" }
        displayOrderItems.text = itemsText
    }

    private fun showNoResults() {
        displayOrderId.text = getString(R.string.no_results)
        displayOrderDate.text = ""
        orderDetailsSection.visibility = View.GONE
        emptyMessage.visibility = View.GONE

        Toast.makeText(this, getString(R.string.no_matching_orders), Toast.LENGTH_SHORT).show()
    }
}

data class HistoryOrder(
    val id: String,
    val customerName: String,
    val date: String,
    val time: String,
    val price: String,
    val items: List<String>
)
