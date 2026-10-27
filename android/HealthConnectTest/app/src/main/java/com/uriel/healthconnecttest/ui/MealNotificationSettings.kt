package com.uriel.healthconnecttest.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.HealthSyncApiClient
import com.uriel.healthconnecttest.sync.MealNotifications
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun MealNotificationSettings(settings: ServerSettings, repository: ServerSettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun enable(value: Boolean) {
        scope.launch {
            if (value && settings.readToken.isNotBlank()) {
                // Establish today's baseline before enabling to avoid historical alerts.
                val baseline = runCatching {
                    HealthSyncApiClient().fetchCurrentMeals(settings)
                }.getOrNull()
                if (baseline?.success == true) {
                    MealNotifications.observe(context, settings.copy(mealNotificationsEnabled = true), baseline.meals, silent = true)
                }
            }
            repository.setMealNotificationsEnabled(value)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enable(granted)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New meal notifications", style = MaterialTheme.typography.titleMedium)
                Text("Each new meal found during sync", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.mealNotificationsEnabled, onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 && !MealNotifications.allowed(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else enable(enabled)
            })
        }
        if (settings.mealNotificationsEnabled) {
            Text(if (!settings.backgroundSyncEnabled) "Enable background sync to receive meals while the app is closed."
                else if (settings.readToken.isBlank()) "Sign in to the dashboard to discover new meals."
                else "Checked on refresh and your background sync schedule.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Notification settings") }
    }
}
