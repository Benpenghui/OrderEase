package com.example.orderease

import android.content.Intent
import android.icu.util.ChineseCalendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import com.example.orderease.data.repository.HolidayRepository
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var titleText: TextView
    private lateinit var todayDateText: TextView
    private lateinit var importantDateText: TextView
    private lateinit var importantDateLabel: TextView
    private var isDeleteMode = false
    private var isEditMode = false
    private var loadOrdersJob: Job? = null
    
    private var showingToday = true // Toggle state
    
    private lateinit var holidayRepository: HolidayRepository

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else if (isDeleteMode || isEditMode) {
                toggleDeleteMode(false)
                toggleEditMode(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = AppDatabase.getDatabase(this)
        holidayRepository = HolidayRepository(db.holidayDao())

        drawerLayout = findViewById(R.id.drawer_layout)
        recyclerView = findViewById(R.id.orders_recycler_view)
        titleText = findViewById(R.id.title)
        todayDateText = findViewById(R.id.today_date)
        importantDateText = findViewById(R.id.important_date)
        importantDateLabel = findViewById(R.id.important_date_label)

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
            if (!isDeleteMode && !isEditMode) {
                showOrderDetailsDialog(orderToDetail)
            }
        }, { orderToEdit ->
            val intent = Intent(this, EditOrderActivity::class.java)
            intent.putExtra("ORDER_ID", orderToEdit.order.orderId)
            startActivity(intent)
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter

        findViewById<ImageView>(R.id.settings_icon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        // Arrow Buttons Logic
        findViewById<ImageButton>(R.id.prev_btn).setOnClickListener {
            toggleOrderView()
        }
        findViewById<ImageButton>(R.id.next_btn).setOnClickListener {
            toggleOrderView()
        }

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_calendar -> startActivity(Intent(this, CalendarActivity::class.java))
                R.id.nav_products -> startActivity(Intent(this, ProductManagementActivity::class.java))
                R.id.nav_analytics -> startActivity(Intent(this, AnalyticsActivity::class.java))
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

    private fun toggleOrderView() {
        showingToday = !showingToday
        refreshView()
    }

    private fun refreshView() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()

        if (showingToday) {
            titleText.text = getString(R.string.today_orders_title)
            todayDateText.text = sdf.format(calendar.time)
            loadTodayOrders()
        } else {
            titleText.text = getString(R.string.week_orders_title)
            
            // Get start (Monday) and end (Sunday) of current week
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val startWeek = sdf.format(calendar.time)
            calendar.add(Calendar.DAY_OF_WEEK, 6)
            val endWeek = sdf.format(calendar.time)
            
            todayDateText.text = "$startWeek - $endWeek"
            loadWeekOrders()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset modes and reload current view
        toggleDeleteMode(false)
        toggleEditMode(false)
        refreshView()
        updateNextFestiveDate()
    }

    private fun updateNextFestiveDate() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            
            // Ensure holidays are loaded
            holidayRepository.refreshHolidays(year, "SG")
            holidayRepository.refreshHolidays(year + 1, "SG")

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = sdf.format(now.time)
            
            val nextHoliday = holidayRepository.getNextHoliday(dateStr)
            val nextLunar = getNextLunarFestival(now)
            
            val displaySdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            if (nextHoliday != null && nextLunar != null) {
                val holidayDate = Calendar.getInstance()
                holidayDate.time = sdf.parse(nextHoliday.date) ?: Date()
                
                if (holidayDate.before(nextLunar.first)) {
                    importantDateText.text = displaySdf.format(holidayDate.time)
                    importantDateLabel.text = "(${nextHoliday.name})"
                } else {
                    importantDateText.text = displaySdf.format(nextLunar.first.time)
                    importantDateLabel.text = "(${nextLunar.second})"
                }
            } else if (nextHoliday != null) {
                val holidayDate = Calendar.getInstance()
                holidayDate.time = sdf.parse(nextHoliday.date) ?: Date()
                importantDateText.text = displaySdf.format(holidayDate.time)
                importantDateLabel.text = "(${nextHoliday.name})"
            } else if (nextLunar != null) {
                importantDateText.text = displaySdf.format(nextLunar.first.time)
                importantDateLabel.text = "(${nextLunar.second})"
            }
        }
    }

    private fun getNextLunarFestival(currentDate: Calendar): Pair<Calendar, String>? {
        val festivals = listOf(
            Pair(1, 1) to getString(R.string.cny_day_1),
            Pair(1, 2) to getString(R.string.cny_day_2),
            Pair(1, 15) to getString(R.string.chap_goh_meh),
            Pair(5, 5) to getString(R.string.dragon_boat),
            Pair(8, 15) to getString(R.string.mid_autumn)
        )

        val results = mutableListOf<Pair<Calendar, String>>()
        val currentYear = currentDate.get(Calendar.YEAR)

        for (year in currentYear..currentYear + 1) {
            for ((monthDay, name) in festivals) {
                val (month, day) = monthDay
                val cal = ChineseCalendar()
                // ChineseCalendar year offset is roughly 2637 years from Gregorian
                cal.set(ChineseCalendar.EXTENDED_YEAR, year + 2637)
                cal.set(ChineseCalendar.MONTH, month - 1)
                cal.set(ChineseCalendar.DAY_OF_MONTH, day)
                
                val solarCal = Calendar.getInstance()
                solarCal.timeInMillis = cal.timeInMillis
                if (solarCal.after(currentDate)) {
                    results.add(solarCal to name)
                }
            }
            
            // CNY Eve is the last day of the 12th month
            val calEve = ChineseCalendar()
            calEve.set(ChineseCalendar.EXTENDED_YEAR, year + 2637)
            calEve.set(ChineseCalendar.MONTH, 11) // 12th month
            calEve.set(ChineseCalendar.DAY_OF_MONTH, calEve.getActualMaximum(ChineseCalendar.DAY_OF_MONTH))
            
            val solarEve = Calendar.getInstance()
            solarEve.timeInMillis = calEve.timeInMillis
            if (solarEve.after(currentDate)) {
                results.add(solarEve to getString(R.string.cny_eve))
            }
        }
        
        return results.minByOrNull { it.first.timeInMillis }
    }

    private fun toggleDeleteMode(enabled: Boolean) {
        isDeleteMode = enabled
        if (enabled) isEditMode = false
        orderAdapter.setDeleteMode(isDeleteMode)
        orderAdapter.setEditMode(isEditMode)
        updateBackCallback()
    }

    private fun toggleEditMode(enabled: Boolean) {
        isEditMode = enabled
        if (enabled) isDeleteMode = false
        orderAdapter.setEditMode(isEditMode)
        orderAdapter.setDeleteMode(isDeleteMode)
        updateBackCallback()
    }

    private fun updateBackCallback() {
        onBackPressedCallback.isEnabled = drawerLayout.isDrawerOpen(GravityCompat.END) || isDeleteMode || isEditMode
    }

    private fun loadTodayOrders() {
        loadOrdersJob?.cancel()
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

    private fun loadWeekOrders() {
        loadOrdersJob?.cancel()
        val calendar = Calendar.getInstance()
        
        // Start of week (Monday)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfWeek = calendar.timeInMillis

        // End of week (Sunday)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfWeek = calendar.timeInMillis

        val db = AppDatabase.getDatabase(this)
        loadOrdersJob = lifecycleScope.launch {
            db.orderDao().getOrdersWithDetailsInRange(startOfWeek, endOfWeek).collectLatest { orders ->
                orderAdapter.updateOrders(orders)
            }
        }
    }

    private fun showDeleteConfirmationDialog(orderWithDetails: OrderWithCustomerAndItems) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_btn_text))
            .setMessage("${getString(R.string.delete_order_confirm).format(orderWithDetails.order.orderId)}")
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
        val syncManager = FirebaseSyncManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            db.orderDao().deleteOrder(orderWithDetails.order)
            syncManager.deleteOrderFromFirebase(orderWithDetails.order.orderId)
        }
    }
}

