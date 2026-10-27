package com.uriel.healthconnecttest.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

enum class MealType(val apiValue: String, val label: String) {
    Breakfast("breakfast", "Breakfast"),
    Lunch("lunch", "Lunch"),
    Dinner("dinner", "Dinner"),
    Snack("snack", "Snack"),
    Other("other", "Other")
}

data class MealLogRequest(
    val clientMealId: String,
    val deviceId: String,
    val eatenAt: String,
    val mealType: MealType,
    val title: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val notes: String?
) {
    fun toJson(): String {
        return JSONObject()
            .put("clientMealId", clientMealId)
            .put("deviceId", deviceId)
            .put("eatenAt", eatenAt)
            .put("timezone", ZoneId.systemDefault().id)
            .put("mealType", mealType.apiValue)
            .put("title", title)
            .put("caloriesKcal", caloriesKcal)
            .put("proteinG", proteinG)
            .put("carbsG", carbsG)
            .put("fatG", fatG)
            .put("notes", notes?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
    }
}

data class NutritionMealInput(
    val clientRecordId: String,
    val eatenAt: String,
    val mealType: MealType,
    val title: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null
)

data class ServerMeal(
    val serverId: Long,
    val syncId: String,
    val clientRecordId: String,
    val eatenAt: String,
    val mealType: MealType,
    val title: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?
) {
    fun toNutritionInput(): NutritionMealInput {
        return NutritionMealInput(
            clientRecordId = clientRecordId,
            eatenAt = eatenAt,
            mealType = mealType,
            title = title,
            caloriesKcal = caloriesKcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            fiberG = fiberG,
            sugarG = sugarG,
            sodiumMg = sodiumMg
        )
    }
}

data class ServerMealsFetchResult(
    val success: Boolean,
    val retryable: Boolean,
    val message: String,
    val meals: List<ServerMeal> = emptyList(),
    val failedParseCount: Int = 0,
    val firstParseError: String = "",
    val nutritionDay: ServerNutritionDay? = null
)

data class ServerNutritionDay(
    val date: java.time.LocalDate,
    val timezone: String,
    val resetHour: Int,
    val caloriesGoal: Double,
    val proteinGoal: Double,
    val fiberGoal: Double,
    val carbsGoal: Double = 0.0,
    val fatGoal: Double = 0.0
)

data class ParsedServerMeals(
    val meals: List<ServerMeal>,
    val failedCount: Int,
    val firstError: String
)

fun MealLogRequest.toNutritionInput(): NutritionMealInput {
    return NutritionMealInput(
        clientRecordId = clientMealId,
        eatenAt = eatenAt,
        mealType = mealType,
        title = title,
        caloriesKcal = caloriesKcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG
    )
}

fun parseServerMeals(json: String): ParsedServerMeals {
    val trimmed = json.trim()
    val array = if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
    } else {
        val root = JSONObject(trimmed)
        root.optJSONArray("meals")
            ?: root.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: JSONArray()
    }

    var failedCount = 0
    var firstError = ""
    val meals = buildList {
        for (index in 0 until array.length()) {
            runCatching {
                add(parseServerMeal(array.getJSONObject(index)))
            }.getOrElse { exception ->
                failedCount += 1
                if (firstError.isBlank()) {
                    firstError = exception.message ?: exception::class.java.simpleName
                }
            }
        }
    }
    return ParsedServerMeals(meals, failedCount, firstError)
}

private fun parseServerMeal(json: JSONObject): ServerMeal {
    val serverId = when {
        json.has("id") && !json.isNull("id") -> json.getLong("id")
        json.has("serverMealId") && !json.isNull("serverMealId") -> json.getLong("serverMealId")
        else -> throw IllegalArgumentException("Server meal is missing id/serverMealId")
    }
    val rawId = json.optString("serverMealId").ifBlank { serverId.toString() }
    val clientMealId = json.optString("clientMealId")
    val syncId = rawId.ifBlank { clientMealId }
        .ifBlank { throw IllegalArgumentException("Server meal is missing id/serverMealId/clientMealId") }
    val clientRecordId = "server-meal-$syncId"
    val title = json.optString("title").ifBlank {
        throw IllegalArgumentException("Server meal $syncId is missing title")
    }
    val eatenAt = json.optString("eatenAt").ifBlank {
        throw IllegalArgumentException("Server meal $syncId is missing eatenAt")
    }

    return ServerMeal(
        serverId = serverId,
        syncId = syncId,
        clientRecordId = clientMealId.ifBlank { clientRecordId },
        eatenAt = eatenAt,
        mealType = parseMealType(json.optString("mealType")),
        title = title,
        caloriesKcal = json.requiredNonNegativeDouble("caloriesKcal", syncId),
        proteinG = json.requiredNonNegativeDouble("proteinG", syncId),
        carbsG = json.requiredNonNegativeDouble("carbsG", syncId),
        fatG = json.requiredNonNegativeDouble("fatG", syncId),
        fiberG = json.optionalNonNegativeDouble("fiberG", syncId),
        sugarG = json.optionalNonNegativeDouble("sugarG", syncId),
        sodiumMg = json.optionalNonNegativeDouble("sodiumMg", syncId)
    )
}

private fun parseMealType(value: String): MealType {
    return MealType.entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
        ?: MealType.Other
}

private fun JSONObject.requiredNonNegativeDouble(name: String, syncId: String): Double {
    if (!has(name) || isNull(name)) {
        throw IllegalArgumentException("Server meal $syncId is missing $name")
    }
    return getDouble(name).also {
        require(it >= 0.0) { "Server meal $syncId has negative $name" }
    }
}

private fun JSONObject.optionalNonNegativeDouble(name: String, syncId: String): Double? {
    if (!has(name) || isNull(name)) return null
    return getDouble(name).also {
        require(it >= 0.0) { "Server meal $syncId has negative $name" }
    }
}
