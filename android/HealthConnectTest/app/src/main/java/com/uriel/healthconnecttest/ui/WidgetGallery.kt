package com.uriel.healthconnecttest.ui

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.widget.*
import com.uriel.healthconnecttest.sync.NutritionMetric

@Composable
fun WidgetGallery() {
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    val manager = remember { AppWidgetManager.getInstance(context) }
    var configureAverage by remember { mutableStateOf(false) }
    var period by remember { mutableIntStateOf(7) }
    var metric by remember { mutableStateOf(NutritionMetric.Calories) }
    fun pin(index: Int, provider: Class<*>) {
        notice = if (manager.isRequestPinAppWidgetSupported) {
            val callback = if (index == 2) PendingIntent.getBroadcast(context, (System.nanoTime() and 0x7fffffff).toInt(),
                Intent(context, NutritionAverageWidgetProvider::class.java)
                    .setAction(NutritionAverageWidgetProvider.PIN_CONFIGURED)
                    .putExtra("period", period).putExtra("metric", metric.name),
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0) else null
            val requested = runCatching { manager.requestPinAppWidget(ComponentName(context, provider), null, callback) }.getOrDefault(false)
            if (requested) "Confirm on your home screen. If no prompt appears, add it from your launcher's Widgets menu."
            else "Open your home screen's Widgets menu and choose Health Companion."
        } else "Long-press your home screen → Widgets → Health Companion."
    }
    Text("Your home screen", style = MaterialTheme.typography.titleLarge)
    Text("Mix today's progress with longer-term averages. Add as many as you like.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    val choices = listOf(
        Triple("Today's progress", "Wide · Calories, protein and fiber with progress bars", NutritionWidgetProvider::class.java),
        Triple("Daily rings", "Compact · Today's nutrition in three rings", NutritionRingsWidgetProvider::class.java),
        Triple("Nutrition average", "2 × 2 · Choose 7, 14 or 30 days and any nutrient", NutritionAverageWidgetProvider::class.java)
    )
    choices.forEachIndexed { index, choice ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(choice.first, style = MaterialTheme.typography.titleMedium)
                Text(choice.second, style = MaterialTheme.typography.bodySmall)
                if (index == 0) {
                    LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.fillMaxWidth())
                    Text("Layout preview", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = {
                    if (index == 2) configureAverage = true else pin(index, choice.third)
                }) { Text("Add widget") }
            }
        }
    }
    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { context.startActivity(Intent(context, WidgetAccentConfigActivity::class.java)) }) { Text("Progress and rings colors") }
    Text("To change an average widget later, long-press it and choose its settings when supported, or remove and add it again.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (configureAverage) AlertDialog(onDismissRequest = { configureAverage = false },
        title = { Text("Choose your average") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 30).forEach { days -> FilterChip(selected = period == days,
                        onClick = { period = days }, label = { Text("${days}D") }) }
                }
                NutritionMetric.entries.forEach { item ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = metric == item, onClick = { metric = item })
                        TextButton(onClick = { metric = item }) { Text(item.label) }
                    }
                }
                Text("Today excluded. Missing days do not count as zero.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { configureAverage = false; pin(2, NutritionAverageWidgetProvider::class.java) }) { Text("Add widget") } },
        dismissButton = { TextButton(onClick = { configureAverage = false }) { Text("Cancel") } })
}
