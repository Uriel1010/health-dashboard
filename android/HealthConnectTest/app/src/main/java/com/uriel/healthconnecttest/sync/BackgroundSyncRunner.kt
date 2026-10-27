package com.uriel.healthconnecttest.sync

import android.content.Context
import android.util.Log
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.health.HealthConnectAvailability
import com.uriel.healthconnecttest.health.HealthConnectManager
import com.uriel.healthconnecttest.widget.NutritionWidgetStore
import com.uriel.healthconnecttest.widget.NutritionWidgetsUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BackgroundSyncRunResult(
    val message: String,
    val retryable: Boolean
)

class BackgroundSyncRunner(
    private val context: Context,
    private val repository: ServerSettingsRepository = ServerSettingsRepository(context.applicationContext),
    private val apiClient: HealthSyncApiClient = HealthSyncApiClient(),
    private val manager: HealthConnectManager = HealthConnectManager(context.applicationContext)
) {
    suspend fun run(settings: ServerSettings): BackgroundSyncRunResult {
        val appContext = context.applicationContext
        val messages = mutableListOf<String>()
        var retryable = false

        if (!settings.backgroundSyncEnabled) {
            if (settings.updateNutritionWidgetEnabled) {
                NutritionWidgetsUpdater.updateAll(appContext)
            }
            return BackgroundSyncRunResult("Skipped: background sync disabled", retryable = false)
        }

        if (settings.syncHealthSnapshotsEnabled) {
            val snapshotResult = runSnapshotUpload(settings)
            messages += snapshotResult.message
            retryable = retryable || snapshotResult.retryable
        } else {
            messages += "Snapshot upload skipped"
        }

        if (settings.syncServerMealsEnabled || settings.mealNotificationsEnabled) {
            val serverMealsResult = runServerMealSync(settings)
            messages += serverMealsResult.message
            retryable = retryable || serverMealsResult.retryable
        } else {
            messages += "Server meal pull skipped"
        }

        if (settings.updateNutritionWidgetEnabled) {
            if (settings.readToken.isNotBlank() && settings.syncServerMealsEnabled) {
                try {
                    NutritionStatisticsStore.save(appContext, settings, apiClient.fetchNutritionStatistics(settings))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { messages += "Saved statistics retained; refresh Trends to retry" }
            }
            NutritionWidgetsUpdater.updateAll(appContext)
            messages += "Widget refreshed"
        } else {
            messages += "Widget refresh skipped"
        }

        return BackgroundSyncRunResult(messages.joinToString(" | "), retryable)
    }

    private suspend fun runSnapshotUpload(settings: ServerSettings): SyncResult {
        if (settings.apiToken.isBlank()) {
            return SyncResult(false, retryable = false, message = "Snapshot upload skipped: ingest token missing")
        }
        if (!manager.isBackgroundReadAvailable()) {
            return SyncResult(false, retryable = false, message = "Snapshot upload skipped: background reads unavailable")
        }
        if (!manager.hasBackgroundReadPermission()) {
            return SyncResult(false, retryable = false, message = "Snapshot upload skipped: background permission missing")
        }

        return runCatching {
            val snapshot = HealthSnapshotBuilder(context.applicationContext, manager)
                .build(settings, requireBackgroundPermission = true)
            apiClient.sendSnapshot(settings, snapshot)
        }.getOrElse { exception ->
            SyncResult(
                success = false,
                retryable = false,
                message = "Snapshot upload failed: ${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
            )
        }
    }

    private suspend fun runServerMealSync(settings: ServerSettings): BackgroundSyncRunResult {
        if (settings.readToken.isBlank()) {
            return BackgroundSyncRunResult("Server meal pull skipped: dashboard session missing", retryable = false)
        }

        val today = LocalDate.now()
        val fetchResult = apiClient.fetchCurrentMeals(settings)
        if (!fetchResult.success) {
            return BackgroundSyncRunResult(fetchResult.message, retryable = fetchResult.retryable)
        }

        MealNotifications.observe(context.applicationContext, settings, fetchResult.meals)
        if (fetchResult.failedParseCount == 0 && settings.updateNutritionWidgetEnabled) {
            NutritionWidgetStore.replaceTodayMeals(context.applicationContext, fetchResult.meals, fetchResult.nutritionDay)
        }
        if (!settings.syncServerMealsEnabled) {
            return BackgroundSyncRunResult("Meal notifications checked", retryable = false)
        }
        val healthConnectWritable = manager.getAvailability() == HealthConnectAvailability.Available &&
            manager.hasNutritionWritePermission()
        val syncedIds = repository.getSyncedServerMealIds().toMutableSet()
        var widgetRecorded = 0
        var written = 0
        var skippedAlreadySynced = 0
        var failed = fetchResult.failedParseCount

        fetchResult.meals.forEach { meal ->
            if (settings.updateNutritionWidgetEnabled) {
                val recorded = NutritionWidgetStore.recordMeal(
                    context = context.applicationContext,
                    source = "background-server-meal",
                    identity = "server-${meal.syncId}",
                    eatenAt = meal.eatenAt,
                    caloriesKcal = meal.caloriesKcal,
                    proteinG = meal.proteinG,
                    fiberG = meal.fiberG
                )
                if (recorded) widgetRecorded += 1
            }

            if (syncedIds.contains(meal.syncId)) {
                skippedAlreadySynced += 1
                return@forEach
            }

            if (!healthConnectWritable) {
                failed += 1
                Log.i(TAG, "Background server meal skipped for Health Connect: nutrition write unavailable")
                return@forEach
            }

            val writeResult = runCatching {
                manager.writeNutritionRecord(meal.toNutritionInput())
            }
            writeResult.onSuccess {
                repository.markServerMealSynced(meal.syncId)
                syncedIds += meal.syncId
                written += 1
            }.onFailure { exception ->
                if (isDuplicateHealthConnectFailure(exception)) {
                    repository.markServerMealSynced(meal.syncId)
                    syncedIds += meal.syncId
                    skippedAlreadySynced += 1
                } else {
                    failed += 1
                    Log.i(
                        TAG,
                        "Background server meal Health Connect write failed: " +
                            "${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
                    )
                }
            }
        }

        val message = "Server meals fetched=${fetchResult.meals.size + fetchResult.failedParseCount}, " +
            "written=$written, skipped=$skippedAlreadySynced, failed=$failed, widgetRecorded=$widgetRecorded"
        return BackgroundSyncRunResult(message, retryable = false)
    }

    suspend fun updateBackgroundStatus(result: BackgroundSyncRunResult) {
        repository.updateBackgroundSyncStatus(
            result.message,
            DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        )
    }

    private fun isDuplicateHealthConnectFailure(exception: Throwable): Boolean {
        val text = "${exception::class.java.simpleName} ${exception.message.orEmpty()}"
        return text.contains("duplicate", ignoreCase = true) ||
            text.contains("conflict", ignoreCase = true) ||
            text.contains("already", ignoreCase = true)
    }

    private companion object {
        const val TAG = "BackgroundSyncRunner"
    }
}
