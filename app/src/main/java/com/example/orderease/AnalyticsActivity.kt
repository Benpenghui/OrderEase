package com.example.orderease

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        val currentMonthText = findViewById<TextView>(R.id.current_month_text)
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFormat.format(Date())
        currentMonthText.text = getString(R.string.current_month_label, monthName)
    }
}
