package com.uriel.healthconnecttest.sync

import android.content.Context
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.widget.NutritionWidgetProgress
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Last complete server response, retained across process restarts and network failures. */
data class SavedNutrition(val meals: List<ServerMeal>, val day: ServerNutritionDay, val updated: Long) {
    val progress get() = NutritionWidgetProgress(
        meals.sumOf { it.caloriesKcal }, meals.sumOf { it.proteinG }, meals.sumOf { it.fiberG ?: 0.0 },
        day.caloriesGoal, day.proteinGoal, day.fiberGoal,
        meals.sumOf { it.carbsG }, meals.sumOf { it.fatG }, day.carbsGoal, day.fatGoal)
}

object NutritionOfflineStore {
    internal fun accountKey(settings: ServerSettings): String = MessageDigest.getInstance("SHA-256")
        .digest("${settings.serverBaseUrl.trimEnd('/')}\n${settings.readToken}".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context) = context.getSharedPreferences("nutrition_offline", Context.MODE_PRIVATE)

    fun read(context: Context, settings: ServerSettings): SavedNutrition? = runCatching {
        if (settings.readToken.isBlank()) return null
        val prefs = prefs(context)
        if (prefs.getString("account", null) != accountKey(settings)) return null
        decode(JSONObject(prefs.getString("meals", null) ?: return null))
    }.getOrNull()

    fun save(context: Context, settings: ServerSettings, result: ServerMealsFetchResult) {
        val day = result.nutritionDay ?: return
        if (!result.success || result.failedParseCount != 0 || settings.readToken.isBlank()) return
        prefs(context).edit().putString("account", accountKey(settings))
            .putString("meals", encode(SavedNutrition(result.meals, day, System.currentTimeMillis())).toString()).apply()
    }

    fun goals(context: Context, settings: ServerSettings): NutritionGoals? = runCatching {
        val prefs = prefs(context)
        if (settings.readToken.isBlank() || prefs.getString("goals_account", null) != accountKey(settings)) return null
        val json = JSONObject(prefs.getString("goals", null) ?: return null)
        NutritionGoals(json.getInt("caloriesKcal"), json.getInt("proteinG"), json.getInt("carbsG"),
            json.getInt("fatG"), json.getInt("fiberG"))
    }.getOrNull()

    fun saveGoals(context: Context, settings: ServerSettings, goals: NutritionGoals) {
        if (settings.readToken.isBlank()) return
        prefs(context).edit().putString("goals_account", accountKey(settings)).putString("goals", JSONObject()
            .put("caloriesKcal", goals.caloriesKcal).put("proteinG", goals.proteinG)
            .put("carbsG", goals.carbsG).put("fatG", goals.fatG).put("fiberG", goals.fiberG).toString()).apply()
    }

    internal fun encode(saved: SavedNutrition): JSONObject {
        val day = saved.day
        val meals = JSONArray()
        saved.meals.forEach { meal -> meals.put(JSONObject()
            .put("id", meal.serverId).put("serverMealId", meal.syncId).put("clientMealId", meal.clientRecordId)
            .put("eatenAt", meal.eatenAt).put("mealType", meal.mealType.apiValue).put("title", meal.title)
            .put("caloriesKcal", meal.caloriesKcal).put("proteinG", meal.proteinG)
            .put("carbsG", meal.carbsG).put("fatG", meal.fatG)
            .put("fiberG", meal.fiberG ?: JSONObject.NULL).put("sugarG", meal.sugarG ?: JSONObject.NULL)
            .put("sodiumMg", meal.sodiumMg ?: JSONObject.NULL)) }
        return JSONObject().put("meals", meals).put("updated", saved.updated)
            .put("day", JSONObject().put("date", day.date.toString()).put("timezone", day.timezone)
                .put("resetHour", day.resetHour).put("caloriesGoal", day.caloriesGoal)
                .put("proteinGoal", day.proteinGoal).put("fiberGoal", day.fiberGoal)
                .put("carbsGoal", day.carbsGoal).put("fatGoal", day.fatGoal))
    }

    internal fun decode(json: JSONObject): SavedNutrition {
        val parsed = parseServerMeals(json.getJSONArray("meals").toString())
        require(parsed.failedCount == 0)
        val day = json.getJSONObject("day")
        return SavedNutrition(parsed.meals, ServerNutritionDay(LocalDate.parse(day.getString("date")),
            day.getString("timezone"), day.getInt("resetHour"), day.getDouble("caloriesGoal"),
            day.getDouble("proteinGoal"), day.getDouble("fiberGoal"), day.getDouble("carbsGoal"),
            day.getDouble("fatGoal")), json.getLong("updated"))
    }
}
