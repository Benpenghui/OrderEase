package com.example.orderease

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.ml.SalesPredictionHelper
import com.example.orderease.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : BaseActivity() {

    private lateinit var predictionHelper: SalesPredictionHelper
    private var predictionCalendar = Calendar.getInstance()
    private lateinit var sessionManager: SessionManager

    // ── Prediction card views ─────────────────────────────────────────────────
    private lateinit var predictionCard: View
    private lateinit var predictionTitleText: TextView
    private lateinit var predictionQtyText: TextView
    private lateinit var predictionDateText: TextView
    private lateinit var predictionFestivalText: TextView
    private lateinit var predictionLunarText: TextView
    private lateinit var predictionTierText: TextView
    private lateinit var predictedItemsListText: TextView

    // ── Analytics card views ──────────────────────────────────────────────────
    private lateinit var currentMonthText: TextView
    private lateinit var mostPopularItem: TextView
    private lateinit var amountSoldText: TextView
    private lateinit var earningsText: TextView

    // Cached data for breakdown calculation
    private var cachedTotalMonthlyQty = 0
    private var cachedProductDistribution = mapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        sessionManager = SessionManager(this)
        bindViews()

        // Existing month label
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        currentMonthText.text = getString(R.string.current_month_label, monthFormat.format(Date()))

        // Load analytics stats and run prediction
        loadData()
    }

    private fun bindViews() {
        currentMonthText      = findViewById(R.id.current_month_text)
        mostPopularItem       = findViewById(R.id.most_popular_item)
        amountSoldText        = findViewById(R.id.amount_sold_text)
        earningsText          = findViewById(R.id.earnings_text)
        
        predictionCard        = findViewById(R.id.prediction_card)
        predictionTitleText   = findViewById(R.id.prediction_title_text)
        predictionQtyText     = findViewById(R.id.prediction_qty_text)
        predictionDateText    = findViewById(R.id.prediction_date_text)
        predictionFestivalText= findViewById(R.id.prediction_festival_text)
        predictionLunarText   = findViewById(R.id.prediction_lunar_text)
        predictionTierText    = findViewById(R.id.prediction_tier_text)
        predictedItemsListText= findViewById(R.id.predicted_items_list)

        findViewById<ImageView>(R.id.prev_prediction_btn).setOnClickListener {
            predictionCalendar.add(Calendar.DAY_OF_YEAR, -1)
            updatePredictionUI()
        }

        findViewById<ImageView>(R.id.next_prediction_btn).setOnClickListener {
            predictionCalendar.add(Calendar.DAY_OF_YEAR, 1)
            updatePredictionUI()
        }

        findViewById<Button>(R.id.back_btn).setOnClickListener {
            finish()
        }
    }

    private fun loadData() {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val shopId = shop?.shopId ?: 1

            val now = Calendar.getInstance()
            val monthStart = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val monthEnd = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }

            // Get orders for the current month
            val orders = db.orderDao()
                .getOrdersWithDetailsInRange(shopId, monthStart.timeInMillis, monthEnd.timeInMillis)
                .first()

            var totalQtySold = 0
            var totalRevenue = 0.0
            val productQtyMap = mutableMapOf<String, Int>()

            for (orderWithDetails in orders) {
                for (itemWithProduct in orderWithDetails.items) {
                    val name = itemWithProduct.product.name
                    val qty  = itemWithProduct.orderItem.quantity
                    val itemRevenueCents = itemWithProduct.orderItem.totalPrice
                    totalQtySold += qty
                    totalRevenue += (itemRevenueCents / 100.0)
                    productQtyMap[name] = (productQtyMap[name] ?: 0) + qty
                }
            }

            // Update monthly stats UI
            val topItem = productQtyMap.maxByOrNull { it.value }?.key ?: "—"
            mostPopularItem.text = topItem
            amountSoldText.text  = totalQtySold.toString()
            earningsText.text    = String.format(Locale.getDefault(), "$%.2f", totalRevenue)

            // Cache data and run initial prediction
            cachedTotalMonthlyQty = totalQtySold
            cachedProductDistribution = productQtyMap
            updatePredictionUI()
        }
    }

    private fun updatePredictionUI() {
        val today = Calendar.getInstance()
        val isToday = predictionCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                      predictionCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        
        predictionTitleText.text = if (isToday) "Today's Prediction" else "Date Prediction"
        
        runPrediction(predictionCalendar)
    }

    private fun runPrediction(cal: Calendar) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!::predictionHelper.isInitialized) {
                    predictionHelper = SalesPredictionHelper(applicationContext)
                }
                
                val result = predictionHelper.predictForDate(cal)

                withContext(Dispatchers.Main) {
                    val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    predictionDateText.text    = dateFormat.format(cal.time)
                    predictionQtyText.text     = result.predictedQty.toString()
                    
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

                    // Calculate breakdown
                    if (result.predictedQty > 0 && cachedTotalMonthlyQty > 0) {
                        val breakdownBuilder = StringBuilder()
                        cachedProductDistribution.forEach { (name, qty) ->
                            val proportion = qty.toDouble() / cachedTotalMonthlyQty
                            val estimatedItemQty = Math.round(result.predictedQty * proportion).toInt()
                            if (estimatedItemQty > 0) {
                                breakdownBuilder.append("• $name: $estimatedItemQty\n")
                            }
                        }
                        predictedItemsListText.text = breakdownBuilder.toString().trim()
                    } else if (result.predictedQty > 0) {
                        predictedItemsListText.text = "No historical data to estimate item breakdown."
                    } else {
                        predictedItemsListText.text = "No orders predicted for this date."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    predictionQtyText.text = "—"
                    android.util.Log.e("SalesPrediction", "Prediction failed", e)
                    predictedItemsListText.text = "Prediction failed: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::predictionHelper.isInitialized) predictionHelper.close()
    }
}
