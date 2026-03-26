package com.example.orderease

import android.content.Intent
import android.graphics.Color
import android.icu.util.ChineseCalendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.Holiday
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import com.example.orderease.data.repository.HolidayRepository
import com.example.orderease.utils.SessionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class CalendarActivity : BaseActivity() {

    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var monthYearText: TextView
    private var selectedDate = Calendar.getInstance()
    private var ordersMap = mutableMapOf<String, Int>()
    private var productBreakdownMap = mutableMapOf<String, MutableMap<String, Int>>()
    private var holidaysList = listOf<Holiday>()
    
    private lateinit var holidayRepository: HolidayRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        sessionManager = SessionManager(this)
        val db = AppDatabase.getDatabase(this)
        holidayRepository = HolidayRepository(db.holidayDao())

        calendarRecyclerView = findViewById(R.id.calendar_recycler_view)
        monthYearText = findViewById(R.id.month_year_text)

        findViewById<ImageView>(R.id.prev_month).setOnClickListener {
            selectedDate.add(Calendar.MONTH, -1)
            refreshCalendar()
        }

        findViewById<ImageView>(R.id.next_month).setOnClickListener {
            selectedDate.add(Calendar.MONTH, 1)
            refreshCalendar()
        }

        findViewById<Button>(R.id.back_btn).setOnClickListener {
            finish()
        }

        refreshCalendar()
        observeHolidays()
    }

    private fun refreshCalendar() {
        monthYearText.text = monthYearFromDate(selectedDate)
        loadOrdersAndPopulate()
        
        // Fetch holidays for the current year
        lifecycleScope.launch {
            holidayRepository.refreshHolidays(selectedDate.get(Calendar.YEAR), "SG")
        }
    }

    private fun observeHolidays() {
        lifecycleScope.launch {
            holidayRepository.getHolidays(selectedDate.get(Calendar.YEAR)).collectLatest { holidays ->
                holidaysList = holidays
                populateCalendar()
            }
        }
    }

    private fun loadOrdersAndPopulate() {
        val monthStart = selectedDate.clone() as Calendar
        monthStart.set(Calendar.DAY_OF_MONTH, 1)
        monthStart.set(Calendar.HOUR_OF_DAY, 0)
        monthStart.set(Calendar.MINUTE, 0)
        monthStart.set(Calendar.SECOND, 0)
        
        val monthEnd = selectedDate.clone() as Calendar
        monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        monthEnd.set(Calendar.HOUR_OF_DAY, 23)
        monthEnd.set(Calendar.MINUTE, 59)
        monthEnd.set(Calendar.SECOND, 59)

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val shopId = shop?.shopId ?: 1

            db.orderDao().getOrdersWithDetailsInRange(shopId, monthStart.timeInMillis, monthEnd.timeInMillis)
                .collectLatest { orders ->
                    processOrders(orders)
                    populateCalendar()
                }
        }
    }

    private fun processOrders(orders: List<OrderWithCustomerAndItems>) {
        ordersMap.clear()
        productBreakdownMap.clear()
        val cal = Calendar.getInstance()
        for (orderWithDetails in orders) {
            cal.timeInMillis = orderWithDetails.order.collectionDate
            val key = String.format("%d-%d-%d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            
            ordersMap[key] = ordersMap.getOrDefault(key, 0) + 1
            
            val dailyBreakdown = productBreakdownMap.getOrPut(key) { mutableMapOf() }
            for (itemWithProduct in orderWithDetails.items) {
                val productName = itemWithProduct.product.name
                val quantity = itemWithProduct.orderItem.quantity
                dailyBreakdown[productName] = dailyBreakdown.getOrDefault(productName, 0) + quantity
            }
        }
    }

    private fun populateCalendar() {
        val daysInMonth = daysInMonthList(selectedDate)
        val calendarAdapter = CalendarAdapter(daysInMonth, selectedDate, ordersMap, holidaysList) { year, month, day ->
            showDateDetailsDialog(year, month, day)
        }
        calendarRecyclerView.layoutManager = GridLayoutManager(this, 7)
        calendarRecyclerView.adapter = calendarAdapter
    }

    private fun showDateDetailsDialog(year: Int, month: Int, day: Int) {
        val key = String.format("%d-%d-%d", year, month, day)
        val orderCount = ordersMap.getOrDefault(key, 0)
        val breakdown = productBreakdownMap[key]
        
        val dateString = String.format("%04d-%02d-%02d", year, month + 1, day)
        val holiday = holidaysList.find { it.date == dateString }
        
        val cal = Calendar.getInstance()
        cal.set(year, month, day)
        val chineseCalendar = ChineseCalendar(cal.time)
        val lMonth = chineseCalendar.get(ChineseCalendar.MONTH) + 1
        val lDay = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH)
        val lunarFestiveName = getLunarFestiveName(lMonth, lDay)

        val message = StringBuilder()
        message.append("Date: $day ${monthYearFromDate(cal)}\n")
        message.append("Lunar: $lMonth Month $lDay Day\n\n")

        if (holiday != null) {
            message.append("Holiday: ${holiday.name}\n")
        }
        if (lunarFestiveName != null) {
            message.append("Festive: $lunarFestiveName\n")
        }

        if (orderCount > 0) {
            message.append("Orders: $orderCount\n")
            if (breakdown != null) {
                message.append("\nItems Needed:\n")
                for ((productName, quantity) in breakdown) {
                    message.append("$productName: $quantity\n")
                }
            }
            if (orderCount >= 5) {
                message.append("\n(Busy Day!)\n")
            }
        }

        if (message.isEmpty() || (holiday == null && lunarFestiveName == null && orderCount == 0)) {
            message.append("No special events or orders for this day.")
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Day Details")
            .setMessage(message.toString())
            .setPositiveButton("OK", null)

        if (orderCount > 0) {
            builder.setNeutralButton("View Orders") { _, _ ->
                val intent = Intent(this, DayOrdersActivity::class.java)
                intent.putExtra("SELECTED_DATE", cal.timeInMillis)
                startActivity(intent)
            }
        }

        builder.show()
    }

    private fun getLunarFestiveName(lunarMonth: Int, lunarDay: Int): String? {
        return when {
            lunarMonth == 1 && lunarDay == 1 -> "Chinese New Year (Day 1)"
            lunarMonth == 1 && lunarDay == 2 -> "Chinese New Year (Day 2)"
            lunarMonth == 1 && lunarDay == 15 -> "Chap Goh Meh (Lantern Festival)"
            lunarMonth == 5 && lunarDay == 5 -> "Dragon Boat Festival"
            lunarMonth == 8 && lunarDay == 15 -> "Mid-Autumn Festival"
            lunarMonth == 12 && (lunarDay == 29 || lunarDay == 30) -> "Chinese New Year Eve"
            else -> null
        }
    }

    private fun monthYearFromDate(calendar: Calendar): String {
        val month = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH)
        val year = calendar.get(Calendar.YEAR)
        return "$month $year"
    }

    private fun daysInMonthList(calendar: Calendar): ArrayList<String> {
        val daysInMonthList = ArrayList<String>()
        val monthCalendar = calendar.clone() as Calendar
        monthCalendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 1..42) {
            if (i <= firstDayOfWeek || i > daysInMonth + firstDayOfWeek) {
                daysInMonthList.add("")
            } else {
                daysInMonthList.add((i - firstDayOfWeek).toString())
            }
        }
        return daysInMonthList
    }
}

