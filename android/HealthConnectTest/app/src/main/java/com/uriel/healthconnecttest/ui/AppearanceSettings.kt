package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uriel.healthconnecttest.data.*
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceSettings() {
    val context = LocalContext.current.applicationContext
    val mode by remember { AppearancePreferences.mode(context) }.collectAsState(initial = AppearanceMode.System)
    val scope = rememberCoroutineScope()
    Text("Appearance", style = MaterialTheme.typography.titleMedium)
    AppearanceMode.entries.forEach { item ->
        Surface(onClick = { scope.launch { AppearancePreferences.setMode(context, item) } }, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == item, onClick = { scope.launch { AppearancePreferences.setMode(context, item) } })
                Text(if (item == AppearanceMode.System) "Follow system" else "${item.name} mode", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text("Nutrition: kcal and grams · Distance: km · Weight: kg", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
