package com.uriel.healthconnecttest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.HealthSyncScheduler
import com.uriel.healthconnecttest.ui.HealthDashboardScreen
import com.uriel.healthconnecttest.ui.theme.HealthConnectTestTheme
import com.uriel.healthconnecttest.widget.NutritionWidgetBackfill
import com.uriel.healthconnecttest.widget.NutritionWidgetsUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthConnectTestTheme {
                HealthDashboardScreen(openFood = intent.getBooleanExtra("open_food", false))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            val settings = ServerSettingsRepository(applicationContext).settings.first()
            if (settings.syncOnAppOpenEnabled) {
                HealthSyncScheduler.enqueueManualWorkerSync(applicationContext)
            } else if (settings.updateNutritionWidgetEnabled) {
                if (settings.syncServerMealsEnabled && settings.readToken.isNotBlank()) {
                    NutritionWidgetBackfill.backfillTodayFromServer(this@MainActivity)
                } else {
                    NutritionWidgetsUpdater.updateAll(this@MainActivity)
                }
            }
        }
    }
}
