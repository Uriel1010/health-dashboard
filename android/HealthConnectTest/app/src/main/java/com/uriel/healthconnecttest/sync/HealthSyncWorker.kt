package com.uriel.healthconnecttest.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import kotlinx.coroutines.flow.first

class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = ServerSettingsRepository(applicationContext)
        val settings = repository.settings.first()
        val mode = inputData.getString(KEY_MODE).orEmpty()
        val runner = BackgroundSyncRunner(applicationContext, repository)

        if (mode == MODE_BATTERY && isCharging(applicationContext)) {
            val result = BackgroundSyncRunResult("Skipped: battery worker while charging", retryable = false)
            runner.updateBackgroundStatus(result)
            return Result.success()
        }

        val result = runner.run(settings)
        runner.updateBackgroundStatus(result)

        return if (result.retryable) Result.retry() else Result.success()
    }

    private fun isCharging(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_CHARGING = "charging"
        const val MODE_BATTERY = "battery"
        const val MODE_MANUAL = "manual"
    }
}
