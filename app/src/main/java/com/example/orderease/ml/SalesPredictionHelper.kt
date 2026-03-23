package com.example.orderease.ml

import android.content.Context
import android.icu.util.ChineseCalendar
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.FloatBuffer
import java.util.Calendar

class SalesPredictionHelper(context: Context) {

    private data class PreprocessorParams(
        val numerical_features: List<String>,
        val scaler_mean: List<Double>,
        val scaler_std: List<Double>,
        val categorical_features: List<String>,
        val ohe_categories: Map<String, List<String>>
    )

    data class PredictionResult(
        val predictedQty: Int,
        val priceTier: String,
        val lunarDay: Int,
        val lunarMonth: Int,
        val lunarPhase: String,
        val festivalImportance: Int,
        val festivalName: String
    )

    private val params: PreprocessorParams
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val json = context.assets.open("preprocessor_params.json")
            .bufferedReader().readText()
        params = Gson().fromJson(json,
            object : TypeToken<PreprocessorParams>() {}.type)

        val modelBytes = context.assets.open("sales_model.onnx").readBytes()
        session = env.createSession(modelBytes)
    }

    /**
     * Predicts total sales quantity for a given date.
     * All lunar features are derived automatically from the date.
     */
    fun predictForDate(date: Calendar): PredictionResult {
        val chineseCal     = ChineseCalendar(date.time)
        val lunarDay       = chineseCal.get(ChineseCalendar.DAY_OF_MONTH)
        val lunarMonth     = chineseCal.get(ChineseCalendar.MONTH) + 1
        val gregMonth      = date.get(Calendar.MONTH) + 1
        val dayOfWeek      = getDayOfWeekString(date)
        val isWeekend      = date.get(Calendar.DAY_OF_WEEK)
            .let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
        val is1st15th      = lunarDay == 1 || lunarDay == 15
        val lunarPhase     = getLunarPhase(lunarDay)
        val festivalName   = getLunarFestivalName(lunarMonth, lunarDay)
        val festivalImportance = getFestivalImportance(festivalName)
        val isPeakSeason   = gregMonth == 9 || gregMonth == 12 || festivalImportance >= 3

        // CNY pricing tier: major lunar festival (importance 4) in Jan or Feb
        val priceTier = if (festivalImportance == 4 &&
            (gregMonth == 1 || gregMonth == 2)) "CNY" else "Regular"

        // Auspicious: lunar days 1, 6, 8, 9, 10, 15, 16, 19, 23, 27, 28
        val auspiciousDays = setOf(1, 6, 8, 9, 10, 15, 16, 19, 23, 27, 28)
        val isAuspicious   = lunarDay in auspiciousDays

        val features = buildFeatureVector(
            isWeekend          = if (isWeekend) 1 else 0,
            gregMonth          = gregMonth,
            lunarMonth         = lunarMonth,
            lunarDay           = lunarDay,
            is1st15th          = if (is1st15th) 1 else 0,
            isAuspicious       = if (isAuspicious) 1 else 0,
            festivalImportance = festivalImportance,
            isPeakSeason       = if (isPeakSeason) 1 else 0,
            priceTier          = priceTier,
            dayOfWeek          = dayOfWeek,
            lunarPhase         = lunarPhase
        )

        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(features),
            longArrayOf(1, features.size.toLong())
        )
        val output   = session.run(mapOf(session.inputNames.first() to inputTensor))
        val rawValue = (output[0].value as Array<FloatArray>)[0][0]
        inputTensor.close()
        output.close()

        return PredictionResult(
            predictedQty       = maxOf(0, rawValue.toInt()),
            priceTier          = priceTier,
            lunarDay           = lunarDay,
            lunarMonth         = lunarMonth,
            lunarPhase         = lunarPhase,
            festivalImportance = festivalImportance,
            festivalName       = festivalName
        )
    }

    // ── Feature vector ────────────────────────────────────────────────────────

    private fun buildFeatureVector(
        isWeekend: Int, gregMonth: Int, lunarMonth: Int, lunarDay: Int,
        is1st15th: Int, isAuspicious: Int, festivalImportance: Int,
        isPeakSeason: Int, priceTier: String, dayOfWeek: String,
        lunarPhase: String
    ): FloatArray {
        // Numerical — must match training order exactly:
        // Is Weekend, Greg. Month, Lunar Month, Lunar Day,
        // Is 1st/15th?, Auspicious?, Festival Importance, Is Peak Season
        val raw = floatArrayOf(
            isWeekend.toFloat(), gregMonth.toFloat(), lunarMonth.toFloat(),
            lunarDay.toFloat(), is1st15th.toFloat(), isAuspicious.toFloat(),
            festivalImportance.toFloat(), isPeakSeason.toFloat()
        )
        val scaled = FloatArray(raw.size) { i ->
            ((raw[i] - params.scaler_mean[i]) / params.scaler_std[i]).toFloat()
        }

        // Categorical — one-hot in training order
        val catValues = mapOf(
            "Price Tier"  to priceTier,
            "Day of Week" to dayOfWeek,
            "Lunar Phase" to lunarPhase
        )
        val oneHot = mutableListOf<Float>()
        for (feat in params.categorical_features) {
            val cats  = params.ohe_categories[feat] ?: continue
            val value = catValues[feat] ?: ""
            cats.forEach { oneHot.add(if (it == value) 1f else 0f) }
        }
        return scaled + oneHot.toFloatArray()
    }

    // ── Lunar helpers — mirrors CalendarActivity logic exactly ────────────────

    /**
     * Maps lunar month + day to a festival name.
     * Mirrors getLunarFestiveName() in CalendarActivity, extended with
     * additional festivals used in the model training data.
     */
    fun getLunarFestivalName(lunarMonth: Int, lunarDay: Int): String {
        return when {
            lunarMonth == 1  && lunarDay == 1                       -> "Chinese New Year's Day"
            lunarMonth == 1  && lunarDay == 2                       -> "2nd Chinese New Year's Day"
            lunarMonth == 1  && lunarDay == 8                       -> "8th Chinese New Year's Day"
            lunarMonth == 1  && lunarDay == 15                      -> "Lantern Festival"
            lunarMonth == 5  && lunarDay == 5                       -> "Dragon Boat Festival"
            lunarMonth == 7  && lunarDay == 7                       -> "Double Seventh Festival"
            lunarMonth == 7  && lunarDay in 1..30                   -> "Ghost Month"
            lunarMonth == 8  && lunarDay == 15                      -> "Mid-Autumn Festival"
            lunarMonth == 9  && lunarDay == 9                       -> "The Double Ninth Festival"
            lunarMonth == 12 && (lunarDay == 29 || lunarDay == 30)  -> "Chinese New Year's Eve"
            else                                                     -> "0"
        }
    }

    /**
     * Approximates lunar phase from lunar day.
     * Matches the 8 phases used during model training.
     */
    private fun getLunarPhase(lunarDay: Int): String = when (lunarDay) {
        1           -> "New Moon"
        in 2..6     -> "Waxing Crescent"
        7, 8        -> "First Quarter"
        in 9..13    -> "Waxing Gibbous"
        14, 15      -> "Full Moon"
        in 16..20   -> "Waning Gibbous"
        21, 22      -> "Last Quarter"
        in 23..30   -> "Waning Crescent"
        else        -> "New Moon"
    }

    private fun getDayOfWeekString(cal: Calendar): String = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> "Mon"
        Calendar.TUESDAY   -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY  -> "Thu"
        Calendar.FRIDAY    -> "Fri"
        Calendar.SATURDAY  -> "Sat"
        else               -> "Sun"
    }

    // ── Festival importance — mirrors Python cleaning code ────────────────────

    fun getFestivalImportance(festivalName: String): Int {
        val n = festivalName.trim().lowercase()
        return when {
            n == "0" || n.isEmpty()                                 -> 0
            "ghost month" in n || "good friday" in n
                    || "labour day" in n || "hari raya" in n
                    || "vesak" in n || "national day" in n
                    || "deepavali" in n                             -> 1
            "new year's day" in n || "laba" in n
                    || "blue dragon" in n || "qingming" in n
                    || "dragon boat" in n || "double seventh" in n
                    || "double ninth" in n || "mid-autumn" in n
                    || "winter solstice" in n || "winter begins" in n
                    || "light snow" in n || "christmas" in n        -> 2
            "valentine" in n || "lantern festival" in n             -> 3
            "new year's eve" in n || "8th chinese" in n
                    || ("chinese" in n && "new year" in n)          -> 4
            else                                                     -> 0
        }
    }

    fun close() {
        session.close()
        env.close()
    }
}