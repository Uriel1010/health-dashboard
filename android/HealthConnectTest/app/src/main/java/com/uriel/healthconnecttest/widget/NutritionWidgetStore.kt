package com.uriel.healthconnecttest.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uriel.healthconnecttest.sync.ServerMeal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private val Context.nutritionWidgetDataStore by preferencesDataStore(name = "nutrition_widget")

data class NutritionWidgetProgress(
    val caloriesKcal: Double,
    val proteinG: Double,
    val fiberG: Double,
    val caloriesGoal: Double = NutritionWidgetStore.CALORIES_GOAL_KCAL,
    val proteinGoal: Double = NutritionWidgetStore.PROTEIN_GOAL_G,
    val fiberGoal: Double = NutritionWidgetStore.FIBER_GOAL_G
) {
    val caloriesProgress: Int = progressPercent(caloriesKcal, caloriesGoal)
    val proteinProgress: Int = progressPercent(proteinG, proteinGoal)
    val fiberProgress: Int = progressPercent(fiberG, fiberGoal)
}

object NutritionWidgetStore {
    const val CALORIES_GOAL_KCAL = 2450.0
    const val PROTEIN_GOAL_G = 120.0
    const val FIBER_GOAL_G = 30.0

    suspend fun getTodayProgress(context: Context): NutritionWidgetProgress {
        val preferences = context.nutritionWidgetDataStore.data.first()
        val today = nutritionDate(java.time.ZonedDateTime.now(ZoneId.of(preferences[ZONE] ?: ZoneId.systemDefault().id)),
            preferences[RESET] ?: 0).toString()
        return if (preferences[DATE] == today) {
            NutritionWidgetProgress(
                caloriesKcal = preferences[CALORIES_KCAL] ?: 0.0,
                proteinG = preferences[PROTEIN_G] ?: 0.0,
                fiberG = preferences[FIBER_G] ?: 0.0,
                caloriesGoal = preferences[CALORIES_GOAL] ?: CALORIES_GOAL_KCAL,
                proteinGoal = preferences[PROTEIN_GOAL] ?: PROTEIN_GOAL_G,
                fiberGoal = preferences[FIBER_GOAL] ?: FIBER_GOAL_G
            )
        } else {
            NutritionWidgetProgress(0.0, 0.0, 0.0,
                preferences[CALORIES_GOAL] ?: CALORIES_GOAL_KCAL,
                preferences[PROTEIN_GOAL] ?: PROTEIN_GOAL_G,
                preferences[FIBER_GOAL] ?: FIBER_GOAL_G)
        }
    }

    suspend fun recordMeal(
        context: Context,
        source: String,
        identity: String,
        eatenAt: String,
        caloriesKcal: Double,
        proteinG: Double,
        fiberG: Double?
    ): Boolean {
        val stored = context.nutritionWidgetDataStore.data.first()
        if (stored[ZONE] != null) return false
        val today = todayKey()
        val mealDay = runCatching {
            OffsetDateTime.parse(eatenAt)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDate()
                .format(DATE_FORMATTER)
        }.getOrNull()
        if (mealDay != today) {
            Log.i(
                TAG,
                "Widget meal skipped source=$source identity=$identity mealDay=$mealDay today=$today"
            )
            return false
        }

        var recorded = false
        var totalCalories = 0.0
        var totalProtein = 0.0
        var totalFiber = 0.0
        context.nutritionWidgetDataStore.edit { preferences ->
            if (preferences[DATE] != today) {
                preferences[DATE] = today
                preferences[CALORIES_KCAL] = 0.0
                preferences[PROTEIN_G] = 0.0
                preferences[FIBER_G] = 0.0
                preferences[MEAL_IDS] = emptySet()
            }

            val mealIds = preferences[MEAL_IDS].orEmpty()
            if (!mealIds.contains(identity)) {
                preferences[MEAL_IDS] = mealIds + identity
                preferences[CALORIES_KCAL] = (preferences[CALORIES_KCAL] ?: 0.0) + caloriesKcal
                preferences[PROTEIN_G] = (preferences[PROTEIN_G] ?: 0.0) + proteinG
                preferences[FIBER_G] = (preferences[FIBER_G] ?: 0.0) + (fiberG ?: 0.0)
                recorded = true
            }
            totalCalories = preferences[CALORIES_KCAL] ?: 0.0
            totalProtein = preferences[PROTEIN_G] ?: 0.0
            totalFiber = preferences[FIBER_G] ?: 0.0
        }
        if (recorded) {
            Log.i(
                TAG,
                "Widget meal recorded source=$source identity=$identity date=$today " +
                    "added=${caloriesKcal}kcal/${proteinG}g protein/${fiberG ?: 0.0}g fiber " +
                    "totals=${totalCalories}kcal/${totalProtein}g protein/${totalFiber}g fiber"
            )
        } else {
            Log.i(
                TAG,
                "Widget meal duplicate skipped source=$source identity=$identity date=$today " +
                    "totals=${totalCalories}kcal/${totalProtein}g protein/${totalFiber}g fiber"
            )
        }
        return recorded
    }

