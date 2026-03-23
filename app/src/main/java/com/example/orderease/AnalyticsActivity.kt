package com.example.orderease

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.ml.SalesPredictionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var predictionHelper: SalesPredictionHelper

    // ── Prediction card views ─────────────────────────────────────────────────
    private lateinit var predictionCard: View
    private lateinit var predictionQtyText: TextView
    private lateinit var predictionDateText: TextView
    private lateinit var predictionFestivalText: TextView
    private lateinit var predictionLunarText: TextView
    private lateinit var predictionTierText: TextView

    // ── Analytics card views (existing) ──────────────────────────────────────
    private lateinit var currentMonthText: TextView
    private lateinit var mostPopularItem: TextView
    private lateinit var amountSoldText: TextView
    private lateinit var earningsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        bindViews()

        // Existing month label
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        currentMonthText.text = getString(R.string.current_month_label, monthFormat.format(Date()))

        // Load analytics stats from Room
        loadMonthlyStats()

        // Run prediction for today
        runPrediction()
    }

    private fun bindViews() {
        currentMonthText      = findViewById(R.id.current_month_text)
        mostPopularItem       = findViewById(R.id.most_popular_item)
        amountSoldText        = findViewById(R.id.amount_sold_text)
        earningsText          = findViewById(R.id.earnings_text)

        predictionCard        = findViewById(R.id.prediction_card)
        predictionQtyText     = findViewById(R.id.prediction_qty_text)
        predictionDateText    = findViewById(R.id.prediction_date_text)
        predictionFestivalText= findViewById(R.id.prediction_festival_text)
        predictionLunarText   = findViewById(R.id.prediction_lunar_text)
        predictionTierText    = findViewById(R.id.prediction_tier_text)
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    private fun runPrediction() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Initialise helper on IO thread (reads assets)
                predictionHelper = SalesPredictionHelper(applicationContext)
                val today  = Calendar.getInstance()
                val result = predictionHelper.predictForDate(today)

                withContext(Dispatchers.Main) {
                    val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    predictionDateText.text    = dateFormat.format(today.time)
                    predictionQtyText.text     = result.predictedQty.toString()
                    
                    // Use resource string with placeholders for lunar info
                    predictionLunarText.text = getString(
                        R.string.lunar_info_format,
                        result.lunarMonth.toString(),
                        result.lunarDay.toString(),
                        result.lunarPhase
                    )
                    
                    predictionTierText.text    = result.priceTier
                    predictionFestivalText.text =
                        if (result.festivalName == "0") getString(R.string.no_festival)
                        else result.festivalName
                    predictionFestivalText.visibility =
                        if (result.festivalName == "0") View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    predictionQtyText.text = "—"
                    // Temporary debug — remove before release
                    android.util.Log.e("SalesPrediction", "Prediction failed", e)
                    predictionDateText.text = e.message ?: "Unknown error"
                }
            }
        }
    }

    // ── Monthly stats from Room ───────────────────────────────────────────────

    private fun loadMonthlyStats() {
        val now        = Calendar.getInstance()
        val monthStart = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val monthEnd = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.orderDao()
                .getOrdersWithDetailsInRange(monthStart.timeInMillis, monthEnd.timeInMillis)
                .collectLatest { orders ->
                    // Total quantity sold this month
                    var totalQty     = 0
                    var totalRevenue = 0.0
                    val productQtyMap = mutableMapOf<String, Int>()

                    for (orderWithDetails in orders) {
                        for (itemWithProduct in orderWithDetails.items) {
                            val name = itemWithProduct.product.name
                            val qty  = itemWithProduct.orderItem.quantity
                            // Fix: Product entity uses 'cost', but we can use 'totalPrice' from orderItem
                            val itemRevenueCents = itemWithProduct.orderItem.totalPrice
                            totalQty += qty
                            totalRevenue += (itemRevenueCents / 100.0)
                            productQtyMap[name] = (productQtyMap[name] ?: 0) + qty
                        }
                    }

                    val topItem = productQtyMap.maxByOrNull { it.value }?.key ?: "—"

                    mostPopularItem.text = topItem
                    amountSoldText.text  = totalQty.toString()
                    earningsText.text    = String.format(Locale.getDefault(), "$%.2f", totalRevenue)
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::predictionHelper.isInitialized) predictionHelper.close()
    }
}
