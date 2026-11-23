package com.uriel.healthconnecttest.sync

import com.uriel.healthconnecttest.data.ServerSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NutritionOfflineStoreTest {
    private val day = ServerNutritionDay(LocalDate.of(2026, 11, 22), "Asia/Jerusalem", 4,
        2400.0, 120.0, 30.0, 250.0, 80.0)
    private val meal = ServerMeal(7, "7", "client-7", "2026-11-23T02:00:00+02:00",
        MealType.Dinner, "Rice & chicken", 700.5, 42.5, 80.0, 23.0, null, 2.0, 400.0)

    @Test fun roundTripRetainsDayMacrosAndMealIdentity() {
        val saved = SavedNutrition(listOf(meal), day, 123456L)
        val restored = NutritionOfflineStore.decode(NutritionOfflineStore.encode(saved))
        assertEquals(saved, restored)
        assertEquals(700.5, restored.progress.caloriesKcal, 0.0)
        assertEquals(42.5, restored.progress.proteinG, 0.0)
        assertEquals(0.0, restored.progress.fiberG, 0.0)
        assertEquals(250.0, restored.progress.carbsGoal, 0.0)
    }

    @Test fun emptyDayReplacesOldMealsWithoutInventingData() {
        val saved = SavedNutrition(emptyList(), day, 5L)
        val restored = NutritionOfflineStore.decode(NutritionOfflineStore.encode(saved))
        assertTrue(restored.meals.isEmpty())
        assertEquals(day.date, restored.day.date)
        assertEquals(0.0, restored.progress.caloriesKcal, 0.0)
    }

    @Test fun accountCacheNeverMatchesAnotherServerOrSession() {
        val settings = ServerSettings(serverBaseUrl = "https://example.com", readToken = "session-a")
        val key = NutritionOfflineStore.accountKey(settings)
        assertEquals(key, NutritionOfflineStore.accountKey(settings.copy(serverBaseUrl = "https://example.com/")))
        assertNotEquals(key, NutritionOfflineStore.accountKey(settings.copy(readToken = "session-b")))
        assertNotEquals(key, NutritionOfflineStore.accountKey(settings.copy(serverBaseUrl = "https://other.example")))
        assertFalse(key.contains("session-a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptMealDoesNotReplaceCompleteSavedData() {
        val json = NutritionOfflineStore.encode(SavedNutrition(listOf(meal), day, 1L))
        json.getJSONArray("meals").getJSONObject(0).remove("caloriesKcal")
        NutritionOfflineStore.decode(json)
    }
}
