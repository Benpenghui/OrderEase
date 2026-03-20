package com.example.orderease

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : BaseActivity() {

    private lateinit var customerNameInput: AutoCompleteTextView
    private lateinit var dateInput: EditText
    private lateinit var searchBtn: Button
    private lateinit var backBtn: ImageView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var emptyMessage: TextView
    private lateinit var syncManager: FirebaseSyncManager

    private var selectedDate: Calendar? = null
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        syncManager = FirebaseSyncManager(this)

        customerNameInput = findViewById(R.id.search_name_input)
        dateInput = findViewById(R.id.search_date_input)
        searchBtn = findViewById(R.id.search_btn)
        backBtn = findViewById(R.id.back_button)
        emptyMessage = findViewById(R.id.empty_message)

        dateInput.setOnClickListener { showDatePicker() }

        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyAdapter = HistoryAdapter(emptyList()) { order ->
            showOrderDetailsDialog(order)
        }
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
        
        searchBtn.setOnClickListener {
            performSearch()
        }

        backBtn.setOnClickListener {
            finish()
        }

        lifecycleScope.launch {
            loadCustomerDropdown()
        }
        
        // Load initial history including today's orders
        loadInitialHistory()
    }

    private suspend fun loadCustomerDropdown() {
        val db = AppDatabase.getDatabase(applicationContext)
        val customers = db.customerDao().getAllCustomers().first()
        val customerNames = customers.map { it.name }.distinct()
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNames)
        customerNameInput.setAdapter(adapter)
    }

    private fun loadInitialHistory() {
        performSearchInternal("", null)
    }

    private fun showDatePicker() {
        val calendar = selectedDate ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val newDate = Calendar.getInstance()
                newDate.set(year, month, day)
                selectedDate = newDate
                dateInput.setText(dateFormatter.format(newDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun performSearch() {
        val nameQuery = customerNameInput.text.toString().trim()
        val dateQuery = selectedDate?.timeInMillis

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

        performSearchInternal(nameQuery, dateQuery)
    }

    private fun performSearchInternal(nameQuery: String, dateQuery: Long?) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val flow = if (nameQuery.isNotEmpty() && dateQuery != null) {
                db.orderDao().searchOrdersByNameAndDate("%$nameQuery%", getStartOfDay(dateQuery), getEndOfDay(dateQuery))
            } else if (nameQuery.isNotEmpty()) {
                db.orderDao().searchOrdersByName("%$nameQuery%")
            } else if (dateQuery != null) {
                db.orderDao().getOrdersWithDetailsInRange(getStartOfDay(dateQuery), getEndOfDay(dateQuery))
            } else {
                // By default, show all orders up to current time (including today)
                db.orderDao().getOrdersWithDetailsInRange(0, System.currentTimeMillis() + 86400000L) 
            }

            flow.collectLatest { orders ->
                historyAdapter.updateOrders(orders)
                emptyMessage.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showOrderDetailsDialog(orderWithDetails: OrderWithCustomerAndItems) {
        val sb = StringBuilder()
        sb.append("Order ID: ${orderWithDetails.order.orderId}\n")
        sb.append("${getString(R.string.customer_label_prefix)}${orderWithDetails.customer.name}\n")
        sb.append("Phone: ${orderWithDetails.customer.phone}\n")
        val collectionSdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sb.append("Collection Date: ${collectionSdf.format(Date(orderWithDetails.order.collectionDate))}\n\n")
        sb.append("Items:\n")
        
        var totalCents = 0
        orderWithDetails.items.forEach { itemWithProduct ->
            val product = itemWithProduct.product
            val item = itemWithProduct.orderItem
            val linePrice = item.totalPrice
            totalCents += linePrice
            sb.append("- ${product.name} x${item.quantity} ($${String.format("%.2f", linePrice / 100.0)})\n")
        }
        sb.append("\n${getString(R.string.total_price_label).format(totalCents / 100.0)}")

        AlertDialog.Builder(this)
            .setTitle("Order Details")
            .setMessage(sb.toString())
            .setPositiveButton("Edit") { _, _ ->
                val intent = Intent(this, EditOrderActivity::class.java)
                intent.putExtra("ORDER_ID", orderWithDetails.order.orderId)
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}

class HistoryAdapter(
    private var orders: List<OrderWithCustomerAndItems>,
    private val onOrderSelected: (OrderWithCustomerAndItems) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val orderId: TextView = view.findViewById(R.id.order_id)
        val customerName: TextView = view.findViewById(R.id.customer_name)
        val orderTime: TextView = view.findViewById(R.id.order_time)
        val orderPrice: TextView = view.findViewById(R.id.order_price)
    }

    fun updateOrders(newOrders: List<OrderWithCustomerAndItems>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_order, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val orderWithDetails = orders[position]
        holder.orderId.text = orderWithDetails.order.orderId.toString()
        holder.customerName.text = orderWithDetails.customer.name
        holder.orderTime.text = dateTimeFormat.format(Date(orderWithDetails.order.collectionDate))

        val totalCents = orderWithDetails.items.sumOf { it.orderItem.totalPrice }
        holder.orderPrice.text = String.format("$%.2f", totalCents / 100.0)
        
        holder.itemView.setOnClickListener { onOrderSelected(orderWithDetails) }
    }

    override fun getItemCount() = orders.size
}
