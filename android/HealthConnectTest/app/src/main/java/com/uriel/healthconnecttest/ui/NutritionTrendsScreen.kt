package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.data.ServerSettings
import com.uriel.healthconnecttest.sync.*
import com.uriel.healthconnecttest.widget.NutritionWidgetsUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

internal fun statisticAmount(value: Double?): String = value?.let {
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it)
} ?: "—"

internal val TrendsIcon = ImageVector.Builder("Trends", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        moveTo(3f, 4f); lineTo(3f, 20f); lineTo(21f, 20f)
        moveTo(6f, 15f); lineTo(11f, 10f); lineTo(15f, 13f); lineTo(21f, 6f)
    }
}.build()

@Composable
fun NutritionTrendsScreen(settings: ServerSettings) {
    var category by rememberSaveable { mutableStateOf("Nutrition") }
    Text("Trends", style = MaterialTheme.typography.headlineSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Nutrition", "Activity", "Weight").forEach { item ->
            Surface(onClick = { category = item }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (category == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)) {
                Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(item, style = MaterialTheme.typography.labelLarge, color = if (category == item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
    if (category == "Nutrition") key(settings.serverBaseUrl, settings.readToken) { AccountTrends(settings) }
    else HealthHistoryTrends(category)
}

@Composable
private fun AccountTrends(settings: ServerSettings) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var statistics by remember { mutableStateOf(NutritionStatisticsStore.read(context, settings)) }
    var goals by remember { mutableStateOf<NutritionGoals?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var cached by remember { mutableStateOf(statistics != null) }
    var period by rememberSaveable { mutableIntStateOf(7) }
    var metricName by rememberSaveable { mutableStateOf(NutritionMetric.Calories.name) }
    var showDays by rememberSaveable { mutableStateOf(false) }
    val metric = NutritionMetric.valueOf(metricName)
    val api = remember { HealthSyncApiClient() }
    suspend fun refresh() {
        if (settings.readToken.isBlank()) { notice = "Sign in in Settings → Account & server to see your trends."; return }
        loading = true
        notice = ""
        try {
            val json = api.fetchNutritionStatistics(settings)
            NutritionStatisticsStore.save(context, settings, json)
            statistics = NutritionStatistics.parse(json)
            cached = false
            goals = api.fetchNutritionGoals(settings)
            NutritionWidgetsUpdater.updateAll(context)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notice = e.message ?: "Unable to refresh statistics" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { refresh() }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (cached) Text("Saved data · Refresh to update", style = MaterialTheme.typography.bodySmall)
    val data = statistics
    val window = data?.windows?.firstOrNull { it.days == period }
    val points = data?.points?.takeLast(period).orEmpty()
    val goal = goals?.let { metric.goal(it).toDouble() } ?: 0.0
    ReferenceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily averages", style = MaterialTheme.typography.titleMedium)
                Text("Last $period days", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { period = when (period) { 7 -> 30; 14 -> 7; else -> 14 } }) { Icon(CompanionIcons.Back, "Previous average period", Modifier.size(18.dp)) }
            IconButton(onClick = { period = when (period) { 7 -> 14; 14 -> 30; else -> 7 } }) { Icon(CompanionIcons.Chevron, "Next average period", Modifier.size(18.dp)) }
        }
        if (window == null) {
            Text(if (loading) "Loading your statistics…" else "No statistics loaded yet.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text("Refresh statistics") }
        } else {
            ReferenceHistoryChart(points.map { it.date to it.values[metric] }, metric.unit, goal)
            Text("■  Average ${metric.label.lowercase()} (${statisticAmount(window.values[metric])} ${metric.unit})", style = MaterialTheme.typography.bodySmall)
            if (goal > 0) Text("– –  Current ${metric.label.lowercase()} goal (${goal.toInt()} ${metric.unit})", style = MaterialTheme.typography.bodySmall)
            Text("${window.from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${window.to.format(DateTimeFormatter.ofPattern("MMM d"))} · Today excluded", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (window != null) ReferenceCard {
        Text("Averages (last $period days)", style = MaterialTheme.typography.titleMedium)
        NutritionMetric.entries.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(onClick = { metricName = item.name }, color = if (item == metric) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (item == NutritionMetric.Calories) CompanionIcons.Flame else CompanionIcons.Food, null, Modifier.size(17.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${window.coverage[item]} days with data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${statisticAmount(window.values[item])} ${item.unit} / day", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${window.loggedDays}/$period days logged", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text("Refresh") }
        }
    }
    if (window != null) {
        TextButton(onClick = { showDays = !showDays }) { Text(if (showDays) "Hide daily values" else "Show daily values") }
        if (showDays) points.asReversed().forEach { point ->
            Text("${point.date} · ${statisticAmount(point.values[metric])} ${metric.unit}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Missing days are not zero. Unknown nutrient values are excluded separately. Logged days may be incomplete. Goals are current, not historical.\nDay boundary: ${data!!.resetHour.toString().padStart(2, '0')}:00 · ${data.timezone}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
