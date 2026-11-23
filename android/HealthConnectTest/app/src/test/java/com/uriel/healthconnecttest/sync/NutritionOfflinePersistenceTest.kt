package com.uriel.healthconnecttest.sync

import android.content.Context
import com.uriel.healthconnecttest.data.ServerSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NutritionOfflinePersistenceTest {
    private val settings = ServerSettings(serverBaseUrl = "https://example.com", readToken = "session-a")
    private val day = ServerNutritionDay(LocalDate.of(2026, 11, 22), "Asia/Jerusalem", 4,
        2400.0, 120.0, 30.0, 250.0, 80.0)
    private val meal = ServerMeal(7, "7", "client-7", "2026-11-23T02:00:00+02:00",
        MealType.Dinner, "Dinner", 700.0, 42.0, 80.0, 23.0, null, null, null)
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val result get() = ServerMealsFetchResult(true, false, "", listOf(meal), nutritionDay = day)

    @Before fun reset() { context.getSharedPreferences("nutrition_offline", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun reopeningRestoresMealsAndDayWithoutNetwork() {
        NutritionOfflineStore.save(context, settings, result)
        val reopened = context.createConfigurationContext(context.resources.configuration)
        val saved = NutritionOfflineStore.read(reopened, settings)!!
        assertEquals(listOf(meal), saved.meals)
        assertEquals(day, saved.day)
        assertTrue(saved.updated > 0)
        assertEquals(700.0, saved.progress.caloriesKcal, 0.0)
    }

    @Test fun failedAndIncompleteRefreshesKeepLastGoodSnapshot() {
        NutritionOfflineStore.save(context, settings, result)
        val before = NutritionOfflineStore.read(context, settings)
        NutritionOfflineStore.save(context, settings, ServerMealsFetchResult(false, true, "Offline"))
        NutritionOfflineStore.save(context, settings, result.copy(meals = emptyList(), failedParseCount = 1))
        assertEquals(before, NutritionOfflineStore.read(context, settings))
    }

    @Test fun successfulEmptyDayAndDeletionArePersisted() {
        NutritionOfflineStore.save(context, settings, result)
        NutritionOfflineStore.save(context, settings, result.copy(meals = emptyList()))
        assertTrue(NutritionOfflineStore.read(context, settings)!!.meals.isEmpty())
    }

    @Test fun otherSessionsAndServersCannotReadSavedMealsOrGoals() {
        val goals = NutritionGoals(2400, 120, 250, 80, 30)
        NutritionOfflineStore.save(context, settings, result)
        NutritionOfflineStore.saveGoals(context, settings, goals)
        assertEquals(goals, NutritionOfflineStore.goals(context, settings))
        listOf(settings.copy(readToken = "session-b"), settings.copy(readToken = ""),
            settings.copy(serverBaseUrl = "https://other.example")).forEach { other ->
            assertNull(NutritionOfflineStore.read(context, other))
            assertNull(NutritionOfflineStore.goals(context, other))
        }
    }

    @Test fun corruptCacheFallsBackToNoData() {
        NutritionOfflineStore.save(context, settings, result)
        context.getSharedPreferences("nutrition_offline", Context.MODE_PRIVATE).edit().putString("meals", "broken").commit()
        assertNull(NutritionOfflineStore.read(context, settings))
    }
}
