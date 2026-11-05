package com.uriel.healthconnecttest.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MealDisplayTimeTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test fun utcWinterMealDisplays2051WithoutChangingInstant() {
        val result = mealDisplayTime("2026-11-05T18:51:00Z", jerusalem)
        assertEquals(LocalTime.of(20,51),result.toLocalTime())
        assertEquals(Instant.parse("2026-11-05T18:51:00Z"),result.toInstant())
    }

    @Test fun daylightSavingUsesThreeHourOffset() {
        assertEquals(LocalTime.of(21,51),mealDisplayTime("2026-07-05T18:51:00Z",jerusalem).toLocalTime())
    }

    @Test fun alreadyLocalOffsetIsNotShiftedAgain() {
        assertEquals(LocalTime.of(20,51),mealDisplayTime("2026-11-05T20:51:00+02:00",jerusalem).toLocalTime())
    }

    @Test fun midnightConversionAlsoUpdatesDisplayedDate() {
        val result = mealDisplayTime("2026-11-05T22:15:00Z",jerusalem)
        assertEquals(LocalDate.of(2026,11,6),result.toLocalDate())
        assertEquals(LocalTime.of(0,15),result.toLocalTime())
    }

    @Test fun otherPhoneZonesDoNotUseHardcodedIsraelOffset() {
        assertEquals(LocalTime.of(13,51),mealDisplayTime("2026-11-05T18:51:00Z",ZoneId.of("America/New_York")).toLocalTime())
    }
}
