package com.uriel.healthconnecttest.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uriel.healthconnecttest.data.ServerSettings
import java.util.concurrent.TimeUnit

object HealthSyncScheduler {
    private const val CHARGING_SYNC_NAME = "health_background_sync_charging"
    private const val BATTERY_SYNC_NAME = "health_background_sync_battery"

    fun schedulePeriodicSync(context: Context, settings: ServerSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.backgroundSyncEnabled) {
            cancelPeriodicSync(context)
            return
        }

        val chargingRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            settings.chargingSyncIntervalMinutes.coerceAtLeast(15).toLong(),
            TimeUnit.MINUTES
        )
            .setInputData(modeData(HealthSyncWorker.MODE_CHARGING))
            .setConstraints(networkConstraints(requiresCharging = true))
            .build()

        workManager.enqueueUniquePeriodicWork(
            CHARGING_SYNC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            chargingRequest
        )

        if (settings.batterySyncIntervalMinutes > 0) {
            val batteryRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                settings.batterySyncIntervalMinutes.coerceAtLeast(15).toLong(),
                TimeUnit.MINUTES
            )
                .setInputData(modeData(HealthSyncWorker.MODE_BATTERY))
                .setConstraints(networkConstraints(requiresCharging = false))
                .build()

            workManager.enqueueUniquePeriodicWork(
                BATTERY_SYNC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                batteryRequest
            )
        } else {
            workManager.cancelUniqueWork(BATTERY_SYNC_NAME)
        }
    }

    fun cancelPeriodicSync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(CHARGING_SYNC_NAME)
        workManager.cancelUniqueWork(BATTERY_SYNC_NAME)
    }

    fun enqueueManualWorkerSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setInputData(modeData(HealthSyncWorker.MODE_MANUAL))
            .setConstraints(networkConstraints(requiresCharging = false))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun networkConstraints(requiresCharging: Boolean): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(requiresCharging)
            .build()
    }

    private fun modeData(mode: String): Data {
        return Data.Builder()
            .putString(HealthSyncWorker.KEY_MODE, mode)
            .build()
    }
}
