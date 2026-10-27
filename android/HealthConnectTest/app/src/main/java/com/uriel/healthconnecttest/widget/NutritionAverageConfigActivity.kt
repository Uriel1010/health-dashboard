package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uriel.healthconnecttest.sync.NutritionMetric
import com.uriel.healthconnecttest.ui.theme.HealthConnectTestTheme
import kotlinx.coroutines.launch

class NutritionAverageConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className != NutritionAverageWidgetProvider::class.java.name) {
            finish(); return
        }
        val preferences = getSharedPreferences("average_widgets", MODE_PRIVATE)
        setContent {
            HealthConnectTestTheme {
                var period by rememberSaveable { mutableIntStateOf(preferences.getInt("period_$id", 7)) }
                var metric by rememberSaveable { mutableStateOf(preferences.getString("metric_$id", "Calories")!!) }
                var saving by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your average widget", style = MaterialTheme.typography.headlineMedium)
                        Text("Choose a period and nutrient for this widget. Add more widgets for different views.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Completed days", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { days -> FilterChip(selected = period == days, enabled = !saving,
                                onClick = { period = days }, label = { Text("$days days") }) }
                        }
                        Text("Nutrient", style = MaterialTheme.typography.titleMedium)
                        NutritionMetric.entries.forEach { item ->
                            OutlinedCard(onClick = { if (!saving) metric = item.name }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp)) {
                                    RadioButton(selected = metric == item.name, enabled = !saving, onClick = { metric = item.name })
                                    Text(item.label, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                        Text("Today is excluded. Missing days are not counted as zero. The widget shows coverage and its saved date range.", style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                            saving = true
                            lifecycleScope.launch {
                                preferences.edit().putInt("period_$id", period).putString("metric_$id", metric).apply()
                                NutritionAverageWidgetProvider.updateAll(applicationContext)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        }) { Text(if (saving) "Adding…" else "Apply widget") }
                        TextButton(enabled = !saving, onClick = { finish() }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
