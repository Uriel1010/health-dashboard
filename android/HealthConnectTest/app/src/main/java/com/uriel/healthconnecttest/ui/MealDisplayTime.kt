package com.uriel.healthconnecttest.ui

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Display an API timestamp in the phone's zone without changing its saved instant. */
internal fun mealDisplayTime(value: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
    OffsetDateTime.parse(value).atZoneSameInstant(zone)
