package com.uriel.healthconnecttest.widget

import java.time.ZonedDateTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionDateTest {
    @Test fun beforeResetBelongsToPreviousDay() {
        assertEquals(LocalDate.parse("2026-10-15"), nutritionDate(ZonedDateTime.parse("2026-10-16T03:59:00+03:00[Asia/Jerusalem]"), 4))
    }
    @Test fun atResetStartsNewDay() {
        assertEquals(LocalDate.parse("2026-10-16"), nutritionDate(ZonedDateTime.parse("2026-10-16T04:00:00+03:00[Asia/Jerusalem]"), 4))
    }
}
