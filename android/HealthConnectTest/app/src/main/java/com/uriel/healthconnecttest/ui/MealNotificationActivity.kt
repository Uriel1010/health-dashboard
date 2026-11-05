package com.uriel.healthconnecttest.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uriel.healthconnecttest.MainActivity
import com.uriel.healthconnecttest.R
import com.uriel.healthconnecttest.data.ServerSettingsRepository
import com.uriel.healthconnecttest.sync.*
import com.uriel.healthconnecttest.ui.theme.HealthConnectTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Opens only the authenticated account's meal; never trusts health content from the Intent. */
class MealNotificationActivity : ComponentActivity() {
    private var meal by mutableStateOf<ServerMeal?>(null)
    private var error by mutableIntStateOf(0)
    private var loading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = "meal-${intent.getLongExtra("meal_id", -1)}"
        getSystemService(android.app.NotificationManager::class.java).cancel(tag, 1)
        MealNotificationContent.updateSummary(this, tag)
        enableEdgeToEdge()
        setContent {
            HealthConnectTestTheme {
                val locale = resources.configuration.locales[0]
                val direction = if (android.text.TextUtils.getLayoutDirectionFromLocale(locale) == 1) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            if (loading) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.meal_notification_loading))
                            } else if (error != 0) {
                                Text(stringResource(error), style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton(onClick = { loadMeal() }) { Text(stringResource(R.string.meal_notification_retry)) }
                            } else meal?.let { item ->
                                Text(item.title, style = MaterialTheme.typography.headlineMedium)
                                Text(MealNotificationContent.nutrients(this@MealNotificationActivity, item), style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                val stamp = runCatching { mealDisplayTime(item.eatenAt).format(
                                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)) }.getOrDefault(item.eatenAt)
                                Text(stamp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(stringResource(R.string.meal_notification_carbs, format.format(item.carbsG)))
                                        Text(stringResource(R.string.meal_notification_fat, format.format(item.fatG)))
                                        item.fiberG?.let { Text(stringResource(R.string.meal_notification_fiber, format.format(it))) }
                                    }
                                }
                            }
                            Button(onClick = {
                                startActivity(Intent(this@MealNotificationActivity, MainActivity::class.java)
                                    .putExtra("open_food", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                                finish()
                            }) { Text(stringResource(R.string.meal_notification_all)) }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); loadMeal() }

    private fun loadMeal() {
        meal = null; error = 0; loading = true
        lifecycleScope.launch {
            val settings = ServerSettingsRepository(applicationContext).settings.first()
            if (intent.getStringExtra("account_scope") != MealPush.scope(settings) || settings.readToken.isBlank()) {
                error = R.string.meal_notification_account_changed; loading = false; return@launch
            }
            val item = withContext(Dispatchers.IO) {
                try { MealPush.fetchMeal(settings, intent.getLongExtra("meal_id", -1)) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            val current = ServerSettingsRepository(applicationContext).settings.first()
            when {
                MealPush.scope(current) != MealPush.scope(settings) -> error = R.string.meal_notification_account_changed
                item == null -> error = R.string.meal_notification_unavailable
                else -> meal = item
            }
            loading = false
        }
    }
}
