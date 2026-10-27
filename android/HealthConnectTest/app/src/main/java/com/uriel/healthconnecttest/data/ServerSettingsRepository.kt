package com.uriel.healthconnecttest.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

class ServerSettingsRepository(private val context: Context) {
    val settings: Flow<ServerSettings> = context.serverSettingsDataStore.data.map { preferences ->
        ServerSettings(
            serverBaseUrl = preferences[SERVER_BASE_URL].orEmpty(),
            deviceId = preferences[DEVICE_ID] ?: "uriel-phone",
            deviceName = preferences[DEVICE_NAME] ?: Build.MODEL.orEmpty(),
            apiToken = preferences[API_TOKEN].orEmpty(),
            readToken = preferences[READ_TOKEN].orEmpty(),
            allowInsecureHttpForLan = preferences[ALLOW_INSECURE_HTTP] ?: false,
            lastSyncStatus = preferences[LAST_SYNC_STATUS] ?: "Never synced",
            lastSyncTime = preferences[LAST_SYNC_TIME].orEmpty(),
            lastManualSyncTime = preferences[LAST_MANUAL_SYNC_TIME].orEmpty(),
            lastBackgroundSyncTime = preferences[LAST_BACKGROUND_SYNC_TIME].orEmpty(),
            lastSyncError = preferences[LAST_SYNC_ERROR].orEmpty(),
            backgroundSyncScheduled = preferences[BACKGROUND_SYNC_SCHEDULED] ?: false,
            backgroundSyncEnabled = preferences[BACKGROUND_SYNC_ENABLED] ?: true,
            syncOnAppOpenEnabled = preferences[SYNC_ON_APP_OPEN_ENABLED] ?: true,
            chargingSyncIntervalMinutes = preferences[CHARGING_SYNC_INTERVAL_MINUTES] ?: 30,
            batterySyncIntervalMinutes = preferences[BATTERY_SYNC_INTERVAL_MINUTES] ?: 180,
            syncHealthSnapshotsEnabled = preferences[SYNC_HEALTH_SNAPSHOTS_ENABLED] ?: true,
            syncServerMealsEnabled = preferences[SYNC_SERVER_MEALS_ENABLED] ?: true,
            updateNutritionWidgetEnabled = preferences[UPDATE_NUTRITION_WIDGET_ENABLED] ?: true,
            mealNotificationsEnabled = preferences[MEAL_NOTIFICATIONS_ENABLED] ?: false
        )
    }

