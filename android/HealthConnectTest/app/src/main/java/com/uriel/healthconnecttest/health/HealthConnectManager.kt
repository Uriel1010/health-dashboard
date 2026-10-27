package com.uriel.healthconnecttest.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.uriel.healthconnecttest.sync.MealType
import com.uriel.healthconnecttest.sync.NutritionMealInput
import androidx.health.connect.client.records.MealType as HealthConnectMealType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class HealthConnectAvailability {
    Available,
    NotInstalledOrUpdateRequired,
    Unsupported
}

class HealthConnectManager(private val context: Context) {
    val permissionByMetric = mapOf(
        HealthMetric.Steps to HealthPermission.getReadPermission(StepsRecord::class),
        HealthMetric.Distance to HealthPermission.getReadPermission(DistanceRecord::class),
        HealthMetric.ActiveCalories to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthMetric.TotalCalories to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthMetric.LatestHeartRate to HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthMetric.RestingHeartRate to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthMetric.Sleep to HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthMetric.Exercise to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthMetric.Weight to HealthPermission.getReadPermission(WeightRecord::class)
    )

    val allRequiredPermissions = permissionByMetric.values.toSet()
    val requiredPermissions = allRequiredPermissions
    val backgroundReadPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    val nutritionWritePermission = HealthPermission.getWritePermission(NutritionRecord::class)

    fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.NotInstalledOrUpdateRequired
            HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.Unsupported
            else -> HealthConnectAvailability.Unsupported
        }
    }

    suspend fun getGrantedPermissions(): Set<String> {
        return client.permissionController.getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        return getGrantedPermissions().containsAll(allRequiredPermissions)
    }

    suspend fun hasBackgroundReadPermission(): Boolean {
        return hasBackgroundReadPermission(getGrantedPermissions())
    }

    suspend fun hasNutritionWritePermission(): Boolean {
        return hasNutritionWritePermission(getGrantedPermissions())
    }

    fun hasBackgroundReadPermission(grantedPermissions: Set<String>): Boolean {
        return grantedPermissions.contains(backgroundReadPermission)
    }

    fun hasNutritionWritePermission(grantedPermissions: Set<String>): Boolean {
        return grantedPermissions.contains(nutritionWritePermission)
    }

    fun isBackgroundReadAvailable(): Boolean {
        if (getAvailability() != HealthConnectAvailability.Available) return false
        return client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    suspend fun readTodaySteps(): Long {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(todayStart(), Instant.now())
            )
        )

        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun readTodayDistanceKilometers(): Double? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(todayStart(), Instant.now())
            )
        )
        return result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
    }

    suspend fun readTodayActiveCaloriesKilocalories(): Double? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(todayStart(), Instant.now())
            )
        )
        return result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    }

    suspend fun readTodayTotalCaloriesKilocalories(): Double? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(todayStart(), Instant.now())
            )
        )
        return result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
    }

    suspend fun readLatestHeartRateSample(): HeartRateSample? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(todayStart(), Instant.now()),
                ascendingOrder = false,
                pageSize = 100
            )
        )
        return response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?.let { HeartRateSample(it.beatsPerMinute, it.time) }
    }

    suspend fun readLatestRestingHeartRate(): HeartRateSample? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(daysAgoStart(7), Instant.now()),
                ascendingOrder = false,
                pageSize = 20
            )
        )
        return response.records
            .maxByOrNull { it.time }
            ?.let { HeartRateSample(it.beatsPerMinute, it.time) }
    }

    suspend fun readLastNightSleep(): SleepSummary? {
        val startTime = LocalDate.now()
            .minusDays(1)
            .atTime(LocalTime.of(18, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val endTime = LocalDate.now()
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                ascendingOrder = false,
                pageSize = 20
            )
        )
        if (response.records.isEmpty()) return null

        val totalMillis = response.records.sumOf { record ->
            val overlapStart = max(record.startTime.toEpochMilli(), startTime.toEpochMilli())
            val overlapEnd = min(record.endTime.toEpochMilli(), endTime.toEpochMilli())
            max(0L, overlapEnd - overlapStart)
        }

        return if (totalMillis > 0L) {
            SleepSummary(Duration.ofMillis(totalMillis), response.records.size)
        } else {
            null
        }
    }

    suspend fun readRecentExercises(): List<ExerciseSummary> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(daysAgoStart(7), Instant.now()),
                ascendingOrder = false,
                pageSize = 20
            )
        )
        return response.records
            .sortedByDescending { it.startTime }
            .take(3)
            .map { record ->
                ExerciseSummary(
                    title = record.title ?: exerciseTypeLabel(record.exerciseType),
                    startTime = record.startTime,
                    duration = Duration.between(record.startTime, record.endTime)
                )
            }
    }

    suspend fun readLatestWeightKilograms(): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(daysAgoStart(90), Instant.now()),
                ascendingOrder = false,
                pageSize = 20
            )
        )
        return response.records
            .maxByOrNull { it.time }
            ?.weight
            ?.inKilograms
    }

    suspend fun writeNutritionRecord(meal: NutritionMealInput) {
        val eatenAt = java.time.OffsetDateTime.parse(meal.eatenAt)
        val startTime = eatenAt.toInstant()
        val endTime = startTime.plus(Duration.ofMinutes(1))
        val zoneOffset = eatenAt.offset
        val record = NutritionRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            metadata = Metadata.manualEntry(meal.clientRecordId, 1L),
            name = meal.title,
            mealType = meal.mealType.toHealthConnectMealType(),
            energy = Energy.kilocalories(meal.caloriesKcal),
            protein = Mass.grams(meal.proteinG),
            totalCarbohydrate = Mass.grams(meal.carbsG),
            totalFat = Mass.grams(meal.fatG),
            dietaryFiber = meal.fiberG?.let { Mass.grams(it) },
            sugar = meal.sugarG?.let { Mass.grams(it) },
            sodium = meal.sodiumMg?.let { Mass.milligrams(it) }
        )
        client.insertRecords(listOf(record))
    }

    private fun todayStart(): Instant {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
    }

    private fun daysAgoStart(days: Long): Instant {
        return LocalDate.now()
            .minusDays(days)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
    }

    private fun exerciseTypeLabel(exerciseType: Int): String {
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            else -> "Workout"
        }
    }

    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(context)
}

private fun MealType.toHealthConnectMealType(): Int {
    return when (this) {
        MealType.Breakfast -> HealthConnectMealType.MEAL_TYPE_BREAKFAST
        MealType.Lunch -> HealthConnectMealType.MEAL_TYPE_LUNCH
        MealType.Dinner -> HealthConnectMealType.MEAL_TYPE_DINNER
        MealType.Snack -> HealthConnectMealType.MEAL_TYPE_SNACK
        MealType.Other -> HealthConnectMealType.MEAL_TYPE_UNKNOWN
    }
}
