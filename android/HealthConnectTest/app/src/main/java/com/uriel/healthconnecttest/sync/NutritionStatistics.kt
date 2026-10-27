package com.uriel.healthconnecttest.sync

import org.json.JSONObject
import java.time.LocalDate

enum class NutritionMetric(val key: String, val label: String, val unit: String) {
    Calories("caloriesKcal", "Calories", "kcal"), Protein("proteinG", "Protein", "g"),
    Carbs("carbsG", "Carbs", "g"), Fat("fatG", "Fat", "g"), Fiber("fiberG", "Fiber", "g");
    fun goal(goals: NutritionGoals): Int = goals.values()[ordinal]
}

data class NutritionAverage(val days: Int, val from: LocalDate, val to: LocalDate, val loggedDays: Int,
    val values: Map<NutritionMetric, Double?>, val coverage: Map<NutritionMetric, Int>)
data class NutritionHistoryDay(val date: LocalDate, val values: Map<NutritionMetric, Double?>)
data class NutritionStatistics(val windows: List<NutritionAverage>, val points: List<NutritionHistoryDay>,
    val currentDay: LocalDate, val timezone: String, val resetHour: Int) {
    companion object {
        fun parse(json: JSONObject): NutritionStatistics {
            fun values(item: JSONObject) = NutritionMetric.entries.associateWith { metric ->
                if (item.isNull(metric.key)) null else item.getDouble(metric.key).also { require(it.isFinite() && it >= 0) }
            }
            val windows = json.getJSONArray("windows")
            val points = json.getJSONArray("points")
            return NutritionStatistics((0 until windows.length()).map { index ->
                val item = windows.getJSONObject(index)
                NutritionAverage(item.getInt("days"), LocalDate.parse(item.getString("fromDate")),
                    LocalDate.parse(item.getString("toDate")), item.getInt("loggedDays"), values(item.getJSONObject("average")),
                    NutritionMetric.entries.associateWith { item.getJSONObject("coverage").getInt(it.key) })
            }, (0 until points.length()).map { index ->
                val item = points.getJSONObject(index)
                NutritionHistoryDay(LocalDate.parse(item.getString("date")), values(item.getJSONObject("totals")))
            }, LocalDate.parse(json.getString("currentNutritionDate")), json.getString("serverTimezone"), json.getInt("nutritionResetHour"))
                .also { require(it.windows.map { window -> window.days } == listOf(7, 14, 30)) }
        }
    }
}
