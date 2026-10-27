package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.sync.ServerMeal
import com.uriel.healthconnecttest.widget.NutritionWidgetProgress

@Composable
fun NutritionOverview(progress: NutritionWidgetProgress?, day: String, meals: List<ServerMeal>,
    loading: Boolean, message: String, onRefresh: () -> Unit, onViewMeals: () -> Unit) {
    Text("Today's nutrition", style = MaterialTheme.typography.headlineSmall)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (progress == null) {
        Text(if (message.isNotBlank()) message else "Sign in to your dashboard in Settings to load nutrition.")
    } else {
        val left = progress.caloriesGoal - progress.caloriesKcal
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${kotlin.math.abs(left).toInt()} kcal", style = MaterialTheme.typography.headlineLarge)
                Text(if (left >= 0) "remaining" else "over daily goal", style = MaterialTheme.typography.titleMedium)
                Text("${progress.caloriesKcal.toInt()} eaten / ${progress.caloriesGoal.toInt()} goal")
                LinearProgressIndicator(progress = { progress.caloriesProgress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        NutritionMacro("Protein", progress.proteinG, progress.proteinGoal, progress.proteinProgress)
        NutritionMacro("Fiber", progress.fiberG, progress.fiberGoal, progress.fiberProgress)
        Text(day, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        Text("Recent meals", style = MaterialTheme.typography.titleMedium)
        if (meals.isEmpty()) Text("No meals in this nutrition day")
        meals.take(3).forEach { meal ->
            ListItem(headlineContent = { Text(meal.title) },
                supportingContent = { Text(meal.mealType.label) },
                trailingContent = { Text("${meal.caloriesKcal.toInt()} kcal") })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        TextButton(onClick = onViewMeals) { Text("All meals") }
    }
    HorizontalDivider()
}

@Composable
private fun NutritionMacro(label: String, amount: Double, goal: Double, percent: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label: ${amount.toInt()} / ${goal.toInt()} g", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(),
            color = if (label == "Protein") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
    }
}
