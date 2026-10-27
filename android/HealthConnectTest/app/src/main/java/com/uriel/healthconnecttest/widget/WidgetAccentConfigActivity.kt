package com.uriel.healthconnecttest.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uriel.healthconnecttest.ui.theme.HealthConnectTestTheme
import kotlinx.coroutines.launch

class WidgetAccentConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            HealthConnectTestTheme {
                WidgetAccentConfigScreen(
                    onApply = { color -> saveAccentAndFinish(color) }
                )
            }
        }
    }

    private fun saveAccentAndFinish(color: Int) {
        lifecycleScope.launch {
            WidgetAccentStore.setAccentColor(applicationContext, color)
            NutritionWidgetsUpdater.updateAll(applicationContext)
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}

@Composable
private fun WidgetAccentConfigScreen(onApply: (Int) -> Unit) {
    val context = LocalContext.current
    val choices = remember {
        listOf(
            AccentChoice("Mint", Color(0xFF73DF8D)),
            AccentChoice("Blue", Color(0xFF66B7FF)),
            AccentChoice("Amber", Color(0xFFFFC857)),
            AccentChoice("Rose", Color(0xFFFF7AAE)),
            AccentChoice("Violet", Color(0xFFB38CFF)),
            AccentChoice("Cyan", Color(0xFF50D5D2))
        )
    }
    var selectedColor by remember { mutableIntStateOf(choices.first().color.toArgb()) }

    LaunchedEffect(Unit) {
        selectedColor = WidgetAccentStore.getAccentColor(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Widget accent",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose the color used by nutrition widgets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                choices.forEach { choice ->
                    AccentSwatch(
                        choice = choice,
                        selected = selectedColor == choice.color.toArgb(),
                        onClick = { selectedColor = choice.color.toArgb() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onApply(selectedColor) }
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    choice: AccentChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(choice.color)
                .border(3.dp, borderColor, CircleShape)
                .clickable(onClick = onClick)
        )
        Text(
            text = choice.name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private data class AccentChoice(
    val name: String,
    val color: Color
)
