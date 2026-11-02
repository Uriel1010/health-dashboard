package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.health.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

@Composable
internal fun HealthHistoryTrends(category: String) {
    val context = LocalContext.current.applicationContext
    val manager = remember { HealthConnectManager(context) }
    var days by rememberSaveable(category) { mutableIntStateOf(7) }
    var revision by remember { mutableIntStateOf(0) }
    var calories by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf<List<CompletedActivityDay>>(emptyList()) }
    var weights by remember { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }
    LaunchedEffect(category, days, revision) {
        loading = true; error = ""; activity = emptyList(); weights = emptyList()
        try {
            check(manager.getAvailability() == HealthConnectAvailability.Available) { "Health Connect is unavailable. Check permissions and data access in Settings." }
            if (category == "Activity") activity = manager.readCompletedActivityDays(days)
            else weights = manager.readCompletedWeightDays(days)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not load Health Connect history" }
        finally { loading = false }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val values = if (category == "Weight") weights.toMap() else activity.associate { it.date to if (calories) it.activeCalories else it.steps }
    val points = (days downTo 1).map { offset -> LocalDate.now().minusDays(offset.toLong()).let { it to values[it] } }
    val known = points.mapNotNull { it.second }
    val unit = if (category == "Weight") "kg" else if (calories) "kcal" else "steps"
    ReferenceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (category == "Weight") "Weight history" else "Daily activity", style = MaterialTheme.typography.titleMedium)
                Text("Last $days completed days", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { days = when (days) { 7 -> 30; 14 -> 7; else -> 14 } }) { Icon(CompanionIcons.Back, "Previous history period", Modifier.size(18.dp)) }
            IconButton(onClick = { days = when (days) { 7 -> 14; 14 -> 30; else -> 7 } }) { Icon(CompanionIcons.Chevron, "Next history period", Modifier.size(18.dp)) }
        }
        if (category == "Activity") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !calories, onClick = { calories = false }, label = { Text("Steps") })
            FilterChip(selected = calories, onClick = { calories = true }, label = { Text("Active calories") })
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        else {
            ReferenceHistoryChart(points, unit)
            Text(if (known.isEmpty()) "No recorded data for this period." else "Average ${statisticAmount(known.average())} $unit · ${known.size}/$days days with data",
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("From Health Connect · Phone calendar days · Today excluded", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { revision++ }, enabled = !loading) { Text("Refresh history") }
    }
    if (known.isNotEmpty()) ReferenceCard {
        Text("Daily values", style = MaterialTheme.typography.titleMedium)
        points.asReversed().forEach { (day, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(day.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${statisticAmount(value)} $unit", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (category == "Weight") Text("The last recorded measurement is used for each day.", style = MaterialTheme.typography.bodySmall)
    }
}
