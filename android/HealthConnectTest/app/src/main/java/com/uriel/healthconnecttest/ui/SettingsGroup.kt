package com.uriel.healthconnecttest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsGroup(
    id: String,
    title: String,
    summary: String,
    openGroup: String,
    onGroupChange: (String) -> Unit,
    attention: Boolean = false,
    content: @Composable () -> Unit
) {
    val expanded = openGroup == id
    val savedContent = rememberSaveableStateHolder()
    val update = id == "updates"
    val fill = if (update) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Surface(Modifier.fillMaxWidth(), color = fill, shape = RoundedCornerShape(if (update) 12.dp else 0.dp)) {
      Column {
        Surface(
            onClick = { onGroupChange(if (expanded) "" else id) },
            color = fill,
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(when (id) {
                    "updates" -> CompanionIcons.Update
                    "account" -> CompanionIcons.User
                    "permissions" -> CompanionIcons.Heart
                    "goals" -> CompanionIcons.Target
                    "preferences" -> CompanionIcons.Settings
                    else -> CompanionIcons.Info
                }, null, Modifier.size(24.dp), tint = if (update) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = if (id == "account" && summary == "Connected") NutritionGreen else if (attention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (attention) Badge(containerColor = MaterialTheme.colorScheme.error)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else if (update) Icons.Default.KeyboardArrowDown else CompanionIcons.Chevron,
                    contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (expanded) savedContent.SaveableStateProvider(id) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
        if (!update) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
      }
    }
}
