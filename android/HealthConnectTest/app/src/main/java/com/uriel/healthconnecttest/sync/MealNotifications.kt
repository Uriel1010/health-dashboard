package com.uriel.healthconnecttest.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uriel.healthconnecttest.R
import com.uriel.healthconnecttest.data.ServerSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.mealNotificationStore by preferencesDataStore(name = "meal_notifications")

object MealNotifications {
    private const val CHANNEL = "new_meals"
    private val mutex = Mutex()
    private val scopeKey = stringPreferencesKey("scope")
    private val seenKey = stringSetPreferencesKey("seen")

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun observe(context: Context, settings: ServerSettings, meals: List<ServerMeal>, silent: Boolean = false, allowUninitialized: Boolean = false) {
        if (!settings.mealNotificationsEnabled) return
        mutex.withLock {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.meal_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.meal_notification_channel_description)
            })
            // Keep discovery independent of Health Connect writes and widget deduplication.
            val scope = MealPush.scope(settings)
            val legacyScope = settings.serverBaseUrl.trimEnd('/') + ":" + settings.readToken.hashCode()
            context.mealNotificationStore.edit { preferences ->
                val initialized = preferences[scopeKey] == scope || preferences[scopeKey] == legacyScope
                val seen = if (initialized) preferences[seenKey].orEmpty() else emptySet()
                val fresh = meals.filterNot { it.serverId.toString() in seen }
                if ((initialized || allowUninitialized) && !silent && allowed(context)) {
                    fresh.forEach { meal ->
                        val notification = MealNotificationContent.build(context, meal, scope)
                        try {
                            manager.notify("meal-${meal.serverId}", 1, notification)
                            MealNotificationContent.updateSummary(context)
                        } catch (_: SecurityException) {
                            // Permission can be revoked between the check and posting.
                        }
                    }
                }
                preferences[scopeKey] = scope
                preferences[seenKey] = (seen + meals.map { it.serverId.toString() })
                    .sortedByDescending { it.toLongOrNull() ?: 0L }.take(2000).toSet()
            }
        }
    }
}
