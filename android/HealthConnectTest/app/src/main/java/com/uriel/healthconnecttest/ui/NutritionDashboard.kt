package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.sync.ServerMeal
import com.uriel.healthconnecttest.widget.NutritionWidgetProgress
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private fun amount(value: Double) = NumberFormat.getIntegerInstance().format(value)

@Composable
fun NutritionDashboard(progress: NutritionWidgetProgress?, day: String, meals: List<ServerMeal>,
    loading: Boolean, message: String, onRefresh: () -> Unit, onViewMeals: () -> Unit, onEditGoals: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Daily nutrition", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text("Your fuel, at a glance", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh nutrition")
        }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (progress == null) {
        Text(if (message.isNotBlank()) message else "Connect your dashboard in Settings to see your meals and goals.")
        OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Try again") }
        return
    }
    val left = progress.caloriesGoal - progress.caloriesKcal
    if (LocalDensity.current.fontScale >= 1.5f) {
        CalorieRing(progress)
        CalorieBalance(left, progress.caloriesGoal)
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CalorieRing(progress)
            Column(Modifier.weight(1f)) { CalorieBalance(left, progress.caloriesGoal) }
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        NutritionMacro("Protein", progress.proteinG, progress.proteinGoal, progress.proteinProgress, Modifier.fillMaxWidth())
        NutritionMacro("Fiber", progress.fiberG, progress.fiberGoal, progress.fiberProgress, Modifier.fillMaxWidth())
        NutritionMacro("Carbs", progress.carbsG, progress.carbsGoal, progress.carbsProgress, Modifier.fillMaxWidth())
        NutritionMacro("Fat", progress.fatG, progress.fatGoal, progress.fatProgress, Modifier.fillMaxWidth())
    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NutritionMacro("Protein", progress.proteinG, progress.proteinGoal, progress.proteinProgress, Modifier.weight(1f))
        NutritionMacro("Fiber", progress.fiberG, progress.fiberGoal, progress.fiberProgress, Modifier.weight(1f))
    }
    if (LocalDensity.current.fontScale < 1.5f) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NutritionMacro("Carbs", progress.carbsG, progress.carbsGoal, progress.carbsProgress, Modifier.weight(1f))
        NutritionMacro("Fat", progress.fatG, progress.fatGoal, progress.fatProgress, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(day, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onEditGoals) { Text("Edit goals") }
    }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent meals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onClick = onViewMeals) { Text("View all") }
    }
    if (meals.isEmpty()) {
        Text("A fresh start", style = MaterialTheme.typography.titleMedium)
        Text("No meals registered for this nutrition day.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    meals.take(3).forEach { meal ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(meal.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("${amount(meal.caloriesKcal)} kcal", style = MaterialTheme.typography.labelLarge)
            }
            val time = runCatching { OffsetDateTime.parse(meal.eatenAt).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
            Text("${meal.mealType.label}  |  $time  |  ${amount(meal.proteinG)} g protein",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun CalorieRing(progress: NutritionWidgetProgress) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        Text("${amount(progress.caloriesKcal)} kcal eaten", style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(progress = { progress.caloriesProgress / 100f }, modifier = Modifier.fillMaxWidth())
        return
    }
    Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { progress.caloriesProgress / 100f },
            modifier = Modifier.fillMaxSize(), strokeWidth = 9.dp,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(amount(progress.caloriesKcal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("kcal eaten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalorieBalance(left: Double, goal: Double) {
    Text(amount(abs(left)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Text(if (left >= 0) "kcal remaining" else "kcal over goal", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Text("${amount(goal)} kcal daily goal", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NutritionMacro(label: String, value: Double, goal: Double, percent: Int, modifier: Modifier) {
    val color = if (label == "Protein") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Text("${amount(value)} g", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = color)
        Text("of ${amount(goal)} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
