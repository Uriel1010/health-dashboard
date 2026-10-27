package com.uriel.healthconnecttest.sync

import android.content.Context
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.health.HealthConnectAvailability
import com.uriel.healthconnecttest.health.HealthConnectManager
import com.uriel.healthconnecttest.health.HealthMetric
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthSnapshotBuilder(
    private val context: Context,
    private val manager: HealthConnectManager = HealthConnectManager(context)
) {
    suspend fun build(settings: ServerSettings, requireBackgroundPermission: Boolean): HealthSnapshot {
        val capturedAt = Instant.now()
        val grantedPermissions = if (manager.getAvailability() == HealthConnectAvailability.Available) {
            manager.getGrantedPermissions()
        } else {
            emptySet()
        }
        val errors = mutableListOf<String>()
        val permissions = HealthMetric.entries.associate { metric ->
            metric.permissionKey to if (grantedPermissions.contains(manager.permissionByMetric.getValue(metric))) {
                "granted"
            } else {
                "missing"
            }
        }.toMutableMap()

        val backgroundPermissionGranted = manager.hasBackgroundReadPermission(grantedPermissions)
        permissions["backgroundRead"] = when {
            !manager.isBackgroundReadAvailable() -> "unavailable"
            backgroundPermissionGranted -> "granted"
            else -> "missing"
        }

        if (requireBackgroundPermission && !backgroundPermissionGranted) {
            errors += "backgroundRead: permission missing"
        }

        fun canRead(metric: HealthMetric): Boolean {
            return grantedPermissions.contains(manager.permissionByMetric.getValue(metric))
        }

        val metrics = HealthSnapshotMetrics(
            stepsToday = readOrNull(HealthMetric.Steps, errors, ::canRead) {
                manager.readTodaySteps()
            },
            distanceKmToday = readOrNull(HealthMetric.Distance, errors, ::canRead) {
                manager.readTodayDistanceKilometers()
            },
            activeCaloriesKcalToday = readOrNull(HealthMetric.ActiveCalories, errors, ::canRead) {
                manager.readTodayActiveCaloriesKilocalories()
            },
            totalCaloriesKcalToday = readOrNull(HealthMetric.TotalCalories, errors, ::canRead) {
                manager.readTodayTotalCaloriesKilocalories()
            },
            latestHeartRateBpm = readOrNull(HealthMetric.LatestHeartRate, errors, ::canRead) {
                manager.readLatestHeartRateSample()?.beatsPerMinute
            },
            restingHeartRateBpm = readOrNull(HealthMetric.RestingHeartRate, errors, ::canRead) {
                manager.readLatestRestingHeartRate()?.beatsPerMinute
            },
            sleepMinutesLastNight = readOrNull(HealthMetric.Sleep, errors, ::canRead) {
                manager.readLastNightSleep()?.duration?.toMinutes()
            },
            latestWeightKg = readOrNull(HealthMetric.Weight, errors, ::canRead) {
                manager.readLatestWeightKilograms()
            },
            recentExercises = readOrNull(HealthMetric.Exercise, errors, ::canRead) {
                manager.readRecentExercises().map {
                    RecentExerciseSnapshot(
                        title = it.title,
                        startTime = DateTimeFormatter.ISO_INSTANT.format(it.startTime),
                        durationMinutes = it.duration.toMinutes()
                    )
                }
            }.orEmpty()
        )

        return HealthSnapshot(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            capturedAt = DateTimeFormatter.ISO_INSTANT.format(capturedAt),
            timezone = ZoneId.systemDefault().id,
            appVersion = appVersion(),
            metrics = metrics,
            permissions = permissions,
            errors = errors
        )
    }

    private suspend fun <T> readOrNull(
        metric: HealthMetric,
        errors: MutableList<String>,
        canRead: (HealthMetric) -> Boolean,
        read: suspend () -> T?
    ): T? {
        if (!canRead(metric)) return null
        return try {
            read()
        } catch (exception: Exception) {
            errors += "${metric.permissionKey}: ${exception.message ?: "read failed"}"
            null
        }
    }

    private fun appVersion(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
}

private val HealthMetric.permissionKey: String
    get() = when (this) {
        HealthMetric.Steps -> "steps"
        HealthMetric.Distance -> "distance"
        HealthMetric.ActiveCalories -> "activeCalories"
        HealthMetric.TotalCalories -> "totalCalories"
        HealthMetric.LatestHeartRate -> "heartRate"
        HealthMetric.RestingHeartRate -> "restingHeartRate"
        HealthMetric.Sleep -> "sleep"
        HealthMetric.Exercise -> "exercise"
        HealthMetric.Weight -> "weight"
    }
