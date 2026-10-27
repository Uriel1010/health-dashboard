package com.uriel.healthconnecttest.sync

import org.json.JSONArray
import org.json.JSONObject

data class RecentExerciseSnapshot(
    val title: String,
    val startTime: String,
    val durationMinutes: Long
)

data class HealthSnapshotMetrics(
    val stepsToday: Long? = null,
    val distanceKmToday: Double? = null,
    val activeCaloriesKcalToday: Double? = null,
    val totalCaloriesKcalToday: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val sleepMinutesLastNight: Long? = null,
    val latestWeightKg: Double? = null,
    val recentExercises: List<RecentExerciseSnapshot> = emptyList()
)

data class HealthSnapshot(
    val deviceId: String,
    val deviceName: String,
    val capturedAt: String,
    val timezone: String,
    val appVersion: String,
    val metrics: HealthSnapshotMetrics,
    val permissions: Map<String, String>,
    val errors: List<String>
) {
    fun toJson(): String {
        return JSONObject()
            .put("deviceId", deviceId)
            .put("deviceName", deviceName)
            .put("capturedAt", capturedAt)
            .put("timezone", timezone)
            .put("appVersion", appVersion)
            .put("metrics", metrics.toJson())
            .put("permissions", JSONObject(permissions))
            .put("errors", JSONArray(errors))
            .toString()
    }
}

data class SyncResult(
    val success: Boolean,
    val retryable: Boolean,
    val message: String
)

data class LoginResult(
    val success: Boolean,
    val retryable: Boolean,
    val message: String,
    val sessionCookie: String = ""
)

private fun HealthSnapshotMetrics.toJson(): JSONObject {
    return JSONObject()
        .putNullable("stepsToday", stepsToday)
        .putNullable("distanceKmToday", distanceKmToday)
        .putNullable("activeCaloriesKcalToday", activeCaloriesKcalToday)
        .putNullable("totalCaloriesKcalToday", totalCaloriesKcalToday)
        .putNullable("latestHeartRateBpm", latestHeartRateBpm)
        .putNullable("restingHeartRateBpm", restingHeartRateBpm)
        .putNullable("sleepMinutesLastNight", sleepMinutesLastNight)
        .putNullable("latestWeightKg", latestWeightKg)
        .put("recentExercises", JSONArray(recentExercises.map { it.toJson() }))
}

private fun RecentExerciseSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("title", title)
        .put("startTime", startTime)
        .put("durationMinutes", durationMinutes)
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}
