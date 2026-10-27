package com.uriel.healthconnecttest.sync

import android.content.Context
import com.uriel.healthconnecttest.data.ServerSettings
import java.security.MessageDigest
import org.json.JSONObject

/** Separate account-scoped history cache; never exposes a previous account's nutrition. */
object NutritionStatisticsStore {
    private fun key(settings: ServerSettings): String = MessageDigest.getInstance("SHA-256")
        .digest("${settings.serverBaseUrl.trimEnd('/')}\n${settings.readToken}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun read(context: Context, settings: ServerSettings): NutritionStatistics? = runCatching {
        val prefs = context.getSharedPreferences("nutrition_statistics", Context.MODE_PRIVATE)
        if (prefs.getString("account", null) != key(settings)) return null
        NutritionStatistics.parse(JSONObject(prefs.getString("json", null) ?: return null))
    }.getOrNull()
    fun save(context: Context, settings: ServerSettings, json: JSONObject) {
        NutritionStatistics.parse(json)
        context.getSharedPreferences("nutrition_statistics", Context.MODE_PRIVATE).edit()
            .putString("account", key(settings)).putString("json", json.toString())
            .putLong("updated", System.currentTimeMillis()).apply()
    }
    fun updated(context: Context): Long = context.getSharedPreferences("nutrition_statistics", Context.MODE_PRIVATE).getLong("updated", 0)
}
