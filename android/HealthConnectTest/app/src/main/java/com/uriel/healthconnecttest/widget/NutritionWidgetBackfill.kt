package com.uriel.healthconnecttest.widget

import android.content.Context
import android.util.Log
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.HealthSyncApiClient
import java.time.LocalDate
import kotlinx.coroutines.flow.first

object NutritionWidgetBackfill {
    suspend fun backfillTodayFromServer(
        context: Context,
        repository: ServerSettingsRepository = ServerSettingsRepository(context.applicationContext),
        apiClient: HealthSyncApiClient = HealthSyncApiClient()
    ) {
        val appContext = context.applicationContext
        val settings = repository.settings.first()
        if (settings.serverBaseUrl.isBlank() || settings.readToken.isBlank()) {
            Log.i(TAG, "Widget server backfill skipped: server URL or dashboard session missing")
            NutritionWidgetsUpdater.updateAll(appContext)
            return
        }

        runCatching {
            val today = LocalDate.now()
            val result = apiClient.fetchCurrentMeals(settings)
            if (!result.success) {
                Log.i(TAG, "Widget server backfill failed: ${result.message}")
                NutritionWidgetsUpdater.updateAll(appContext)
                return
            }

            var recorded = 0
            if (result.failedParseCount == 0) NutritionWidgetStore.replaceTodayMeals(appContext, result.meals, result.nutritionDay)
            com.uriel.healthconnecttest.sync.MealNotifications.observe(appContext, settings, result.meals)
            result.meals.forEach { meal ->
                val wasRecorded = NutritionWidgetStore.recordMeal(
                    context = appContext,
                    source = "server-backfill",
                    identity = "server-${meal.syncId}",
                    eatenAt = meal.eatenAt,
                    caloriesKcal = meal.caloriesKcal,
                    proteinG = meal.proteinG,
                    fiberG = meal.fiberG
                )
                if (wasRecorded) recorded += 1
            }
            Log.i(
                TAG,
                "Widget server backfill complete fetched=${result.meals.size + result.failedParseCount} " +
                    "recorded=$recorded parseFailed=${result.failedParseCount}"
            )
            NutritionWidgetsUpdater.updateAll(appContext)
        }.getOrElse { exception ->
            Log.i(
                TAG,
                "Widget server backfill exception: ${exception::class.java.simpleName}: ${exception.message ?: "no message"}"
            )
            NutritionWidgetsUpdater.updateAll(appContext)
        }
    }

    private const val TAG = "NutritionWidgetBackfill"
}
