package com.uriel.healthconnecttest.sync

import org.junit.Assert.*
import org.junit.Test

class NutritionGoalsTest {
    @Test fun parsesAllFiveGoals() {
        assertEquals(NutritionGoals(2200, 140, 250, 70, 30), NutritionGoals.parse(listOf("2200", "140", "250", "70", "30")))
    }
    @Test fun supportsServerBoundaryValues() {
        assertEquals(listOf(10000, 2000, 2000, 2000, 500), NutritionGoals.parse(listOf("10000", "2000", "2000", "2000", "500")).values())
        assertEquals(listOf(0, 0, 0, 0, 0), NutritionGoals.parse(List(5) { "0" }).values())
    }
    @Test fun rejectsInvalidAndOutOfRangeInputs() {
        for (value in listOf("", "-1", "12.5", "abc", "2147483648", "10001")) {
            assertNotNull(NutritionGoals.errors(listOf(value, "140", "250", "70", "30"))[0])
        }
        assertNotNull(NutritionGoals.errors(listOf("2200", "2001", "250", "70", "30"))[1])
        assertNotNull(NutritionGoals.errors(listOf("2200", "140", "250", "70", "501"))[4])
    }
    @Test(expected = IllegalArgumentException::class) fun refusesInvalidModel() { NutritionGoals(2200, -1, 250, 70, 30) }
}
