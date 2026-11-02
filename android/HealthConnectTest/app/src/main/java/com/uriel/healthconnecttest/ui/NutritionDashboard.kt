package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.sync.ServerMeal
import com.uriel.healthconnecttest.widget.NutritionWidgetProgress
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private fun amount(value: Double) = NumberFormat.getIntegerInstance().format(value)

@Composable
fun NutritionDashboard(progress: NutritionWidgetProgress?, day: String, meals: List<ServerMeal>, loading: Boolean,
    message: String, onRefresh: () -> Unit, onViewMeals: () -> Unit, onEditGoals: () -> Unit) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val failure = message.isNotBlank() && message != "Meals refreshed"
    if (failure) NutritionError(message, onRefresh)
    if (progress == null) {
        ReferenceCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(CompanionIcons.Bowl, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Text("Nutrition data unavailable", style = MaterialTheme.typography.titleMedium)
                Text(if (message.contains("required", true) || message.contains("sign in", true))
                    "Sign in under Settings → Account & server to load today's meals."
                    else "We couldn't load today's meals and totals.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onRefresh, enabled = !loading) { Text("Refresh nutrition") }
            }
        }
        return
    }
    ReferenceCard {
        val shortDay = runCatching { LocalDate.parse(day.take(10)).format(DateTimeFormatter.ofPattern("EEE, MMM d")) }.getOrDefault("Today")
        Text("Nutrition", style = MaterialTheme.typography.titleMedium)
        Text("Nutrition day: $shortDay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val calorieContent: @Composable () -> Unit = {
            Box(Modifier.size(128.dp).padding(5.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.caloriesProgress / 100f }, modifier = Modifier.fillMaxSize(),
                    color = NutritionGreen, strokeWidth = 8.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(amount(progress.caloriesKcal), style = MaterialTheme.typography.headlineSmall)
                    Text("/ ${amount(progress.caloriesGoal)} kcal", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val balance: @Composable () -> Unit = {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("${amount(abs(progress.caloriesGoal - progress.caloriesKcal))} kcal", style = MaterialTheme.typography.titleLarge)
                Text(if (progress.caloriesKcal <= progress.caloriesGoal) "remaining" else "over goal", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (LocalDensity.current.fontScale >= 1.5f) { calorieContent(); balance() }
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            calorieContent(); balance()
        }
        CompactMacro("Protein", progress.proteinG, progress.proteinGoal, NutritionTeal)
        CompactMacro("Carbs", progress.carbsG, progress.carbsGoal, NutritionOrange)
        CompactMacro("Fat", progress.fatG, progress.fatGoal, Color(0xFFE8B12D))
        CompactMacro("Fiber", progress.fiberG, progress.fiberGoal, NutritionGreen)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onEditGoals, contentPadding = PaddingValues(0.dp)) { Text("Edit goals", style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = onRefresh, enabled = !loading, contentPadding = PaddingValues(0.dp)) { Text("Refresh", style = MaterialTheme.typography.labelMedium) }
        }
    }
    ReferenceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent meals (${meals.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onViewMeals, contentPadding = PaddingValues(0.dp)) { Text("View all", style = MaterialTheme.typography.labelMedium) }
        }
        if (meals.isEmpty()) Text("No meals registered for this nutrition day.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        meals.take(3).forEachIndexed { index, meal ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReferenceMealRow(meal)
        }
    }
}

@Composable
private fun CompactMacro(label: String, consumed: Double, goal: Double, color: Color) {
    val fraction = if (goal > 0) (consumed / goal).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("${amount(consumed)} / ${amount(goal)} g", style = MaterialTheme.typography.bodySmall)
            Text(if (goal > 0) "${(fraction * 100).toInt()}%" else "—", Modifier.widthIn(min = 42.dp).padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(start = 78.dp).height(4.dp),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {})
    }
}
