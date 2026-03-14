package com.example.orderease

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var monthYearText: TextView
    private var selectedDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        calendarRecyclerView = findViewById(R.id.calendar_recycler_view)
        monthYearText = findViewById(R.id.month_year_text)

        setMonthView()

        findViewById<View>(R.id.prev_month).setOnClickListener {
            selectedDate.add(Calendar.MONTH, -1)
            setMonthView()
        }

        findViewById<View>(R.id.next_month).setOnClickListener {
            selectedDate.add(Calendar.MONTH, 1)
            setMonthView()
        }

        findViewById<Button>(R.id.back_btn).setOnClickListener {
            finish()
        }
    }

    private fun setMonthView() {
        monthYearText.text = monthYearFromDate(selectedDate)
        val daysInMonth = daysInMonthList(selectedDate)

        val calendarAdapter = CalendarAdapter(daysInMonth)
        calendarRecyclerView.layoutManager = GridLayoutManager(this, 7)
        calendarRecyclerView.adapter = calendarAdapter
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

class CalendarAdapter(private val daysOfMonth: ArrayList<String>) :
    RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.day_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = daysOfMonth[position]
        holder.dayText.text = day
        
        // Reset background
        holder.dayText.background = null

        // Placeholder logic for specific days as per design
        if (day == "9" || day == "13") {
            holder.dayText.setBackgroundResource(R.drawable.calendar_day_busy)
            holder.dayText.setTextColor(android.graphics.Color.WHITE)
        } else if (day == "6" || day == "21") {
            holder.dayText.setBackgroundResource(R.drawable.calendar_day_holiday)
            holder.dayText.setTextColor(android.graphics.Color.BLACK)
        } else {
            holder.dayText.setTextColor(android.graphics.Color.BLACK)
        }
    }

    override fun getItemCount(): Int = daysOfMonth.size
}