class CalendarAdapter(
    private val daysOfMonth: ArrayList<String>,
    private val currentMonth: Calendar,
    private val ordersMap: Map<String, Int>,
    private val holidaysList: List<Holiday>,
    private val onDayClick: (Int, Int, Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.day_text)
        val lunarText: TextView = view.findViewById(R.id.lunar_text)
        val container: View = view.findViewById(R.id.item_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val dayStr = daysOfMonth[position]
        holder.dayText.text = dayStr
        holder.lunarText.text = ""
        holder.container.background = null
        holder.dayText.setTextColor(Color.BLACK)
        holder.lunarText.setTextColor(Color.GRAY)

        if (dayStr.isNotEmpty()) {
            val day = dayStr.toInt()
            val year = currentMonth.get(Calendar.YEAR)
            val month = currentMonth.get(Calendar.MONTH)

            // Set Lunar Text and Check for Lunar Festive Dates
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            val chineseCalendar = ChineseCalendar(cal.time)
            val lMonth = chineseCalendar.get(ChineseCalendar.MONTH) + 1
            val lDay = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH)

            holder.lunarText.text = if (lDay == 1) "${lMonth}月" else lDay.toString()

            val key = String.format("%d-%d-%d", year, month, day)
            val orderCount = ordersMap.getOrDefault(key, 0)

            holder.container.setOnClickListener {
                onDayClick(year, month, day)
            }

            // 1. Check for orders count > 5 (Red background)
            if (orderCount >= 5) {
                holder.container.setBackgroundResource(R.drawable.calendar_day_busy)
                holder.dayText.setTextColor(Color.WHITE)
                holder.lunarText.setTextColor(Color.WHITE)
            } 
            // 2. Check for Lunar Festive Dates (Dark Red Circle)
            else if (isLunarFestive(lMonth, lDay)) {
                holder.container.setBackgroundResource(R.drawable.calendar_day_lunar_festive)
                holder.dayText.setTextColor(Color.parseColor("#8B0000")) // Dark Red
                holder.lunarText.setTextColor(Color.parseColor("#8B0000"))
            }
            // 3. Check for regular Holidays (Black Circle)
            else if (isHoliday(year, month, day)) {
                holder.container.setBackgroundResource(R.drawable.calendar_day_holiday)
            }
        } else {
            holder.container.setOnClickListener(null)
        }
    }

    private fun isLunarFestive(lunarMonth: Int, lunarDay: Int): Boolean {
        return (lunarMonth == 1 && (lunarDay == 1 || lunarDay == 2 || lunarDay == 15)) || // CNY Day 1, 2, Chap Goh Meh
               (lunarMonth == 5 && lunarDay == 5) || // Dragon Boat
               (lunarMonth == 8 && lunarDay == 15) || // Mid-Autumn
               (lunarMonth == 12 && (lunarDay == 29 || lunarDay == 30)) // CNY Eve (can be 29 or 30)
    }

    private fun isHoliday(year: Int, month: Int, day: Int): Boolean {
        val dateString = String.format("%04d-%02d-%02d", year, month + 1, day)
        return holidaysList.any { it.date == dateString }
    }

    override fun getItemCount(): Int = daysOfMonth.size
}
