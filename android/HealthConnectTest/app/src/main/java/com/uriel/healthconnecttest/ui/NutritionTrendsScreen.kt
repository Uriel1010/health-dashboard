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
    key(settings.serverBaseUrl, settings.readToken) { AccountTrends(settings) }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Nutrition trends", style = MaterialTheme.typography.headlineMedium)
            Text("The bigger picture", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh statistics")
        }
    }
    Text("7, 14 and 30 completed nutrition days. Today is always excluded.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 14, 30).forEach { days -> FilterChip(selected = period == days, onClick = { period = days }, label = { Text("$days days") }) }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.error)
    if (cached) Text("Saved data · Refresh to get the latest completed days", style = MaterialTheme.typography.labelMedium)
    val data = statistics ?: return
    val window = data.windows.first { it.days == period }
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    Text("${window.from.format(dateFormat)} – ${window.to.format(dateFormat)}", style = MaterialTheme.typography.titleMedium)
    Text("${window.loggedDays} of $period days logged", color = MaterialTheme.colorScheme.onSurfaceVariant)
    NutritionMetric.entries.forEach { item ->
        val average = window.values[item]
        val goal = goals?.let { item.goal(it) }
        OutlinedCard(onClick = { metricName = item.name }, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = if (item == metric) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.label, style = MaterialTheme.typography.labelLarge)
                Text("${statisticAmount(average)} ${item.unit}/day", style = MaterialTheme.typography.headlineSmall)
                Text("${window.coverage[item]} days with data" + (goal?.let { " · Current goal $it ${item.unit}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val points = data.points.takeLast(period)
    val goal = goals?.let { metric.goal(it).toDouble() } ?: 0.0
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val maximum = maxOf(points.maxOfOrNull { it.values[metric] ?: 0.0 } ?: 0.0, goal, 1.0) * 1.12
    Text("${metric.label} by day", style = MaterialTheme.typography.titleMedium)
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
        contentDescription = "${metric.label} over $period days. Missing days are gaps. Daily values are available below."
    }) {
        val width = size.width / period
        if (goal > 0) drawLine(track, Offset(0f, size.height * (1 - goal / maximum).toFloat()),
            Offset(size.width, size.height * (1 - goal / maximum).toFloat()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        points.forEachIndexed { index, point ->
            val value = point.values[metric]
            val height = if (value == null) 3.dp.toPx() else maxOf((value / maximum * size.height).toFloat(), 2.dp.toPx())
            drawRoundRect(if (value == null) track else accent, Offset(index * width + 2.dp.toPx(), size.height - height),
                Size(maxOf(width - 4.dp.toPx(), 1f), height), CornerRadius(3.dp.toPx()))
        }
    }
    Text("Dashed line: current goal · Muted marks: no data", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { showDays = !showDays }) { Text(if (showDays) "Hide daily values" else "Show daily values") }
    if (showDays) points.asReversed().forEach { point ->
        Text("${point.date.format(dateFormat)} · ${statisticAmount(point.values[metric])} ${metric.unit}", style = MaterialTheme.typography.bodyMedium)
    }
    Text("Averages use recorded days, not missing days. Logged days may still be partial; unknown nutrient values are excluded separately. Goals are current, not historical.\nDay boundary: ${data.resetHour.toString().padStart(2, '0')}:00 · ${data.timezone}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
