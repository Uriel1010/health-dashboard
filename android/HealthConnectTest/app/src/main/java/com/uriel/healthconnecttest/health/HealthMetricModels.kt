package com.uriel.healthconnecttest.health

import java.time.Duration
import java.time.Instant

enum class HealthMetric(val title: String, val timeRangeLabel: String) {
    Steps("Today's steps", "Today"),
    Distance("Today's distance", "Today"),
    ActiveCalories("Active calories", "Today"),
    TotalCalories("Total calories", "Today"),
    LatestHeartRate("Latest heart rate", "Today"),
    RestingHeartRate("Resting heart rate", "Last 7 days"),
    Sleep("Last night sleep", "Yesterday 6 PM to today noon"),
    Exercise("Recent workouts", "Last 7 days"),
    Weight("Latest weight", "Last 90 days")
}

enum class MetricStatus {
    Ready,
    NoData,
    PermissionMissing,
    Error
}

data class MetricCardState(
    val metric: HealthMetric,
    val value: String,
    val unit: String,
    val timeRangeLabel: String = metric.timeRangeLabel,
    val status: MetricStatus,
    val details: List<String> = emptyList()
)

data class HeartRateSample(
    val beatsPerMinute: Long,
    val time: Instant
)

data class SleepSummary(
    val duration: Duration,
    val sessionCount: Int
)

data class ExerciseSummary(
    val title: String,
    val startTime: Instant,
    val duration: Duration
)