class OrderAdapter(
    private var orders: List<OrderWithCustomerAndItems>,
    private val onDeleteClick: (OrderWithCustomerAndItems) -> Unit,
    private val onItemClick: (OrderWithCustomerAndItems) -> Unit,
    private val onEditClick: (OrderWithCustomerAndItems) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault()) // Added for week view
    private var isDeleteMode = false
    private var isEditMode = false

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val orderId: TextView = view.findViewById(R.id.order_id)
        val customerName: TextView = view.findViewById(R.id.customer_name)
        val orderTime: TextView = view.findViewById(R.id.order_time)
        val orderPrice: TextView = view.findViewById(R.id.order_price)
        val deleteBtn: ImageButton = view.findViewById(R.id.delete_item_btn)
        val editBtn: ImageButton = view.findViewById(R.id.edit_item_btn)
    }

    fun updateOrders(newOrders: List<OrderWithCustomerAndItems>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }

    fun setDeleteMode(enabled: Boolean) {
        this.isDeleteMode = enabled
        if (enabled) isEditMode = false
        notifyDataSetChanged()
    }

    fun setEditMode(enabled: Boolean) {
        this.isEditMode = enabled
        if (enabled) isDeleteMode = false
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
        
        // Show date + time for week view
        val dateObj = Date(order.collectionDate)
        holder.orderTime.text = "${dateFormat.format(dateObj)} ${timeFormat.format(dateObj)}"

        val totalCents = items.sumOf { it.orderItem.totalPrice }
        holder.orderPrice.text = String.format("$%.2f", totalCents / 100.0)

        holder.deleteBtn.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
        holder.deleteBtn.setOnClickListener {
            onDeleteClick(orderWithDetails)
        }

        holder.editBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.editBtn.setOnClickListener {
            onEditClick(orderWithDetails)
        }

        holder.itemView.setOnClickListener {
            if (!isDeleteMode && !isEditMode) {
                onItemClick(orderWithDetails)
            }
        }
    }

    override fun getItemCount() = orders.size
}