    suspend fun replaceTodayMeals(
        context: Context,
        meals: Collection<ServerMeal>,
        day: com.uriel.healthconnecttest.sync.ServerNutritionDay? = null
    ) {
        val stored = context.nutritionWidgetDataStore.data.first()
        val zone = ZoneId.of(day?.timezone ?: stored[ZONE] ?: ZoneId.systemDefault().id)
        val reset = day?.resetHour ?: stored[RESET] ?: 0
        val today = (day?.date ?: nutritionDate(java.time.ZonedDateTime.now(zone), reset)).toString()
        val todayMeals = meals.filter { meal ->
            runCatching {
                OffsetDateTime.parse(meal.eatenAt)
                    .atZoneSameInstant(zone)
                    .let { nutritionDate(it, reset) }.toString() == today
            }.getOrDefault(false)
        }
        context.nutritionWidgetDataStore.edit { preferences ->
            preferences[DATE] = today
            preferences[ZONE] = zone.id
            preferences[RESET] = reset
            if (day != null) {
                preferences[CALORIES_GOAL] = day.caloriesGoal
                preferences[PROTEIN_GOAL] = day.proteinGoal
                preferences[FIBER_GOAL] = day.fiberGoal
            }
            preferences[CALORIES_KCAL] = todayMeals.sumOf(ServerMeal::caloriesKcal)
            preferences[PROTEIN_G] = todayMeals.sumOf(ServerMeal::proteinG)
            preferences[FIBER_G] = todayMeals.sumOf { it.fiberG ?: 0.0 }
            preferences[MEAL_IDS] = todayMeals.mapTo(mutableSetOf()) { "server-${it.syncId}" }
        }
        Log.i(TAG, "Widget totals reconciled from ${todayMeals.size} server meals for $today")
    }

    private val DATE = stringPreferencesKey("date")
    private val ZONE = stringPreferencesKey("zone")
    private val RESET = intPreferencesKey("reset")
    private val CALORIES_GOAL = doublePreferencesKey("calories_goal")
    private val PROTEIN_GOAL = doublePreferencesKey("protein_goal")
    private val FIBER_GOAL = doublePreferencesKey("fiber_goal")
    private val CALORIES_KCAL = doublePreferencesKey("calories_kcal")
    private val PROTEIN_G = doublePreferencesKey("protein_g")
    private val FIBER_G = doublePreferencesKey("fiber_g")
    private val MEAL_IDS = stringSetPreferencesKey("meal_ids")
    private const val TAG = "NutritionWidgetStore"
}

internal fun nutritionDate(time: java.time.ZonedDateTime, resetHour: Int): java.time.LocalDate =
    time.toLocalDate().let { if (time.hour < resetHour) it.minusDays(1) else it }

private fun todayKey(): String {
    return java.time.LocalDate.now().format(DATE_FORMATTER)
}

private fun progressPercent(value: Double, goal: Double): Int {
    if (goal <= 0.0) return 0
    return ((value / goal) * 100.0).toInt().coerceIn(0, 100)
}

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
