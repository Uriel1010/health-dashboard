package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.sync.ServerMeal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.ZoneId

internal object CompanionIcons {
    private fun outline(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    val Food = outline("Food", "M5 3v6c0 3 5 3 5 0V3 M7.5 3v18 M17 21V3c-4 3-4 9 0 9")
    val Home = outline("Home", "M3 10 12 3 21 10 M5 9v12h5v-7h4v7h5V9")
    val Heart = outline("Heart", "M12 21 3.8 12.5C-2 6 7 0 12 7 17 0 26 6 20.2 12.5Z")
    val Chart = outline("Trends", "M4 20v-5 M10 20v-9 M16 20V8 M22 20V3 M3 10l6-4 6 1 6-5")
    val Settings = outline("Settings", "M9 3h6l1 3 3 1 2 5-2 4-3 1-1 4H9l-1-4-3-1-2-4 2-5 3-1Z M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0")
    val User = outline("Account", "M16 6a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 22v-3c0-7 16-7 16 0v3")
    val Target = outline("Goals", "M20 12a8 8 0 1 1-8-8 M16 12a4 4 0 1 1-4-4 M12 12 22 2 M17 2h5v5")
    val Update = outline("Update", "M12 21V3 M5 10l7-7 7 7")
    val Calendar = outline("Calendar", "M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2 M7 2v5 M17 2v5 M3 10h18 M7 14h2 M13 14h2 M7 17h2")
    val Cloud = outline("Sync", "M6 18a5 5 0 0 1 0-10 6 6 0 0 1 12 0 5 5 0 0 1 0 10 M12 18V9 M8 13l4-4 4 4")
    val Info = outline("About", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 11v6 M12 7v.2")
    val Chevron = outline("Open", "M9 5l7 7-7 7")
    val Back = outline("Back", "M14 5l-7 7 7 7")
    val Flame = outline("Calories", "M12 2c1 7 7 7 7 13a7 7 0 0 1-14 0c0-4 3-5 4-9 0 6 3 5 3-4Z")
    val Steps = outline("Steps", "M8 3c-3-2-5 4-3 7 2 3 5 0 3-7 M17 6c3-2 5 4 3 7-2 3-5 0-3-7 M6 15v5 M17 18v4")
    val Pin = outline("Distance", "M12 22S4 13 4 9a8 8 0 0 1 16 0c0 4-8 13-8 13Z M15 9a3 3 0 1 1-6 0 3 3 0 0 1 6 0")
    val Bowl = outline("Nutrition", "M3 9c0-3 18-3 18 0S3 12 3 9 M3 9l3 10h12l3-10 M8 5l1-3 M14 5l1-3")
}

internal val NutritionGreen = Color(0xFF45AF54)
internal val NutritionTeal = Color(0xFF329BA7)
internal val NutritionOrange = Color(0xFFED9A22)

@Composable
internal fun ReferenceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun MealIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(44.dp).background(Color(0xFFF3EDDF), RoundedCornerShape(9.dp))) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Color(0xFFE1D3B8), size.width * .43f, center)
        drawCircle(Color(0xFFFFFBF0), size.width * .38f, center)
        listOf(.30f to .35f, .55f to .28f, .68f to .48f, .30f to .62f, .53f to .68f).forEachIndexed { index, point ->
            drawCircle(if (index % 2 == 0) Color(0xFF6C9848) else Color(0xFFDC953D), size.width * .11f,
                Offset(size.width * point.first, size.height * point.second))
        }
        drawCircle(Color(0xFFB7753D), size.width * .105f, center)
    }
}

@Composable
internal fun ReferenceMealRow(meal: ServerMeal, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MealIllustration()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(meal.title, style = MaterialTheme.typography.titleSmall)
            val time = runCatching { mealDisplayTime(meal.eatenAt).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault("")
            Text("${meal.mealType.label} · $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${meal.caloriesKcal.toInt()} kcal", style = MaterialTheme.typography.labelMedium)
            Text("P: ${meal.proteinG.toInt()} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun NutritionError(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CompanionIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Could not refresh nutrition data", style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
internal fun SyncDetailRow(label: String, value: String, good: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (good == true) androidx.compose.material.icons.Icons.Default.CheckCircle else CompanionIcons.Info,
            null, Modifier.size(18.dp), tint = if (good == true) NutritionGreen else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.widthIn(max = 180.dp), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun MealDateTimeField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val local = runCatching { mealDisplayTime(value).toLocalDateTime() }.getOrElse {
        runCatching { LocalDateTime.parse(value) }.getOrDefault(LocalDateTime.now())
    }
    Text("Eaten at", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = {
        android.app.DatePickerDialog(context, { _, year, month, day ->
            android.app.TimePickerDialog(context, { _, hour, minute ->
                onChange(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }, local.hour, local.minute, false).show()
        }, local.year, local.monthValue - 1, local.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(8.dp)) {
        Icon(CompanionIcons.Calendar, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        Text(local.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy, h:mm a")), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ConfigureDialogSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