    suspend fun saveConnectionSettings(
        serverBaseUrl: String,
        deviceId: String,
        deviceName: String,
        apiToken: String,
        readToken: String,
        allowInsecureHttpForLan: Boolean
    ) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[SERVER_BASE_URL] = serverBaseUrl.trim()
            preferences[DEVICE_ID] = deviceId.trim()
            preferences[DEVICE_NAME] = deviceName.trim()
            preferences[API_TOKEN] = apiToken.trim()
            preferences[READ_TOKEN] = readToken.trim()
            preferences[ALLOW_INSECURE_HTTP] = allowInsecureHttpForLan
        }
    }

    suspend fun saveSyncScheduleSettings(
        backgroundSyncEnabled: Boolean,
        syncOnAppOpenEnabled: Boolean,
        chargingSyncIntervalMinutes: Int,
        batterySyncIntervalMinutes: Int,
        syncHealthSnapshotsEnabled: Boolean,
        syncServerMealsEnabled: Boolean,
        updateNutritionWidgetEnabled: Boolean
    ) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[BACKGROUND_SYNC_ENABLED] = backgroundSyncEnabled
            preferences[SYNC_ON_APP_OPEN_ENABLED] = syncOnAppOpenEnabled
            preferences[CHARGING_SYNC_INTERVAL_MINUTES] = chargingSyncIntervalMinutes
            preferences[BATTERY_SYNC_INTERVAL_MINUTES] = batterySyncIntervalMinutes
            preferences[SYNC_HEALTH_SNAPSHOTS_ENABLED] = syncHealthSnapshotsEnabled
            preferences[SYNC_SERVER_MEALS_ENABLED] = syncServerMealsEnabled
            preferences[UPDATE_NUTRITION_WIDGET_ENABLED] = updateNutritionWidgetEnabled
        }
    }

    suspend fun getSyncedServerMealIds(): Set<String> {
        return context.serverSettingsDataStore.data.first()[SYNCED_SERVER_MEAL_IDS].orEmpty()
    }

    suspend fun setMealNotificationsEnabled(enabled: Boolean) {
        context.serverSettingsDataStore.edit { it[MEAL_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun markServerMealSynced(syncId: String) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[SYNCED_SERVER_MEAL_IDS] = preferences[SYNCED_SERVER_MEAL_IDS].orEmpty() + syncId
        }
    }

    suspend fun forgetServerMealSynced(syncId: String) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[SYNCED_SERVER_MEAL_IDS] =
                preferences[SYNCED_SERVER_MEAL_IDS].orEmpty() - syncId
        }
    }

    suspend fun updateSyncStatus(status: String, syncTime: String) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[LAST_SYNC_STATUS] = status
            preferences[LAST_SYNC_TIME] = syncTime
            if (status.contains("failed", ignoreCase = true) ||
                status.contains("error", ignoreCase = true) ||
                status.contains("HTTP 4", ignoreCase = true) ||
                status.contains("HTTP 5", ignoreCase = true)
            ) {
                preferences[LAST_SYNC_ERROR] = status
            } else {
                preferences.remove(LAST_SYNC_ERROR)
            }
        }
    }

    suspend fun updateManualSyncStatus(status: String, syncTime: String) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[LAST_SYNC_STATUS] = status
            preferences[LAST_SYNC_TIME] = syncTime
            preferences[LAST_MANUAL_SYNC_TIME] = syncTime
            updateErrorPreference(preferences, status)
        }
    }

    suspend fun updateBackgroundSyncStatus(status: String, syncTime: String) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[LAST_SYNC_STATUS] = status
            preferences[LAST_SYNC_TIME] = syncTime
            preferences[LAST_BACKGROUND_SYNC_TIME] = syncTime
            updateErrorPreference(preferences, status)
        }
    }

    suspend fun markBackgroundSyncScheduled() {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[BACKGROUND_SYNC_SCHEDULED] = true
        }
    }

    suspend fun setBackgroundSyncScheduled(scheduled: Boolean) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[BACKGROUND_SYNC_SCHEDULED] = scheduled
        }
    }

    fun maskToken(token: String): String {
        if (token.isBlank()) return ""
        return if (token.length <= 8) {
            "****"
        } else {
            "${token.take(4)}...${token.takeLast(4)}"
        }
    }

    private companion object {
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val API_TOKEN = stringPreferencesKey("api_token")
        val READ_TOKEN = stringPreferencesKey("read_token")
        val ALLOW_INSECURE_HTTP = booleanPreferencesKey("allow_insecure_http")
        val LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")
        val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        val LAST_MANUAL_SYNC_TIME = stringPreferencesKey("last_manual_sync_time")
        val LAST_BACKGROUND_SYNC_TIME = stringPreferencesKey("last_background_sync_time")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val BACKGROUND_SYNC_SCHEDULED = booleanPreferencesKey("background_sync_scheduled")
        val SYNCED_SERVER_MEAL_IDS = stringSetPreferencesKey("synced_server_meal_ids")
        val MEAL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("meal_notifications_enabled")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val SYNC_ON_APP_OPEN_ENABLED = booleanPreferencesKey("sync_on_app_open_enabled")
        val CHARGING_SYNC_INTERVAL_MINUTES = intPreferencesKey("charging_sync_interval_minutes")
        val BATTERY_SYNC_INTERVAL_MINUTES = intPreferencesKey("battery_sync_interval_minutes")
        val SYNC_HEALTH_SNAPSHOTS_ENABLED = booleanPreferencesKey("sync_health_snapshots_enabled")
        val SYNC_SERVER_MEALS_ENABLED = booleanPreferencesKey("sync_server_meals_enabled")
        val UPDATE_NUTRITION_WIDGET_ENABLED = booleanPreferencesKey("update_nutrition_widget_enabled")
    }
}

private fun updateErrorPreference(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    status: String
) {
    val lastSyncError = stringPreferencesKey("last_sync_error")
    if (status.contains("failed", ignoreCase = true) ||
        status.contains("error", ignoreCase = true) ||
        status.contains("HTTP 4", ignoreCase = true) ||
        status.contains("HTTP 5", ignoreCase = true)
    ) {
        preferences[lastSyncError] = status
    } else {
        preferences.remove(lastSyncError)
    }
}
