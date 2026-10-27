package com.uriel.healthconnecttest.data

data class ServerSettings(
    val serverBaseUrl: String = "",
    val deviceId: String = "uriel-phone",
    val deviceName: String = "",
    val apiToken: String = "",
    val readToken: String = "",
    val allowInsecureHttpForLan: Boolean = false,
    val lastSyncStatus: String = "Never synced",
    val lastSyncTime: String = "",
    val lastManualSyncTime: String = "",
    val lastBackgroundSyncTime: String = "",
    val lastSyncError: String = "",
    val backgroundSyncScheduled: Boolean = false,
    val backgroundSyncEnabled: Boolean = true,
    val syncOnAppOpenEnabled: Boolean = true,
    val chargingSyncIntervalMinutes: Int = 30,
    val batterySyncIntervalMinutes: Int = 180,
    val syncHealthSnapshotsEnabled: Boolean = true,
    val syncServerMealsEnabled: Boolean = true,
    val updateNutritionWidgetEnabled: Boolean = true,
    val mealNotificationsEnabled: Boolean = false
)
