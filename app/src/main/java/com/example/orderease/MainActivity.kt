package com.example.orderease

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var todayDateText: TextView
    private var isDeleteMode = false
    private var loadOrdersJob: Job? = null
    
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else if (isDeleteMode) {
                toggleDeleteMode(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        recyclerView = findViewById(R.id.orders_recycler_view)
        todayDateText = findViewById(R.id.today_date)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                updateBackCallback()
            }
            override fun onDrawerClosed(drawerView: View) {
                updateBackCallback()
            }
        })

        orderAdapter = OrderAdapter(emptyList(), { orderToDelete ->
            showDeleteConfirmationDialog(orderToDelete)
        }, { orderToDetail ->
            showOrderDetailsDialog(orderToDetail)
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter

        findViewById<ImageView>(R.id.settings_icon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_calendar -> startActivity(Intent(this, CalendarActivity::class.java))
                R.id.nav_products -> startActivity(Intent(this, ProductManagementActivity::class.java))
                R.id.nav_analytics -> startActivity(Intent(this, AnalyticsActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }

        findViewById<Button>(R.id.add_order_btn).setOnClickListener {
            startActivity(Intent(this, AddOrderActivity::class.java))
        }

        findViewById<Button>(R.id.delete_btn).setOnClickListener {
            toggleDeleteMode(!isDeleteMode)
        }

        findViewById<Button>(R.id.history_btn).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Update today's date text
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        todayDateText.text = sdf.format(Date())
        
        // Reset delete mode and reload orders
        toggleDeleteMode(false)
        loadTodayOrders()
    }

    private fun toggleDeleteMode(enabled: Boolean) {
        isDeleteMode = enabled
        orderAdapter.setDeleteMode(isDeleteMode)
        updateBackCallback()
    }

    private fun updateBackCallback() {
        onBackPressedCallback.isEnabled = drawerLayout.isDrawerOpen(GravityCompat.END) || isDeleteMode
    }

    private fun loadTodayOrders() {
        loadOrdersJob?.cancel() // Cancel previous collection if active
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfDay = calendar.timeInMillis

        val db = AppDatabase.getDatabase(this)
        loadOrdersJob = lifecycleScope.launch {
            db.orderDao().getOrdersWithDetailsInRange(startOfDay, endOfDay).collectLatest { orders ->
                orderAdapter.updateOrders(orders)
            }
        }
    }

    private fun showDeleteConfirmationDialog(orderWithDetails: OrderWithCustomerAndItems) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_btn_text))
            .setMessage("${getString(R.string.logout_confirm)} ${orderWithDetails.order.orderId}?")
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                deleteOrder(orderWithDetails)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
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

    private fun deleteOrder(orderWithDetails: OrderWithCustomerAndItems) {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.orderDao().deleteOrder(orderWithDetails.order)
        }
    }
}

class OrderAdapter(
    private var orders: List<OrderWithCustomerAndItems>,
    private val onDeleteClick: (OrderWithCustomerAndItems) -> Unit,
    private val onItemClick: (OrderWithCustomerAndItems) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var isDeleteMode = false

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val orderId: TextView = view.findViewById(R.id.order_id)
        val customerName: TextView = view.findViewById(R.id.customer_name)
        val orderTime: TextView = view.findViewById(R.id.order_time)
        val orderPrice: TextView = view.findViewById(R.id.order_price)
        val deleteBtn: ImageButton = view.findViewById(R.id.delete_item_btn)
    }

    fun updateOrders(newOrders: List<OrderWithCustomerAndItems>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isDeleteMode = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val orderWithDetails = orders[position]
        val order = orderWithDetails.order
        val customer = orderWithDetails.customer
        val items = orderWithDetails.items

        holder.orderId.text = order.orderId.toString()
        holder.customerName.text = customer.name
        
        // Use collectionDate for the display time on the main screen
        holder.orderTime.text = timeFormat.format(Date(order.collectionDate))

        val totalCents = items.sumOf { it.orderItem.totalPrice }
        holder.orderPrice.text = String.format("$%.2f", totalCents / 100.0)

        holder.deleteBtn.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
        holder.deleteBtn.setOnClickListener {
            onDeleteClick(orderWithDetails)
        }

        holder.itemView.setOnClickListener {
            if (!isDeleteMode) {
                onItemClick(orderWithDetails)
            }
        }
    }

    override fun getItemCount() = orders.size
}
